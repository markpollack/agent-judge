package org.springframework.samples.petclinic.scheduling.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.samples.petclinic.scheduling.model.AuditEventType;
import org.springframework.samples.petclinic.scheduling.model.ClinicSettings;
import org.springframework.samples.petclinic.scheduling.model.RequestStatus;
import org.springframework.samples.petclinic.scheduling.model.Reservation;
import org.springframework.samples.petclinic.scheduling.model.ReservationStatus;
import org.springframework.samples.petclinic.scheduling.model.ReservationType;
import org.springframework.samples.petclinic.scheduling.model.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.model.StaffClaim;
import org.springframework.samples.petclinic.scheduling.repository.ReservationRepository;
import org.springframework.samples.petclinic.scheduling.repository.SchedulingRequestRepository;
import org.springframework.samples.petclinic.scheduling.repository.StaffClaimRepository;
import org.springframework.samples.petclinic.scheduling.service.ClinicSettingsService;
import org.springframework.samples.petclinic.security.Account;
import org.springframework.samples.petclinic.security.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service("schedulingLifecycleProcessor")
public class LifecycleProcessor {

	private static final Logger log = LoggerFactory.getLogger(LifecycleProcessor.class);

	private final RetentionService retentionService;

	private final SchedulingRequestRepository schedulingRequestRepository;

	private final ReservationRepository reservationRepository;

	private final StaffClaimRepository staffClaimRepository;

	private final AccountRepository accountRepository;

	private final LockCoordinator lockCoordinator;

	private final AuditService auditService;

	private final NotificationService notificationService;

	private final ClinicSettingsService clinicSettingsService;

	private final SchedulingTimeService timeService;

	public LifecycleProcessor(RetentionService retentionService,
			SchedulingRequestRepository schedulingRequestRepository, ReservationRepository reservationRepository,
			StaffClaimRepository staffClaimRepository, AccountRepository accountRepository,
			LockCoordinator lockCoordinator, AuditService auditService, NotificationService notificationService,
			ClinicSettingsService clinicSettingsService, SchedulingTimeService timeService) {
		this.retentionService = retentionService;
		this.schedulingRequestRepository = schedulingRequestRepository;
		this.reservationRepository = reservationRepository;
		this.staffClaimRepository = staffClaimRepository;
		this.accountRepository = accountRepository;
		this.lockCoordinator = lockCoordinator;
		this.auditService = auditService;
		this.notificationService = notificationService;
		this.clinicSettingsService = clinicSettingsService;
		this.timeService = timeService;
	}

