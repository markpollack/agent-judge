# Agent Eval portable results, version 2

This is the normative producer and authoritative-reading contract for version-2
`Judgment`, `Verdict`, and `Interpretation`. JSON objects, arrays, strings, booleans,
finite numbers and null represent values; Java class names never identify a wire type.
The Java entry points are `Verdicts.interpret(Verdict)` and
`Verdicts.interpret(Map<String,Object>)`. They implement the same reading.

## Version selection

Every Judgment and Verdict object, including child checks, ordered and named copies,
and composite child Verdicts, carries the exact JSON integer `schemaVersion: 2`.
A missing, null, fractional, string, boolean or different stamp is invalid for a v2
result. No coercion such as `2.9` to `2` is allowed. Other value objects do not have
independent version stamps. A version-2 tree cannot embed historical result shapes.

Interpretation has `schemaVersion: 2`. `sourceVersion: 2` identifies v2 source data.
Historical unversioned records retain sourceVersion 0 (before the recorded seats and
decision form) or 1 (the unversioned 0.17 form with seats and decision). An unknown
integer version is retained as sourceVersion; a malformed or missing modern stamp
uses -1. An explicit stamp always selects version handling before historical shape
heuristics. Modern semantic fields on structural result edges without a stamp are
unsupported, even when a forged legacy `status: pass` is also present. Incidental
metadata keys do not select the protocol.

An unsupported modern result has `reading: null`,
`readingSupport: UNDETERMINED`, and explicit defects. It must never supply an
ACCEPTED or REJECTED determination. This includes unknown semantic tokens, mixed
versions, malformed values, absent required fields and contradictory orchestration.
This is a reading-support statement, never a statistical confidence claim.

## Judgment

Required fields are `schemaVersion`, `producerStatus`, `reasoning`, `checks`, and
`metadata`. Optional fields are `assessment`, `certainty`, `distribution`,
`reasonCode`, `provenance`, and `policyApplication`; absence is preserved. Producers
omit absent optionals; readers also accept explicit null for optional fields.
`checks` and `metadata` are always present, including when empty.

`producerStatus` is one of `pass`, `fail`, `abstain`, `not_applicable`, `error`.
It is the producer's finding before application policy. There is no independently
writable top-level status, score or label in a modern Judgment.

| Value | Fields and constraints |
|---|---|
| Assessment | Product of optional `proposition`, `numeric`, `category`; at least one is present. |
| Proposition | `value` is true, false or null (unknown); false is an explicit negative. |
| NumericAssessment | Finite `value`, `lower`, `upper`, with lower < upper and value in bounds; `kind`, nonblank `scaleId`, ordered `levels`, optional `qualityDirection`. |
| NumericKind | `MEASUREMENT` has no levels. `ORDINAL_EXPECTATION` has at least two unique nonblank levels, bounds 0 and k−1, and may have fractional value. |
| QualityDirection | `INCREASING` or `DECREASING`; absence forbids an inferred quality projection. |
| Category | Optional `selected` and complete declared ordered `alternatives`; alternatives are nonempty, unique and nonblank; selected, when present, belongs to them. A declared singleton is not a claim that no other real-world categories exist. |
| Certainty | Finite `value` in [0,1], nonblank `metricId`, `origin` (`REPORTED` or `DERIVED`), `target`, optional `derivationId`. DERIVED requires a versioned derivation ID. |
| Distribution | `target`, nonblank `domainId`, ordered `masses` of `{alternative, probability}`. Complete unique keys, finite masses in [0,1], sum within 1e-6 of one. Accepted masses are preserved without renormalization. |
| AssessmentTarget | `PROPOSITION`, `NUMERIC`, `CATEGORY`; the component must be present. Distribution keys are false/true, the ordinal level IDs, or the category alternatives respectively. A continuous measurement cannot invent a discrete domain. |
| Check | `{id, judgment}` with unique nonblank roster ID and a complete v2 Judgment. The child cannot itself have checks. All five outcomes and optional native facts are retained. |

