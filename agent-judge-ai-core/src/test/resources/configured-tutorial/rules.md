# Technical Design and Constraints: Smart Appointment Scheduling

## Overview

The feature extends the existing Spring MVC, Thymeleaf, Spring Data JPA application with authenticated scheduling workflows, persistent request and appointment state, Spring AI/Ollama interpretation, and single-request Timefold solving. The implementation remains a modular monolith and supports H2, MySQL, and PostgreSQL.

## Design

Components: MVC controllers and form models; explicit transactional application services; JPA aggregates and repositories; a shared effective-calendar and slot-constraint policy; Spring AI and Timefold adapters; Spring Security account/session services; and periodic lifecycle processing.

Boundaries: Web code performs binding and presentation only. Application services own authorization-sensitive lookup, state transitions, transactions, audit, and notification creation. Ollama and Timefold execute through adapters outside database transactions. Database state remains authoritative when an asynchronous result is committed.

Flow: Valid intake persists a versioned request with captured interpretation settings before language and consent routing. Consent persists an AI operation before dispatch. Confirmation produces an immutable request snapshot, while pre-confirmation fallback materializes the same owner horizon from the initial staff-queue instant. Matching builds an immutable planning snapshot, asks Timefold for one assignment, then revalidates and reserves that assignment under database locks. Acceptance converts the reservation into an Appointment. Staff fallback and direct lifecycle actions use the same live constraint and reservation services.

Key dependencies: reuse Spring MVC, Thymeleaf, Validation, Data JPA, message bundles, and Testcontainers; add Spring Security, Flyway, Spring AI 2.0.1 with Ollama, and Timefold 2.5.0. Do not add Spring Statemachine, Quartz, a message broker, an outbox framework, or Spring Session JDBC.

## Codebase Alignment

The existing package-by-domain style, constructor injection, MVC form handling, JPA repositories, Thymeleaf templates, message bundles, and H2/MySQL/PostgreSQL profiles remain in use. Scheduling introduces an application-service layer because cross-aggregate state transitions, locking, audit, and notifications cannot safely remain in controllers. The build intentionally moves from Java 17 to Java 21 as required by the feature specification. Existing `schema.sql` ownership is replaced by Flyway.

## Rules

### RULE-1
**Covers:** UC3-AC47–UC3-AC66, UC4-AC33–UC4-AC45, UC5-AC20–UC5-AC48, UC6-AC7–UC6-AC52, UC7-AC12, UC7-AC44
**MUST** implement request, reservation, appointment, account, and configuration mutations as explicit application-service operations with centralized enum transition policies; controllers, repositories, scheduled workers, and JPA callbacks MUST NOT perform independent workflow transitions.
**Reason:** The chosen explicit-policy design keeps every permitted transition, transaction boundary, and side effect inspectable without adding Spring Statemachine or hiding behavior in persistence callbacks.

### RULE-2
**Covers:** UC2-AC1–UC2-AC5, UC2-AC39, UC2-AC43, UC4-AC6–UC4-AC14, UC4-AC31, UC4-AC39–UC4-AC41, UC5-AC10–UC5-AC18, UC5-AC36–UC5-AC39, UC5-AC46–UC5-AC48, UC6-AC15–UC6-AC18, UC6-AC27–UC6-AC39, UC6-AC51–UC6-AC52, UC7-AC17–UC7-AC39
**MUST** persist appointments, reservations, operations, locks, and deadlines as `Instant` values; represent recurring clinic rules with `LocalDate`, `LocalTime`, and `DayOfWeek`; and derive all current time and clinic-local conversions from injected `Clock` and validated `ZoneId` beans. A local candidate is bookable only when `ZoneRules.getValidOffsets(localDateTime)` returns exactly one offset.
**Reason:** One time model is required for deterministic deadlines, exclusive local-date horizons, DST rejection, restart recovery, and tests across three databases.

### RULE-3
**Covers:** UC2-AC44, UC2-AC45, UC3-AC64–UC3-AC65, UC4-AC15–UC4-AC18, UC4-AC31–UC4-AC48, UC5-AC14–UC5-AC27, UC5-AC35, UC5-AC39, UC5-AC41, UC5-AC43, UC6-AC4–UC6-AC10, UC6-AC19, UC7-AC21, UC7-AC26–UC7-AC36
**MUST** persist guided holds and staff offers in one reservation table containing reservation type, lifecycle status, request, owner, pet, veterinarian, half-open start/end instants, and absolute deadline. Confirmed bookings MUST remain separate Appointment rows, and temporary reservations MUST NOT be represented as provisional appointments or request columns.
**Reason:** One reservation model gives matching, staff calendar rendering, overlap checks, expiry, cancellation, and recovery a single authoritative source without conflating temporary and confirmed state.

