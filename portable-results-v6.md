# Portable retained results V6

`Judgment`, `Verdict` and `EvaluationResult` use integer `schemaVersion: 6`. Descriptions retain `descriptionVersion: 3`. The domain stores neither a writable format version nor a duplicate conclusion. Use matching `io.github.markpollack:agent-judge-json-jackson2:0.18.0` for storage; core contains no Jackson engine API. Jackson 2 is the supported engine.

```java
var codec = NativeRequirementCodecs.codec(); // RFC2119/EARS plus text/AllOf
var json = codec.write(evaluation);
var reopened = codec.readEvaluation(json); // no producer or policy calls
reopened.verdict().requireUsable();         // same complete Verdict
```

A plain `new VerdictCodec()` supports text and AllOf. Additional native specifications use explicit `SpecificationCodec` registrations. For an explicitly configured Jackson mapper, register `ResultJson.module()`; default built-in rule and text/AllOf reconstruction is available. `VerdictCodec` additionally owns strict coercion, duplicate/trailing input checks and document bounds. Do not substitute unchecked POJO binding for current storage.

| Value | V6 retention |
|---|---|
| Judgment | Original status/finding, support, probabilities, Checks, provenance, metadata, actual optional Requirement, invocation owners/references; optional complete `RefusedReturn` |
| RefusedReturn | Unchanged complete `original`, actual configured `expected` Requirement and `REQUIREMENT_MISMATCH` reason |
| Seat | Position, label/key source, execution, participation, local applicability, separate rejection; optional positive finite `declaredWeight` |
| Verdict | Collective and complete individual Judgments, names, seats, provenance, attempts, cardinality, optional parent/roster, invocation owners; optional retained rule token/configuration |
| Policy result | Not requested, decided, or failed; decided and failed may both retain `PolicyAttribution(id, version, configuration)` |

A wrong-associated structured single returns an ERROR carrier with `returned_result_rejected`, expected Requirement and the item invocation. Its RefusedReturn keeps the actual original Requirement, original status, own Checks and lower invocations unchanged. Evaluation retains that original as the opinion, `RETURNED_REJECTED` with no invocation failure cause, separate carrier rejection and `NOT_RECORDED` participation. Machinery refusals never turn into subject FAILs. Reports and assertions read the same retained record.

Weights live once on seats; `Verdict.weights()` is removed. Absent means effective 1.0. Unweighted rules retain explicit weights and ignore them; weighted rules apply them to eligible treatments at the original positions. Labels may repeat. `AllEligiblePassStrategy` keeps the stable `allMustPass` token and eligible-opinion behavior.

`VotingStrategy.aggregate(List<Ballot>)` is pure deterministic reduction. Declare every behavior-affecting setting in `configuration()`/`describe()`. `AggregationPopulation.resolve`, `eligibleBallots`, `evidence`, `policyExitAggregate` and `noResult`, plus `AggregationEvidence.attach`, provide the shared accounting contract. `RetainedRule` binds that complete immutable declaration before execution. Retained validation uses the same rule and the same pre-reduction ballots: participation is `NOT_RECORDED` at that point. Recorded seat participation remains a separate output checked against the reduction. It never guesses a rule from output. Additional pure factories must be explicitly trusted:

```java
var codec = new VerdictCodec().withVotingRules(Map.of("passingWeight:v1",
    configuration -> new PassingWeight(
        ((Number) configuration.get("minimumPassingWeight")).doubleValue())));
```

The complete `PassingWeight` implementation and these executable calls are in
[`RetainedCustomRuleTest`](agent-judge-json-jackson2/src/test/java/io/github/markpollack/judge/serialization/RetainedCustomRuleTest.java). It sums absolute eligible passing weight; it is a custom rule rather than a renamed built-in.

A declaration captured before a failed or refused reduction remains on the Verdict. If declaration capture itself fails, no declaration is invented. Reopening a failed reduction reconstructs its declaration but never reruns that failed reduction.

Both writer and reader require known tokens. Reconstruction must return the same complete token/configuration; contradictory or extra settings are refused. There is no class-name loading or hidden rule discovery. Rule authors own immutability and purity; external calls, mutable hidden behavior and captured producers violate the contract.

`Verdict.builder().single(name).judgment(original).build()` and `.panel(strategy).opinion(name, original[, weight]).build()` derive ordinary retained facts. Use `advancedBuilder()` deliberately for complete records, including refused children. `requireUsable()` checks bounds, conclusion semantics and invocation-reference closure, returning the unchanged Verdict. Evaluation, policy, reports, retained assertions and codecs use this boundary.

Required collections stay required: Judgment `checks`, `metadata`, `invocations`, `invocationIds`; Verdict `individual`, `individualByName`, `seats`, `compositeAttempts`, `roster`, `invocations`. Missing or null arrays are refused, rather than fabricated as empty. Unknown fields, malformed scalar types, duplicate/trailing input, dangling references, conflicting owners and contradictory reductions are refused.

Complete Judgment trees allow aliases, refuse active-path cycles, and allow at most 256 distinct nodes and eight nested complete refusals. V6 repeats whole values at declared joins. Reading restores aliases for single identity, selected cascade facts, used meta-member judgments, named seats and a refused original joined to its seat. Equal independent opinions and Check nodes are not interned. The strict writer checks reconstruction before returning bytes; incidental in-memory aliases without a representable V6 join may therefore be refused if expansion exceeds the existing bounds. This changes no V6 fields or version.

Current documents allow at most 1 MiB UTF-8 and 64 JSON nesting levels. `PreservationLimitException.original()` retains the complete available object or document; generic execution containment must propagate it, including asynchronous wrappers. No original is silently truncated. The caller owns alternate archival storage for a refused oversized original. Native capture limits similarly retain the SDK object; SDK JSON snapshots are not original HTTP bytes.

Policy attribution carries inline portable configuration. A digest or reference inside it does not carry external bytes: the caller owns those bytes and their resolution. A failed live policy keeps its original Throwable in memory; storage carries only explicit failure type/message data and the attribution. Reopening never loads the exception class or executes policy.

Typed V2/V3/V4/V5 are explicitly refused. Read V5 archivally at `c0ae61dda4a65e9278485cb528925d71f27a1007`, and typed V4 at `7387aab1bf9d3bd56e4d9a932f2f40e2978d676d`, with their original codec and semantics. The V4 reader refuses typed V2/V3; no verified typed V2/V3 archival reader pin is supplied. Preserve historical bytes and version tags. No automatic historical migration is supplied. Archival diagnostics remain distinct from current typed reading.
