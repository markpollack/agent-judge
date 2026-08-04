<!--
Copyright (c) 2026 Mark Pollack
See LICENSE.txt in the repository root for license terms.
-->

# Roadmap: Complete the Normalized Judgment API Migration — Agent Judge

> **Created**: 2026-08-03T00:00-04:00
> **Last updated**: 2026-08-04T13:41-04:00
> **Design version**: design-normalized-judgment.md r10
> **Status**: **COMPLETE** — all five stages verified
> **Active branch**: normalized-judgment-api
> **Current state**: clean full reactor passes across all eleven modules — 487 tests, 0 failures,
> 0 errors, 0 skipped; agent-judge-core at 95.29% line / 93.67% branch coverage
> **Consumer repositories**: reviewed read-only, unchanged

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
- Baseline HEAD for the post-review refinement: 5584f9a
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

### Step 2.0: Fresh-Session Context Load — COMPLETE

**Entry criteria**:

- [x] Work in /home/mark/projects/agent-judge
- [x] Confirm branch is normalized-judgment-api
- [x] Confirm HEAD is dc6ca2d or a documented descendant — HEAD is 76a34ab, the documented
      descendant that added this roadmap
- [x] Confirm the only expected untracked content is .campus/
- [x] Read CLAUDE.md completely
- [x] Read this roadmap completely
- [x] Read VISION.md completely
- [x] Read DESIGN.md completely
- [x] Read vision-design-review.md completely
- [x] Read design-normalized-judgment.md completely
- [x] Read ddd-review.md completely
- [x] Read the full dc6ca2d commit message
- [x] Inspect the dc6ca2d diff at least by module and public API

**Work items**:

- [x] VERIFY the implementation checkpoint agrees with the design; do not assume it
- [x] IDENTIFY Maven profiles, test exclusions, integration requirements, and JaCoCo configuration
- [x] RECORD any environmental prerequisites before running the reactor
- [x] PRESERVE .campus/ without modification

**Checkpoint verification result**:

`Judgment` and `JudgmentStatus` implement the normative design as written: the compact constructor
enforces every invariant in design-normalized-judgment.md §2, `score`/`label` are refused per the
status table, `effectiveScore()` is derived rather than stored, no `Throwable` is present,
`AGGREGATION_KEY` is reserved against `Builder.metadata(...)`, and wire names come from stable
explicit fields with case-sensitive parsing.

One checkpoint claim does not hold. dc6ca2d and design-normalized-judgment.md both state that all
modules reached main **and test** compilation. `agent-judge-llm` test sources still reference the
deleted `BooleanScore` and the removed `Judgment.builder().status(...)`/`.score(Score)` API:

- `agent-judge-llm/src/test/java/.../llm/LLMJudgeTest.java:117`
- `agent-judge-llm/src/test/java/.../llm/CorrectnessJudgeTest.java:78,94`

Predicted as an expected API-migration failure before the reactor run; confirmed in Step 2.1.

**Build configuration inventory**:

| Item | Finding |
|---|---|
| Reactor modules | 10, all listed in the root pom; samples/ and scripts/ are not modules |
| Profiles | `owasp` (CVE gate), `failsafe` (integration tests), `release` (flatten/GPG/sources/javadoc) — none active by default |
| Integration tests | No `*IT.java` sources exist; the `failsafe` profile is inert for this migration |
| Surefire | 3.5.2, no includes/excludes/skips; passes `ANTHROPIC_API_KEY`/`OPENAI_API_KEY` through |
| JaCoCo | agent-judge-core only: `prepare-agent`, `report` at `test`, `check` with BUNDLE limits LINE 0.80 / BRANCH 0.75 |
| Test-count baseline | 45 test source files: core 18, ai-core 5, exec 5, agent-client 3, koog 3, langchain4j 3, llm 3, file 2, spring-ai 2, rag 1 |

**Environmental prerequisites**:

- Java 21 (GraalVM CE 21.0.2) and the Maven wrapper (3.8.6) are present; `./mvnw` is used per CLAUDE.md.
- No `SNAPSHOT` dependency other than the project's own `0.14.0-SNAPSHOT`.
- No test declares `@EnabledIfEnvironmentVariable`, `@Disabled`, or an API-key guard, so no test is
  silently skipped for a missing credential. Surefire's key pass-through is inert here.
- `agent-judge-exec` tests exercise Maven build/coverage runners, so a populated `~/.m2` is needed
  for those to run offline-clean.

**Exit criteria**:

- [x] Current repository state and remaining scope are understood
- [x] No production or test file has been changed during context load
- [x] Exact full-reactor command is stated before execution — `./mvnw clean verify` from the
      repository root, per Step 2.1
- [x] Update this roadmap's checkboxes

---

### Step 2.1: Establish the Full-Reactor Result — COMPLETE

**Entry criteria**:

- [x] Step 2.0 complete
- [x] Working tree contains no new changes

**Work items**:

- [x] RUN from the repository root:

      ./mvnw clean verify

- [x] DO NOT substitute an incremental build
- [x] CAPTURE every module's test count, failures, errors, and skipped tests
- [x] CAPTURE the JaCoCo line and branch result for agent-judge-core
- [x] CLASSIFY every failure before editing
- [x] VERIFY the build actually executed downstream modules rather than stopping or skipping them

#### Run 1 — `./mvnw clean verify` (authoritative gate)

**BUILD FAILURE.** Total time 5.833 s.

| Module | Result | Tests |
|---|---|---|
| Agent Judge (parent) | SUCCESS | — |
| Agent Judge Core | SUCCESS | 282 run, 0 failures, 0 errors, 0 skipped |
| Agent Judge Exec | **FAILURE** | 38 run, 2 failures, 0 errors, 0 skipped |
| File, AI Core, LLM, Koog, LangChain4j, RAG, AgentClient, Spring AI | SKIPPED | not executed |

JaCoCo on agent-judge-core: bundle analyzed with 39 classes, `All coverage checks have been met`
against LINE 0.80 / BRANCH 0.75.

Default fail-fast stopped the reactor at the third module, so run 1 alone does not satisfy "the
build actually executed downstream modules". A second run was therefore made for classification
visibility only.

#### Run 2 — `./mvnw clean verify -fae` (classification visibility)

**BUILD FAILURE.** Total time 10.649 s.

