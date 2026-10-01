# Acceptance Criteria: UC6 — Manage Appointment Lifecycle

## Functional

### UC6-AC1: Keep appointments separate from Visits

**Covers:** UC6-B1

When a future booking is created, the system shall persist it as an Appointment without creating a legacy Visit.

### UC6-AC2: Show an owner's upcoming bookings

**Covers:** UC6-B2

When an authenticated owner requests upcoming appointments, the system shall show only that owner's upcoming `BOOKED` appointments.

### UC6-AC3: Show the full staff calendar

**Covers:** UC6-B3

When authenticated staff requests the clinic calendar, the system shall show all appointments.

### UC6-AC4: Permit non-overlapping future appointments

**Covers:** UC6-B4

When a new future appointment for an owner and pet does not overlap their other appointments, the system shall permit the appointment when all other constraints succeed.

### UC6-AC5: Permit adjacent future appointments

**Covers:** UC6-B4

When a new appointment starts exactly when another appointment for the same owner or pet ends, the system shall not treat the half-open intervals as overlapping.

### UC6-AC6: Reject overlapping future appointments

**Covers:** UC6-B4

If a new future appointment overlaps another appointment for the same owner or pet, then the system shall reject the booking and shall not create the overlap.

### UC6-AC7: Cancel an owned appointment before start

**Covers:** UC6-B5

While an owned appointment is `BOOKED` and its start instant is in the future, when the owner cancels it, the system shall move the appointment to `CANCELLED`.

### UC6-AC8: Reject owner cancellation at start

**Covers:** UC6-B6

If an owner attempts to cancel a `BOOKED` appointment at its start instant, then the system shall reject cancellation and shall preserve the appointment state.

### UC6-AC9: Reject owner cancellation after start

**Covers:** UC6-B6

If an owner attempts to cancel a `BOOKED` appointment after its start instant, then the system shall reject cancellation and shall preserve the appointment state.

### UC6-AC10: Free resources after cancellation

**Covers:** UC6-B7

When appointment cancellation commits, the system shall atomically free the appointment's veterinarian, owner, and pet interval.

### UC6-AC11: Keep the linked request terminal

**Covers:** UC6-B8

When an appointment linked to a terminal scheduling request is cancelled, the system shall preserve the scheduling request's terminal state.

### UC6-AC12: Require a new request for owner rebooking

**Covers:** UC6-B9

When an owner wants to rebook after appointment cancellation, the system shall require creation of a new scheduling request.

### UC6-AC13: Create an unlinked direct appointment

**Covers:** UC6-B10

When staff submits a feasible direct booking without a scheduling request and with a nonblank offline-coordination reason, the system shall create an unlinked appointment with staff-direct booking source.

### UC6-AC14: Reject a direct booking without a reason

**Covers:** UC6-B10

If staff submits an unlinked direct booking with a blank reason, then the system shall reject the booking and shall not create an appointment.

### UC6-AC15: Permit direct booking inside the staff horizon

**Covers:** UC6-B11

When a direct-booking start is in the future and before the exclusive end of the configured staff horizon, the system shall accept its time boundary when all other constraints succeed.

### UC6-AC16: Permit direct booking at the future grid boundary

**Covers:** UC6-B11

When a direct-booking start is exactly the next future clinic-local grid boundary, the system shall accept its time boundary when all other constraints succeed.

### UC6-AC17: Reject direct booking in the past

**Covers:** UC6-B11

If a direct-booking start is before the next future clinic-local grid boundary, then the system shall reject the booking and shall not create an appointment.

### UC6-AC18: Reject direct booking beyond the staff horizon

**Covers:** UC6-B11

If a direct-booking start is at or after the exclusive end of the configured staff horizon, then the system shall reject the booking and shall not create an appointment.

### UC6-AC19: Enforce hard direct-booking constraints

**Covers:** UC6-B12

If a staff direct booking violates a veterinarian, owner, pet, specialty, availability, closure, leave, 15-minute-grid, horizon, or DST constraint, then the system shall reject the booking and shall not create an appointment.

### UC6-AC20: Block unrelated booking during an active pet request

**Covers:** UC6-B13

If staff attempts an unrelated direct booking for a pet with a nonterminal scheduling request, then the system shall reject the booking and shall preserve the request.

### UC6-AC21: Reschedule a booked appointment

**Covers:** UC6-B14

While an appointment is `BOOKED`, when staff submits a future feasible replacement interval with a nonblank reason, the system shall move the appointment to that interval.

### UC6-AC22: Reject rescheduling without a reason

**Covers:** UC6-B14

If staff submits an appointment reschedule with a blank reason, then the system shall reject the reschedule and shall preserve the existing interval.

### UC6-AC23: Retain identity and history after rescheduling

**Covers:** UC6-B15

When an appointment is rescheduled, the system shall retain its appointment identity and prior change history.

### UC6-AC24: Prevent terminal rescheduling

**Covers:** UC6-B16

If staff attempts to reschedule a terminal appointment, then the system shall reject the action and shall preserve the terminal appointment.

### UC6-AC25: Cancel a booked appointment before its end

**Covers:** UC6-B17

While a `BOOKED` appointment has not reached its scheduled end, when staff cancels it with a nonblank reason, the system shall move the appointment to `CANCELLED`.

### UC6-AC26: Reject staff cancellation without a reason

**Covers:** UC6-B17

If staff submits appointment cancellation with a blank reason, then the system shall reject cancellation and shall preserve the appointment state.

