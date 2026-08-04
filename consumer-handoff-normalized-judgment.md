<!--
Copyright (c) 2026 Mark Pollack
See LICENSE.txt in the repository root for license terms.
-->

# Consumer migration handoff — normalized `Judgment` (0.13.0 → 0.14.0)

> **Status**: written against the verified 0.14.0-SNAPSHOT implementation
> **Date**: 2026-08-03
> **Applies to**: any consumer of `io.github.markpollack:agent-judge-*`
> **Normative contract**: [design-normalized-judgment.md](design-normalized-judgment.md)
> **This document does not authorize edits to any consumer repository.**

0.14 replaces the sealed `Score` hierarchy with three independent facts on `Judgment`. You can
migrate from this document alone; you should not need to read the implementation diff.

---

## 1. Read this first: the two changes that fail silently

Most of this migration is a compile error, which is safe — the compiler shows you every site. Two
changes are not, and both can leave you with code that builds, runs, and is wrong.

### 1.1 `Majority` and `Consensus` aggregates no longer carry a score

Under 0.13, every aggregate carried a `Score`, so a threshold gate behind a consensus or majority
jury worked. Under 0.14, **those two strategies deliberately emit no score** — they aggregate
outcomes, not quantities. Only `Average`, `WeightedAverage`, and `Median` produce one.

The dangerous port is the one that looks most obvious:

```java
// 0.13
double score = Scores.toNormalized(verdict.aggregated().score(), Map.of());

// WRONG in 0.14 — compiles, runs, and fails every gate
Double score = verdict.aggregated().score();          // null for majority/consensus, always
double value = score == null ? 0.0 : score;           // 0.0 on a unanimous PASS

// CORRECT
double value = verdict.aggregated().effectiveScore().orElse(0.0);   // 1.0 on a unanimous PASS
```

This is silent because the old `Scores.toNormalized` mapped `null` to `0.0`, so a null-guard that
returns `0.0` looks like faithful behavior preservation. It is not: in 0.13 the null case was
unreachable for these strategies, and in 0.14 it is the only case.

**If you take one action from this document: find every place you read an aggregate's score behind
a `Majority` or `Consensus` jury, and make sure it goes through `effectiveScore()`.**

### 1.2 `ErrorPolicy` now defaults to `PROPAGATE` on all five strategies

If you never passed an `ErrorPolicy`, your behavior changes without a compile error.

| Strategy | 0.13 default | 0.14 default |
|---|---|---|
| `Majority` | `TREAT_AS_FAIL` (implicit) | `PROPAGATE` |
| `Consensus`, `Average`, `WeightedAverage`, `Median` | none — errors silently scored `0.0` | `PROPAGATE` |

Under `PROPAGATE`, one errored judge makes the whole aggregate `ERROR`. Previously an errored judge
became a negative vote it never cast. If you want the old behavior, ask for it explicitly:

```java
new MajorityVotingStrategy(TiePolicy.FAIL, ErrorPolicy.TREAT_AS_FAIL);
```

Note that an `ERROR` aggregate has no score and `effectiveScore()` is empty, so an error now reaches
your gate as "no measurement" rather than as zero. Decide whether that should open or close the gate;
`PROPAGATE` plus a `.orElse(0.0)` fails closed.

---

## 2. Dependency bump

```xml
<agent-judge.version>0.14.0</agent-judge.version>
```

Coordinates are unchanged: `io.github.markpollack:agent-judge-core` and the sibling modules. Every
consumer must recompile — there is no compatibility shim, deliberately, because a bridge would
preserve two spellings of a vocabulary this release exists to make univocal.

`agent-judge-core` no longer declares `reactor-core`. If you relied on inheriting Reactor from it,
declare it yourself. (Optional scope already prevented transitive inheritance, so this is unlikely
to affect you.)

---

## 3. The value model

`Judgment` carries three independent facts:

| Field | Contract |
|---|---|
| `status` | required — `PASS`, `FAIL`, `ABSTAIN`, `ERROR` |
| `score` | optional `Double`, **already normalized to `[0.0, 1.0]`** when present |
| `label` | optional non-blank `String` |

Which combinations are legal:

| Status | `score` | `label` |
|---|---|---|
| `PASS` | allowed | allowed |
| `FAIL` | allowed | allowed |
| `ABSTAIN` | **forbidden** | allowed |
| `ERROR` | **forbidden** | **forbidden** |