| Module | Result | Tests |
|---|---|---|
| Agent Judge (parent) | SUCCESS | — |
| Agent Judge Core | SUCCESS | 282 run, 0 failures, 0 errors, 0 skipped |
| Agent Judge Exec | **FAILURE** | 38 run, 2 failures, 0 errors, 0 skipped |
| Agent Judge File Comparison | SUCCESS | 11 run, 0 failures, 0 errors, 0 skipped |
| Agent Judge AI Core | **FAILURE** | test compilation failed; no tests run |
| Agent Judge LLM | SKIPPED | banned after the AI Core failure |
| Agent Judge Koog | SUCCESS | 5 run, 0 failures, 0 errors, 0 skipped |
| Agent Judge LangChain4j | SUCCESS | 9 run, 0 failures, 0 errors, 0 skipped |
| Agent Judge RAG | SKIPPED | depends on the banned LLM module |
| Agent Judge AgentClient | SKIPPED | depends on the failed AI Core module |
| Agent Judge Spring AI | SUCCESS | 10 run, 0 failures, 0 errors, 0 skipped |

Executed total across both runs: 355 tests, 2 failures, 0 errors, 0 skipped. Three modules — LLM,
RAG, AgentClient — have still never executed a test suite on this branch. That gap is closed in
Step 2.4, not here.

#### Failure classification

Every failure below is supported by test output plus source inspection of the production code it
covers. None was dismissed for merely resembling the migration.

**F1 — `CoveragePreservationJudgeTest.noReportReturnsFail:87`** — `expected: FAIL but was: ABSTAIN`.
*Expected DELTA semantic change.* design-normalized-judgment.md [DELTA-4a] decided that a missing
JaCoCo report is `ABSTAIN`: absence of a report is not a completed negative finding about coverage.
`CoveragePreservationJudge.java:85` implements that decision correctly. The test still pins the
pre-migration behavior; [DELTA-4a] names this exact test as one that flips and is renamed to
`noReportReturnsAbstain`. dc6ca2d did not touch this test file at all.

**F2 — `CoverageImprovementJudgeTest.noReportReturnsFail:105`** — `expected: FAIL but was: ABSTAIN`.
*Expected DELTA semantic change.* Identical to F1, against `CoverageImprovementJudge.java:109`.
dc6ca2d edited this file but left this test method unmigrated.

**F3 — `agent-judge-ai-core` test compilation:
`LabelJudgmentClassifierTests.java:[57,33] double cannot be dereferenced`.**
*Expected API migration, applied incorrectly at the checkpoint.* The method `categoricalScoreOnMatch`
previously read `((CategoricalScore) judgment.score()).value()`. dc6ca2d rewrote the declaration to
`double score = judgment.score();` but left the following line calling `score.value()` on a
primitive. Beyond not compiling, the assertion is now semantically wrong: under [DELTA-6] a label is
not a score, and `LabelJudgmentClassifier.passFail(...)` declares no numeric mapping, so a matching
classification carries a label and **no** score.

**F4 — `agent-judge-llm` test sources reference deleted types.** *Expected API migration; predicted
from source, not yet executed.* `LLMJudgeTest.java:115-117` calls the removed
`Judgment.builder().status(...).score(new BooleanScore(true))`, and
`CorrectnessJudgeTest.java:78,94` cast `judgment.score()` to `BooleanScore`. `BooleanScore` no
longer exists and the builder has no public `status(...)` or `score(...)`. This module will fail
test compilation as soon as AI Core stops blocking it. It also falsifies the dc6ca2d and
design-normalized-judgment.md claim that all modules reached main **and test** compilation.

**F5 — `agent-judge-rag` and `agent-judge-agent-client` never executed.** *Not a failure; blocked
transitively.* RAG depends on LLM, AgentClient on AI Core. A source scan for removed API
(`Score` and its subtypes, `ScoreType`, `Scores`, `Judgment.builder().status/.score`, builder
`reasoning(...)`, `Judgment.error(String, Throwable)`, `Judgment.error()`) finds no hit in either
module, so neither is predicted to fail. Prediction only — their suites must actually run in
Step 2.4.

No failure classified as an unexpected regression, an environmental or integration failure, or a
flaky or nondeterministic failure. Both runs produced identical failures, so nothing observed is
order- or timing-dependent.

**Exit criteria**:

- [x] A complete reactor result exists — for the eight modules that executed; the three blocked
      modules are recorded above as unexecuted rather than counted as passing
- [x] Every failure has a classification supported by test output and source inspection
- [x] No failure has been dismissed solely because it appears related to the migration
- [x] Update this roadmap's checkboxes

---

### Step 2.2: Repair Downstream Consequences — COMPLETE

**Entry criteria**:

- [x] Step 2.1 complete
- [x] Every observed failure is classified

**Work items**:

- [x] UPDATE downstream tests to assert settled semantics, not merely new constants
- [x] UPDATE production code only where the settled contract requires it — **no production change
      was required.** Every repair was to test code. In all four cases the production code already
      implemented the settled contract and the test still pinned pre-migration behavior.
- [x] ADD contrast cases when an old test collapsed two domain outcomes
- [x] PRESERVE FAIL as a completed negative finding
- [x] PRESERVE ERROR as inability to complete because of an execution error
- [x] PRESERVE ABSTAIN as no applicable finding
- [x] VERIFY CascadedJury policies still read individual judgments where designed
- [x] VERIFY provider bridge tests do not manufacture scores for status-only judgments
- [x] RUN focused clean module tests after each repair
- [x] COMMIT a coherent downstream repair only after its exit criteria pass

**Repairs applied**:

| Failure | Repair | Domain distinction now asserted |
|---|---|---|
| F1 | `CoveragePreservationJudgeTest.noReportReturnsFail` renamed to `noReportReturnsAbstain`, asserting `ABSTAIN` | plus new `missingReportAbstainsWhereMeasuredDropFails`: no measurement versus a measured drop past threshold |
| F2 | `CoverageImprovementJudgeTest.noReportReturnsFail` renamed to `noReportReturnsAbstain`, asserting `ABSTAIN` | plus new `missingReportAbstainsWithoutScoreWhereMeasuredRegressionFailsAtZero`: absent score versus a measured zero |
| F3 | `LabelJudgmentClassifierTests.categoricalScoreOnMatch` replaced by `matchRecordsTheLabelAndNoScore` | plus new `declaredNumericMeaningIsRecordedAlongsideTheLabel`: a label is a number only under a declared policy, per [DELTA-6] |
| F4 | `LLMJudgeTest.TestLLMJudge.parseResponse` now uses `Judgment.passing()`; `CorrectnessJudgeTest` structured YES/NO cases assert stored score absent and `effectiveScore()` projecting 1.0/0.0 | a Boolean verdict's outcome is its status; the numeric view is derived, never a measurement |

The two coverage contrast cases are the executable form of [DELTA-4a]; the classifier pair is the
executable form of [DELTA-6]; the `CorrectnessJudge` pair discharges the "bridges must not
manufacture scores for status-only judgments" check on the module that actually produces them.

`CascadedJury` was verified by inspection and by its passing suite: `hasAnyFail` and `allPassed`
still read `tierVerdict.individual()`, so tier escalation continues to read individual judgments
rather than the aggregate, exactly as [DELTA-2] requires.