The derived numeric quality ratio is `(value-lower)/(upper-lower)` for INCREASING,
and one minus that ratio for DECREASING, computed without overflowing finite bounds.
Endpoints are exact; [-1e308,1e308] maps its midpoint to 0.5. It never creates certainty
or changes the raw value. An ordinal expectation is not P(satisfied).
`effectiveScore` is an explicit multi-seat voting view: only operational PASS/FAIL
have a vote; a declared quality projection supplies it, otherwise PASS/FAIL supply
1/0. Certainty and distribution are never voting scores.

Producer ERROR and NOT_APPLICABLE forbid assessment, certainty, distribution and
policy application. ERROR requires an instrument cause; NOT_APPLICABLE requires an
explanation and an upstream capability declaration. Producer ABSTAIN may retain
assessment/support. ABSTAIN, NOT_APPLICABLE and ERROR require producer explanation.
A subject-family reason code belongs only to producer FAIL. Producer reasoning and
reasonCode remain raw facts when policy changes the operational outcome.

### Policy and operational values

`PolicyRef` is `{id, revision, configurationDigest}` with nonblank identity/revision
and a SHA-256 configuration digest. Policy applications use an explicit `kind`:

* `applied`: `{kind, policy, action, reason}`. Action is `USE_ASSESSMENT`, `ABSTAIN`,
  or `ESCALATE`, and the policy explanation is nonblank.
* `failure`: `{kind, policy, reasonCode, reason}`. The cause is a machinery-origin
  instrument code; the explanation is nonblank.

No policy application means operational status equals producerStatus.
USE_ASSESSMENT preserves producerStatus, including FAIL and ABSTAIN. ABSTAIN and
ESCALATE both derive operational ABSTAIN while preserving their distinct actions.
PolicyFailure derives ERROR without erasing the raw assessment. ERROR and N/A bypass
policy and cannot carry an application. Policy cannot promote raw ABSTAIN to PASS.

Operational reasonCode/reasoning are the PolicyFailure cause/explanation for ERROR,
null cause and policy explanation for withheld ABSTAIN, otherwise the producer's
cause/explanation. These values are derived, never independently writable.

### Provenance, declarations and metadata

`EvaluationProvenance` contains `instrumentId`, `revision`, `configurationDigest`,
`evidence` (artifact references), optional `response`, and `calibrationClaims`.
`ArtifactRef` is `{id, sha256, selector?}`: the digest identifies the exact retained
bytes, not a reconstructed or implicitly canonicalized representation.
`CalibrationClaim` contains a versioned `id`, `issuer`, `scope`, `statement`, nonempty
unique `signalIds`, and nonempty `sources` with unique artifact IDs. Required strings
are nonblank. Claims are provider declarations, not empirical certification by the
library. An empty list means no retained declaration, not proof of miscalibration.

Metadata is incidental portable information. Keys are nonblank strings; values are
strings, booleans, finite numbers, interoperable integers within ±(2^53−1), arrays
and string-keyed objects, recursively. Nulls and runtime objects are not metadata
values. Collections are copied and recursively immutable. The reserved `aggregation`
block records a reduction's strategy/population/policies; it is not an authenticity
signature. `elapsedMillis` is a nonnegative interoperable integer.

## Verdict and execution facts

Required fields are `schemaVersion`, `declaredCardinality`, `aggregated`, `individual`,
`individualByName`, `weights`, `seats`, `decision`, `compositeAttempts`. Empty arrays
and objects are explicit. `declaredCardinality` is a nonnegative exact integer:
original configured input count before invocation failures or exclusions, never the
number of surviving eligible inputs.

Each seat is `{position, verdictKey, keySource, execution}`. Position is an exact
nonnegative integer smaller than declaredCardinality. Seats are strictly increasing,
one per ordered input; their keys join ordered inputs to the named map. Duplicate
keys retain the last ordered value in the named map. `keySource` is `DECLARED`,
`DEDUPLICATED`, or `POSITIONAL`; only DECLARED asserts an independently declared name.
`execution` is required and is one of:

* `RETURNED`: a valid value returned, including an ERROR or PolicyFailure.
* `CONTAINED_FAILURE`: orchestration replaced a thrown/null invocation, unreadable
  judge metadata or undeclared N/A. Its synthetic Judgment has producer ERROR with
  `judge_failed`, `judge_metadata_unreadable`, or `undeclared_not_applicable`, and
  empty checks/metadata with no assessment, support, provenance or policy.

The three-argument Java Seat constructor explicitly asserts RETURNED. A wire reader
must never supply that assertion for a missing field. A `judge_failed` cause alone
cannot distinguish a valid returned ERROR from a caught invocation. This is why
execution evidence is separate from judgment semantics. Wire consistency does not
prove a producer is truthful about execution.

SimpleJury declares its configured judge count and retains every invocation seat.
MetaJury declares its configured member count and retains every member attempt;
only USED members become seats, at their original positions, carrying the exact
member aggregate. A failed member forces a parent STAGE_FAILED machinery ERROR,
including when just one of multiple configured members survived.

`decision.kind` is `own`, `tier`, or `undecided`. OWN has no tier/basis. TIER requires
a direct cascade tier name and basis `tier_outcome` or `individual_rejection`.
UNDECIDED requires an ERROR with a machinery cause; it is not ABSTAIN.

Exactly one declared valid RETURNED input requires OWN and whole-value equality of
aggregate, sole ordered input and its named value. This includes returned ERROR,
policy failure, escalation, checks, support, provenance and metadata. No strategy or
numeric projection is used. A contained invocation is an explicit exception and
uses the configured reduction rules. A one-seat result cannot bypass identity by
changing its decision to UNDECIDED or merely labeling its input `judge_failed`.

A returned Judgment can already contain aggregation metadata from an earlier
explicit reduction. Identity preserves that complete value. The carried block is
not evidence of a new one-seat reduction and is not checked against the new count.

For actual OWN reductions, authoritative reading validates the recorded built-in
strategy against retained operational inputs, error/exclusion policies, configured
weights and threshold: producer status, numeric assessment, cause, complete
population/error-origin counts and reduction evidence must agree. Built-ins are
`consensus`, `majority`, `allMustPass`, `average`, `median`, `weightedAverage`, and
`conjunctive`. Unknown/custom rules are undetermined. Existing majority evidence does
not retain its configured tie policy; each documented tie outcome is admissible,
without claiming which tie policy was configured. A reduced aggregate may carry a
policy application; the reduction check applies to its retained raw producer facts.

### Composite attempts and cascade routing

An attempt contains `name`, `relation`, optional `policy`, `disposition`, optional
`dispositionReason`, and exactly one of `verdict` or `failure`. Relations are
`meta_member` and `cascade_tier`. Cascade policy is required only for cascade tiers.
Sibling names are unique. Live trees retain the existing maximum depth 8 and total
attempt count 32. Failure is `{code: "jury_execution_failed"}`. Dispositions are
`used` and `stage_failed`; a reason exists exactly for stage_failed:
`execution_failed`, `child_undecided`, `undeclared_not_applicable`, or
`invalid_tier_result`. A failed attempt keeps the actual returned child, if any.
Its presence does not certify the child's rejected claim.

A selected TIER_OUTCOME copies the aggregate, ordered/named inputs, weights, seats
(including execution), and declaredCardinality exactly from a USED tier. The first
stopping tier must be selected; later attempts are invalid. INDIVIDUAL_REJECTION
requires REJECT_ON_ANY_FAIL, a genuine FAIL input and the retained failed-stage
reason. Its rejection survives an outer cascade's TIER_OUTCOME copy even when the
carried aggregate is a machinery ERROR. Selected decision chains are followed.

