# Roadmap: Complete the Normalized Judgment API Migration — Agent Judge

> **Created**: 2026-08-03T00:00-04:00
> **Last updated**: 2026-08-03T00:00-04:00
> **Design version**: design-normalized-judgment.md r6
> **Implementation checkpoint**: dc6ca2d
> **Active branch**: normalized-judgment-api

## Overview

Complete and verify the normalized Judgment API migration in three remaining stages: first establish a
fresh-session context and run the entire Maven reactor from a clean state, then repair only verified
downstream consequences and prove the coverage and public-contract gates, and finally write the
consumer migration handoff and close the implementation record.

The domain design is settled. Judgment now carries three independent facts: required status,
optional normalized score, and optional label. The Score sealed hierarchy has been removed. This
roadmap is for verification, downstream completion, and handoff; it is not authorization to reopen
the value model without evidence of a real contradiction or defect.

This root roadmap is the active plan for the normalized-Judgment work. The ignored
plans/ROADMAP.md records the older product roadmap and remains historical context.

> **Before every commit**: Verify all exit criteria for the current step. Do not remove or weaken an
> exit criterion to mark a step complete. Run clean builds at verification boundaries; incremental
> compiler success is not evidence for this migration.

## Source-of-Truth Order

When documents appear to disagree, use this order:

1. VISION.md — product purpose, boundaries, and outcome scoreboard.
2. DESIGN.md — current system architecture and compatibility policy.
3. design-normalized-judgment.md — normative domain and wire contract.
4. This roadmap — current completion status, remaining work, and stopping conditions.
5. dc6ca2d and its tests — implemented checkpoint.
6. ddd-review.md and vision-design-review.md — review evidence and rationale.
7. a5b15f8 — preserved pre-change characterization baseline.
8. 49a6c73 — normalized-Judgment design/review checkpoint.

The Phase 0 instructions in the design document describe completed historical work. They must not be
repeated.

## Protected Repository State

- Branch: normalized-judgment-api
- Expected HEAD at handoff: dc6ca2d
- The pre-existing untracked .campus/ directory belongs to the user.
- Do not add, edit, remove, or commit .campus/.
- Do not merge, rebase, push, or modify a downstream repository unless explicitly authorized.
- Do not perform licensing, REUSE, SBOM, or copyright-header changes in this workstream.

---

## Stage 0: Design and Characterization — COMPLETE

### Step 0.1: Domain Review and Design — COMPLETE

**Work completed**:

- [x] Audited the Score hierarchy, Judgment, all five voting strategies, and judgment-producing seams
- [x] Completed the DDD review in ddd-review.md
- [x] Resolved all eight design questions and ten DELTAs in design-normalized-judgment.md
- [x] Chose status + optional normalized score + optional label as the Judgment value model
- [x] Chose lower-case stable wire names and omitted absent optionals
- [x] Removed Throwable from the Judgment transport model
- [x] Kept and repaired ErrorPolicy.IGNORE based on external framework and research evidence
- [x] Defined immutable aggregation evidence under reserved metadata.aggregation
- [x] Defined consensus, abstention, error, and weighted-population semantics

**Exit criteria met**:

- [x] All design decisions have an explicit resolution
- [x] Normative truth tables and invariants are documented
- [x] DDD review and implementation design are committed

**Commit**: 49a6c73 — Add DDD review and normalized-judgment design proposal

---

### Step 0.2: Preserve the Pre-Change Baseline — COMPLETE

**Work completed**:

- [x] Added VotingStrategyCharacterizationTest before changing production behavior
- [x] Preserved 36 characterization cases:
  - 16 INTENDED
  - 19 DEFECT
  - 1 BASELINE
- [x] Added explicit coverage for MajorityVotingStrategy's former default TREAT_AS_FAIL behavior
- [x] Verified the baseline against the untouched implementation

**Exit criteria met**:

- [x] Baseline behavior exists in a commit independent of the migration
- [x] Intended behavior, defects, and merely observed baseline behavior are distinguished

**Commit**: a5b15f8 — Characterize current voting strategy behavior

---

## Stage 1: Normalized Judgment Core Migration — COMPLETE

### Step 1.1: Replace the Value Model — COMPLETE

**Work completed**:

- [x] Replaced Score with required JudgmentStatus, optional normalized score, and optional label
- [x] Deleted Score, BooleanScore, NumericalScore, CategoricalScore, ScoreType, and Scores
- [x] Added compact-constructor invariants for every construction path
- [x] Added staged fluent factories for verdicts, scores, classifications, abstentions, and errors
- [x] Added effectiveScore() as a derived compatibility view
- [x] Removed Throwable-bearing error values
- [x] Added stable lower-case JudgmentStatus wire names and strict parsing
- [x] Omitted absent score and label values from JSON

---

### Step 1.2: Replace Aggregation Semantics — COMPLETE

**Work completed**:

- [x] Excluded ABSTAIN from numeric and vote populations
- [x] Added ErrorPolicy to all five strategies with PROPAGATE as the default
- [x] Kept and fixed IGNORE as distinct from TREAT_AS_ABSTAIN
- [x] Changed Consensus disagreement to ABSTAIN while preserving unanimous FAIL
- [x] Rejected an all-zero configured weight map
- [x] Returned ABSTAIN when a valid population has no eligible weight after filtering
- [x] Added AggregationPopulation and deeply immutable AggregationEvidence
- [x] Reserved metadata.aggregation
- [x] Normalized strategy and policy identifiers to stable lower-camel-case tokens

---

### Step 1.3: Migrate Producers and Core Tests — COMPLETE

**Work completed**:

- [x] Migrated production references across all 11 modules sufficiently for main and test compilation
- [x] Changed CommandJudge and FileContentJudge exception paths from FAIL to ERROR
- [x] Changed missing JaCoCo report outcomes from FAIL to ABSTAIN
- [x] Updated the 21 affected core tests to demonstrate semantic distinctions rather than swap constants
- [x] Added contrast cases for disagreement versus unanimous failure
- [x] Distinguished IGNORE from TREAT_AS_ABSTAIN through aggregation evidence
- [x] Distinguished invalid zero-weight configuration from valid no-eligible-weight outcomes
- [x] Verified a clean agent-judge-core test run

**Verified result**:

- [x] 282 agent-judge-core tests
- [x] 0 failures
- [x] Clean test execution, not an incremental compiler result

**Not yet claimed**:

- [ ] Full reactor test suites pass
- [ ] JaCoCo 80% line / 75% branch gates pass
- [ ] Downstream module runtime/integration tests pass

**Commit**: dc6ca2d — Replace the Score hierarchy with three facts on Judgment

---

## Stage 2: Full-Reactor Verification

### Step 2.0: Fresh-Session Context Load

**Entry criteria**:

- [ ] Work in /home/mark/projects/agent-judge
- [ ] Confirm branch is normalized-judgment-api
- [ ] Confirm HEAD is dc6ca2d or a documented descendant
- [ ] Confirm the only expected untracked content is .campus/
- [ ] Read CLAUDE.md completely
- [ ] Read this roadmap completely
- [ ] Read VISION.md completely
- [ ] Read DESIGN.md completely
- [ ] Read vision-design-review.md completely
- [ ] Read design-normalized-judgment.md completely
- [ ] Read ddd-review.md completely
- [ ] Read the full dc6ca2d commit message
- [ ] Inspect the dc6ca2d diff at least by module and public API

**Work items**:

- [ ] VERIFY the implementation checkpoint agrees with the design; do not assume it
- [ ] IDENTIFY Maven profiles, test exclusions, integration requirements, and JaCoCo configuration
- [ ] RECORD any environmental prerequisites before running the reactor
- [ ] PRESERVE .campus/ without modification

**Exit criteria**:

- [ ] Current repository state and remaining scope are understood
- [ ] No production or test file has been changed during context load
- [ ] Exact full-reactor command is stated before execution
- [ ] Update this roadmap's checkboxes

---

### Step 2.1: Establish the Full-Reactor Result

**Entry criteria**:

- [ ] Step 2.0 complete
- [ ] Working tree contains no new changes

**Work items**:

- [ ] RUN from the repository root:

      ./mvnw clean verify

- [ ] DO NOT substitute an incremental build
- [ ] CAPTURE every module's test count, failures, errors, and skipped tests
- [ ] CAPTURE the JaCoCo line and branch result for agent-judge-core
- [ ] CLASSIFY every failure before editing:
  - expected API migration;
  - expected DELTA semantic change;
  - unexpected regression;
  - environmental or integration failure;
  - flaky or nondeterministic failure.