**Focused clean run** — `./mvnw clean test -pl agent-judge-exec,agent-judge-ai-core,agent-judge-llm -am`:
BUILD SUCCESS. Core 282, Exec 40, AI Core 33, LLM 13; 0 failures, 0 errors, 0 skipped.

**Full clean reactor** — `./mvnw clean verify`: BUILD SUCCESS, all 11 modules, 16.196 s. This is the
first execution of the LLM, RAG, and AgentClient suites on this branch; all three pass. Detailed
totals are recorded in Step 2.4.

**Exit criteria**:

- [x] All downstream module suites pass in focused clean runs
- [x] No test merely duplicates implementation without asserting a domain distinction
- [x] No settled design decision has been reopened without a written, evidenced contradiction
- [x] Update this roadmap's checkboxes
- [x] COMMIT if production or test changes were required

---

### Step 2.3: Contract and Obsolete-API Audit — COMPLETE

**Entry criteria**:

- [x] Step 2.2 complete

**Work items**:

- [x] DECIDE whether to remove the unused ReactiveJudge API and optional Reactor dependency
      — **DECIDED: remove.** Owner decision, 2026-08-03: "Remove ReactiveJudge. If someone needs it,
      they will ask." Applied and verified; see below.
- [x] SEARCH all active production and test sources for obsolete references
- [x] ALLOW historical references in design and review documents
- [x] VERIFY Judgment remains status + optional normalized score + optional label
- [x] VERIFY score is refused for ABSTAIN and ERROR
- [x] VERIFY label is permitted for ABSTAIN and refused for ERROR
- [x] VERIFY lower-case wire statuses and strict upper-case rejection
- [x] VERIFY absent optionals are omitted and no type metadata is emitted
- [x] VERIFY Judgment contains no Throwable
- [x] VERIFY metadata.aggregation is reserved and deeply immutable
- [x] VERIFY all five strategies expose an ErrorPolicy defaulting to PROPAGATE
- [x] VERIFY IGNORE and TREAT_AS_ABSTAIN remain observably distinct
- [x] VERIFY Consensus disagreement and unanimous failure remain distinct
- [x] VERIFY all-zero configured weights are rejected before aggregation
- [x] VERIFY strategy names are stable lower-camel-case identifiers

#### ReactiveJudge — decided and removed

The 2026-08-03 review's finding was confirmed exactly as written before the decision was taken.
`ReactiveJudge` was a single one-method interface returning `Mono<Judgment>`, and it was the only
reason `agent-judge-core` declared `reactor-core`. Across all ten modules and the samples tree there
was no implementation, test, production caller, or sample; a search for `reactor`, `Mono`, or `Flux`
in Java sources returned that one file plus a `@see ReactiveJudge` tag in `AsyncJudge`.

Owner decision: remove. A reactive consumer can wrap `Judge` directly, and a dedicated adapter can
be introduced when a real consumer asks for one.

Applied:

- deleted `agent-judge-core/src/main/java/io/github/markpollack/judge/ReactiveJudge.java`;
- removed the optional `reactor-core` dependency from `agent-judge-core/pom.xml`;
- removed the now-unreferenced `reactor-bom` import and `reactor.version` property from the root pom;
- removed the dangling `@see ReactiveJudge` tag from `AsyncJudge`;
- corrected DESIGN.md's core dependency boundary and VISION.md's architecture principle, both of
  which asserted the optional reactive dependency as current fact.

`AsyncJudge` is untouched in substance: it uses only JDK `CompletableFuture`.

**Resolution evidence.** `./mvnw dependency:tree -Dincludes='io.projectreactor:*'` was captured
before and after. The only delta is the removal of core's own entry; every other module's transitive
`reactor-core` is unchanged in both version and scope:

| Module | Before | After |
|---|---|---|
| agent-judge-core | reactor-core 3.8.6 compile | **absent** |
| agent-judge-llm | reactor-core 3.8.6 compile | reactor-core 3.8.6 compile |
| agent-judge-koog | reactor-core 3.8.6 provided | reactor-core 3.8.6 provided |
| agent-judge-rag | reactor-core 3.8.6 compile | reactor-core 3.8.6 compile |
| agent-judge-agent-client | reactor-core 3.8.6 provided | reactor-core 3.8.6 provided |
| agent-judge-spring-ai | reactor-core 3.8.6 provided | reactor-core 3.8.6 provided |

Removing the `reactor-bom` import was provably inert: `spring-boot-dependencies` is imported first
and already governed `reactor-core` at 3.8.6, which is why the declared BOM version (2024.0.5) was
never the one in effect. Reactor therefore remains in the build as a transitive Spring AI dependency
of the provider modules — what was removed is core's own declared dependency and the published
`Mono`-shaped API coupling, not Reactor from the reactor as a whole.

#### Obsolete-API search result

No obsolete API reference remains in active code. Specifically:

- the `io.github.markpollack.judge.score` package no longer exists;
- no active Java source references `Score`, `BooleanScore`, `NumericalScore`, `CategoricalScore`,
  `ScoreType`, or `Scores` as a type;
- no source calls the removed builder `reasoning(...)`, `status(...)`, `score(Score)`,
  `Judgment.error(String, Throwable)`, or the `Throwable error()` accessor;
- `samples/` contains no Java sources, so it cannot hold a stale reference.

Five surviving mentions of `BooleanScore` are prose inside `@DisplayName` strings and one comment in
`VotingStrategyCharacterizationTest`, of the form "was BooleanScore(false), a real 0.0". These name
the removed model in order to record what changed, which is that file's purpose. They are retained
deliberately.

#### Stale-semantics repairs made during the audit

The type search was clean, but several active tests still *described* mechanisms the migration
removed. Names and comments were corrected; no assertion was weakened.

| Location | Problem | Repair |
|---|---|---|
| `WeightedAverageStrategyTest.allZeroWeightsShouldResultInNaN` | Name asserted the [DELTA-10] defect; the body already asserted rejection | renamed `allZeroWeightsAreRejectedAsInvalidConfiguration` |
| `WeightedAverageStrategyTest.shouldFallbackToAverageWhenNoWeights` / `...WhenNullWeights` | Named a delegation to `AverageVotingStrategy` that [DELTA-10] removed | renamed to `emptyWeightsResolveToOneAndComputeInStrategy` / `nullWeightsResolveToOneAndComputeInStrategy`, each now also asserting `"strategy": "weightedAverage"` evidence |
| `ConsensusStrategyTest.shouldFailWhenMixedNumericalAndBooleanDisagree` | Name said FAIL while the body asserted `ABSTAIN` | renamed `disagreementAcrossScoredAndStatusOnlyJudgmentsAbstains` |
| `ConsensusStrategyTest.shouldTreatNumericalBelowThresholdAsFail` and neighbours | Comments described the removed 0.5 score-thresholding in `ConsensusStrategy.toBoolean` | renamed `unanimousFailAmongScoredJudgments`; stale "≥ 0.5 → pass" comments removed across the numeric cases |
| `ConsensusStrategyTest.shouldHandleExactThresholdAsPass` | Tested a construction threshold, not strategy behavior, and duplicated an existing case | replaced by `lowScoresDoNotOverrideAPassingStatus`, which pins [DELTA-1] in a case the removed implementation would have decided the other way |

