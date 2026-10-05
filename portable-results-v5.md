# Historical retained results V5

This document describes the archived V5 contract at `c0ae61dda4a65e9278485cb528925d71f27a1007`. Current storage uses [V6](portable-results-v6.md) and explicitly refuses V5; preserve original historical bytes.

V5 `Judgment`, `Verdict` and `EvaluationResult` JSON uses integer `schemaVersion: 5`. Descriptions use `descriptionVersion: 3`. Domain values contain no writable format version or duplicate conclusion. `VerdictCodec` validates current domain semantics and complete invocation ownership before returning or writing a usable result.

Typed V2/V3/V4 documents are refused before nested decoding. For archival V4 reading, use Agent Judge commit `7387aab1bf9d3bd56e4d9a932f2f40e2978d676d` and its original codec/routing semantics. Do not change historical version tags, fill missing originals, or treat historical routing as current execution. `serialization.diagnostics.StoredVerdicts` still diagnoses unversioned archival shapes without converting them into current domain Verdicts.

| Value | Retained facts |
|---|---|
| Judgment | Original status, finding, confidence, distribution, checks, provenance, metadata; optional actual Requirement; required `invocations` and `invocationIds` arrays |
| Verdict | Collective Judgment, individual inputs, names, weights, seats, provenance, all attempts, declared cardinality; optional actual parent Requirement; required ordered `roster` and owned `invocations` arrays |
| Seat | Original answer remains in the individual inputs. Local `notApplicableWhen`, `RETURNED_REJECTED` and separate ERROR `rejection` record refused exclusion treatment |
| Invocation | `id`, versioned `protocol`, `completed`, optional `model`, measured `durationMillis`, portable `nativeFacts`, protected `artifacts`. Original thrown cause is memory only |
| Requirement | One owner of id/revision/text/source and typed specification; stable `specificationType` registry name, never a stored Java class name |

A generated RFC2119/EARS investigative audit owns one invocation at its root; every item references that owner. A structured roster explicitly counts per-item executions. Nested original facts remain retained, and reporting resolves identical shared owners once; conflicting IDs or unresolved references are rejected. Ordinary composition declares no synthetic shared Requirement or coverage roster.

Use `NativeRequirementCodecs.codec()` from `agent-judge-ai-core` for text, AllOf, `rfc2119:v1` and `ears:v1` specifications, reconstructing their exact pure Requirement implementations. For an application type, register `VerdictCodec.withSpecifications(Map.of("my-type:v1", new SpecificationCodec<>(MySpecification.class, MyRequirement::new)))`. The factory must preserve all five Requirement components. A simple class-only registration constructs a `GeneralRequirement` and cannot silently demote a specialized implementation.

The `used` attempt token means **accepted parent input**. Relation and declared cardinality determine its validity: a valid UNDECIDED result is accepted by cascade and one-member Meta identity; a multi-member reduction refuses it. Acceptance does not mean contribution, selection, successful determination or policy reliance. Seats record actual participation separately.

Canonical routing tokens are `STOP_ON_ANY_OPINION_FAIL`, `STOP_ON_ALL_OPINIONS_PASS`, `STOP_ON_CONCLUSION_PASS`, `STOP_ON_CONCLUSION_FAIL`, `STOP_ON_CONCLUSIVE` and `FINAL_TIER`. All non-final rules require a valid admissible tier. A final refusal terminates with INCONCLUSIVE while retaining the original. `roster_item` attempts retain complete independent coverage; `protocol_unbound` prevents unknown/duplicate answer identities from establishing an authoritative violation.

A reliably bound violation plus a failed or missing sibling concludes FAIL. Satisfied plus missing concludes INCONCLUSIVE. A globally unbound envelope retains its native answer and protocol failure. Justified declared exclusions remain NOT_APPLICABLE; undeclared exclusions preserve the original plus separate ERROR treatment.

AllOf validates a returned child's association and semantics before accepting its determination. A rejected original remains on a `stage_failed` constituent with `invalid_tier_result`, without an execution failure. The parent can still conclude FAIL from another valid child. Retained validation verifies that the rejected original actually violates its required association or semantics, and never consumes its apparent conclusion. The original can be semantically invalid as a standalone Verdict while the complete parent is valid; its constructor-valid V5 data remains intact.

A structured RFC2119/EARS answer naming another Requirement is retained with its actual association under `protocol_unbound`. It supplies no determination for the requested item. Its native invocation and lower observations remain available. A returned answer is distinct from `execution_failed`, which carries a failure instead of a Verdict.

Collections are required even when empty. Unknown fields, duplicate JSON keys, scalar coercion, missing required values, inconsistent roster order/cardinality, contradictory seat treatment, invalid current routing/reduction, unknown specifications, conflicting invocations and unresolved references fail loudly. Composite execution is bounded at depth 8 and 32 attempts; rosters have a separate 256-item bound. Portable numeric values normalize to their JSON representation rather than retaining Java wrapper subtype identity.

Failed policy storage retains its original type name and message as explicit data; reopening never loads an exception class. Invocation/seat/reduction/attempt causes remain memory only. Original native details use portable maps and protected artifact references. Reopening, reports and retained assertions execute no producer or policy.

Spring AI captures the native SDK response before normalizing usage. Malformed usage omits the normalized `usage` vector, records `mappingFailure` separately from `captureFailure`, and makes the judging invocation incomplete while retaining text, model, response ID, finish reason and captured native detail. An SDK `EmptyUsage` denotes unknown quantities, so common token keys stay absent. Explicitly supplied zero quantities remain present. SDK defaults may be visible in `nativeResponseJson`; they are not provider-reported quantities.