These are enforced in the compact constructor, so they hold on every construction path. `ABSTAIN`
and `ERROR` additionally require non-blank `reasoning`.

The meaning of each status, which the rest of this document depends on:

```
PASS    the judge completed and accepted the subject
FAIL    the judge completed and rejected the subject
ABSTAIN the judge reached no applicable finding
ERROR   the judge could not complete
```

`ABSTAIN` is not a failing vote. `ERROR` is not a negative finding. A score is not a second status.

### 3.1 `score()` versus `effectiveScore()` — pick deliberately

```java
Double stored = judgment.score();                 // null unless the judge measured something
OptionalDouble view = judgment.effectiveScore();  // score, else 1.0/0.0 for PASS/FAIL, else empty
```

`effectiveScore()` is derived, never stored:

- the stored `score` when present;
- otherwise `1.0` for `PASS` and `0.0` for `FAIL`;
- otherwise **empty** for `ABSTAIN` and `ERROR`.

Use `score()` when the distinction between a real measurement and a status projection matters — for
example when reporting "the judge rated this 0.82" or when averaging only judges that actually
measured. Use `effectiveScore()` for threshold gates, where a Boolean verdict legitimately means 1.0
or 0.0.

Do not use `effectiveScore()` to mean "quality". A `PASS` with no measurement projects to 1.0, which
is a statement about the outcome, not about quality being perfect.

---

## 4. Removed API and what replaces it

### 4.1 Deleted types

`Score`, `BooleanScore`, `NumericalScore`, `CategoricalScore`, `ScoreType`, `Scores` — the entire
`io.github.markpollack.judge.score` package is gone.

### 4.2 Deleted members

| Removed | Replacement |
|---|---|
| `Judgment.Builder.status(JudgmentStatus)` | `Judgment.builder().pass()` / `.fail()` / `.abstain()` / `.error()`, or `verdict(boolean)` |
| `Judgment.Builder.score(Score)` | `score(double)` after selecting PASS/FAIL, or `scored(...).passingAt(...)` when a threshold derives the outcome |
| `Judgment.error(String, Throwable)` | `Judgment.error(String)` — log the exception where you catch it |
| `Throwable Judgment.error()` accessor | `boolean hasError()`, or `status() == ERROR` |
| `"error"` metadata key convention | none — a `Judgment` is not an exception transport |
| `Scores.toNormalized(Score, Map)` | `Judgment.effectiveScore()` |
| `NumericalScore.value()` / `.normalized()` | one number: `Judgment.score()`, already normalized |
| `ReactiveJudge` | wrap `Judge` yourself (§4.4) |

### 4.3 Construction, before and after

**Boolean verdict.** The Boolean score always duplicated the status; now the status carries it alone.

```java
// 0.13
Judgment.builder().score(new BooleanScore(passed)).status(passed ? PASS : FAIL)
        .reasoning("Build " + (passed ? "succeeded" : "failed")).build();

// 0.14
Judgment.verdict(passed)
        .reasoning("Build " + (passed ? "succeeded" : "failed"))
        .build();
```

**Normalized numeric score.** The staged builder has no `build()` until you state the outcome, so a
score without a stated outcome policy will not compile.

```java
// 0.13
Judgment.builder().score(NumericalScore.normalized(0.82)).status(PASS).reasoning("...").build();

// 0.14 — threshold-derived outcome
Judgment.scored(0.82)
        .passingAt(0.7)
        .reasoning("Quality exceeded the acceptance threshold")
        .build();

// 0.14 — independently decided outcome
Judgment.builder().pass().score(0.82).reasoning("...").build();
```

**Raw-range score.** Normalization happens at construction; the raw value and range are not retained.

```java
// 0.13 — kept both numbers, and the wrong one was easy to read
Judgment.builder().score(new NumericalScore(8.5, 0.0, 10.0)).status(PASS).build();

// 0.14 — normalizes to 0.85 at construction
Judgment.scored(8.5, 0.0, 10.0).passingAt(0.7).reasoning("...").build();
```

A degenerate range where `max <= min` is now rejected rather than special-cased.

**Classification.** A label is not implicitly an outcome, and not implicitly a number.

```java
// 0.13
Judgment.builder().score(new CategoricalScore("relevant", List.of("relevant", "irrelevant")))
        .status(PASS).reasoning("...").build();

// 0.14
Judgment.builder().pass().label("relevant").reasoning("...").build();

// with a declared numeric meaning, where a policy owner declares it
Judgment.builder().pass().label("good").score(0.6).reasoning("...").build();
```