### RULE-4
**Covers:** UC3-AC9–UC3-AC11, UC3-AC47–UC3-AC66, UC4-AC31–UC4-AC48, UC5-AC6–UC5-AC11, UC5-AC20–UC5-AC48, UC6-AC4–UC6-AC10, UC6-AC13–UC6-AC27, UC6-AC32–UC6-AC52
**MUST** protect reservation-changing transactions with JPA pessimistic write locks and live revalidation. When several resource types are involved, locks MUST be acquired in this global order, with identifiers ascending within a type: Owner, Pet, Vet, SchedulingRequest, Appointment, Reservation. External AI or solver work MUST NOT run while a database transaction or pessimistic lock is open.
**Reason:** Stable row locking is portable across H2, MySQL, and PostgreSQL and serializes races on existing resource rows without relying on vendor-specific interval constraints or serializable-isolation retries.

### RULE-5
**Covers:** UC2-AC24–UC2-AC44, UC4-AC1–UC4-AC28, UC4-AC31–UC4-AC32, UC4-AC46–UC4-AC48, UC5-AC13–UC5-AC18, UC5-AC28–UC5-AC32, UC5-AC41, UC5-AC48, UC6-AC4–UC6-AC6, UC6-AC13–UC6-AC24, UC6-AC42, UC6-AC48–UC6-AC50
**MUST** define hard scheduling feasibility once as a pure policy over an immutable scheduling snapshot. Timefold MUST own automated feasibility and lexicographic ranking through that policy; after solving, the reservation service MUST rebuild a live snapshot under RULE-4 locks and apply the same policy to the winning assignment before insert. Staff offers, direct bookings, and reschedules MUST call the same policy without invoking Timefold ranking.
**Reason:** The selected design makes Timefold substantive while preventing constraint drift between automated suggestions and transactional booking paths.

### RULE-6
**Covers:** UC3-AC22–UC3-AC26, UC3-AC53, UC3-AC60, UC4-AC1–UC4-AC5, UC4-AC38, UC7-AC27–UC7-AC36, UC7-AC39
**MUST** identify every AI or solver execution with a persisted opaque operation token, operation kind, dispatch time, absolute deadline, and attempt count before dispatch. After commit, separate bounded Spring executors MAY run AI and solver tasks once; completion MUST use a conditional state-and-token update, executor rejection MUST enter the specified fallback, and polling or recovery MUST NOT dispatch work.
**Reason:** Persisted operation identity makes in-process asynchronous execution restart-safe and idempotent without a durable job queue or automatic retries.

### RULE-7
**Covers:** UC5-AC20–UC5-AC43, UC6-AC7–UC6-AC52, UC7-AC1–UC7-AC16, UC7-AC44
**MUST** write the domain mutation, append-only audit metadata, and every required in-app notification explicitly in the same application-service transaction. A rollback MUST remove all three effects, and no asynchronous listener or outbox projection MAY be the source of an owner-visible notification.
**Reason:** Notifications and audit are local database effects whose required consistency is clearest when controlled by the service committing the state change.

### RULE-8
**Covers:** UC1-AC1–UC1-AC47, UC3-AC1–UC3-AC3, UC4-AC29–UC4-AC30, UC5-AC7, UC5-AC19, UC6-AC2–UC6-AC3, UC7-AC7–UC7-AC16, UC7-AC40–UC7-AC43
**MUST** enforce role and ownership authorization both at the Spring Security route boundary and in application-service repository queries. Owner lookups MUST include the authenticated account's immutable Owner identifier and return the same not-found result for absent and foreign records. Templates and response DTOs MUST be role-specific rather than serializing JPA entities.
**Reason:** Navigation hiding alone cannot prevent forged requests, identifier probing, or accidental disclosure of staff calendar fields.

### RULE-9
**Covers:** feature-wide
**MUST** make Flyway the only schema creator: disable Spring SQL schema initialization; provide vendor-specific V1 legacy-baseline migrations that reproduce the current schema and sample data on an empty database; add scheduling at V2 or later; keep automatic baseline disabled; and require the explicit one-time V1 baseline procedure for a verified legacy schema. Unknown non-empty schemas MUST fail migration.
**Reason:** The current vendor-specific `schema.sql` files cannot provide ordered upgrades, and automatic baselining would falsely bless unknown schemas.