	public void processOverdueDeadlines() {
		Instant now = timeService.now();

		// 1. Sensitive Data Retention Purges
		try {
			retentionService.purgeOverdueRequests();
		}
		catch (Exception e) {
			log.error("Error during retention purge cycle", e);
		}

		// 2. Overdue Fallback Deadlines (Fallback before offer precedence per RULE-4)
		try {
			List<Integer> expiredFallbackIds = schedulingRequestRepository.findExpiredFallbackRequestIds(now,
					PageRequest.of(0, 50));
			for (Integer reqId : expiredFallbackIds) {
				try {
					processOverdueFallback(reqId);
				}
				catch (Exception e) {
					log.error("Error processing overdue fallback for request ID: {}", reqId, e);
				}
			}
		}
		catch (Exception e) {
			log.error("Error querying expired fallbacks", e);
		}

		// 3. Overdue Guided Holds
		try {
			List<Integer> expiredHoldIds = reservationRepository.findExpiredGuidedHoldIds(now, PageRequest.of(0, 50));
			for (Integer holdId : expiredHoldIds) {
				try {
					processOverdueGuidedHold(holdId);
				}
				catch (Exception e) {
					log.error("Error processing overdue guided hold for reservation ID: {}", holdId, e);
				}
			}
		}
		catch (Exception e) {
			log.error("Error querying expired guided holds", e);
		}

		// 4. Overdue Staff Offers
		try {
			List<Integer> expiredOfferIds = reservationRepository.findExpiredStaffOfferIds(now, PageRequest.of(0, 50));
			for (Integer offerId : expiredOfferIds) {
				try {
					processOverdueStaffOffer(offerId);
				}
				catch (Exception e) {
					log.error("Error processing overdue staff offer for reservation ID: {}", offerId, e);
				}
			}
		}
		catch (Exception e) {
			log.error("Error querying expired staff offers", e);
		}

		// 5. Overdue Account Locks
		try {
			List<Integer> expiredAccountIds = accountRepository.findExpiredLockedAccountIds(now, PageRequest.of(0, 50));
			for (Integer accountId : expiredAccountIds) {
				try {
					processOverdueAccountLock(accountId);
				}
				catch (Exception e) {
					log.error("Error unlocking account ID: {}", accountId, e);
				}
			}
		}
		catch (Exception e) {
			log.error("Error querying locked accounts", e);
		}
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void processOverdueFallback(Integer requestId) {
		SchedulingRequest initial = schedulingRequestRepository.findById(requestId).orElse(null);
		if (initial == null || (initial.getStatus() != RequestStatus.STAFF_QUEUED
				&& initial.getStatus() != RequestStatus.STAFF_OFFERED)) {
			return;
		}

		Instant now = timeService.now();
		if (initial.getFallbackDeadline() == null || initial.getFallbackDeadline().isAfter(now)) {
			return;
		}

		Integer ownerId = initial.getOwner() != null ? initial.getOwner().getId() : null;
		Integer petId = initial.getPet() != null ? initial.getPet().getId() : null;
		Integer vetId = initial.getPreferredVet() != null ? initial.getPreferredVet().getId() : null;

		lockCoordinator.lockResources(ownerId != null ? List.of(ownerId) : null, petId != null ? List.of(petId) : null,
				vetId != null ? List.of(vetId) : null, List.of(requestId), null, null);

		SchedulingRequest locked = schedulingRequestRepository.findById(requestId).orElse(null);
		if (locked == null || (locked.getStatus() != RequestStatus.STAFF_QUEUED
				&& locked.getStatus() != RequestStatus.STAFF_OFFERED)) {
			return;
		}

		if (locked.getFallbackDeadline() == null || locked.getFallbackDeadline().isAfter(now)) {
			return;
		}

		// Expire active reservations
		List<Reservation> reservations = reservationRepository.findByRequestId(requestId);
		for (Reservation r : reservations) {
			if (r.getStatus() == ReservationStatus.ACTIVE) {
				r.setStatus(ReservationStatus.EXPIRED);
				reservationRepository.save(r);
				auditService.recordEvent(AuditEventType.OFFER_EXPIRED, "SYSTEM", "SYSTEM", "RESERVATION",
						String.valueOf(r.getId()), "FALLBACK_DEADLINE_EXPIRED",
						Map.of("requestId", String.valueOf(requestId)));
			}
		}

		// Delete staff claim
		Optional<StaffClaim> claimOpt = staffClaimRepository.findByRequestId(requestId);
		claimOpt.ifPresent(staffClaimRepository::delete);

		locked.setStatus(RequestStatus.EXPIRED);
		if (locked.getRetentionDeadline() == null) {
			ClinicSettings settings = clinicSettingsService.getSettings();
			locked.setRetentionDeadline(now.plus(Duration.ofDays(settings.getSensitiveDataRetentionDays())));
		}
		schedulingRequestRepository.save(locked);

		auditService.recordEvent(AuditEventType.REQUEST_EXPIRED, "SYSTEM", "SYSTEM", "SCHEDULING_REQUEST",
				String.valueOf(requestId), "FALLBACK_DEADLINE_EXPIRED", Map.of("requestId", String.valueOf(requestId)));

		notificationService.sendNotification(locked.getOwner(), "notification.offerExpired.title",
				"notification.offerExpired.message", "", "/scheduling/requests/" + requestId);

		log.info("Expired fallback request ID: {}", requestId);
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void processOverdueGuidedHold(Integer reservationId) {
		Reservation initial = reservationRepository.findById(reservationId).orElse(null);
		if (initial == null || initial.getStatus() != ReservationStatus.ACTIVE
				|| initial.getReservationType() != ReservationType.GUIDED_HOLD) {
			return;
		}

		Instant now = timeService.now();
		if (initial.getExpiresAt().isAfter(now)) {
			return;
		}

		Integer ownerId = initial.getOwner() != null ? initial.getOwner().getId() : null;
		Integer petId = initial.getPet() != null ? initial.getPet().getId() : null;
		Integer vetId = initial.getVet() != null ? initial.getVet().getId() : null;
		Integer reqId = initial.getRequest() != null ? initial.getRequest().getId() : null;

		lockCoordinator.lockResources(ownerId != null ? List.of(ownerId) : null, petId != null ? List.of(petId) : null,
				vetId != null ? List.of(vetId) : null, reqId != null ? List.of(reqId) : null, null,
				List.of(reservationId));

		Reservation lockedRes = reservationRepository.findById(reservationId).orElse(null);
		if (lockedRes == null || lockedRes.getStatus() != ReservationStatus.ACTIVE
				|| lockedRes.getReservationType() != ReservationType.GUIDED_HOLD
				|| lockedRes.getExpiresAt().isAfter(now)) {
			return;
		}

		lockedRes.setStatus(ReservationStatus.EXPIRED);
		reservationRepository.save(lockedRes);

		if (reqId != null) {
			SchedulingRequest lockedReq = schedulingRequestRepository.findById(reqId).orElse(null);
			if (lockedReq != null && lockedReq.getStatus() == RequestStatus.SLOT_HELD) {
				lockedReq.setStatus(RequestStatus.MATCHING);
				schedulingRequestRepository.save(lockedReq);
			}
		}

		auditService.recordEvent(AuditEventType.HOLD_EXPIRED, "SYSTEM", "SYSTEM", "RESERVATION",
				String.valueOf(reservationId), "HOLD_TIMEOUT",
				Map.of("requestId", String.valueOf(reqId), "reservationId", String.valueOf(reservationId)));

		log.info("Expired guided hold reservation ID: {}", reservationId);
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void processOverdueStaffOffer(Integer reservationId) {
		Reservation initial = reservationRepository.findById(reservationId).orElse(null);
		if (initial == null || initial.getStatus() != ReservationStatus.ACTIVE
				|| initial.getReservationType() != ReservationType.STAFF_OFFER) {
			return;
		}

		Instant now = timeService.now();
		if (initial.getExpiresAt().isAfter(now)) {
			return;
		}

		Integer ownerId = initial.getOwner() != null ? initial.getOwner().getId() : null;
		Integer petId = initial.getPet() != null ? initial.getPet().getId() : null;
		Integer vetId = initial.getVet() != null ? initial.getVet().getId() : null;
		Integer reqId = initial.getRequest() != null ? initial.getRequest().getId() : null;

		lockCoordinator.lockResources(ownerId != null ? List.of(ownerId) : null, petId != null ? List.of(petId) : null,
				vetId != null ? List.of(vetId) : null, reqId != null ? List.of(reqId) : null, null,
				List.of(reservationId));

		Reservation lockedRes = reservationRepository.findById(reservationId).orElse(null);
		if (lockedRes == null || lockedRes.getStatus() != ReservationStatus.ACTIVE
				|| lockedRes.getReservationType() != ReservationType.STAFF_OFFER
				|| lockedRes.getExpiresAt().isAfter(now)) {
			return;
		}

		lockedRes.setStatus(ReservationStatus.EXPIRED);
		reservationRepository.save(lockedRes);

		if (reqId != null) {
			SchedulingRequest lockedReq = schedulingRequestRepository.findById(reqId).orElse(null);
			if (lockedReq != null && lockedReq.getStatus() == RequestStatus.STAFF_OFFERED) {
				if (lockedReq.getFallbackDeadline() != null && !lockedReq.getFallbackDeadline().isAfter(now)) {
					lockedReq.setStatus(RequestStatus.EXPIRED);
					if (lockedReq.getRetentionDeadline() == null) {
						ClinicSettings settings = clinicSettingsService.getSettings();
						lockedReq
							.setRetentionDeadline(now.plus(Duration.ofDays(settings.getSensitiveDataRetentionDays())));
					}
					schedulingRequestRepository.save(lockedReq);
					auditService.recordEvent(AuditEventType.REQUEST_EXPIRED, "SYSTEM", "SYSTEM", "SCHEDULING_REQUEST",
							String.valueOf(reqId), "FALLBACK_DEADLINE_EXPIRED",
							Map.of("requestId", String.valueOf(reqId)));
				}
				else {
					lockedReq.setStatus(RequestStatus.STAFF_QUEUED);
					schedulingRequestRepository.save(lockedReq);
					notificationService.sendNotification(lockedReq.getOwner(), "notification.offerExpired.title",
							"notification.offerExpired.message", "", "/scheduling/requests/" + reqId);
				}
			}
		}

		auditService.recordEvent(AuditEventType.OFFER_EXPIRED, "SYSTEM", "SYSTEM", "RESERVATION",
				String.valueOf(reservationId), "OFFER_TIMEOUT",
				Map.of("requestId", String.valueOf(reqId), "reservationId", String.valueOf(reservationId)));

		log.info("Expired staff offer reservation ID: {}", reservationId);
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void processOverdueAccountLock(Integer accountId) {
		Account lockedAccount = accountRepository.findByIdForUpdate(accountId).orElse(null);
		if (lockedAccount == null || lockedAccount.getLockedUntil() == null) {
			return;
		}

		Instant now = timeService.now();
		if (lockedAccount.getLockedUntil().isAfter(now)) {
			return;
		}

		lockedAccount.setFailedLoginCount(0);
		lockedAccount.setLockedUntil(null);
		accountRepository.save(lockedAccount);

		auditService.recordEvent(AuditEventType.ACCOUNT_UNLOCKED, "SYSTEM", "SYSTEM", "ACCOUNT",
				String.valueOf(accountId), "LOCK_TIMEOUT_EXPIRED", Map.of("username", lockedAccount.getUsername()));

		log.info("Unlocked account ID: {} after lockout expiration", accountId);
	}

}