### UC6-AC27: Reject staff cancellation at or after end

**Covers:** UC6-B18

If staff attempts to cancel an appointment at or after its scheduled end, then the system shall reject cancellation and shall preserve the appointment state.

### UC6-AC28: Prevent completion before scheduled end

**Covers:** UC6-B19

If staff attempts to complete an appointment before its scheduled end, then the system shall reject completion and shall preserve the appointment state.

### UC6-AC29: Permit completion at scheduled end

**Covers:** UC6-B19

When a `BOOKED` appointment reaches its scheduled end, the system shall permit staff to complete it when all completion validation succeeds.

### UC6-AC30: Permit completion after scheduled end

**Covers:** UC6-B19

When a `BOOKED` appointment is past its scheduled end, the system shall permit staff to complete it when all completion validation succeeds.

### UC6-AC31: Require a staff completion description

**Covers:** UC6-B20

If staff submits appointment completion with a blank clinical description, then the system shall reject completion and shall not create a Visit.

### UC6-AC32: Create one Visit on completion

**Covers:** UC6-B21

When an appointment first transitions to `COMPLETED`, the system shall create exactly one linked legacy Visit.

### UC6-AC33: Reuse the Visit on repeated completion

**Covers:** UC6-B21

When completion is submitted again for an already completed appointment, the system shall return the existing Visit without creating another Visit.

### UC6-AC34: Date the Visit in clinic-local time

**Covers:** UC6-B22

When completion creates a Visit, the system shall date the Visit with the appointment start's clinic-local calendar date.

### UC6-AC35: Use only the staff-authored description

**Covers:** UC6-B23

When completion creates a Visit, the system shall use the submitted staff-authored completion description as the Visit description without copying owner text or AI content.

### UC6-AC36: Prevent no-show before scheduled end

**Covers:** UC6-B24

If staff attempts to mark an appointment `NO_SHOW` before its scheduled end, then the system shall reject the transition and shall preserve the appointment state.

### UC6-AC37: Permit no-show at or after scheduled end

**Covers:** UC6-B24

When a `BOOKED` appointment is at or past its scheduled end, the system shall permit staff to move it to `NO_SHOW`.

### UC6-AC38: Create no Visit for no-show

**Covers:** UC6-B25

When an appointment moves to `NO_SHOW`, the system shall create no Visit.

### UC6-AC39: Correct no-show to completed

**Covers:** UC6-B26

While an appointment is `NO_SHOW`, when staff supplies a nonblank completion description, the system shall move it to `COMPLETED`.

### UC6-AC40: Keep completed and cancelled appointments irreversible

**Covers:** UC6-B27

If a user attempts to change the status of a `COMPLETED` or `CANCELLED` appointment, then the system shall reject the transition and shall preserve the terminal status.

### UC6-AC41: Preserve a deactivated veterinarian on existing appointments

**Covers:** UC6-B28

When a veterinarian is deactivated, the system shall retain that veterinarian on existing appointments.

### UC6-AC42: Prevent new assignment to a deactivated veterinarian

**Covers:** UC6-B28

If a new or rescheduled appointment targets a deactivated veterinarian, then the system shall reject the assignment and shall preserve existing appointments.

### UC6-AC43: Preserve legacy Visits as history

**Covers:** UC6-B29

When scheduling is introduced, the system shall preserve existing date-only Visits as historical records regardless of their stored dates.

### UC6-AC44: Exclude legacy Visits from overlap checks

**Covers:** UC6-B30

When timed availability is evaluated, the system shall exclude legacy Visits from overlap calculations.

### UC6-AC45: Remove the legacy booking path

**Covers:** UC6-B31

If a user requests the legacy “new visit” form or route as a booking mechanism, then the system shall prevent creation of a booking through that path.

### UC6-AC46: Resolve stale lifecycle actions safely

**Covers:** UC6-B32

If a stale or concurrent lifecycle action loses to an already committed transition, then the system shall return the current appointment state without applying another transition.

### UC6-AC48: Validate unlinked clinical routing

**Covers:** UC6-B33

If staff submits an unlinked direct booking as `GENERAL` with a required specialty or as `SPECIALTY` without one active required specialty, then the system shall reject the booking and shall not create an Appointment.

### UC6-AC49: Persist appointment clinical routing

**Covers:** UC6-B34

When an Appointment is created, the system shall persist its care type and optional required specialty.

### UC6-AC50: Reuse clinical routing during rescheduling

**Covers:** UC6-B35

When staff reschedules an Appointment, the system shall evaluate veterinarian eligibility using the Appointment's stored care type and required specialty.

### UC6-AC51: Resolve conflicts through rescheduling

**Covers:** UC6-B36

When staff successfully reschedules an Appointment, the system shall mark every unresolved calendar-conflict record for that Appointment resolved as `RESCHEDULED` while retaining its conflict history.

### UC6-AC52: Resolve conflicts through cancellation

**Covers:** UC6-B37

When an Appointment is successfully cancelled, the system shall mark every unresolved calendar-conflict record for that Appointment resolved as `CANCELLED` while retaining its conflict history.

## Non-functional

### UC6-AC47: Commit one concurrent lifecycle transition

**Covers:** UC6-B32

When concurrent lifecycle actions target the same appointment state, the system shall commit at most one valid transition from that state.

## Coverage exclusions

- Authorization: route and cross-owner data authorization is owned by UC1; this use case defines the lifecycle actions available after authorization.
- Performance: the specification defines no measurable calendar-query or lifecycle-transition latency contract.