#### Contract-coverage gaps closed

Two contracts this step requires were verified in source but had no executable test. Both are now
covered in `VotingStrategyCharacterizationTest.ErrorPolicyAccounting`:

- `defaultPolicyPropagatesOnEveryStrategy` — the default `ErrorPolicy` was pinned only for
  `MajorityVotingStrategy`. The other four constructed a default and were never exercised with an
  `ERROR` input. This is the same shape of gap that left Majority's own pre-migration default
  uncharacterized, per design-normalized-judgment.md §7. All five defaults now assert aggregate
  `ERROR` plus `"errorPolicy": "propagate"` evidence.
- `ignoreReleasesWeightInWeightedAggregation` — design-normalized-judgment.md §7 requires showing
  that weighted aggregation renormalizes over `eligibleWeight` after an `IGNORE` rather than
  consuming the ignored weight. No test exercised `IGNORE` on any numeric strategy. The new case
  weights an errored judgment at 3.0 against a passing 0.8 weighted 1.0 and asserts the result is
  0.8 with `inputWeight` 4.0 and `eligibleWeight` 1.0.

#### Contract-to-test map

| Contract | Executable coverage |
|---|---|
| status + optional normalized score + optional label | `JudgmentTest.Invariants.scoreBoundaries`, `scoreRejected`, `labelNonBlank` |
| score refused for ABSTAIN and ERROR | `JudgmentTest.Invariants.noScoreForNonMeasurements` |
| label permitted for ABSTAIN, refused for ERROR | `JudgmentTest.Invariants.labelAllowedOnAbstainOnly` |
| lower-case wire statuses, upper case rejected | `JudgmentTest.Serialization.statusWireNames` |
| absent optionals omitted, no type metadata | `JudgmentTest.Serialization.booleanWire`, `noNullsOrTypeTags` |
| no Throwable in Judgment | `JudgmentTest.Serialization.errorWire` |
| metadata.aggregation reserved and deeply immutable | `JudgmentTest.ReservedNamespace.callersRefused`, `unrelatedKeysFine`, `evidenceDeeplyImmutable` |
| all five strategies default to PROPAGATE | `ErrorPolicyAccounting.defaultPolicyPropagatesOnEveryStrategy` **(new)** |
| IGNORE distinct from TREAT_AS_ABSTAIN | `ErrorPolicyAccounting.ignoreVersusTreatAsAbstain`, `noResultReasoningNamesTheCause`, `ignoreReleasesWeightInWeightedAggregation` **(new)** |
| Consensus disagreement distinct from unanimous failure | `ConsensusStrategyTest.disagreementYieldsAbstainAndIsDistinctFromUnanimousFail` |
| all-zero configured weights rejected | `WeightedAverageStrategyTest.allZeroWeightsAreRejectedAsInvalidConfiguration`, characterization `allZeroWeightsRejected` |
| strategy names are stable lower-camel-case | `nameIsTheStableTokenUsedInEvidence` in each strategy test; `universalKeysAlwaysPresent` cross-checks all five |

#### Compatibility consequences for the consumer handoff

Carried forward to Step 3.1 in addition to the items the roadmap already lists:

1. `ReactiveJudge` is removed. This is source- and binary-breaking for any external implementor,
   permitted at the 0.14 pre-1.0 boundary but owed a migration note: a reactive consumer should wrap
   `Judge` itself, for example
   `Mono.fromCallable(() -> judge.judge(context)).subscribeOn(Schedulers.boundedElastic())`.
2. `agent-judge-core` no longer declares `reactor-core`, not even optionally. A consumer that
   relied on inheriting Reactor from agent-judge-core — which optional scope already prevented for
   transitive users — must declare it directly.
3. `~/projects/docs/docs/agent-judge/api-reference.mdx` still documents the removed Score hierarchy
   and `ReactiveJudge`. Documentation reconciliation is outside this roadmap's stages; recorded so it
   is not mistaken for done.

**Exit criteria**:

- [x] No obsolete API reference remains in active code
- [x] Every public contract above is covered by an executable test
- [x] Any unavoidable compatibility consequence is listed for the consumer handoff
- [x] Update this roadmap's checkboxes

**Step remains open** until the ReactiveJudge decision is recorded.

---

### Step 2.4: JaCoCo and Full-Reactor Gate — COMPLETE

**Entry criteria**:

- [x] Steps 2.1-2.3 complete

The gate was run twice. The first run passed before the ReactiveJudge decision was taken; the
decision then changed production code, so the gate was re-run afterwards and is recorded below in
its final form. Both runs produced identical totals and coverage, which is the expected result of
deleting an interface that had no implementation, test, or caller.

**Work items**:

- [x] RUN:

      ./mvnw clean verify

- [x] VERIFY agent-judge-core line coverage is at least 80%
- [x] VERIFY agent-judge-core branch coverage is at least 75%
- [x] VERIFY all configured modules reached their expected lifecycle phases
- [x] RECORD exact reactor summary and aggregate test totals
- [x] INVESTIGATE suspiciously fast modules or stale output rather than accepting them

#### Reactor summary — `./mvnw clean verify`, BUILD SUCCESS, 16.491 s

| Module | Result | Time | Tests | Failures | Errors | Skipped |
|---|---|---|---|---|---|---|
| Agent Judge (parent) | SUCCESS | 0.031 s | — | — | — | — |
| Agent Judge Core | SUCCESS | 3.838 s | 284 | 0 | 0 | 0 |
| Agent Judge Exec | SUCCESS | 1.499 s | 40 | 0 | 0 | 0 |
| Agent Judge File Comparison | SUCCESS | 1.391 s | 11 | 0 | 0 | 0 |
| Agent Judge AI Core | SUCCESS | 0.996 s | 33 | 0 | 0 | 0 |
| Agent Judge LLM | SUCCESS | 2.129 s | 13 | 0 | 0 | 0 |
| Agent Judge Koog | SUCCESS | 1.901 s | 5 | 0 | 0 | 0 |
| Agent Judge LangChain4j | SUCCESS | 0.920 s | 9 | 0 | 0 | 0 |
| Agent Judge RAG | SUCCESS | 0.851 s | 18 | 0 | 0 | 0 |
| Agent Judge AgentClient | SUCCESS | 1.906 s | 10 | 0 | 0 | 0 |
| Agent Judge Spring AI | SUCCESS | 0.920 s | 10 | 0 | 0 | 0 |
| **Aggregate** | | | **433** | **0** | **0** | **0** |

