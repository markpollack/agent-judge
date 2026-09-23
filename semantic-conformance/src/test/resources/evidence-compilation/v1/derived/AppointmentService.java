package org.springframework.samples.petclinic.scheduling.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.owner.PetRepository;
import org.springframework.samples.petclinic.owner.Visit;
import org.springframework.samples.petclinic.owner.VisitRepository;
import org.springframework.samples.petclinic.scheduling.dto.AppointmentDto;
import org.springframework.samples.petclinic.scheduling.model.Appointment;
import org.springframework.samples.petclinic.scheduling.model.AppointmentStatus;
import org.springframework.samples.petclinic.scheduling.model.AuditEventType;
import org.springframework.samples.petclinic.scheduling.model.BookingSource;
import org.springframework.samples.petclinic.scheduling.model.CareType;
import org.springframework.samples.petclinic.scheduling.model.ClinicSettings;
import org.springframework.samples.petclinic.scheduling.model.ConflictStatus;
import org.springframework.samples.petclinic.scheduling.model.RequestStatus;
import org.springframework.samples.petclinic.scheduling.model.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.repository.AppointmentRepository;
import org.springframework.samples.petclinic.scheduling.repository.SchedulingRequestRepository;
import org.springframework.samples.petclinic.system.ResourceNotFoundException;
import org.springframework.samples.petclinic.vet.Specialty;
import org.springframework.samples.petclinic.vet.SpecialtyRepository;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppointmentService {

	private final AppointmentRepository appointmentRepository;

	private final OwnerRepository ownerRepository;

	private final PetRepository petRepository;

	private final VetRepository vetRepository;

	private final SpecialtyRepository specialtyRepository;

	private final SchedulingRequestRepository schedulingRequestRepository;

	private final VisitRepository visitRepository;

	private final LockCoordinator lockCoordinator;

	private final LiveFeasibilityService liveFeasibilityService;

	private final CalendarConflictService calendarConflictService;

	private final ClinicSettingsService clinicSettingsService;

	private final SchedulingTimeService timeService;

	private final AuditService auditService;

	private final NotificationService notificationService;

	public AppointmentService(AppointmentRepository appointmentRepository, OwnerRepository ownerRepository,
			PetRepository petRepository, VetRepository vetRepository, SpecialtyRepository specialtyRepository,
			SchedulingRequestRepository schedulingRequestRepository, VisitRepository visitRepository,
			LockCoordinator lockCoordinator, LiveFeasibilityService liveFeasibilityService,
			CalendarConflictService calendarConflictService, ClinicSettingsService clinicSettingsService,
			SchedulingTimeService timeService, AuditService auditService, NotificationService notificationService) {
		this.appointmentRepository = appointmentRepository;
		this.ownerRepository = ownerRepository;
		this.petRepository = petRepository;
		this.vetRepository = vetRepository;
		this.specialtyRepository = specialtyRepository;
		this.schedulingRequestRepository = schedulingRequestRepository;
		this.visitRepository = visitRepository;
		this.lockCoordinator = lockCoordinator;
		this.liveFeasibilityService = liveFeasibilityService;
		this.calendarConflictService = calendarConflictService;
		this.clinicSettingsService = clinicSettingsService;
		this.timeService = timeService;
		this.auditService = auditService;
		this.notificationService = notificationService;
	}

	@Transactional(readOnly = true)
	public List<AppointmentDto> getOwnerUpcomingAppointments(Integer ownerId) {
		if (ownerId == null) {
			return Collections.emptyList();
		}
		Instant now = timeService.now();
		List<Appointment> appts = appointmentRepository
			.findByOwnerIdAndStatusAndStartTimeGreaterThanEqualOrderByStartTimeAsc(ownerId, AppointmentStatus.BOOKED,
					now);
		return appts.stream().map(AppointmentDto::from).collect(Collectors.toList());
	}

	@Transactional(readOnly = true)
	public List<AppointmentDto> getAllOwnerAppointments(Integer ownerId) {
		if (ownerId == null) {
			return Collections.emptyList();
		}
		List<Appointment> appts = appointmentRepository.findByOwnerIdOrderByStartTimeDesc(ownerId);
		return appts.stream().map(AppointmentDto::from).collect(Collectors.toList());
	}

	@Transactional(readOnly = true)
	public AppointmentDto getAppointmentDtoForOwner(Integer appointmentId, Integer ownerId) {
		Appointment appt = appointmentRepository.findByIdAndOwnerId(appointmentId, ownerId)
			.orElseThrow(() -> new ResourceNotFoundException("Appointment not found"));
		return AppointmentDto.from(appt);
	}

	@Transactional(readOnly = true)
	public AppointmentDto getAppointmentDto(Integer appointmentId) {
		Appointment appt = appointmentRepository.findById(appointmentId)
			.orElseThrow(() -> new ResourceNotFoundException("Appointment not found"));
		return AppointmentDto.from(appt);
	}

	@Transactional
	public AppointmentDto cancelAppointmentByOwner(Integer appointmentId, Integer ownerId, String reason,
			String actorUsername) {
		Appointment initial = appointmentRepository.findByIdAndOwnerId(appointmentId, ownerId)
			.orElseThrow(() -> new ResourceNotFoundException("Appointment not found"));

		Integer petId = initial.getPet().getId();
		Integer vetId = initial.getVet().getId();
		Integer reqId = initial.getRequest() != null ? initial.getRequest().getId() : null;

		lockCoordinator.lockResources(List.of(ownerId), List.of(petId), List.of(vetId),
				(reqId != null ? List.of(reqId) : null), List.of(appointmentId), null);

		Appointment appointment = appointmentRepository.findById(appointmentId)
			.orElseThrow(() -> new ResourceNotFoundException("Appointment not found"));

		if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
			return AppointmentDto.from(appointment);
		}

		if (appointment.getStatus() == AppointmentStatus.COMPLETED
				|| appointment.getStatus() == AppointmentStatus.NO_SHOW) {
			throw new IllegalStateException("Cannot cancel a completed or no-show appointment");
		}

		Instant now = timeService.now();
		if (now.isAfter(appointment.getStartTime())) {
			throw new IllegalStateException("Cannot cancel an appointment at or after its scheduled start time");
		}

		appointment.setStatus(AppointmentStatus.CANCELLED);
		String actualReason = (reason != null && !reason.trim().isEmpty()) ? reason.trim() : "Cancelled by owner";
		appointment.setCancellationReason(actualReason);
		Appointment saved = appointmentRepository.save(appointment);

		SchedulingRequest req = saved.getRequest();
		if (req != null && req.getRetentionDeadline() == null) {
			ClinicSettings settings = clinicSettingsService.getSettings();
			req.setRetentionDeadline(now.plus(Duration.ofDays(settings.getSensitiveDataRetentionDays())));
			schedulingRequestRepository.save(req);
		}

		calendarConflictService.resolveConflictsForAppointment(appointmentId, ConflictStatus.CANCELLED, actorUsername);

		auditService.recordEvent(AuditEventType.APPOINTMENT_CANCELLED, actorUsername != null ? actorUsername : "owner",
				"OWNER", "APPOINTMENT", String.valueOf(saved.getId()), "CANCELLED",
				Map.of("reason", actualReason, "cancelledBy", "owner"));

		notificationService.createNotification(ownerId, "notification.appointmentCancelled.title",
				"notification.appointmentCancelled.message", "/scheduling/appointments/" + saved.getId());

		return AppointmentDto.from(saved);
	}

	@Transactional
	public AppointmentDto directBookByStaff(Integer ownerId, Integer petId, Integer vetId, Instant startTime,
			Integer durationMinutes, CareType careType, Integer requiredSpecialtyId, String offlineReason,
			String actorUsername) {

		if (offlineReason == null || offlineReason.trim().isEmpty()) {
			throw new IllegalArgumentException("Offline reason is required");
		}

		if (careType == null) {
			careType = CareType.GENERAL;
		}

		Specialty requiredSpecialty = null;
		if (careType == CareType.GENERAL) {
			if (requiredSpecialtyId != null) {
				throw new IllegalArgumentException("General care cannot have a required specialty");
			}
		}
		else if (careType == CareType.SPECIALTY) {
			if (requiredSpecialtyId == null) {
				throw new IllegalArgumentException("Specialty care requires a specialty");
			}
			requiredSpecialty = specialtyRepository.findById(requiredSpecialtyId)
				.orElseThrow(() -> new IllegalArgumentException("Specialty not found"));
			if (!requiredSpecialty.isActive()) {
				throw new IllegalArgumentException("Specialty is inactive");
			}
		}

		ClinicSettings settings = clinicSettingsService.getSettings();
		if (durationMinutes == null || durationMinutes <= 0 || durationMinutes % 15 != 0) {
			throw new IllegalArgumentException("Duration must be a positive multiple of 15 minutes");
		}
		if (durationMinutes < settings.getMinDurationMinutes() || durationMinutes > settings.getMaxDurationMinutes()) {
			throw new IllegalArgumentException("Duration out of clinic settings bounds");
		}

		if (startTime == null || startTime.getEpochSecond() % 900 != 0) {
			throw new IllegalArgumentException("Start time must be aligned to a 15-minute grid");
		}

		Instant nextGrid = timeService.quantizeToNext15MinuteBoundary(timeService.now());
		if (startTime.isBefore(nextGrid)) {
			throw new IllegalArgumentException("Start time must be in the future");
		}

		Instant staffHorizonEnd = timeService.calculateStaffHorizonEnd(timeService.now(),
				settings.getStaffBookingHorizonDays());
		if (!startTime.isBefore(staffHorizonEnd)) {
			throw new IllegalArgumentException("Start time exceeds staff horizon");
		}

		lockCoordinator.lockResources(List.of(ownerId), List.of(petId), List.of(vetId), null, null, null);

		Owner owner = ownerRepository.findById(ownerId)
			.orElseThrow(() -> new ResourceNotFoundException("Owner not found"));
		Pet pet = petRepository.findById(petId).orElseThrow(() -> new ResourceNotFoundException("Pet not found"));
		Vet vet = vetRepository.findById(vetId).orElseThrow(() -> new ResourceNotFoundException("Vet not found"));
		if (!vet.isActive()) {
			throw new IllegalStateException("Veterinarian is inactive");
		}

		List<SchedulingRequest> activeRequests = schedulingRequestRepository.findByPetIdAndStatusNotIn(petId,
				List.of(RequestStatus.CONFIRMED, RequestStatus.CANCELLED, RequestStatus.EXPIRED));
		if (!activeRequests.isEmpty()) {
			throw new IllegalStateException("Pet has an unresolved scheduling request");
		}

		Instant endTime = startTime.plus(Duration.ofMinutes(durationMinutes));

		boolean feasible = liveFeasibilityService.checkFeasibility(vetId, ownerId, petId, startTime, endTime, careType,
				requiredSpecialty, staffHorizonEnd, null);
		if (!feasible) {
			throw new IllegalStateException("Selected slot is not feasible or conflicts with existing availability");
		}

		Appointment appt = new Appointment();
		appt.setOwner(owner);
		appt.setPet(pet);
		appt.setVet(vet);
		appt.setCareType(careType);
		appt.setRequiredSpecialty(requiredSpecialty);
		appt.setStartTime(startTime);
		appt.setEndTime(endTime);
		appt.setStatus(AppointmentStatus.BOOKED);
		appt.setBookingSource(BookingSource.STAFF_DIRECT);
		appt.setOfflineReason(offlineReason.trim());

		Appointment saved = appointmentRepository.save(appt);

		auditService.recordEvent(AuditEventType.APPOINTMENT_BOOKED, actorUsername != null ? actorUsername : "staff",
				"STAFF", "APPOINTMENT", String.valueOf(saved.getId()), "BOOKED",
				Map.of("offlineReason", offlineReason.trim(), "bookingSource", "STAFF_DIRECT", "vetId", vetId,
						"ownerId", ownerId, "petId", petId));

		notificationService.createNotification(ownerId, "notification.appointmentBooked.title",
				"notification.appointmentBooked.message", "/scheduling/appointments/" + saved.getId());

		return AppointmentDto.from(saved);
	}

	@Transactional
	public AppointmentDto rescheduleAppointment(Integer appointmentId, Integer newVetId, Instant newStartTime,
			Integer newDurationMinutes, String reason, String actorUsername) {

		if (reason == null || reason.trim().isEmpty()) {
			throw new IllegalArgumentException("Reschedule reason is required");
		}

		Appointment initial = appointmentRepository.findById(appointmentId)
			.orElseThrow(() -> new ResourceNotFoundException("Appointment not found"));

		if (initial.getStatus() != AppointmentStatus.BOOKED) {
			throw new IllegalStateException("Cannot reschedule an appointment that is not BOOKED");
		}

		Integer ownerId = initial.getOwner().getId();
		Integer petId = initial.getPet().getId();
		Integer oldVetId = initial.getVet().getId();
		Set<Integer> vetIds = new HashSet<>(List.of(oldVetId, newVetId));
		Integer reqId = initial.getRequest() != null ? initial.getRequest().getId() : null;

		lockCoordinator.lockResources(List.of(ownerId), List.of(petId), vetIds, (reqId != null ? List.of(reqId) : null),
				List.of(appointmentId), null);

		Appointment appointment = appointmentRepository.findById(appointmentId)
			.orElseThrow(() -> new ResourceNotFoundException("Appointment not found"));

		if (appointment.getStatus() != AppointmentStatus.BOOKED) {
			throw new IllegalStateException("Cannot reschedule an appointment that is not BOOKED");
		}

		CareType careType = appointment.getCareType();
		Specialty requiredSpecialty = appointment.getRequiredSpecialty();

		int duration = (newDurationMinutes != null && newDurationMinutes > 0) ? newDurationMinutes
				: (int) Duration.between(appointment.getStartTime(), appointment.getEndTime()).toMinutes();

		ClinicSettings settings = clinicSettingsService.getSettings();
		if (duration % 15 != 0 || duration < settings.getMinDurationMinutes()
				|| duration > settings.getMaxDurationMinutes()) {
			throw new IllegalArgumentException("Duration out of clinic settings bounds");
		}

		if (newStartTime == null || newStartTime.getEpochSecond() % 900 != 0) {
			throw new IllegalArgumentException("Start time must be aligned to a 15-minute grid");
		}

		Instant nextGrid = timeService.quantizeToNext15MinuteBoundary(timeService.now());
		if (newStartTime.isBefore(nextGrid)) {
			throw new IllegalArgumentException("Start time must be in the future");
		}

		Instant staffHorizonEnd = timeService.calculateStaffHorizonEnd(timeService.now(),
				settings.getStaffBookingHorizonDays());
		if (!newStartTime.isBefore(staffHorizonEnd)) {
			throw new IllegalArgumentException("Start time exceeds staff horizon");
		}

		Instant newEndTime = newStartTime.plus(Duration.ofMinutes(duration));

		Vet newVet = vetRepository.findById(newVetId).orElseThrow(() -> new ResourceNotFoundException("Vet not found"));
		if (!newVet.isActive()) {
			throw new IllegalStateException("Veterinarian is inactive");
		}

		boolean feasible = liveFeasibilityService.checkFeasibility(newVetId, ownerId, petId, newStartTime, newEndTime,
				careType, requiredSpecialty, staffHorizonEnd, null, appointmentId);
		if (!feasible) {
			throw new IllegalStateException("New time slot is not feasible or conflicts with existing availability");
		}

		Instant priorStart = appointment.getStartTime();
		Instant priorEnd = appointment.getEndTime();
		Integer priorVetId = appointment.getVet().getId();

		appointment.setVet(newVet);
		appointment.setStartTime(newStartTime);
		appointment.setEndTime(newEndTime);
		appointment.setOfflineReason(reason.trim());
		Appointment saved = appointmentRepository.save(appointment);

		calendarConflictService.resolveConflictsForAppointment(appointmentId, ConflictStatus.RESCHEDULED,
				actorUsername);

		auditService.recordEvent(AuditEventType.APPOINTMENT_RESCHEDULED,
				actorUsername != null ? actorUsername : "staff", "STAFF", "APPOINTMENT", String.valueOf(saved.getId()),
				"RESCHEDULED", Map.of("reason", reason.trim(), "priorStartTime", priorStart.toString(), "newStartTime",
						newStartTime.toString(), "priorVetId", priorVetId, "newVetId", newVetId));

		notificationService.createNotification(ownerId, "notification.appointmentRescheduled.title",
				"notification.appointmentRescheduled.message", "/scheduling/appointments/" + saved.getId());

		return AppointmentDto.from(saved);
	}

	@Transactional
	public AppointmentDto cancelAppointmentByStaff(Integer appointmentId, String reason, String actorUsername) {
		if (reason == null || reason.trim().isEmpty()) {
			throw new IllegalArgumentException("Cancellation reason is required");
		}

		Appointment initial = appointmentRepository.findById(appointmentId)
			.orElseThrow(() -> new ResourceNotFoundException("Appointment not found"));

		if (initial.getStatus() == AppointmentStatus.CANCELLED) {
			return AppointmentDto.from(initial);
		}

		if (initial.getStatus() == AppointmentStatus.COMPLETED || initial.getStatus() == AppointmentStatus.NO_SHOW) {
			throw new IllegalStateException("Cannot cancel a completed or no-show appointment");
		}

		Instant now = timeService.now();
		if (!now.isBefore(initial.getEndTime())) {
			throw new IllegalStateException("Cannot cancel an appointment at or after its scheduled end time");
		}

		Integer ownerId = initial.getOwner().getId();
		Integer petId = initial.getPet().getId();
		Integer vetId = initial.getVet().getId();
		Integer reqId = initial.getRequest() != null ? initial.getRequest().getId() : null;

		lockCoordinator.lockResources(List.of(ownerId), List.of(petId), List.of(vetId),
				(reqId != null ? List.of(reqId) : null), List.of(appointmentId), null);

		Appointment appointment = appointmentRepository.findById(appointmentId)
			.orElseThrow(() -> new ResourceNotFoundException("Appointment not found"));

		if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
			return AppointmentDto.from(appointment);
		}

		if (appointment.getStatus() == AppointmentStatus.COMPLETED
				|| appointment.getStatus() == AppointmentStatus.NO_SHOW) {
			throw new IllegalStateException("Cannot cancel a completed or no-show appointment");
		}

		if (!now.isBefore(appointment.getEndTime())) {
			throw new IllegalStateException("Cannot cancel an appointment at or after its scheduled end time");
		}

		appointment.setStatus(AppointmentStatus.CANCELLED);
		appointment.setCancellationReason(reason.trim());
		Appointment saved = appointmentRepository.save(appointment);

		SchedulingRequest req = saved.getRequest();
		if (req != null && req.getRetentionDeadline() == null) {
			ClinicSettings settings = clinicSettingsService.getSettings();
			req.setRetentionDeadline(now.plus(Duration.ofDays(settings.getSensitiveDataRetentionDays())));
			schedulingRequestRepository.save(req);
		}

		calendarConflictService.resolveConflictsForAppointment(appointmentId, ConflictStatus.CANCELLED, actorUsername);

		auditService.recordEvent(AuditEventType.APPOINTMENT_CANCELLED, actorUsername != null ? actorUsername : "staff",
				"STAFF", "APPOINTMENT", String.valueOf(saved.getId()), "CANCELLED",
				Map.of("reason", reason.trim(), "cancelledBy", "staff"));

		notificationService.createNotification(ownerId, "notification.appointmentCancelled.title",
				"notification.appointmentCancelled.message", "/scheduling/appointments/" + saved.getId());

		return AppointmentDto.from(saved);
	}

	@Transactional
	public AppointmentDto completeAppointment(Integer appointmentId, String clinicalDescription, String actorUsername) {
		if (clinicalDescription == null || clinicalDescription.trim().isEmpty()) {
			throw new IllegalArgumentException("Clinical description is required for completion");
		}

		Appointment initial = appointmentRepository.findById(appointmentId)
			.orElseThrow(() -> new ResourceNotFoundException("Appointment not found"));

		Instant now = timeService.now();
		if (now.isBefore(initial.getEndTime())) {
			throw new IllegalStateException("Cannot complete an appointment before its scheduled end time");
		}

		if (initial.getStatus() == AppointmentStatus.CANCELLED) {
			throw new IllegalStateException("Cannot complete a cancelled appointment");
		}

		if (initial.getStatus() == AppointmentStatus.COMPLETED && initial.getVisit() != null) {
			return AppointmentDto.from(initial);
		}

		Integer ownerId = initial.getOwner().getId();
		Integer petId = initial.getPet().getId();
		Integer vetId = initial.getVet().getId();
		Integer reqId = initial.getRequest() != null ? initial.getRequest().getId() : null;

		lockCoordinator.lockResources(List.of(ownerId), List.of(petId), List.of(vetId),
				(reqId != null ? List.of(reqId) : null), List.of(appointmentId), null);

		Appointment appointment = appointmentRepository.findById(appointmentId)
			.orElseThrow(() -> new ResourceNotFoundException("Appointment not found"));

		if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
			throw new IllegalStateException("Cannot complete a cancelled appointment");
		}

		if (appointment.getStatus() == AppointmentStatus.COMPLETED && appointment.getVisit() != null) {
			return AppointmentDto.from(appointment);
		}

		Visit visit = appointment.getVisit();
		if (visit == null) {
			visit = new Visit();
			visit.setDate(appointment.getStartTime().atZone(timeService.getClinicZoneId()).toLocalDate());
			visit.setDescription(clinicalDescription.trim());
			Pet pet = appointment.getPet();
			pet.addVisit(visit);
			visit = visitRepository.save(visit);
			appointment.setVisit(visit);
		}

		appointment.setStatus(AppointmentStatus.COMPLETED);
		appointment.setCompletedAt(now);
		appointment.setCompletionNotes(clinicalDescription.trim());
		Appointment saved = appointmentRepository.save(appointment);

		SchedulingRequest req = saved.getRequest();
		if (req != null && req.getRetentionDeadline() == null) {
			ClinicSettings settings = clinicSettingsService.getSettings();
			req.setRetentionDeadline(now.plus(Duration.ofDays(settings.getSensitiveDataRetentionDays())));
			schedulingRequestRepository.save(req);
		}

		auditService.recordEvent(AuditEventType.APPOINTMENT_COMPLETED, actorUsername != null ? actorUsername : "staff",
				"STAFF", "APPOINTMENT", String.valueOf(saved.getId()), "COMPLETED",
				Map.of("visitId", visit.getId(), "completedAt", now.toString()));

		return AppointmentDto.from(saved);
	}

	@Transactional
	public AppointmentDto markNoShow(Integer appointmentId, String actorUsername) {
		Appointment initial = appointmentRepository.findById(appointmentId)
			.orElseThrow(() -> new ResourceNotFoundException("Appointment not found"));

		Instant now = timeService.now();
		if (now.isBefore(initial.getEndTime())) {
			throw new IllegalStateException("Cannot mark an appointment as no-show before its scheduled end time");
		}

		if (initial.getStatus() == AppointmentStatus.NO_SHOW) {
			return AppointmentDto.from(initial);
		}

		if (initial.getStatus() != AppointmentStatus.BOOKED) {
			throw new IllegalStateException("Cannot mark an appointment as no-show unless it is BOOKED");
		}

		Integer ownerId = initial.getOwner().getId();
		Integer petId = initial.getPet().getId();
		Integer vetId = initial.getVet().getId();
		Integer reqId = initial.getRequest() != null ? initial.getRequest().getId() : null;

		lockCoordinator.lockResources(List.of(ownerId), List.of(petId), List.of(vetId),
				(reqId != null ? List.of(reqId) : null), List.of(appointmentId), null);

		Appointment appointment = appointmentRepository.findById(appointmentId)
			.orElseThrow(() -> new ResourceNotFoundException("Appointment not found"));

		if (appointment.getStatus() == AppointmentStatus.NO_SHOW) {
			return AppointmentDto.from(appointment);
		}

		if (appointment.getStatus() != AppointmentStatus.BOOKED) {
			throw new IllegalStateException("Cannot mark an appointment as no-show unless it is BOOKED");
		}

		appointment.setStatus(AppointmentStatus.NO_SHOW);
		Appointment saved = appointmentRepository.save(appointment);

		SchedulingRequest req = saved.getRequest();
		if (req != null && req.getRetentionDeadline() == null) {
			ClinicSettings settings = clinicSettingsService.getSettings();
			req.setRetentionDeadline(now.plus(Duration.ofDays(settings.getSensitiveDataRetentionDays())));
			schedulingRequestRepository.save(req);
		}

		auditService.recordEvent(AuditEventType.APPOINTMENT_NO_SHOW, actorUsername != null ? actorUsername : "staff",
				"STAFF", "APPOINTMENT", String.valueOf(saved.getId()), "NO_SHOW", Map.of("markedAt", now.toString()));

		return AppointmentDto.from(saved);
	}

}