Existing REJECT_ON_ANY_FAIL and ACCEPT_ON_ALL_PASS retain their historical rules.
FINAL_TIER always stops. STOP_ON_USABLE_ASSESSMENT requires a single-seat leaf OWN
identity. When it occurs, the cascade's FINAL_TIER has the same bound. ERROR and N/A
stop before requiring policy, including PolicyFailure. Otherwise AppliedPolicy is
required. Continue only on operational ABSTAIN whose action is ESCALATE. Withheld
ABSTAIN stops. Final ESCALATE remains ABSTAIN with its unfulfilled request retained.

A bounded tier's invocation/configuration failure or invalid returned identity/policy
records the failed attempt and terminates with a parent `stage_failed` machinery
ERROR and UNDECIDED decision. That parent's own root population is empty (cardinality
0); the attempt retains the child's actual population. No later fallback runs.

## Interpretation views and summaries

The root and every depth-first stage retain attempt facts, full path, operational
status/reasoning/cause, decision and declaredCardinality, seats and a `judgment` view.
A seat additionally retains position/key source and execution. Each modern
JudgmentView contains producerStatus, operational status/reasonCode/reasoning,
complete Assessment, Certainty, Distribution, EvaluationProvenance (including all
CalibrationClaims), PolicyApplication, producerReasonCode/producerReasoning, rich
checks and recursively immutable metadata. Each check has `name` (the source ID),
`detail`, `legacyPassed: null`, and a complete child JudgmentView.

Stage.evidence describes this stage's actual OWN reduction only. It is absent for
identity and selected-tier copies; carried producer aggregation metadata remains in
JudgmentView.metadata. Failed stages with no returned verdict have no status or
JudgmentView. Missing status is never read as FAIL.

Modern supported operational PASS/FAIL/ABSTAIN/N/A/ERROR read respectively ACCEPTED,
REJECTED, UNDECIDED, NOT_APPLICABLE and NOT_ASSESSED, except the recorded individual
rejection precedence described above. A consumer must check readingSupport before
using a subject determination. Interpretation does not choose application rates,
acceptance denominators, or an uncertainty threshold.

Summaries are deterministic functions of Interpretation fields. They report raw
and operational facts separately, native product/support, policy, provenance/claims,
checks, deciding path, reading, support and defects. Model prose is never reparsed to
infer a missing fact, and no narrative is manufactured as evidence.

## Historical reading

Unversioned 0.13–0.17 source records continue through their historical tolerant
reader and original resource bytes. Uppercase historical statuses, bounded scores,
legacy nesting, absent dispositions and old failure encodings retain their meanings.
Missing modern optional facts are not corruption. Historical supported/contradicted
readings remain distinct from their recorded statuses. The bounded-score calculation
uses stable finite-range normalization and rejects raw out-of-bounds values.

A historical check is `{name, legacyPassed, detail, judgment: null}` in Interpretation.
Both true and false leave finer status explicitly absent. In particular, false
cannot be reclassified as a verified FAIL: historical producers also used it for
uncertainty. Historical labels are retained as `legacyLabel`, scores as legacyScore
and the existing normalized seat score/recorded scale. No category domain, certainty,
policy, provenance, execution or original cardinality is invented. Old result bytes
are never rewritten into modern data by the reader.

## Conformance

Committed examples live in
`agent-judge-core/src/test/resources/conformance/v2/portable-results.json`.
They include raw negative identity, policy withholding/escalation, rich checks and
provider declarations, policy failure, valid returned ERROR versus invocation
containment, meta/cascade routing, malformed versions and historical boolean checks.
Each vector has an ID, source Verdict/map and expected reading/support plus selected
semantic paths. Ordinary tests consume the committed bytes; generation is not part
of verification. Java conformance tests additionally exercise whole-value equality,
all statuses/actions, every built-in reduction/policy combination, contradictory
input counts and numeric reduction values, malformed fields and bounded traversal.

Producer JSON round trips and authoritative interpretation do not establish fidelity
through any downstream recording DTO or external store. A consumer adopting this
version must retain the entire shape and prove its own save/load path.