If you map labels to numbers, `LabelJudgmentClassifier` now owns that policy:

```java
LabelJudgmentClassifier.builder()
    .pass("excellent", 1.0)
    .pass("good", 0.6)
    .fail("poor", 0.0)
    .build();
```

Labels declared without a score record a label and no score. Unmatched output abstains with neither.

**One-member jury.** A `Verdict` still represents both the jury's conclusion and its evidence, even
when the jury has only one member. Use the factory to avoid repeating the same immutable judgment:

```java
Judgment individual = Judgment.pass("File exists");
Verdict verdict = Verdict.single("file-exists", individual);
```

This is equivalent to setting `aggregated`, `individual`, and `individualByName` explicitly. A
`Verdict` without `aggregated` is rejected because it has evidence but no jury conclusion.

**Abstention and error.** Neither carries a manufactured score any more.

```java
// 0.13 — both stored BooleanScore(false), a real 0.0
Judgment.abstain("No baseline available");
Judgment.error("Judge invocation failed", exception);

// 0.14
Judgment.abstain("No baseline available");                 // no score; reasoning must be non-blank
logger.error("Judge invocation failed", exception);        // log it where you catch it
return Judgment.error("Judge invocation timed out");       // no score, no label, no Throwable
```

### 4.4 `ReactiveJudge` is removed

It had no implementation, test, caller, or sample in the producing repository. If you need it, wrap
`Judge` directly:

```java
Mono.fromCallable(() -> judge.judge(context)).subscribeOn(Schedulers.boundedElastic());
```

A dedicated adapter can be added if a real consumer asks for one.

---

## 5. Aggregation behavior changes

`ABSTAIN` now leaves the population entirely — numerator, denominator, vote count, and weight.

| Strategy | Reads | 0.13 treatment of ABSTAIN | 0.14 |
|---|---|---|---|
| `Majority` | `status` | excluded from counts | unchanged |
| `Consensus` | `status` (was `score`) | counted as a fail vote | excluded; consensus over applicable judges |
| `Average` | `effectiveScore()` | counted as `0.0` | excluded from both numerator and denominator |
| `WeightedAverage` | `effectiveScore()` | counted as `0.0`, weight consumed | excluded, weight released and remainder renormalized |
| `Median` | `effectiveScore()` | counted as `0.0` | excluded |

Nothing eligible now yields `ABSTAIN` rather than a manufactured failing score.

### 5.1 Consensus distinguishes disagreement from unanimous failure

0.13 returned `FAIL` for both, leaving the difference only in prose.

| Inputs | 0.14 result |
|---|---|
| all `PASS` | `PASS` |
| all `FAIL` | `FAIL` |
| `PASS` + `ABSTAIN` | `PASS` |
| `FAIL` + `ABSTAIN` | `FAIL` |
| `PASS` + `FAIL` | **`ABSTAIN`** — the applicable judges disagree |
| all `ABSTAIN` | `ABSTAIN` |
| any `ERROR` | per `ErrorPolicy`, default `PROPAGATE` |

If you previously treated a consensus `FAIL` as "rejected", note that a split panel now abstains
instead. A consumer that must fail closed on disagreement should check for `ABSTAIN` explicitly.

Cascade escalation is unchanged: `REJECT_ON_ANY_FAIL` and `ACCEPT_ON_ALL_PASS` read the tier's
**individual** judgments, not its aggregate, so the new consensus semantics surface only at a
`FINAL_TIER` or when you read `Verdict.aggregated()` yourself.

### 5.2 `ErrorPolicy` has four values

| Policy | Effect |
|---|---|
| `PROPAGATE` *(new, default)* | the aggregate becomes `ERROR` |
| `TREAT_AS_FAIL` | the errored judgment participates as a `FAIL` |
| `TREAT_AS_ABSTAIN` | it becomes a non-vote; the conversion is recorded in the evidence |
| `IGNORE` | removed from the population entirely — count, denominator, and weight — while the original `ERROR` stays in `Verdict.individual()` for audit |

`IGNORE` and `TREAT_AS_ABSTAIN` can reach the same status. They are distinguished by the aggregation
evidence, not by the status.

### 5.3 Weight configuration is validated