### RULE-10
**Covers:** UC3-AC18–UC3-AC21, UC7-AC7, UC7-AC12–UC7-AC25, UC7-AC40–UC7-AC43
**MUST** use allowlisted DTOs at AI, web, audit, notification, and diagnostics boundaries. Raw request text, AI input/output, passwords, password hashes, clinical descriptions, and staff-only notes MUST NOT appear in `toString()`, exception messages, structured log arguments, audit payloads, notification previews, or diagnostics.
**Reason:** The feature deliberately retains sensitive text for a limited time, so implicit entity serialization or logging would bypass its privacy boundary.

### RULE-11
**Covers:** feature-wide
**MUST** keep Maven and Gradle on Java 21 with equivalent runtime and test dependencies, Spring AI 2.0.1, and Timefold 2.5.0. Both builds MUST run unit and MVC security tests; the Maven CI workflow MUST own the single H2/MySQL/PostgreSQL migration-and-concurrency matrix, while the Gradle workflow MUST NOT duplicate that matrix.
**Reason:** The specification requires two equivalent builds but only one database integration matrix; Maven already owns the project's richer verification lifecycle.

### RULE-12
**Covers:** UC3-AC23–UC3-AC26, UC3-AC53, UC3-AC56–UC3-AC66, UC4-AC3–UC4-AC5, UC4-AC36–UC4-AC45, UC5-AC6, UC5-AC20–UC5-AC27, UC5-AC37–UC5-AC48, UC6-AC32–UC6-AC52, UC7-AC27–UC7-AC39
**MUST** make commands idempotent by checking the locked aggregate's current state, version, operation token, and current reservation before applying effects. Expected stale, conflict, exhausted, clarification, fallback, and not-found outcomes MUST be returned as typed application outcomes rather than converted to unhandled exceptions.
**Reason:** Polling, browser retries, deadline processing, and concurrent staff/owner actions are normal workflow inputs, not exceptional infrastructure failures.

### RULE-13
**Covers:** UC3-AC67–UC3-AC83, UC4-AC22, UC4-AC24
**MUST** persist each structured owner window as normalized request-owned symbolic rows and persist its confirmation-time expansion as normalized request-owned half-open `Instant` interval rows classified as `ALLOWED`, `PREFERRED`, or `EXCLUDED`. UC3 MUST replace materialized rows transactionally whenever confirmation or a correctable availability edit changes their meaning; UC4 MUST consume the materialized rows as the sole owner-window authority for solver and live feasibility. JSON columns and the legacy single preferred-start/preferred-end columns MUST NOT be authoritative for structured availability.
**Reason:** Multiple recurring, disjoint, preferred, and excluded ranges must remain portable across H2, MySQL, and PostgreSQL and must reach every feasibility check without lossy scalar projection.

## Cross-Reference

| AC area | Shared rules |
|---|---|
| UC1 access, identity, and sessions | RULE-1, RULE-7, RULE-8, RULE-10, RULE-12 |
| UC2 time, calendar, and settings | RULE-2, RULE-5, RULE-9, RULE-11 |
| UC3 intake and interpretation | RULE-1, RULE-2, RULE-4, RULE-6, RULE-8, RULE-10, RULE-12, RULE-13 |
| UC4 matching and guided holds | RULE-1–RULE-6, RULE-8, RULE-12, RULE-13 |
| UC5 staff fallback | RULE-1–RULE-5, RULE-7, RULE-8, RULE-12 |
| UC6 appointment lifecycle | RULE-1–RULE-5, RULE-7, RULE-8, RULE-12 |
| UC7 notification, audit, retention, and recovery | RULE-1–RULE-4, RULE-6–RULE-8, RULE-10, RULE-12 |

Detailed AC-by-AC mappings are in each use case's `rules.md`.

## Design Exclusions

- No whole-clinic Timefold plan, reoptimization of confirmed appointments, or solver use for staff-selected slots
- No Spring Statemachine, Quartz, external broker, durable job queue, transactional outbox, or Spring Session JDBC
- No provisional Appointment status for holds and no database-vendor-specific exclusion-lock design
- No JSON availability document, model-calculated relative date, or single start/end projection as the owner-window source of truth
- No external notification transport or public scheduling API

## External Dependencies

- Ollama remains optional at runtime. Its absence is represented through the specified staff fallback and diagnostics behavior.
- Reproducible AI integration verification requires the configured model tag to be pinned to the approved digest outside ordinary unit tests.
- MySQL and PostgreSQL migration/concurrency verification requires Docker in the designated Maven CI matrix.