#### JaCoCo — agent-judge-core

| Counter | Covered | Total | Ratio | Gate | Result |
|---|---|---|---|---|---|
| LINE | 686 | 732 | 93.72% | 80% | PASS |
| BRANCH | 269 | 291 | 92.44% | 75% | PASS |

Plugin output: bundle `agent-judge-core` analyzed with 39 classes, `All coverage checks have been
met`.

#### Fast-module and stale-output investigation

Every module was checked rather than accepted on elapsed time. The reactor emits no
`Tests are skipped` or `No tests to run` line anywhere. Each of the ten code modules ran surefire
and reported a non-zero test count; the fastest, Koog at 1.979 s with 5 tests, has exactly three
test sources, and its count matches. No module was under-executed:

- the run began with `clean`, so every `target` was deleted and rebuilt — no stale classes or
  surefire reports could be read;
- module test counts equal the sum of their per-class counts in the same log;
- the three modules that had never executed before this session — LLM, RAG, AgentClient — all ran
  and passed;
- no integration-test phase was silently skipped: the repository contains no `*IT.java` and the
  `failsafe` profile was not active.

**Exit criteria**:

- [x] Full clean reactor passes
- [x] 0 unexpected failures or errors — 0 failures and 0 errors of any kind
- [x] JaCoCo line and branch gates pass
- [x] Exact test totals, skipped tests, and reactor result are recorded in this roadmap
- [x] Update this roadmap's checkboxes
- [x] COMMIT verification-related changes if any

---

### Step 2.K: Stage 2 Consolidation — COMPLETE

**Entry criteria**:

- [x] Steps 2.0-2.4 complete

**Work items**:

- [x] UPDATE the implementation-status section in design-normalized-judgment.md
- [x] RECORD downstream findings and final clean-build evidence in this roadmap
- [x] CONFIRM no accidental files were created or committed
- [x] CONFIRM .campus/ remains untouched

#### Stage 2 commits

| Commit | Content |
|---|---|
| 9325ef3 | Migrated the five remaining downstream test files (Steps 2.1-2.2) |
| 014a779 | Closed two contract-coverage gaps, retired stale aggregation test prose (Step 2.3) |
| c7d445c | Removed ReactiveJudge and the Reactor dependency from core (Step 2.3 decision) |
| this commit | Stage 2 consolidation (Step 2.K) |

Stage 2 changed **no production behavior**. The only production change was deleting an unused
interface on an explicit owner decision. Every other repair was to test code, in each case because
the test still pinned behavior the settled design had deliberately changed.

#### Findings worth carrying forward

1. **The dc6ca2d checkpoint overstated its own verification.** It claimed all modules reached main
   and test compilation; two modules' test sources did not compile, and three module suites had
   never run. Corrected in design-normalized-judgment.md rather than silently fixed.
2. **Two required contracts held only in source.** The default `ErrorPolicy` was pinned for one
   strategy of five, and `IGNORE` was never exercised on a numeric strategy. Both are now executable.
3. **Stale prose outlived the code it described.** Test names and comments still narrated a weighted
   delegation, a NaN result, and a 0.5 consensus threshold that no longer exist — the same defect
   shape ddd-review.md Issue 4 raised against `MajorityVotingStrategy`'s Javadoc.
4. **Public documentation is not yet reconciled.** `~/projects/docs/docs/agent-judge/api-reference.mdx`
   still documents the Score hierarchy and `ReactiveJudge`. Outside this roadmap's stages.

#### Repository hygiene

- `git status` shows `.campus/` as the only untracked entry, exactly as at session start.
- `.campus/` appears in no commit on any branch; it was never added, edited, or removed.
- No accidental files were created in the repository. Build logs and dependency-tree captures were
  written to the session scratchpad outside the working tree.
- No licensing, REUSE, SBOM, or copyright-header change entered any Stage 2 commit.
- No downstream repository was read or modified.

**Exit criteria**:

- [x] On-disk status matches the verified implementation
- [x] Stage 3 can begin without relying on conversation history
- [x] Update this roadmap's checkboxes
- [x] COMMIT documentation consolidation

---

## Stage 3: Consumer Migration Handoff

### Step 3.0: Consumer Surface Inventory — COMPLETE

**Entry criteria**:

- [x] Stage 2 complete
- [x] Full reactor and JaCoCo gates pass

**Work items**:

- [x] REVIEW agent-workflow read-only for Agent Judge API consumption
- [x] REVIEW agentworks-pr-review read-only if present and relevant
- [x] IDENTIFY dependency coordinates and version bumps
- [x] IDENTIFY source and binary compatibility breaks
- [x] IDENTIFY score extraction, threshold, error-policy, consensus, and coverage-gate consumers
- [x] DO NOT edit either consumer repository in this stage

**Known required findings to confirm**:

- [x] JudgeGate.extractScore and TieredGate.extractScore currently consume NumericalScore.value()
      — **confirmed**, `JudgeGate.java:90` and `TieredGate.java:71`
- [x] Their gate thresholds are normalized while the old value could be raw-range — **confirmed**
- [x] Consumers must distinguish optional stored score from effectiveScore() — **confirmed, and
      sharper than stated**; see the consensus finding below
- [x] Missing JaCoCo reports now yield ABSTAIN rather than FAIL — **no impact on either consumer**;
      neither depends on agent-judge-exec, so no coverage judge is reachable. `BuildSuccessJudge`
      appears only in agent-workflow Javadoc examples.
- [x] VotingStrategy.getName() identifiers changed — **no impact**; neither repository calls
      `getName()` on a strategy
- [x] ErrorPolicy defaults changed to PROPAGATE — **confirmed as a silent behavioral change**;
      neither repository configures an `ErrorPolicy`, so both inherit the new default

#### Consumer coordinates

| Repository | Branch | HEAD | Depends on | Pinned version |
|---|---|---|---|---|
| agent-workflow | v3 | b10144a | agent-judge-core (api, core, flows) | 0.12.0 |
| agentworks-pr-review | v3-serving | 03fc68f | agent-judge-core | 0.11.0-SNAPSHOT |

Both must move to 0.14.0. Neither was modified; both were read only.

#### agent-workflow — named migration actions

| Location | Removed API | Action |
|---|---|---|
| `SpringAiJuryAdapter:113,165`, `TerminationStrategy:158`, `TurnLimitedResult:56`, `JuryTerminationStrategy:97`, `StateMachineLoop:261`, `EvaluatorOptimizerLoop:251`, `JuryPassedException:68,75`, `TurnLimitedLoop:317` | `Scores.toNormalized(verdict.aggregated().score(), Map.of())` — 10 call sites in 8 files | replace with `effectiveScore()` and state an explicit policy for an absent score |
| `JudgeGate.java:89-93` | `Score` + `instanceof NumericalScore` + `ns.value()` | replace with `effectiveScore()`; this also fixes the raw-versus-normalized defect |
| `TieredGate.java:70-74` | same | same |
| `GateTest.java:408,425` | `Judgment.builder().score(new NumericalScore(...)).status(...)` | rebuild via `Judgment.scored(...).withStatus(...)` |
| `GateTest.java:410,429` | builder `reasoning(String)` | `because(String)` |