| Case | 0.14 behavior |
|---|---|
| any weight negative, `NaN`, or infinite | `IllegalArgumentException` |
| all weights zero | `IllegalArgumentException` — no judge can influence the result |
| a single zero weight | legal; that judge does not count |
| positive input weight, zero eligible weight after filtering | `ABSTAIN` with evidence |

In 0.13 an all-zero weight map produced a `NaN` score that slipped past range validation and emitted
a `FAIL` whose reasoning literally read `NaN`. `WeightedAverageStrategy` also no longer delegates to
`AverageVotingStrategy` when weights are empty; it resolves missing weights to `1.0` internally, so
the number is unchanged but the evidence correctly names `weightedAverage`.

### 5.4 Aggregation evidence

Every aggregate carries an immutable map under the reserved key `metadata.aggregation`:

```json
"metadata": {
  "aggregation": {
    "strategy": "majority",
    "errorPolicy": "ignore",
    "inputCount": 4,
    "eligibleCount": 2,
    "explicitAbstainCount": 1,
    "errorCount": 1,
    "ignoredErrorCount": 1,
    "errorsTreatedAsAbstainCount": 0,
    "errorsTreatedAsFailCount": 0,
    "passCount": 2,
    "failCount": 0
  }
}
```

The nine universal keys above appear on every strategy. `Majority` and `Consensus` add
`passCount`/`failCount`; `WeightedAverage` adds `inputWeight`/`eligibleWeight`; `Average` and
`Median` add neither. Key names are declared as constants on `AggregationEvidence`.

`aggregation` is reserved: `Judgment.Builder.metadata("aggregation", ...)` throws. Strategies never
copy your input metadata into an aggregate, so an aggregate's metadata contains the evidence block
and nothing else.

### 5.5 `VotingStrategy.getName()` identifiers

Normalized to stable lower-camel-case, matching the evidence tokens:

| 0.13 | 0.14 |
|---|---|
| `majority` | `majority` |
| `Consensus` | `consensus` |
| `AverageVoting` | `average` |
| `WeightedAverage` | `weightedAverage` |
| `MedianVoting` | `median` |

`ErrorPolicy` tokens are `propagate`, `treatAsFail`, `treatAsAbstain`, `ignore`.

---

## 6. Judge behavior changes

### 6.1 A missing JaCoCo report abstains instead of failing

`CoverageImprovementJudge` and `CoveragePreservationJudge` returned `FAIL` when no report existed.
A missing report says nothing about coverage, so both now return `ABSTAIN`.

**This is a fail-open change for anyone relying on "no report ⇒ gate closed."** Since `ABSTAIN` is
excluded from the population, a jury of one coverage judge now yields a no-result `ABSTAIN` verdict
rather than `FAIL`.

To keep failing closed, assert the report exists as its own judge:

```java
Judge reportExists = context -> Files.exists(context.workspace().resolve("target/site/jacoco/jacoco.xml"))
        ? Judgment.pass("JaCoCo report present")
        : Judgment.fail("No JaCoCo report — coverage cannot be verified");
```

Then place it in a `REJECT_ON_ANY_FAIL` tier ahead of the coverage judges, or check for `ABSTAIN`
explicitly at your gate.

### 6.2 Two judges report a caught exception as `ERROR`

`CommandJudge` and `FileContentJudge` returned `FAIL` when execution threw; both now return `ERROR`,
conforming to the seven judges that already did. A command that runs and returns a disallowed exit
code is still `FAIL`; a command that cannot be started is `ERROR`.

Combined with the `PROPAGATE` default, an infrastructure failure that previously became a quiet
negative vote now surfaces as an aggregate `ERROR`.

---

## 7. Wire contract

`Judgment` serializes as a flat JSON product with lower-case status names:

```json
{"status":"pass","reasoning":"All checks passed","checks":[],"metadata":{}}
{"status":"pass","score":0.82,"reasoning":"Quality exceeded the acceptance threshold","checks":[],"metadata":{}}
{"status":"pass","label":"relevant","reasoning":"The document directly supports the claim","checks":[],"metadata":{}}
{"status":"error","reasoning":"Judge invocation timed out","checks":[],"metadata":{}}
```

- absent optionals are **omitted**, never emitted as `null`;
- status wire names are `pass`, `fail`, `abstain`, `error`;
- parsing is **exact and case-sensitive** — `"PASS"` is rejected;
- no polymorphic type metadata, no wrapper object, no Java class name;
- no `Throwable` anywhere in the projection.

