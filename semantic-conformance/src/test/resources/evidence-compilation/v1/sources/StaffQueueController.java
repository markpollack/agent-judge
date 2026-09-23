package org.springframework.samples.petclinic.scheduling.controller;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.samples.petclinic.scheduling.model.CareType;
import org.springframework.samples.petclinic.scheduling.model.Reservation;
import org.springframework.samples.petclinic.scheduling.model.ReservationStatus;
import org.springframework.samples.petclinic.scheduling.model.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.model.StaffClaim;
import org.springframework.samples.petclinic.scheduling.repository.ReservationRepository;
import org.springframework.samples.petclinic.scheduling.repository.SchedulingRequestRepository;
import org.springframework.samples.petclinic.scheduling.service.StaffFallbackService;
import org.springframework.samples.petclinic.vet.SpecialtyRepository;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class StaffQueueController {

	private final StaffFallbackService staffFallbackService;

	private final SchedulingRequestRepository schedulingRequestRepository;

	private final ReservationRepository reservationRepository;

	private final VetRepository vetRepository;

	private final SpecialtyRepository specialtyRepository;

	public StaffQueueController(StaffFallbackService staffFallbackService,
			SchedulingRequestRepository schedulingRequestRepository, ReservationRepository reservationRepository,
			VetRepository vetRepository, SpecialtyRepository specialtyRepository) {
		this.staffFallbackService = staffFallbackService;
		this.schedulingRequestRepository = schedulingRequestRepository;
		this.reservationRepository = reservationRepository;
		this.vetRepository = vetRepository;
		this.specialtyRepository = specialtyRepository;
	}

	@GetMapping("/scheduling/staff/queue")
	public String showQueue(Model model) {
		List<SchedulingRequest> queue = staffFallbackService.getFallbackQueue();
		model.addAttribute("queue", queue);
		return "scheduling/staffQueue";
	}

	@PostMapping("/scheduling/staff/queue/{id}/claim")
	public String claimRequest(@PathVariable("id") Integer id, Principal principal,
			RedirectAttributes redirectAttributes) {
		try {
			staffFallbackService.claimRequest(id, principal.getName());
			redirectAttributes.addFlashAttribute("successMessage", "Request claimed successfully");
			return "redirect:/scheduling/staff/requests/" + id;
		}
		catch (Exception ex) {
			redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
			return "redirect:/scheduling/staff/queue";
		}
	}

	@PostMapping("/scheduling/staff/queue/{id}/release")
	public String releaseClaim(@PathVariable("id") Integer id, Principal principal,
			RedirectAttributes redirectAttributes) {
		try {
			staffFallbackService.releaseClaim(id, principal.getName());
			redirectAttributes.addFlashAttribute("successMessage", "Claim released");
			return "redirect:/scheduling/staff/queue";
		}
		catch (Exception ex) {
			redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
			return "redirect:/scheduling/staff/queue";
		}
	}

	@GetMapping("/scheduling/staff/requests/{id}")
	public String showRequestDetail(@PathVariable("id") Integer id, Principal principal, Model model) {
		SchedulingRequest request = schedulingRequestRepository.findWithDetailsById(id)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found"));

		Optional<StaffClaim> claimOpt = staffFallbackService.getClaimForRequest(id);
		List<Reservation> activeReservations = reservationRepository.findWithVetByRequestId(id);
		Reservation activeOffer = null;
		for (Reservation r : activeReservations) {
			if (r.getStatus() == ReservationStatus.ACTIVE) {
				activeOffer = r;
				break;
			}
		}

		model.addAttribute("request", request);
		model.addAttribute("claim", claimOpt.orElse(null));
		model.addAttribute("activeOffer", activeOffer);
		model.addAttribute("vets", vetRepository.findAll());
		model.addAttribute("specialties", specialtyRepository.findAll());
		model.addAttribute("currentUsername", principal != null ? principal.getName() : "");

		return "scheduling/staffRequestDetail";
	}

	@PostMapping("/scheduling/staff/requests/{id}/edit-interpretation")
	public String updateInterpretation(@PathVariable("id") Integer id,
			@RequestParam(value = "durationMinutes", required = false) Integer durationMinutes,
			@RequestParam(value = "careType", required = false) CareType careType,
			@RequestParam(value = "requiredSpecialtyId", required = false) Integer requiredSpecialtyId,
			@RequestParam(value = "preferredVetId", required = false) Integer preferredVetId,
			@RequestParam(value = "preferredStartWindow",
					required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant preferredStartWindow,
			@RequestParam(value = "preferredEndWindow",
					required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant preferredEndWindow,
			Principal principal, RedirectAttributes redirectAttributes) {
		try {
			staffFallbackService.updateStructuredInterpretation(id, durationMinutes, careType, requiredSpecialtyId,
					preferredVetId, preferredStartWindow, preferredEndWindow, principal.getName());
			redirectAttributes.addFlashAttribute("successMessage", "Structured facts updated successfully");
			return "redirect:/scheduling/staff/requests/" + id;
		}
		catch (Exception ex) {
			redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
			return "redirect:/scheduling/staff/requests/" + id;
		}
	}

	@PostMapping("/scheduling/staff/requests/{id}/offer")
	public String createOffer(@PathVariable("id") Integer id, @RequestParam("vetId") Integer vetId,
			@RequestParam("startTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startTime,
			Principal principal, RedirectAttributes redirectAttributes) {
		try {
			staffFallbackService.createStaffOffer(id, vetId, startTime, principal.getName());
			redirectAttributes.addFlashAttribute("successMessage", "Staff offer issued successfully");
			return "redirect:/scheduling/staff/requests/" + id;
		}
		catch (Exception ex) {
			redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
			return "redirect:/scheduling/staff/requests/" + id;
		}
	}

	@PostMapping("/scheduling/staff/requests/{id}/revoke-offer")
	public String revokeOffer(@PathVariable("id") Integer id, @RequestParam("reservationId") Integer reservationId,
			Principal principal, RedirectAttributes redirectAttributes) {
		try {
			staffFallbackService.revokeStaffOffer(id, reservationId, principal.getName());
			redirectAttributes.addFlashAttribute("successMessage", "Staff offer revoked");
			return "redirect:/scheduling/staff/requests/" + id;
		}
		catch (Exception ex) {
			redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
			return "redirect:/scheduling/staff/requests/" + id;
		}
	}

	@PostMapping("/scheduling/staff/requests/{id}/direct-book")
	public String directBook(@PathVariable("id") Integer id, @RequestParam("vetId") Integer vetId,
			@RequestParam("startTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startTime,
			@RequestParam("offlineReason") String offlineReason, Principal principal,
			RedirectAttributes redirectAttributes) {
		try {
			staffFallbackService.directBook(id, vetId, startTime, offlineReason, principal.getName());
			redirectAttributes.addFlashAttribute("successMessage", "Appointment directly booked successfully");
			return "redirect:/scheduling/staff/requests/" + id;
		}
		catch (Exception ex) {
			redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
			return "redirect:/scheduling/staff/requests/" + id;
		}
	}

	@PostMapping("/scheduling/staff/requests/{id}/cancel")
	public String cancelRequest(@PathVariable("id") Integer id, @RequestParam("reason") String reason,
			Principal principal, RedirectAttributes redirectAttributes) {
		try {
			staffFallbackService.cancelRequestByStaff(id, reason, principal.getName());
			redirectAttributes.addFlashAttribute("successMessage", "Request cancelled");
			return "redirect:/scheduling/staff/queue";
		}
		catch (Exception ex) {
			redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
			return "redirect:/scheduling/staff/requests/" + id;
		}
	}

}