- [ ] VERIFY the build actually executed downstream modules rather than stopping or skipping them

**Exit criteria**:

- [ ] A complete reactor result exists
- [ ] Every failure has a classification supported by test output and source inspection
- [ ] No failure has been dismissed solely because it appears related to the migration
- [ ] Update this roadmap's checkboxes

---

### Step 2.2: Repair Downstream Consequences

**Entry criteria**:

- [ ] Step 2.1 complete
- [ ] Every observed failure is classified

**Work items**:

- [ ] UPDATE downstream tests to assert settled semantics, not merely new constants
- [ ] UPDATE production code only where the settled contract requires it
- [ ] ADD contrast cases when an old test collapsed two domain outcomes
- [ ] PRESERVE FAIL as a completed negative finding
- [ ] PRESERVE ERROR as inability to complete because of an execution error
- [ ] PRESERVE ABSTAIN as no applicable finding
- [ ] VERIFY CascadedJury policies still read individual judgments where designed
- [ ] VERIFY provider bridge tests do not manufacture scores for status-only judgments
- [ ] RUN focused clean module tests after each repair
- [ ] COMMIT a coherent downstream repair only after its exit criteria pass

**Exit criteria**:

- [ ] All downstream module suites pass in focused clean runs
- [ ] No test merely duplicates implementation without asserting a domain distinction
- [ ] No settled design decision has been reopened without a written, evidenced contradiction
- [ ] Update this roadmap's checkboxes
- [ ] COMMIT if production or test changes were required

---

### Step 2.3: Contract and Obsolete-API Audit

**Entry criteria**:

- [ ] Step 2.2 complete

**Work items**:

- [ ] DECIDE whether to remove the unused ReactiveJudge API and optional Reactor dependency; current evidence shows no implementation, test, production caller, or sample. Do not retain it merely because it has existed since 0.1.0.
- [ ] SEARCH all active production and test sources for obsolete references to:
  - Score
  - BooleanScore
  - NumericalScore
  - CategoricalScore
  - ScoreType
  - Scores
  - old builder reasoning(...) calls
- [ ] ALLOW historical references in design and review documents
- [ ] VERIFY Judgment remains status + optional normalized score + optional label
- [ ] VERIFY score is refused for ABSTAIN and ERROR
- [ ] VERIFY label is permitted for ABSTAIN and refused for ERROR
- [ ] VERIFY lower-case wire statuses and strict upper-case rejection
- [ ] VERIFY absent optionals are omitted and no type metadata is emitted
- [ ] VERIFY Judgment contains no Throwable
- [ ] VERIFY metadata.aggregation is reserved and deeply immutable
- [ ] VERIFY all five strategies expose an ErrorPolicy defaulting to PROPAGATE
- [ ] VERIFY IGNORE and TREAT_AS_ABSTAIN remain observably distinct
- [ ] VERIFY Consensus disagreement and unanimous failure remain distinct
- [ ] VERIFY all-zero configured weights are rejected before aggregation
- [ ] VERIFY strategy names are stable lower-camel-case identifiers

**Exit criteria**:

- [ ] No obsolete API reference remains in active code
- [ ] Every public contract above is covered by an executable test
- [ ] Any unavoidable compatibility consequence is listed for the consumer handoff
- [ ] Update this roadmap's checkboxes

---

### Step 2.4: JaCoCo and Full-Reactor Gate

**Entry criteria**:

- [ ] Steps 2.1-2.3 complete

**Work items**:

- [ ] RUN:

      ./mvnw clean verify

- [ ] VERIFY agent-judge-core line coverage is at least 80%
- [ ] VERIFY agent-judge-core branch coverage is at least 75%
- [ ] VERIFY all configured modules reached their expected lifecycle phases
- [ ] RECORD exact reactor summary and aggregate test totals
- [ ] INVESTIGATE suspiciously fast modules or stale output rather than accepting them

**Exit criteria**:

- [ ] Full clean reactor passes
- [ ] 0 unexpected failures or errors
- [ ] JaCoCo line and branch gates pass
- [ ] Exact test totals, skipped tests, and reactor result are recorded in this roadmap
- [ ] Update this roadmap's checkboxes
- [ ] COMMIT verification-related changes if any

---

### Step 2.K: Stage 2 Consolidation