> **`Scores` was not dead code.** ddd-review.md Warning 1 called `Scores.toNormalized` dead,
> "referenced only by its own unit test — verified by grep across all modules". That grep covered
> agent-judge's modules only. It is the single most-used Agent Judge API in agent-workflow, at 10
> call sites. Deleting it is correct, but the blast radius recorded in design-normalized-judgment.md
> §6 understated the consumer cost, and the handoff must lead with this.

> **The consensus trap — the most dangerous finding in this inventory.** Under 0.13, an aggregate
> from `ConsensusStrategy` or `MajorityVotingStrategy` carried a `BooleanScore`, so
> `Scores.toNormalized(...)` returned 1.0 or 0.0 and threshold gates worked. Under 0.14 those two
> strategies **emit no score at all**, by design. A consumer that ports
> `Scores.toNormalized(x.score(), Map.of())` to the visually obvious `x.score()` gets `null` on
> every verdict. Worse, the old `Scores.toNormalized` mapped `null` to 0.0 and both gates already
> return 0.0 for a null score — so the port compiles, runs, and silently fails every gate including
> unanimous passes. `agent-workflow` wires exactly this combination in `GateTest:413`
> (`ConsensusStrategy` behind a score gate). The correct port is `effectiveScore()`.

#### agentworks-pr-review — named migration actions

| Location | Removed API | Action |
|---|---|---|
| `VersionPatternJudge:58,86` | `.score(new BooleanScore(...))` + `.status(...)` + `.reasoning(...)` | `Judgment.verdict(boolean).because(...)` — the Boolean score was always a duplicate of the status |
| `BuildJudge:59` | `.score(new BooleanScore(allPassed))` | `Judgment.verdict(allPassed).because(...)` |
| `QualityJudge:78` | `.score(NumericalScore.normalized(score))` | `Judgment.scored(score).withStatus(...)` or `.passingAt(...)` |
| `TestAssessments:64,79,90,106` | same builder shape in test fixtures | same |

Its `CascadedJury`, `TierConfig`, and `TierPolicy` usage is structurally unaffected: tier policies
read individual judgments, which [DELTA-2] deliberately left untouched.

#### Explicit no-impact findings

- `Judgment.elapsed()` — not consumed by either repository. Every `elapsed()` hit in agent-workflow
  is `LoopState.elapsed()`, an unrelated type.
- `Judgment.error(String, Throwable)` and the `Throwable error()` accessor — no call site in either
  repository, so removing the Throwable transport breaks neither.
- `VotingStrategy.getName()` — no consumer.
- Coverage judges and `agent-judge-exec` — not a dependency of either repository.
- agent-workflow's own `workflow-spec/.../v3/envelope/Judgment.java` DTO
  (`String memberId, @Nullable Double score, ...`) is unaffected: it is a hand-rolled wire type that
  never referenced the Score hierarchy. It remains the strongest evidence the 0.14 shape is right.

**Exit criteria**:

- [x] Every known consumer has a named migration action or an explicit no-impact finding
- [x] No downstream repository was modified
- [x] Update this roadmap's checkboxes

---

### Step 3.1: Write the Consumer Handoff — COMPLETE

**Entry criteria**:

- [x] Step 3.0 complete

**Work items**:

- [x] CREATE consumer-handoff-normalized-judgment.md in this repository
- [x] INCLUDE the required Agent Judge dependency/version bump
- [x] INCLUDE before/after construction examples for boolean verdicts, normalized numeric scores,
      raw-range normalization, classifications, abstentions, and errors
- [x] EXPLAIN that Judgment.score(), when present, is already normalized to [0,1]
- [x] EXPLAIN when to use the optional stored score and when effectiveScore() is appropriate
- [x] REQUIRE JudgeGate.extractScore and TieredGate.extractScore to stop reading a raw NumericalScore
- [x] EXPLAIN the missing-JaCoCo-report change from FAIL to ABSTAIN
- [x] EXPLAIN how a fail-closed consumer can add an explicit report-existence judge or gate
- [x] LIST stable lower-camel-case strategy identifiers
- [x] LIST the PROPAGATE default and all ErrorPolicy choices
- [x] EXPLAIN Consensus disagreement ABSTAIN versus unanimous FAIL
- [x] EXPLAIN that ERROR no longer transports Throwable
- [x] EXPLAIN metadata.aggregation and its stable evidence keys
- [x] LIST removed classes and incompatible methods
- [x] LIST any release-note-worthy behavior changes discovered during Stage 2

The document leads with the two changes that fail **silently**, because everything else in this
migration is a compile error and therefore self-announcing. Those two are the consensus/majority
score-absence trap found in Step 3.0 and the `ErrorPolicy` default moving to `PROPAGATE`.

**Exit criteria**:

- [x] A consumer can migrate without reading the implementation diff
- [x] Score semantics and threshold semantics cannot be confused
- [x] Every deliberate DELTA that affects consumers is named
- [x] The handoff does not authorize downstream edits — stated in its header and §9
- [x] Update this roadmap's checkboxes
- [x] COMMIT

---

### Step 3.2: Handoff Review — COMPLETE

**Entry criteria**:

- [x] Step 3.1 complete

**Work items**:

- [x] CROSS-CHECK the handoff against design-normalized-judgment.md
- [x] CROSS-CHECK it against the implemented public API
- [x] VERIFY every code example compiles or is covered by an equivalent test
- [x] VERIFY every named consumer location still exists
- [x] REMOVE no migration warning merely because there is no current production consumer

#### Code examples compile

Every Java example in the handoff was extracted into a single scratch compilation unit and compiled
with `javac` against `agent-judge-core/target/classes`, `agent-judge-ai-core/target/classes`, and
the resolved Maven classpath. **Exit code 0.** The file lives in the session scratchpad, not in the
repository.

This covers the correct and the deliberately-wrong port in §1.1, the explicit `ErrorPolicy`
construction, `score()`/`effectiveScore()`/`hasError()`, all six construction forms, the
`LabelJudgmentClassifier` declared-score builder, the fail-closed report-existence judge, and
evidence access through `Judgment.AGGREGATION_KEY`. Compiling the *wrong* port is the point: it is
what makes the §1.1 warning necessary.

#### Facts verified against source rather than against the design document

