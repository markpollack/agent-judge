package org.springframework.samples.petclinic.scheduling.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.samples.petclinic.scheduling.model.Appointment;
import org.springframework.samples.petclinic.scheduling.model.AppointmentStatus;
import org.springframework.samples.petclinic.scheduling.model.AuditEventType;
import org.springframework.samples.petclinic.scheduling.model.BookingSource;
import org.springframework.samples.petclinic.scheduling.model.CareType;
import org.springframework.samples.petclinic.scheduling.model.ClinicSettings;
import org.springframework.samples.petclinic.scheduling.model.RequestStatus;
import org.springframework.samples.petclinic.scheduling.model.Reservation;
import org.springframework.samples.petclinic.scheduling.model.ReservationStatus;
import org.springframework.samples.petclinic.scheduling.model.ReservationType;
import org.springframework.samples.petclinic.scheduling.model.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.model.StaffClaim;
import org.springframework.samples.petclinic.scheduling.repository.AppointmentRepository;
import org.springframework.samples.petclinic.scheduling.repository.ClinicSettingsRepository;
import org.springframework.samples.petclinic.scheduling.repository.ReservationRepository;
import org.springframework.samples.petclinic.scheduling.repository.SchedulingRequestRepository;
import org.springframework.samples.petclinic.scheduling.repository.StaffClaimRepository;
import org.springframework.samples.petclinic.vet.Specialty;
import org.springframework.samples.petclinic.vet.SpecialtyRepository;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StaffFallbackService {

	private final SchedulingRequestRepository schedulingRequestRepository;

	private final StaffClaimRepository staffClaimRepository;

	private final ReservationRepository reservationRepository;

	private final AppointmentRepository appointmentRepository;

	private final VetRepository vetRepository;

	private final SpecialtyRepository specialtyRepository;

	private final ClinicSettingsRepository clinicSettingsRepository;

	private final LockCoordinator lockCoordinator;

	private final LiveFeasibilityService liveFeasibilityService;

	private final SchedulingTimeService timeService;

	private final AuditService auditService;

	private final NotificationService notificationService;

	public StaffFallbackService(SchedulingRequestRepository schedulingRequestRepository,
			StaffClaimRepository staffClaimRepository, ReservationRepository reservationRepository,
			AppointmentRepository appointmentRepository, VetRepository vetRepository,
			SpecialtyRepository specialtyRepository, ClinicSettingsRepository clinicSettingsRepository,
			LockCoordinator lockCoordinator, LiveFeasibilityService liveFeasibilityService,
			SchedulingTimeService timeService, AuditService auditService, NotificationService notificationService) {
		this.schedulingRequestRepository = schedulingRequestRepository;
		this.staffClaimRepository = staffClaimRepository;
		this.reservationRepository = reservationRepository;
		this.appointmentRepository = appointmentRepository;
		this.vetRepository = vetRepository;
		this.specialtyRepository = specialtyRepository;
		this.clinicSettingsRepository = clinicSettingsRepository;
		this.lockCoordinator = lockCoordinator;
		this.liveFeasibilityService = liveFeasibilityService;
		this.timeService = timeService;
		this.auditService = auditService;
		this.notificationService = notificationService;
	}

	@Transactional(readOnly = true)
	public List<SchedulingRequest> getFallbackQueue() {
		return schedulingRequestRepository
			.findByStatusInOrderByUrgentDescFirstQueuedAtAsc(List.of(RequestStatus.STAFF_QUEUED));
	}

	@Transactional(readOnly = true)
	public Optional<StaffClaim> getClaimForRequest(Integer requestId) {
		return staffClaimRepository.findByRequestId(requestId);
	}

	@Transactional
	public StaffClaim claimRequest(Integer requestId, String staffUsername) {
		SchedulingRequest request = lockCoordinator.lockRequest(requestId);
		if (request == null) {
			throw new IllegalArgumentException("Request not found");
		}
		if (request.getStatus() != RequestStatus.STAFF_QUEUED && request.getStatus() != RequestStatus.STAFF_OFFERED) {
			throw new IllegalStateException("Request is not in staff fallback status");
		}

		Instant now = timeService.now();
		Optional<StaffClaim> claimOpt = staffClaimRepository.findByRequestId(requestId);

		StaffClaim claim;
		if (claimOpt.isEmpty()) {
			claim = new StaffClaim();
			claim.setRequest(request);
			claim.setStaffUsername(staffUsername);
			claim.setClaimedAt(now);
			claim.setLastActivityAt(now);
			claim.setReclaimableAfter(now.plus(Duration.ofMinutes(30)));
			staffClaimRepository.save(claim);

			auditService.recordEvent(AuditEventType.STAFF_CLAIM_ACQUIRED, staffUsername, "STAFF", "STAFF_CLAIM",
					String.valueOf(claim.getId()), "CLAIMED", Map.of("requestId", String.valueOf(requestId)));
		}
		else {
			claim = claimOpt.get();
			if (Objects.equals(claim.getStaffUsername(), staffUsername)) {
				claim.setLastActivityAt(now);
				claim.setReclaimableAfter(now.plus(Duration.ofMinutes(30)));
				staffClaimRepository.save(claim);
			}
			else {
				if (now.isBefore(claim.getReclaimableAfter())) {
					throw new IllegalStateException("Request is actively claimed by " + claim.getStaffUsername());
				}
				String previousClaimant = claim.getStaffUsername();
				claim.setStaffUsername(staffUsername);
				claim.setClaimedAt(now);
				claim.setLastActivityAt(now);
				claim.setReclaimableAfter(now.plus(Duration.ofMinutes(30)));
				staffClaimRepository.save(claim);

				auditService.recordEvent(AuditEventType.STAFF_CLAIM_RECLAIMED, staffUsername, "STAFF", "STAFF_CLAIM",
						String.valueOf(claim.getId()), "RECLAIMED", Map.of("requestId", String.valueOf(requestId),
								"previousClaimant", previousClaimant != null ? previousClaimant : ""));
			}
		}

		return claim;
	}

	@Transactional
	public void releaseClaim(Integer requestId, String staffUsername) {
		SchedulingRequest request = lockCoordinator.lockRequest(requestId);
		if (request == null) {
			throw new IllegalArgumentException("Request not found");
		}

		Optional<StaffClaim> claimOpt = staffClaimRepository.findByRequestId(requestId);
		if (claimOpt.isPresent()) {
			StaffClaim claim = claimOpt.get();
			if (Objects.equals(claim.getStaffUsername(), staffUsername)) {
				staffClaimRepository.delete(claim);
				auditService.recordEvent(AuditEventType.STAFF_CLAIM_RELEASED, staffUsername, "STAFF", "STAFF_CLAIM",
						String.valueOf(claim.getId()), "RELEASED", Map.of("requestId", String.valueOf(requestId)));
			}
		}
	}

	@Transactional
	public void updateStructuredInterpretation(Integer requestId, Integer durationMinutes, CareType careType,
			Integer requiredSpecialtyId, Integer preferredVetId, Instant preferredStartWindow,
			Instant preferredEndWindow, String staffUsername) {
		SchedulingRequest request = lockCoordinator.lockRequest(requestId);
		if (request == null) {
			throw new IllegalArgumentException("Request not found");
		}

		verifyClaimant(requestId, staffUsername);

		ClinicSettings settings = clinicSettingsRepository.findById(1).orElseGet(ClinicSettings::new);

		// Validate every field first so that, per UC5-AC13, a single invalid field
		// rejects the whole edit and fully preserves the prior structured
		// interpretation instead of partially applying/clamping values.
		if (durationMinutes != null && (durationMinutes < settings.getMinDurationMinutes()
				|| durationMinutes > settings.getMaxDurationMinutes() || durationMinutes % 15 != 0)) {
			throw new IllegalArgumentException("Duration must be between " + settings.getMinDurationMinutes() + " and "
					+ settings.getMaxDurationMinutes() + " minutes in 15-minute increments");
		}

		Specialty specialty = null;
		if (requiredSpecialtyId != null) {
			specialty = specialtyRepository.findById(requiredSpecialtyId)
				.orElseThrow(() -> new IllegalArgumentException("Specialty not found"));
			if (!specialty.isActive()) {
				throw new IllegalArgumentException("Specialty is inactive");
			}
		}

		Vet vet = null;
		if (preferredVetId != null) {
			vet = vetRepository.findById(preferredVetId)
				.orElseThrow(() -> new IllegalArgumentException("Vet not found"));
			if (!vet.isActive()) {
				throw new IllegalArgumentException("Vet is inactive");
			}
		}

		if (preferredStartWindow != null && preferredEndWindow != null
				&& !preferredStartWindow.isBefore(preferredEndWindow)) {
			throw new IllegalArgumentException("Preferred start window must be before the preferred end window");
		}

		// All validations passed; apply the edits.
		if (durationMinutes != null) {
			request.setDurationMinutes(durationMinutes);
		}

		if (careType != null) {
			request.setCareType(careType);
		}

		if (requiredSpecialtyId != null) {
			request.setRequiredSpecialty(specialty);
		}
		else if (careType == CareType.GENERAL) {
			request.setRequiredSpecialty(null);
		}

		if (preferredVetId != null) {
			request.setPreferredVet(vet);
		}
		else {
			request.setPreferredVet(null);
		}

		if (preferredStartWindow != null) {
			request.setPreferredStartWindow(preferredStartWindow);
		}
		if (preferredEndWindow != null) {
			request.setPreferredEndWindow(preferredEndWindow);
		}

		schedulingRequestRepository.save(request);
		refreshClaimActivity(requestId, staffUsername);

		auditService.recordEvent(AuditEventType.STAFF_INTERPRETATION_UPDATED, staffUsername, "STAFF",
				"SCHEDULING_REQUEST", String.valueOf(requestId), "STAFF_UPDATED", Map.of("careType",
						request.getCareType().name(), "duration", String.valueOf(request.getDurationMinutes())));
	}

	@Transactional
	public Reservation createStaffOffer(Integer requestId, Integer vetId, Instant startTime, String staffUsername) {
		SchedulingRequest request = lockCoordinator.lockRequest(requestId);
		if (request == null) {
			throw new IllegalArgumentException("Request not found");
		}

		verifyClaimant(requestId, staffUsername);

		Integer ownerId = request.getOwner().getId();
		Integer petId = request.getPet().getId();

		lockCoordinator.lockResources(List.of(ownerId), List.of(petId), List.of(vetId), List.of(requestId), null, null);

		Vet vet = vetRepository.findById(vetId).orElseThrow(() -> new IllegalArgumentException("Vet not found"));

		int duration = request.getDurationMinutes() != null ? request.getDurationMinutes() : 30;
		Instant endTime = startTime.plus(Duration.ofMinutes(duration));

		boolean feasible = liveFeasibilityService.checkFeasibility(vetId, ownerId, petId, startTime, endTime,
				request.getCareType(), request.getRequiredSpecialty(), request.getOwnerHorizonEnd(), requestId);

		if (!feasible) {
			throw new IllegalStateException("Proposed slot is not feasible (violates calendar, availability, or DST)");
		}

		// Revoke prior active reservations
		List<Reservation> reservations = reservationRepository.findByRequestId(requestId);
		for (Reservation r : reservations) {
			if (r.getStatus() == ReservationStatus.ACTIVE) {
				r.setStatus(ReservationStatus.REVOKED);
				reservationRepository.save(r);
			}
		}

		Instant now = timeService.now();
		Instant expiresAt = now.plus(Duration.ofHours(24));
		if (request.getFallbackDeadline() != null && expiresAt.isAfter(request.getFallbackDeadline())) {
			expiresAt = request.getFallbackDeadline();
		}

		Reservation offer = new Reservation();
		offer.setRequest(request);
		offer.setOwner(request.getOwner());
		offer.setPet(request.getPet());
		offer.setVet(vet);
		offer.setReservationType(ReservationType.STAFF_OFFER);
		offer.setStatus(ReservationStatus.ACTIVE);
		offer.setStartTime(startTime);
		offer.setEndTime(endTime);
		offer.setExpiresAt(expiresAt);
		reservationRepository.save(offer);

		refreshClaimActivity(requestId, staffUsername);

		auditService.recordEvent(AuditEventType.STAFF_OFFER_ISSUED, staffUsername, "STAFF", "RESERVATION",
				String.valueOf(offer.getId()), "OFFER_ISSUED", Map.of("requestId", String.valueOf(requestId), "vetId",
						String.valueOf(vetId), "startTime", startTime.toString(), "expiresAt", expiresAt.toString()));

		notificationService.sendNotification(request.getOwner(), "notification.staffOffer.title",
				"notification.staffOffer.message", startTime.toString(), "/scheduling/requests/" + requestId);

		return offer;
	}

	@Transactional
	public void revokeStaffOffer(Integer requestId, Integer reservationId, String staffUsername) {
		verifyClaimant(requestId, staffUsername);

		Reservation reservation = reservationRepository.findById(reservationId)
			.orElseThrow(() -> new IllegalArgumentException("Reservation not found"));

		if (reservation.getStatus() == ReservationStatus.ACTIVE) {
			reservation.setStatus(ReservationStatus.REVOKED);
			reservationRepository.save(reservation);

			refreshClaimActivity(requestId, staffUsername);

			auditService.recordEvent(AuditEventType.STAFF_OFFER_REVOKED, staffUsername, "STAFF", "RESERVATION",
					String.valueOf(reservationId), "OFFER_REVOKED", Map.of("requestId", String.valueOf(requestId)));
		}
	}

	@Transactional
	public Appointment directBook(Integer requestId, Integer vetId, Instant startTime, String offlineReason,
			String staffUsername) {
		if (offlineReason == null || offlineReason.trim().isEmpty()) {
			throw new IllegalArgumentException("Offline reason is required for direct staff booking");
		}

		SchedulingRequest request = lockCoordinator.lockRequest(requestId);
		if (request == null) {
			throw new IllegalArgumentException("Request not found");
		}

		verifyClaimant(requestId, staffUsername);

		Integer ownerId = request.getOwner().getId();
		Integer petId = request.getPet().getId();

		lockCoordinator.lockResources(List.of(ownerId), List.of(petId), List.of(vetId), List.of(requestId), null, null);

		Vet vet = vetRepository.findById(vetId).orElseThrow(() -> new IllegalArgumentException("Vet not found"));

		int duration = request.getDurationMinutes() != null ? request.getDurationMinutes() : 30;
		Instant endTime = startTime.plus(Duration.ofMinutes(duration));

		boolean feasible = liveFeasibilityService.checkFeasibility(vetId, ownerId, petId, startTime, endTime,
				request.getCareType(), request.getRequiredSpecialty(), request.getOwnerHorizonEnd(), requestId);

		if (!feasible) {
			throw new IllegalStateException("Selected slot is not feasible (violates calendar, availability, or DST)");
		}

		// Clear active reservations
		List<Reservation> reservations = reservationRepository.findByRequestId(requestId);
		for (Reservation r : reservations) {
			if (r.getStatus() == ReservationStatus.ACTIVE) {
				r.setStatus(ReservationStatus.CLEARED);
				reservationRepository.save(r);
			}
		}

		Appointment appointment = new Appointment();
		appointment.setRequest(request);
		appointment.setOwner(request.getOwner());
		appointment.setPet(request.getPet());
		appointment.setVet(vet);
		appointment.setCareType(request.getCareType());
		appointment.setRequiredSpecialty(request.getRequiredSpecialty());
		appointment.setStartTime(startTime);
		appointment.setEndTime(endTime);
		appointment.setStatus(AppointmentStatus.BOOKED);
		appointment.setBookingSource(BookingSource.STAFF_DIRECT);
		appointment.setOfflineReason(offlineReason.trim());
		appointmentRepository.save(appointment);

		request.setStatus(RequestStatus.CONFIRMED);
		schedulingRequestRepository.save(request);

		// Delete staff claim
		Optional<StaffClaim> claimOpt = staffClaimRepository.findByRequestId(requestId);
		claimOpt.ifPresent(staffClaimRepository::delete);

		auditService.recordEvent(AuditEventType.APPOINTMENT_BOOKED, staffUsername, "STAFF", "APPOINTMENT",
				String.valueOf(appointment.getId()), "STAFF_DIRECT_BOOKED",
				Map.of("requestId", String.valueOf(requestId), "offlineReason", offlineReason.trim()));

		auditService.recordEvent(AuditEventType.REQUEST_CONFIRMED, staffUsername, "STAFF", "SCHEDULING_REQUEST",
				String.valueOf(requestId), "CONFIRMED", Map.of("appointmentId", String.valueOf(appointment.getId())));

		notificationService.sendNotification(request.getOwner(), "notification.appointmentConfirmed.title",
				"notification.appointmentConfirmed.message", appointment.getStartTime().toString(),
				"/appointments/" + appointment.getId());

		return appointment;
	}

	@Transactional
	public void cancelRequestByStaff(Integer requestId, String cancellationReason, String staffUsername) {
		if (cancellationReason == null || cancellationReason.trim().isEmpty()) {
			throw new IllegalArgumentException("Cancellation reason is required");
		}

		SchedulingRequest request = lockCoordinator.lockRequest(requestId);
		if (request == null) {
			throw new IllegalArgumentException("Request not found");
		}

		if (request.getStatus() == RequestStatus.CANCELLED || request.getStatus() == RequestStatus.CONFIRMED
				|| request.getStatus() == RequestStatus.EXPIRED) {
			return; // Idempotent
		}

		// Clear active reservations
		List<Reservation> reservations = reservationRepository.findByRequestId(requestId);
		for (Reservation r : reservations) {
			if (r.getStatus() == ReservationStatus.ACTIVE) {
				r.setStatus(ReservationStatus.CLEARED);
				reservationRepository.save(r);
			}
		}

		// Delete staff claim
		Optional<StaffClaim> claimOpt = staffClaimRepository.findByRequestId(requestId);
		claimOpt.ifPresent(staffClaimRepository::delete);

		request.setStatus(RequestStatus.CANCELLED);
		if (request.getRetentionDeadline() == null) {
			ClinicSettings settings = clinicSettingsRepository.findDefaultSettings().orElseGet(ClinicSettings::new);
			request.setRetentionDeadline(
					timeService.now().plus(Duration.ofDays(settings.getSensitiveDataRetentionDays())));
		}
		schedulingRequestRepository.save(request);

		auditService.recordEvent(AuditEventType.REQUEST_CANCELLED, staffUsername, "STAFF", "SCHEDULING_REQUEST",
				String.valueOf(requestId), "STAFF_CANCELLED", Map.of("reason", cancellationReason.trim()));

		notificationService.sendNotification(request.getOwner(), "notification.requestCancelled.title",
				"notification.requestCancelled.message", cancellationReason.trim(),
				"/scheduling/requests/" + requestId);
	}

	@Transactional
	public void expireFallback(Integer requestId) {
		SchedulingRequest request = lockCoordinator.lockRequest(requestId);
		if (request == null) {
			return;
		}

		Instant now = timeService.now();
		if (request.getStatus() == RequestStatus.STAFF_QUEUED && request.getFallbackDeadline() != null
				&& !now.isBefore(request.getFallbackDeadline())) {

			// Clear active reservations
			List<Reservation> reservations = reservationRepository.findByRequestId(requestId);
			for (Reservation r : reservations) {
				if (r.getStatus() == ReservationStatus.ACTIVE) {
					r.setStatus(ReservationStatus.EXPIRED);
					reservationRepository.save(r);
				}
			}

			// Delete staff claim
			Optional<StaffClaim> claimOpt = staffClaimRepository.findByRequestId(requestId);
			claimOpt.ifPresent(staffClaimRepository::delete);

			request.setStatus(RequestStatus.EXPIRED);
			if (request.getRetentionDeadline() == null) {
				ClinicSettings settings = clinicSettingsRepository.findDefaultSettings().orElseGet(ClinicSettings::new);
				request.setRetentionDeadline(
						timeService.now().plus(Duration.ofDays(settings.getSensitiveDataRetentionDays())));
			}
			schedulingRequestRepository.save(request);

			auditService.recordEvent(AuditEventType.REQUEST_EXPIRED, "system", "SYSTEM", "SCHEDULING_REQUEST",
					String.valueOf(requestId), "FALLBACK_DEADLINE_EXPIRED", Map.of());

			notificationService.sendNotification(request.getOwner(), "notification.offerExpired.title",
					"notification.offerExpired.message", "", "/scheduling/requests/" + requestId);
		}
	}

	private void verifyClaimant(Integer requestId, String staffUsername) {
		Optional<StaffClaim> claimOpt = staffClaimRepository.findByRequestId(requestId);
		if (claimOpt.isEmpty()) {
			throw new IllegalStateException("Request is not claimed");
		}
		StaffClaim claim = claimOpt.get();
		if (!Objects.equals(claim.getStaffUsername(), staffUsername)) {
			throw new IllegalStateException("Request is claimed by " + claim.getStaffUsername());
		}
	}

	private void refreshClaimActivity(Integer requestId, String staffUsername) {
		Optional<StaffClaim> claimOpt = staffClaimRepository.findByRequestId(requestId);
		if (claimOpt.isPresent()) {
			StaffClaim claim = claimOpt.get();
			if (Objects.equals(claim.getStaffUsername(), staffUsername)) {
				Instant now = timeService.now();
				claim.setLastActivityAt(now);
				claim.setReclaimableAfter(now.plus(Duration.ofMinutes(30)));
				staffClaimRepository.save(claim);
			}
		}
	}

}
