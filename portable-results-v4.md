# Portable results: schema version 4

> Historical V4 contract at `7387aab1bf9d3bd56e4d9a932f2f40e2978d676d`. Current APIs use [V5](portable-results-v5.md) and strictly refuse V4; this document is archival context.

The V4 stored Judgment, Verdict and EvaluationResult format uses the existing integer `schemaVersion` discriminator with value **4**. Java domain records do not store a format version or a writable second conclusion. `VerdictCodec` validates before returning a usable domain Verdict, using the same typed rules as `Verdict.conclusion()`.

## Actual changes from version 3

| Surface | Version 4 |
|---|---|
| Judgment policy application | Removed. Producer status, reason, findings and support remain producer facts. |
| Policy execution | EvaluationResult has one `policyResult` tagged `notRequested`, `decided`, or `failed`. |
| Requirement association | Optional `requirement` on each Verdict node; no duplicate on EvaluationResult. |
| Native specification | Explicit `specificationType` name and typed `specification`, plus id/revision/text/source. |
| Constituent composition | `CONSTITUENT` attempts and `CONSTITUENTS` provenance; complete nested Verdicts retained. |
| Routing | Attempt field `routingRule`; `STOP_ON_CONCLUSIVE` examines the derived conclusion. |
| Seats | Explicit `participation` separate from invocation `execution` and producer Judgment. |
| Reduction failures | Optional `reductionFailure` with a stable code and original in-memory exception. |
| Interpretation | No public domain Interpretation. Historical diagnostic reading is separate stored-format support. |

These changes break the prior stored policy-mutation contract. The codec refuses version 2 and version 3 rather than guessing how to migrate an internal Judgment policy into one final Verdict-level decision. Frozen earlier resources remain byte-for-byte unchanged.

An evaluation stores `schemaVersion`, `verdict`, and `policyResult`. The three policy shapes are:

```json
{"kind":"notRequested"}
```

```json
{"kind":"decided","decision":{"action":"RELY","reason":"Trust the retained rejection"}}
```

```json
{"kind":"failed","failure":{"type":"java.lang.IllegalStateException","message":"Unavailable"}}
```

A failed policy retains its original Throwable in memory. Storage writes only the original type name and message; reopening creates `VerdictCodec.StoredPolicyFailure` as explicit data. It never loads or instantiates arbitrary exception classes, and never writes stack traces or polymorphic class tags. Rewriting a reopened failure preserves the original type name.

Seat and child-attempt exceptions are likewise available in memory. Portable seat facts and child failure codes define their equality; transient exceptions are excluded. Child and reduction failures retain stable codes in storage. Retained unsuccessful child Verdicts remain complete even if their own reduction was unusable. A failed-stage disposition does not authorize using that reduction: usable child results are validated recursively, and an individual rejection is checked against the actual retained negative opinion.

## Typed requirements

The built-in specification names are `text` for String and `allOf` for AllOf. Register each additional concrete native type explicitly:

```java
VerdictCodec codec = new VerdictCodec(Map.of("apiLimit", ApiLimit.class));
String document = codec.write(result);
EvaluationResult reopened = codec.readEvaluation(document);
```

A stable registry name identifies the declared Jackson representation of that native class. No class-name polymorphism, arbitrary `toString()` conversion, or universal context type is used. All-of rosters contain complete Requirement values; attempts identify entries by their unique child IDs and retain each child's association. Native specification classes must preserve their value semantics, and callers must retain stable snapshots.

Unknown specification names, duplicate registrations, unknown fields/discriminators, malformed version integers, scalar coercions, duplicate JSON keys, trailing tokens, missing required values, contradictory identity/reduction facts and unavailable aggregation semantics are rejected. `readEvaluation` rejects incomplete policy-result shapes. A read failure is an exception, never an ordinary INCONCLUSIVE Verdict or partial EvaluationResult.

Bare Jackson annotations provide the wire shape and structural construction. Use `VerdictCodec` to obtain a semantically validated usable result. Hand-built domain records are also validated when `conclusion()`, `Evaluations`, or `VerdictReport` is used.

## Historical support and deliberate retained identifiers

`serialization.diagnostics.StoredVerdicts` retains tolerant diagnostics for unversioned pre-0.17 and unversioned 0.17 shapes. Missing facts remain missing and unsupported readings carry defects. This does not migrate historical records into usable current domain Verdicts. Current stored reading delegates to the same domain conclusion rules, with no second modern semantic authority.

The existing historical diagnostic shape remains version 3 with the historical `policyApplication` field name for opaque historical data. Its format is identified separately from the source document. `StoredReading.sourceVersion()` is the source version, not a domain result version. Historical `RequirementOutcome` and support vocabulary are confined to stored diagnostics. Use VerdictReport for current application reporting.

Aggregation evidence intentionally retains established `errorPolicy` and `notApplicablePolicy` wire keys; its values describe ErrorHandling and ExclusionHandling. The existing version-2 Jury description format retains its `policy` tier key and strategy parameter keys; current Java accessors use routing/handling terminology. Historical readers still recognize old `policyApplication` and other keys to refuse disguised modern records. Those strings are stored identifiers, not compatibility aliases for removed Java APIs.

`Participation.NOT_RECORDED` explicitly represents manually authored records without a treatment declaration. Built-in voting paths populate actual treatments; one-seat identity preserves the producer value, and a propagated or contained reduction failure marks seats NOT_REDUCED. Stored declared treatments are checked against retained inputs and aggregation facts.

No codec invokes a producer or policy. Reporting and assertions on a reopened EvaluationResult inspect only retained facts.