| Claim | Verification |
|---|---|
| Exact JSON in §7 | Character-identical to the strings asserted in `JudgmentTest.Serialization` |
| Evidence key names in §5.4 | Match the `AggregationEvidence` constants |
| `ErrorPolicy` tokens | Match the enum's stable token fields |
| 0.13 `getName()` values in §5.5 | Read from `dc6ca2d^`: `majority`, `Consensus`, `AverageVoting`, `WeightedAverage`, `MedianVoting` |
| `Scores.toNormalized` mapped null to 0.0 | Read from the deleted source at `dc6ca2d^` |
| §1.1's claim that the null case was unreachable in 0.13 | Confirmed: `MajorityVotingStrategy:116` and `ConsensusStrategy:80` at `dc6ca2d^` always attached a `BooleanScore` to the aggregate |
| §6.2's FAIL-to-ERROR change | Confirmed at `CommandJudge:166` and `FileContentJudge:111`, both now `Judgment.erroring()` |
| All 14 named consumer locations | Every file still exists; the `Scores.toNormalized` count re-verified at 10 call sites across 8 files |

#### Warnings deliberately retained despite no current consumer

Per the roadmap's instruction not to drop a warning merely because nothing consumes it today, the
handoff still documents the removal of `ReactiveJudge`, `Judgment.error(String, Throwable)`, the
`Throwable error()` accessor, the `"error"` metadata convention, and the `VotingStrategy.getName()`
identifier changes — none of which any inventoried consumer uses.

**Exit criteria**:

- [x] Handoff and implementation agree
- [x] Handoff examples are valid — compiled, not merely reviewed
- [x] Consumer risks are explicit and actionable
- [x] Update this roadmap's checkboxes
- [x] COMMIT if review changes were required — no corrections were needed; the review confirmed the
      document as written

---

## Stage 4: Final Project Checkpoint

### Step 4.1: Final Clean Verification — COMPLETE

**Entry criteria**:

- [x] Stages 2 and 3 complete

**Work items**:

- [x] RUN:

      ./mvnw clean verify

- [x] RECORD exact reactor summary, test totals, skipped tests, and JaCoCo result
- [x] RUN git status and inspect every remaining path
- [x] VERIFY .campus/ is the only expected untracked content
- [x] VERIFY no licensing/header changes entered the migration diff
- [x] VERIFY roadmap, design status, and consumer handoff agree

#### Final reactor — BUILD SUCCESS, 15.226 s

| Module | Result | Tests | Failures | Errors | Skipped |
|---|---|---|---|---|---|
| Agent Judge (parent) | SUCCESS | — | — | — | — |
| Agent Judge Core | SUCCESS | 284 | 0 | 0 | 0 |
| Agent Judge Exec | SUCCESS | 40 | 0 | 0 | 0 |
| Agent Judge File Comparison | SUCCESS | 11 | 0 | 0 | 0 |
| Agent Judge AI Core | SUCCESS | 33 | 0 | 0 | 0 |
| Agent Judge LLM | SUCCESS | 13 | 0 | 0 | 0 |
| Agent Judge Koog | SUCCESS | 5 | 0 | 0 | 0 |
| Agent Judge LangChain4j | SUCCESS | 9 | 0 | 0 | 0 |
| Agent Judge RAG | SUCCESS | 18 | 0 | 0 | 0 |
| Agent Judge AgentClient | SUCCESS | 10 | 0 | 0 | 0 |
| Agent Judge Spring AI | SUCCESS | 10 | 0 | 0 | 0 |
| **Aggregate** | | **433** | **0** | **0** | **0** |

JaCoCo on agent-judge-core: LINE 686/732 = **93.72%** (gate 80%), BRANCH 269/291 = **92.44%**
(gate 75%). Plugin reports `All coverage checks have been met`. Totals and coverage are identical to
the Step 2.4 gate, as expected — Stages 3 and 4 changed no code.

#### Working-tree inspection

`git status --porcelain --untracked-files=all` lists exactly one path: `.campus/status.json`. It is
the user's file, was present at session start, and appears in no commit on any branch. There are no
modified or staged paths and no other untracked content.

#### Licensing and header verification

The full branch diff against main was inspected, not sampled:

- no path matching `LICENSE`, `NOTICE`, `REUSE`, `.spdx`, `sbom`, `bom.json`, `COPYING`, `dep5`, or
  `.reuse` was touched;
- copyright and license-header lines show +3 / −9 across the branch. Every one of those is a whole
  file being added or deleted — 9 deleted Java files (the 6 score types, 2 score tests, and
  `ReactiveJudge`) and 3 added ones (`AggregationEvidence`, `AggregationPopulation`,
  `VotingStrategyCharacterizationTest`);
- header lines changed inside **modified** files: **0**, confirmed by
  `git diff --diff-filter=M main..HEAD -- '*.java'`.

No licensing, REUSE, SBOM, or copyright-header work entered this branch.

#### Document agreement

roadmap.md and design-normalized-judgment.md both state 433 tests and 93.72% / 92.44%, matching the
run above. VISION.md and DESIGN.md carried stale status text describing the migration as in progress
and were corrected in the completion commit. consumer-handoff-normalized-judgment.md states no test
totals and contradicts nothing.

CLAUDE.md was also stale and was corrected, but it is gitignored in this repository and therefore
not part of any commit. That edit is local only and will not reach a fresh clone.

**Exit criteria**:

- [x] Full reactor passes from clean state
- [x] JaCoCo gates pass
- [x] No unexplained working-tree changes remain
- [x] All migration documentation is current
- [x] Update this roadmap's checkboxes

---

### Step 4.K: Completion Record — COMPLETE

**Entry criteria**:

- [x] Step 4.1 complete

**Work items**:

- [x] MARK this roadmap complete with final commit IDs and verification totals
- [x] UPDATE design-normalized-judgment.md status to implemented and verified
- [x] SUMMARIZE intentional semantic changes separately from mechanical API changes
- [x] STATE that consumer repositories remain unchanged unless separately authorized
- [x] COMMIT the completion record

#### Commit record

| Commit | Stage | Content |
|---|---|---|
| 49a6c73 | 0.1 | DDD review and normalized-Judgment design proposal |
| a5b15f8 | 0.2 | Pre-change characterization baseline, 36 cases |
| dc6ca2d | 1 | Score hierarchy replaced by three facts on Judgment |
| 76a34ab | — | Completion roadmap |
| 9325ef3 | 2.1-2.2 | Remaining downstream tests migrated |
| 014a779 | 2.3 | Contract-coverage gaps closed, stale test prose retired |
| c7d445c | 2.3 | ReactiveJudge and the Reactor dependency removed |
| fbbd48f | 2.K | Verified state recorded on disk |
| aa2b3c9 | 3.1-3.2 | Consumer migration handoff written and reviewed |
| this commit | 4.K | Completion record |

**Final verification**: `./mvnw clean verify` from clean — BUILD SUCCESS, eleven modules, 433 tests,
0 failures, 0 errors, 0 skipped; agent-judge-core at 93.72% line and 92.44% branch coverage.