**Entry criteria**:

- [ ] Steps 2.0-2.4 complete

**Work items**:

- [ ] UPDATE the implementation-status section in design-normalized-judgment.md
- [ ] RECORD downstream findings and final clean-build evidence in this roadmap
- [ ] CONFIRM no accidental files were created or committed
- [ ] CONFIRM .campus/ remains untouched

**Exit criteria**:

- [ ] On-disk status matches the verified implementation
- [ ] Stage 3 can begin without relying on conversation history
- [ ] Update this roadmap's checkboxes
- [ ] COMMIT documentation consolidation

---

## Stage 3: Consumer Migration Handoff

### Step 3.0: Consumer Surface Inventory

**Entry criteria**:

- [ ] Stage 2 complete
- [ ] Full reactor and JaCoCo gates pass

**Work items**:

- [ ] REVIEW agent-workflow read-only for Agent Judge API consumption
- [ ] REVIEW agentworks-pr-review read-only if present and relevant
- [ ] IDENTIFY dependency coordinates and version bumps
- [ ] IDENTIFY source and binary compatibility breaks
- [ ] IDENTIFY score extraction, threshold, error-policy, consensus, and coverage-gate consumers
- [ ] DO NOT edit either consumer repository in this stage

**Known required findings to confirm**:

- [ ] JudgeGate.extractScore and TieredGate.extractScore currently consume NumericalScore.value()
- [ ] Their gate thresholds are normalized while the old value could be raw-range
- [ ] Consumers must distinguish optional stored score from effectiveScore()
- [ ] Missing JaCoCo reports now yield ABSTAIN rather than FAIL
- [ ] VotingStrategy.getName() identifiers changed
- [ ] ErrorPolicy defaults changed to PROPAGATE

**Exit criteria**:

- [ ] Every known consumer has a named migration action or an explicit no-impact finding
- [ ] No downstream repository was modified
- [ ] Update this roadmap's checkboxes

---

### Step 3.1: Write the Consumer Handoff

**Entry criteria**:

- [ ] Step 3.0 complete

**Work items**:

- [ ] CREATE consumer-handoff-normalized-judgment.md in this repository
- [ ] INCLUDE the required Agent Judge dependency/version bump
- [ ] INCLUDE before/after construction examples for:
  - boolean verdicts;
  - normalized numeric scores;
  - raw-range normalization;
  - classifications;
  - abstentions;
  - errors.
- [ ] EXPLAIN that Judgment.score(), when present, is already normalized to [0,1]
- [ ] EXPLAIN when to use the optional stored score and when effectiveScore() is appropriate
- [ ] REQUIRE JudgeGate.extractScore and TieredGate.extractScore to stop reading a raw NumericalScore
- [ ] EXPLAIN the missing-JaCoCo-report change from FAIL to ABSTAIN
- [ ] EXPLAIN how a fail-closed consumer can add an explicit report-existence judge or gate
- [ ] LIST stable lower-camel-case strategy identifiers
- [ ] LIST the PROPAGATE default and all ErrorPolicy choices
- [ ] EXPLAIN Consensus disagreement ABSTAIN versus unanimous FAIL
- [ ] EXPLAIN that ERROR no longer transports Throwable
- [ ] EXPLAIN metadata.aggregation and its stable evidence keys
- [ ] LIST removed classes and incompatible methods
- [ ] LIST any release-note-worthy behavior changes discovered during Stage 2

**Exit criteria**:

- [ ] A consumer can migrate without reading the implementation diff
- [ ] Score semantics and threshold semantics cannot be confused
- [ ] Every deliberate DELTA that affects consumers is named
- [ ] The handoff does not authorize downstream edits
- [ ] Update this roadmap's checkboxes
- [ ] COMMIT

---

### Step 3.2: Handoff Review

**Entry criteria**:

- [ ] Step 3.1 complete

**Work items**:

- [ ] CROSS-CHECK the handoff against design-normalized-judgment.md
- [ ] CROSS-CHECK it against the implemented public API
- [ ] VERIFY every code example compiles or is covered by an equivalent test
- [ ] VERIFY every named consumer location still exists
- [ ] REMOVE no migration warning merely because there is no current production consumer

**Exit criteria**:

- [ ] Handoff and implementation agree
- [ ] Handoff examples are valid
- [ ] Consumer risks are explicit and actionable
- [ ] Update this roadmap's checkboxes
- [ ] COMMIT if review changes were required

