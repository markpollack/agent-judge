package org.springframework.samples.petclinic.scheduling.service;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.scheduling.model.Appointment;
import org.springframework.samples.petclinic.scheduling.model.Reservation;
import org.springframework.samples.petclinic.scheduling.model.SchedulingRequest;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.stereotype.Service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;

@Service
public class LockCoordinator {

	@PersistenceContext
	private EntityManager entityManager;

	public LockCoordinator() {
	}

	public LockCoordinator(EntityManager entityManager) {
		this.entityManager = entityManager;
	}

	public LockedResources lockResources(Collection<Integer> ownerIds, Collection<Integer> petIds,
			Collection<Integer> vetIds, Collection<Integer> requestIds, Collection<Integer> appointmentIds,
			Collection<Integer> reservationIds) {

		LockedResources locked = new LockedResources();

		// 1. Owner
		if (ownerIds != null) {
			for (Integer id : sortIds(ownerIds)) {
				locked.addOwner(entityManager.find(Owner.class, id, LockModeType.PESSIMISTIC_WRITE));
			}
		}

		// 2. Pet
		if (petIds != null) {
			for (Integer id : sortIds(petIds)) {
				locked.addPet(entityManager.find(Pet.class, id, LockModeType.PESSIMISTIC_WRITE));
			}
		}

		// 3. Vet
		if (vetIds != null) {
			for (Integer id : sortIds(vetIds)) {
				locked.addVet(entityManager.find(Vet.class, id, LockModeType.PESSIMISTIC_WRITE));
			}
		}

		// 4. SchedulingRequest
		if (requestIds != null) {
			for (Integer id : sortIds(requestIds)) {
				locked.addRequest(entityManager.find(SchedulingRequest.class, id, LockModeType.PESSIMISTIC_WRITE));
			}
		}

		// 5. Appointment
		if (appointmentIds != null) {
			for (Integer id : sortIds(appointmentIds)) {
				locked.addAppointment(entityManager.find(Appointment.class, id, LockModeType.PESSIMISTIC_WRITE));
			}
		}

		// 6. Reservation
		if (reservationIds != null) {
			for (Integer id : sortIds(reservationIds)) {
				locked.addReservation(entityManager.find(Reservation.class, id, LockModeType.PESSIMISTIC_WRITE));
			}
		}

		return locked;
	}

	public Owner lockOwner(Integer ownerId) {
		if (ownerId == null) {
			return null;
		}
		return entityManager.find(Owner.class, ownerId, LockModeType.PESSIMISTIC_WRITE);
	}

	public Pet lockPet(Integer petId) {
		if (petId == null) {
			return null;
		}
		return entityManager.find(Pet.class, petId, LockModeType.PESSIMISTIC_WRITE);
	}

	public Vet lockVet(Integer vetId) {
		if (vetId == null) {
			return null;
		}
		return entityManager.find(Vet.class, vetId, LockModeType.PESSIMISTIC_WRITE);
	}

	public SchedulingRequest lockRequest(Integer requestId) {
		if (requestId == null) {
			return null;
		}
		return entityManager.find(SchedulingRequest.class, requestId, LockModeType.PESSIMISTIC_WRITE);
	}

	public Appointment lockAppointment(Integer appointmentId) {
		if (appointmentId == null) {
			return null;
		}
		return entityManager.find(Appointment.class, appointmentId, LockModeType.PESSIMISTIC_WRITE);
	}

	public Reservation lockReservation(Integer reservationId) {
		if (reservationId == null) {
			return null;
		}
		return entityManager.find(Reservation.class, reservationId, LockModeType.PESSIMISTIC_WRITE);
	}

	private TreeSet<Integer> sortIds(Collection<Integer> ids) {
		TreeSet<Integer> sorted = new TreeSet<>();
		for (Integer id : ids) {
			if (id != null) {
				sorted.add(id);
			}
		}
		return sorted;
	}

	public static class LockedResources {

		private final java.util.List<Owner> owners = new java.util.ArrayList<>();

		private final java.util.List<Pet> pets = new java.util.ArrayList<>();

		private final java.util.List<Vet> vets = new java.util.ArrayList<>();

		private final java.util.List<SchedulingRequest> requests = new java.util.ArrayList<>();

		private final java.util.List<Appointment> appointments = new java.util.ArrayList<>();

		private final java.util.List<Reservation> reservations = new java.util.ArrayList<>();

		void addOwner(Owner owner) {
			if (owner != null) {
				owners.add(owner);
			}
		}

		void addPet(Pet pet) {
			if (pet != null) {
				pets.add(pet);
			}
		}

		void addVet(Vet vet) {
			if (vet != null) {
				vets.add(vet);
			}
		}

		void addRequest(SchedulingRequest request) {
			if (request != null) {
				requests.add(request);
			}
		}

		void addAppointment(Appointment appointment) {
			if (appointment != null) {
				appointments.add(appointment);
			}
		}

		void addReservation(Reservation reservation) {
			if (reservation != null) {
				reservations.add(reservation);
			}
		}

		public List<Owner> getOwners() {
			return owners;
		}

		public List<Pet> getPets() {
			return pets;
		}

		public List<Vet> getVets() {
			return vets;
		}

		public List<SchedulingRequest> getRequests() {
			return requests;
		}

		public List<Appointment> getAppointments() {
			return appointments;
		}

		public List<Reservation> getReservations() {
			return reservations;
		}

	}

}