#### Intentional semantic changes

These change what a judgment or verdict *means*. Each is a deliberate domain correction with an
evidenced rationale, and each is named in the consumer handoff.

1. **Abstention is no longer a negative vote.** `ABSTAIN` leaves the aggregation population entirely
   — numerator, denominator, vote count, and weight. It previously carried `BooleanScore(false)`, so
   a judge that declined to assess cast the strongest possible negative vote.
2. **Errors follow an explicit policy, defaulting to `PROPAGATE`.** Previously `Majority` defaulted
   to an implicit `TREAT_AS_FAIL` and the other four silently scored an error as `0.0`.
3. **`IGNORE` genuinely filters.** It removes an errored judgment from the population rather than
   mapping it to an abstention, making it observably distinct from `TREAT_AS_ABSTAIN`.
4. **Consensus reads status, not score, and reports disagreement as `ABSTAIN`.** Unanimous failure
   and an undecided panel are no longer the same outcome.
5. **Nothing eligible yields `ABSTAIN`** rather than a manufactured failing score.
6. **`Majority` and `Consensus` emit no score.** They aggregate outcomes, not quantities.
7. **An all-zero weight map is rejected** as invalid configuration; it previously produced a `NaN`
   score that slipped past range validation. A valid configuration whose eligible weight vanishes
   after filtering abstains instead.
8. **`WeightedAverage` no longer delegates to `Average`** for empty weights; the number is unchanged
   but the evidence now names the strategy the caller chose.
9. **A missing JaCoCo report abstains rather than fails.** Absence of a report is not a finding about
   coverage. This is fail-open for anyone relying on "no report ⇒ gate closed".
10. **`CommandJudge` and `FileContentJudge` report a caught exception as `ERROR`**, conforming to the
    seven judges that already did.
11. **A degenerate score range (`max <= min`) is rejected** rather than special-cased.

#### Mechanical API changes

These change how the same meaning is expressed.

- `Score`, `BooleanScore`, `NumericalScore`, `CategoricalScore`, `ScoreType`, and `Scores` deleted;
  the whole `judge.score` package is gone.
- `Judgment` carries required `status`, optional normalized `score`, optional `label`, with all
  invariants in the compact constructor.
- Staged factories replace the free-for-all builder; `because(String)` replaces `reasoning(String)`.
- `effectiveScore()` added as the single derived numeric view.
- Throwable transport removed: no `error(String, Throwable)`, no `Throwable error()` accessor, no
  `"error"` metadata convention.
- Stable lower-case status wire names with case-sensitive parsing; absent optionals omitted.
- `AggregationEvidence` and `AggregationPopulation` added; `metadata.aggregation` reserved and deeply
  immutable.
- `VotingStrategy.getName()` identifiers normalized to lower-camel-case.
- `ReactiveJudge` and `agent-judge-core`'s optional `reactor-core` dependency removed. This one is an
  owner decision about scope rather than a consequence of the value model.

#### Consumer repositories

`agent-workflow` and `agentworks-pr-review` were reviewed **read-only** and **remain unchanged**.
Their local working-tree modifications predate this session. Migrating them is separate work that
requires its own authorization; consumer-handoff-normalized-judgment.md is written so it can be done
without reading this branch's diff.

#### Work deliberately not done

- Public documentation at `docs/agent-judge/` still describes the Score hierarchy and
  `ReactiveJudge`.
- Licensing, REUSE, SPDX, SBOM, and copyright-header work — a separate workstream, explicitly out of
  scope here and verified absent from this branch.
- No merge, rebase, push, release, or publish.

**Exit criteria**:

- [x] A cold session can establish final state entirely from repository files and commits
- [x] No required work remains in the normalized-Judgment migration
- [x] Branch is ready for review and a separately authorized merge

---

## Stage 5: Post-review API and Verdict Refinement — COMPLETE

### Step 5.1: Restore concise derived-outcome construction — COMPLETE

**Work completed**:

- [x] Kept `builder().pass()/fail()/abstain()/error()` as the general outcome-first grammar
- [x] Added `Judgment.verdict(boolean)` without storing a duplicate Boolean score
- [x] Added `Judgment.scored(normalized).passingAt(threshold)`
- [x] Added raw-range normalization through `Judgment.scored(value, minimum, maximum)`
- [x] Rejected non-finite values, out-of-range values, invalid ranges, and invalid thresholds
- [x] Pinned below, exact-threshold, and above-threshold outcomes

### Step 5.2: Make every Verdict complete — COMPLETE

**Work completed**:

- [x] Required a non-null aggregate in the `Verdict` compact constructor
- [x] Replaced the former null-aggregate characterization with a rejection test
- [x] Added `Verdict.single(name, judgment)` for a complete one-member jury
- [x] Preserved both roles of the one judgment: aggregate conclusion and named/ordered evidence
- [x] Rejected blank identities and null judgments in the convenience factory

### Step 5.3: Reconcile documentation and verify — COMPLETE

**Work completed**:

- [x] Removed nonexistent `because`, `withStatus`, and `classified` APIs from the active design and
  consumer-handoff examples
- [x] Documented the distinction between a complete one-member verdict and an aggregate-free result
- [x] Recorded M1 and M4 as implemented in the 2026-08-04 Agent Workflow integration review packet
- [x] Ran `./mvnw clean verify` from a clean reactor state

**Verified result**:

- Eleven reactor modules: SUCCESS
- 487 tests, 0 failures, 0 errors, 0 skipped
- agent-judge-core line coverage: 729/765 = 95.29% (gate 80%)
- agent-judge-core branch coverage: 296/316 = 93.67% (gate 75%)

**Still outside this scoped stage**: the remaining Agent Workflow integration-review items—portable
metadata, schema-visible optionality, the consensus-disagreement decision, status-first consumer
gates, journal projection, and cross-repository conformance fixtures. This stage does not mark those
separate integration tasks complete.

---

## Roadmap status: COMPLETE

Every stopping condition below is satisfied. The branch is ready for review and a separately
authorized merge.

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
| 2026-08-03T16:00-04:00 | Stage 2 completed and recorded: reactor result, failure classification, downstream repairs, contract audit, ReactiveJudge removal, and final gate | Full-reactor verification executed; owner decided the ReactiveJudge question |
| 2026-08-03T16:15-04:00 | Stage 3 completed: read-only consumer inventory and the reviewed consumer handoff | Stage 2 gates passed |
| 2026-08-03T16:20-04:00 | Stage 4 completed; roadmap marked COMPLETE | Final clean verification passed and the completion record was written |
| 2026-08-04T13:41-04:00 | Stage 5 completed: concise verdict/scored helpers, mandatory aggregate, one-member factory, documentation reconciliation, and clean verification | Agent Workflow integration review M1/M4 accepted for implementation |