---

## Stage 4: Final Project Checkpoint

### Step 4.1: Final Clean Verification

**Entry criteria**:

- [ ] Stages 2 and 3 complete

**Work items**:

- [ ] RUN:

      ./mvnw clean verify

- [ ] RECORD exact reactor summary, test totals, skipped tests, and JaCoCo result
- [ ] RUN git status and inspect every remaining path
- [ ] VERIFY .campus/ is the only expected untracked content
- [ ] VERIFY no licensing/header changes entered the migration diff
- [ ] VERIFY roadmap, design status, and consumer handoff agree

**Exit criteria**:

- [ ] Full reactor passes from clean state
- [ ] JaCoCo gates pass
- [ ] No unexplained working-tree changes remain
- [ ] All migration documentation is current
- [ ] Update this roadmap's checkboxes

---

### Step 4.K: Completion Record

**Entry criteria**:

- [ ] Step 4.1 complete

**Work items**:

- [ ] MARK this roadmap complete with final commit IDs and verification totals
- [ ] UPDATE design-normalized-judgment.md status to implemented and verified
- [ ] SUMMARIZE intentional semantic changes separately from mechanical API changes
- [ ] STATE that consumer repositories remain unchanged unless separately authorized
- [ ] COMMIT the completion record

**Exit criteria**:

- [ ] A cold session can establish final state entirely from repository files and commits
- [ ] No required work remains in the normalized-Judgment migration
- [ ] Branch is ready for review and a separately authorized merge

---

## Historical Roadmap Reconciliation

The ignored plans/ROADMAP.md is a historical product roadmap, not the current backlog. Its unchecked
boxes were audited against repository history and the documentation site.

| Historical item | Evidence | Actual state |
|---|---|---|
| Step 3.6 AgentClient bridge | 10f115d | Delivered |
| Step 3.7 Spring AI bridge | d5e438f | Delivered |
| Step 3.8 README rewrite | 97896b6 | Delivered |
| Step 3.8 release 0.10.0 | 8f4cdb2 and tag v0.10.0 | Delivered |
| Step 3.8 documentation | Current docs cover four adapters, RAG, REFUSED, and Judge/Journal boundary | Substantially delivered; 0.14 update remains |
| Step 3.K learnings consolidation | No step-3.6/3.7/3.8 learnings, LEARNINGS.md, or stage summary | Not completed; superseded by current review |
| Step 3.K inbox triage | Numerous completed review files remain in plans/inbox | Not completed; optional housekeeping |
| Stage 4 AI Core | 1246a08 and release 0.11.0 | Delivered |
| Candidate future features | No committed stage or consumer gate | Candidates, not unfinished commitments |

Do not rebuild delivered modules or fabricate retrospective per-step learnings merely to satisfy old
checkboxes. Preserve the historical roadmap, use vision-design-review.md as the reconciliation record,
and track current work here.

## Out of Scope

- Reopening the settled status/score/label model without a verified contradiction
- Adding a new error hierarchy or transporting Throwable
- Editing agent-workflow or agentworks-pr-review during the handoff stage
- Merging, rebasing, pushing, releasing, or publishing
- Copyright-header replacement
- Customized-license, REUSE, SPDX, SBOM, or broader due-diligence work
- General refactoring unrelated to migration failures
- Treating .campus/ as project-owned content

## Final Stopping Condition

This roadmap is complete only when:

- the full Maven reactor passes from a clean checkout state;
- all downstream suites have actually executed;
- agent-judge-core satisfies 80% line and 75% branch coverage;
- active code contains no obsolete Score-hierarchy references;
- the normalized Judgment and aggregation contracts remain executable in tests;
- the consumer migration handoff is written and reviewed;
- the design and roadmap accurately record final implementation status;
- .campus/ remains untouched;
- no licensing work or downstream repository mutation has been mixed into this branch.

If an external condition prevents completion, record the exact command, failure, affected module,
evidence, and safe next action. Do not mark the corresponding exit criterion complete.

## Revision History

| Timestamp | Change | Trigger |
|---|---|---|
| 2026-08-03T00:00-04:00 | Initial normalized-Judgment completion roadmap | Core checkpoint dc6ca2d completed; fresh-session handoff required |