Field order is pinned for deterministic examples but is **presentational**. Parse by field name.

**The metadata guarantee is conditional.** `status`, `score`, `label`, `reasoning`, and `checks` are
unconditionally serializable. A *serializable* `Judgment` additionally requires every `metadata`
value to be a string, a finite number, a boolean, a list, or a string-keyed map, recursively. A judge
that puts a `Duration` or an arbitrary object in metadata produces a judgment that is valid
in-process but not portable.

`Judgment.elapsed()` is a known instance of this: it blind-casts a metadata entry to `Duration`,
which does not serialize cleanly. It is unchanged in 0.14 and recorded here as a follow-up.

---

## 8. Migration checklist

1. Bump to `0.14.0` and compile. Every deleted type and member is a compile error — work through
   them with §4.
2. Replace `Scores.toNormalized(x.score(), Map.of())` with `x.effectiveScore()`, deciding at each
   site what an absent score means. **Do not** replace it with `x.score()` (§1.1).
3. Find every score-threshold gate behind a `Majority` or `Consensus` jury and confirm it reads
   `effectiveScore()`.
4. Search for `instanceof NumericalScore` and any use of `NumericalScore.value()`. If a threshold
   compared against `value()` rather than `normalized()`, that was a latent defect — the raw value
   was compared against a normalized threshold — and it is fixed by moving to the single normalized
   number.
5. Decide your `ErrorPolicy` explicitly rather than inheriting `PROPAGATE` (§1.2).
6. Re-check any consumer that treated a consensus `FAIL` as rejection; a split panel now abstains
   (§5.1).
7. If you gate on coverage, add an explicit report-existence judge (§6.1).
8. If you parse `Judgment` JSON, confirm you accept lower-case status names and tolerate omitted
   `score`/`label` keys (§7).
9. Re-run your own suites. A jury-heavy suite is the place these changes surface.

---

## 9. Known consumer findings

Recorded from a read-only inventory. **No consumer repository was modified.**

### agent-workflow (branch `v3`, pins 0.12.0)

- 10 `Scores.toNormalized` call sites across 8 files: `SpringAiJuryAdapter` (2),
  `JuryPassedException` (2), `TerminationStrategy`, `TurnLimitedResult`, `JuryTerminationStrategy`,
  `StateMachineLoop`, `EvaluatorOptimizerLoop`, `TurnLimitedLoop`. This is the largest single item.
- `JudgeGate.extractScore` and `TieredGate.extractScore` both read `NumericalScore.value()` — the
  **raw** value — and compare it against a normalized gate threshold. Latent only because no current
  judge uses a non-unit range. §8 step 4 applies.
- `GateTest` wires `ConsensusStrategy` behind a score gate, which is exactly the §1.1 trap.
- `GateTest` constructs judgments through the removed builder and calls `reasoning(String)`.
- No impact: `getName()`, `ErrorPolicy` configuration, `Judgment.elapsed()`, `Judgment.error()`
  accessor, coverage judges.
- Its own `workflow-spec/.../v3/envelope/Judgment.java` DTO is unaffected.

### agentworks-pr-review (branch `v3-serving`, pins 0.11.0-SNAPSHOT)

- `VersionPatternJudge` (2 sites), `BuildJudge`, and `QualityJudge` construct judgments through the
  removed builder with `BooleanScore`/`NumericalScore`. `TestAssessments` does the same in 4 test
  fixtures.
- The Boolean-score sites are the clean case: `Judgment.verdict(passed).reasoning(...)` replaces a
  score that only ever duplicated the status.
- `CascadedJury`, `TierConfig`, and `TierPolicy` usage is structurally unaffected — tier policies
  read individual judgments.
- No impact: `getName()`, `ErrorPolicy` configuration, `elapsed()`, coverage judges.

---

## 10. Follow-ups not addressed in 0.14

- `Judgment.elapsed()` blind-casts a metadata entry to `Duration` and does not serialize cleanly
  (§7). Redesigning `metadata` was out of scope.
- Whether `metadata` should be narrowed to the JSON-safe value algebra rather than documented as a
  conditional guarantee.
- Machine-readable error classification. `ERROR` carries only `reasoning` today; an explicit
  `errorCode` would be added if a concrete consumer needs one. `label` must not be overloaded for it.
- Public documentation at `docs/agent-judge/` still describes the Score hierarchy and
  `ReactiveJudge`, and is reconciled separately from this handoff.
