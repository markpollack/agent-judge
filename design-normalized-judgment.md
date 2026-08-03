# Design proposal — normalized `Judgment` API

> **Status**: **implemented and verified against a clean full reactor; consumer handoff pending**
> **Date**: 2026-08-03
> **Revision**: r6. r3 incorporated the design review's six contract-hardening points (constructor
> invariants, stable evidence keys, corrected removal count, cascade reconciliation, qualified
> metadata guarantee, pinned property order) plus two consequences it opened (`label` permitted on
> `ABSTAIN` but not `ERROR`; evidence split universal versus strategy-specific). r4 moved the evidence
> keys into a **reserved `metadata.aggregation` namespace** with contract constants and normalised the
> strategy/policy tokens. r5 fixes three defects found in r4 — the reservation test tested nesting
> rather than reservation, the evidence block was only shallowly immutable, and the value algebra
> listed `null` despite `Map.copyOf` refusing it — and adds [DELTA-10], which separates an invalid
> weight configuration (reject) from a valid one whose usable weight vanishes after filtering
> (`ABSTAIN`). r6 corrects the characterization bookkeeping: the counts were 16/19, not 14/13, and
> exactly **one** `INTENDED` assertion flips — §7 now enumerates every affected test by name and
> records that `Majority`'s default `ErrorPolicy` change is an uncovered gap rather than a flip.
> **Supersedes**: `agent-workflow/plans/v3/inbox/HANDOFF-agent-judge-normalized-judgment-api-2026-08-03.md`
> where marked **[DELTA]**; adopts it unchanged everywhere else
> **Evidence base**: `ddd-review.md` (this repo) and
> `agent-judge-core/src/test/java/io/github/markpollack/judge/jury/VotingStrategyCharacterizationTest.java`
> (36 characterization cases — 16 `INTENDED`, 19 `DEFECT`, 1 `BASELINE` — preserved before
> migration in a5b15f8 and migrated in dc6ca2d)

---

## Implementation status — verified 2026-08-03

The design is implemented and verified against a clean full Maven reactor.

- **49a6c73** committed this design and the DDD review.
- **a5b15f8** preserved the pre-change characterization baseline, including the previously uncovered
  default-`ErrorPolicy` behavior.
- **dc6ca2d** replaced the Score hierarchy, migrated the producers and strategies, and updated the
  core tests to demonstrate the intended semantic distinctions.
- **9325ef3** migrated the five remaining downstream test files that still pinned pre-migration
  behavior.
- **014a779** closed two contract-coverage gaps and retired stale aggregation test prose.
- **c7d445c** removed `ReactiveJudge` and the Reactor dependency from core.

**Verified result** — `./mvnw clean verify` from a clean state, BUILD SUCCESS across all eleven
reactor modules: **433 tests, 0 failures, 0 errors, 0 skipped**. `agent-judge-core` covers
**93.72% of lines** (gate 80%) and **92.44% of branches** (gate 75%).

> **Correction to the dc6ca2d checkpoint claim.** That checkpoint stated that all modules reached
> main *and test* compilation. They did not. `agent-judge-ai-core` and `agent-judge-llm` test
> sources still referenced deleted types and did not compile, which also meant the LLM, RAG, and
> AgentClient suites had never executed on this branch. Only `agent-judge-core` had actually been
> run. This is recorded rather than quietly fixed, because the claim is what made the remaining work
> look smaller than it was.

Verification added beyond the §7 plan below, both closing gaps where a contract held in source but
in no test:

- the default `ErrorPolicy` is now pinned on **all five** strategies, not only `Majority`;
- `IGNORE` is shown to release an ignored judgment's weight in `WeightedAverageStrategy` rather than
  consuming it.

Design changes made after r6, on owner decision rather than new evidence about the value model:

- `ReactiveJudge` and `agent-judge-core`'s optional `reactor-core` dependency are removed. This does
  not touch the judgment or aggregation contract; it removes an unused published API. Recorded here
  because DESIGN.md's core dependency boundary changed with it.

Still pending:

1. A written consumer migration handoff for `agent-workflow` and other consumers.
2. Public documentation reconciliation for 0.14, which is outside the roadmap's stages.

The active execution plan is [roadmap.md](roadmap.md). The Phase 0 section below is retained as the
historical verification protocol that produced a5b15f8; it is complete and must not be repeated.

---

## 0. What this is and why

The practical trigger was serialization: `Judgment` does not project to sane JSON for `agent-workflow`
v3, and the consumer gave up and hand-rolled its own DTO
(`workflow-spec/.../envelope/Judgment.java`) whose shape is `String memberId, @Nullable Double score,
…`. That the consumer independently arrived at the proposed target shape is the strongest available
evidence the shape is right.

The fix is nonetheless a **domain correction**, and it must be justified as one, because the model is
wrong in ways that have nothing to do with JSON. Characterization proved three defects on the current
code:

| Current behaviour | Why it is wrong |
|---|---|
| `Judgment.abstain(...)` stores `BooleanScore(false)` | "I decline to assess" is recorded as "I assessed: worst possible" |
| `Average(pass, abstain)` = `0.5`; `Median(1.0, abstain, abstain)` = `0.0` → verdict flips to `FAIL` | an abstaining judge silently casts the strongest negative vote |
| `Consensus(statusOnly(PASS), statusOnly(PASS))` = `FAIL` | `Majority` counts `status`, `Consensus` counts `score` — "vote" means two things |

Serialization is the incidental benefit. The defects above are the reason.

---

## 1. Adopted from the handoff without change

These are settled; listed so the delta section is unambiguous.

- Retire `Score`, `BooleanScore`, `NumericalScore`, `CategoricalScore`, `ScoreType`, `Scores`. No
  replacement hierarchy, no compatibility shim. Pre-release, one clean model.
- `Judgment` carries three independent facts: required `status`, optional normalized `score` in
  `[0.0, 1.0]`, optional non-blank `label`.
- Invariants refuse at construction: null status, NaN, ±infinity, out-of-range score, blank label.
- `checks` and `metadata` stay immutable copies.
- Intent-specific entry points: `verdict(boolean)`, `passing()`, `failing()`, `scored(...)`,
  `classified(...)`, `abstaining()`, `error(...)`; direct conveniences `pass(String)`, `fail(String)`,
  `abstain(String)`, `error(String)` retained and delegating through the same invariants.
  (The handoff's `Throwable`-carrying error overloads are **not** adopted — see [DELTA-9].)
- Staged builders: `scored(...)` returns a stage with **no** `build()`, forcing `.passingAt(t)` or
  `.withStatus(s)`; `classified(...)` forces `.as(status)`. `Judgment.scored(0.82).build()` must not
  compile.
- `because(String)` is the primary fluent spelling; `withCheck` / `withChecks`;
  `metadata(String, Object)` / `metadata(Map)`.
- One central `effectiveScore()` returning `OptionalDouble` — `score` if present, else `1.0`/`0.0`
  for `PASS`/`FAIL`, else empty for `ABSTAIN`/`ERROR`. Derived view, never stored.
- No `instanceof` score dispatch anywhere in the jury package.
- `ABSTAIN` excluded from numeric denominators; `ERROR` never silently zeroed; explicit no-result
  outcome instead of dividing by zero.
- Non-goals honoured: no polymorphic Jackson annotations, no redesign of `Check`, no redesign of
  jury orchestration, no arbitrary label→number coercion.

---

## 2. Target value

```java
@JsonInclude(JsonInclude.Include.NON_NULL)                       // [DELTA-7]
public record Judgment(
        JudgmentStatus status,          // required
        @Nullable Double score,         // normalized [0.0, 1.0] when present
        @Nullable String label,         // non-blank when present
        String reasoning,
        List<Check> checks,
        Map<String, Object> metadata) {
```

**Component order changed** from `(score, status, reasoning, checks, metadata)` to the above, because
`status` is the required primary fact and reads first. All in-repo construction goes through the
builder, so the positional change costs nothing internally.

Property order is pinned with `@JsonPropertyOrder` rather than left to record declaration order, so
the exact-JSON tests assert something guaranteed instead of something incidental. **Field order is
not semantically contractual** — the field *set* and their names and types are. Consumers must not
depend on ordering.

### Invariants — enforced in the compact constructor, not only in the builders

Every rule below holds for *any* construction path, including the public canonical constructor.
A staged builder that cannot express an invalid combination is a usability feature; it is not the
enforcement mechanism.

| Rule | Rationale |
|---|---|
| `status` non-null | it is the primary fact |
| `reasoning` non-null | never `null`; empty string permitted in general |
| `reasoning` non-**blank** when `status` is `ERROR` or `ABSTAIN` | after [DELTA-9] it is the *only* carrier of why the judge could not evaluate — a blank one is an empty report |
| `checks`, `metadata` non-null, copied immutably | unchanged from today |
| `score`, when present, finite and within `[0.0, 1.0]` | rejects NaN, ±∞, out-of-range |
| **`score` must be absent when `status` is `ERROR` or `ABSTAIN`** | neither represents a completed measurement; a score asserts one was made |
| **`label` must be absent when `status` is `ERROR`** | the judge never completed, so it produced no classification |
| `label` **permitted** when `status` is `ABSTAIN` | a classifier may legitimately map a recognised label to `ABSTAIN` — `LabelJudgmentClassifier.Builder.abstain(String)` is an existing API. The judge *did* classify; the classification's declared meaning is "abstain" |
| `label`, when present, non-blank | unchanged from the handoff |

Summarised:

| Status | `score` | `label` |
|---|---|---|
| `PASS` | allowed | allowed |
| `FAIL` | allowed | allowed |
| `ABSTAIN` | **forbidden** | allowed |
| `ERROR` | **forbidden** | **forbidden** |

An abstaining classifier can complete successfully and produce a meaningful category —
`not_applicable`, `insufficient_evidence` — while still casting no vote. `ERROR` means the judge did
not complete, so a label there would either impersonate a completed classification or quietly become
an undeclared error-code channel.

> If machine-readable error classification is needed later, add an explicit `errorCode`. Do **not**
> overload `label` to carry it.

The `ERROR`-with-a-score prohibition is not contradicted by Inspect's `score_on_error`: that records
an agent-run error alongside a *separately completed* scorer result. `JudgmentStatus.ERROR` here means
specifically that **this judge** could not complete.

### Wire shape (pinned by test, exact)

```json
{"status":"pass","reasoning":"All checks passed","checks":[],"metadata":{}}
{"status":"pass","score":0.82,"reasoning":"Quality exceeded the acceptance threshold","checks":[],"metadata":{}}
{"status":"pass","label":"relevant","reasoning":"The document directly supports the claim","checks":[],"metadata":{}}
{"status":"error","reasoning":"Judge invocation timed out","checks":[],"metadata":{}}
```

Absent optionals are omitted, never `null`. No type tag, no wrapper object, no Java class name.

### Wire names for `JudgmentStatus` — **decided**

Lower case is the wire contract: `pass`, `fail`, `abstain`, `error`. Java keeps its own conventions
(`JudgmentStatus.ERROR`); JSON gets portable ones (`"error"`).

The mapping is an **explicit stable field**, never derived via `name().toLowerCase()`, so that
renaming a Java identifier cannot silently alter the published contract:

```java
public enum JudgmentStatus {

    PASS("pass"), FAIL("fail"), ABSTAIN("abstain"), ERROR("error");

    private final String wireName;

    JudgmentStatus(String wireName) { this.wireName = wireName; }

    @JsonValue
    public String wireName() { return wireName; }

    @JsonCreator
    public static JudgmentStatus fromWire(String value) {
        return Arrays.stream(values())
                .filter(status -> status.wireName.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown judgment status: " + value));
    }
}
```

Parsing is **exact and case-sensitive**. Upper case is refused rather than silently accepted — there
is no existing compatibility evidence requiring leniency, and accepting both spellings would make the
contract ambiguous from day one. Required tests: exact serialization, exact deserialization, unknown
value rejected, upper case rejected.

---

## 3. Deltas from the handoff

Each delta names the finding that produced it. All are resolved; two (`[DELTA-2]`, `[DELTA-3]`) record
a **reversal of my own earlier recommendation** and say so explicitly, so the reasoning that failed is
visible rather than quietly overwritten.

### [DELTA-1] `VotingStrategy` gets a written contract: `status` is the outcome of record

*Source: `ddd-review.md` Issue 4 — "vote" has two meanings.*

The handoff requires each strategy to state its missing-result policy, but does not resolve the
prior question: **what does a judgment even mean to a strategy?** Today `Majority` reads `status` and
`Consensus` reads `score`, so they are not substitutable — the whole point of the interface.

Proposal: state in `VotingStrategy`'s Javadoc, and honour in all five implementations, that
`status` is the authoritative outcome and `score` refines it. Every strategy consumes judgments
through exactly one of two doors:

- **status-counting strategies** (`Majority`, `Consensus`) read `status`;
- **numeric strategies** (`Average`, `WeightedAverage`, `Median`) read `effectiveScore()`.

Also delete `MajorityVotingStrategy`'s stale Javadoc claim that it thresholds numeric scores at 0.5,
plus its unused `THRESHOLD` constant and unused `NumericalScore` import — the code never did this.

### [DELTA-2] `ConsensusStrategy` excludes ABSTAIN and decides among the applicable judges — **decided**

*Source: `ddd-review.md` Warning 3 and `plans/inbox/abstain-aware-consensus.md`; resolved by the maintainer.*

The handoff does not mention this. Today both "unanimously failed" and "could not agree" return
`FAIL`; the distinction survives only in the reasoning string, and it is the one fact a consensus
strategy exists to report. Worse, the `score`-reading fall-through lets an `ABSTAIN` count as a fail
vote and *manufacture* unanimity — `Consensus(fail, abstain)` currently reports "Unanimous consensus".

**`ABSTAIN` means "not applicable to this run" — it is not a vote at all**, so it is excluded from
the consensus population rather than blocking unanimity. Consensus is then computed over the
*applicable* judges.

> **Corrected.** An earlier revision of this document had `ABSTAIN` block unanimity
> (`PASS + ABSTAIN → ABSTAIN`). That was wrong. It was reasoned from the abstract meaning of the word
> "consensus" without the consumer evidence, and it would break the exact case the library exists to
> support: judges that legitimately do not apply to a given run.

The grounding is `plans/inbox/abstain-aware-consensus.md` (2026-06-01), filed from the real consumer
**weekly-kb-sync**, whose Stage 1 judges abstain structurally:

```text
OverrideRespectedJudge  → abstains when no HumanOverrides exist
DryRunNoMutationJudge   → abstains when the run is not dry-run
StatusFileFormatJudge   → abstains when no LIGHT status files generated
GitCleanlinessJudge     → abstains when no processable project directories
```

As that brief puts it: `false because the property was violated` (FAIL) and `false because the judge
was not applicable` (ABSTAIN) are not the same fact. The consumer currently works around this by
using `MajorityVotingStrategy` for the tier with abstaining judges — "adequate but fragile", in its
words. This is corroborated by prior art: Ragas converts failed evaluations to NaN and aggregates
with `nanmean`; Inspect records an explicit *unscored* result excluded from metrics and reducers;
Snorkel treats abstention as *no emitted label* rather than a negative label.

| Inputs | Result |
|---|---|
| all `PASS` | `PASS` |
| all `FAIL` | `FAIL` |
| `PASS` + `ABSTAIN` | **`PASS`** |
| `FAIL` + `ABSTAIN` | **`FAIL`** |
| `PASS` + `FAIL` | `ABSTAIN` — the applicable judges disagree |
| all `ABSTAIN` | `ABSTAIN` |
| any `ERROR` | per `ErrorPolicy`, default `PROPAGATE` |

`PASS` means every applicable judge passed; `FAIL` means every applicable judge failed; disagreement
among applicable judges is `ABSTAIN`. `ERROR` stays a distinct execution failure and follows explicit
error handling, never disagreement handling.

No `ConsensusPolicy` enum is needed. "Consensus" has a definite meaning; whether disagreement *ought
to* count as a failure is a downstream policy decision, and folding it into the consensus calculation
would obscure the fact being computed.

#### Interaction with `CascadedJury` — verified against the code

**This change does not alter cascade escalation at all.** `CascadedJury` inspects the tier's
**individual** judgments, never its aggregate:

```java
hasAnyFail(tierVerdict)  → tierVerdict.individual().anyMatch(j -> j.status() == FAIL)
allPassed(tierVerdict)   → tierVerdict.individual().allMatch(j -> j.status() == PASS)
```

`ConsensusStrategy` produces the **aggregate**. So the new consensus semantics surface only at a
`FINAL_TIER`, or when a caller reads `Verdict.aggregated()` directly — the escalation decisions of
`REJECT_ON_ANY_FAIL` and `ACCEPT_ON_ALL_PASS` are untouched.

Consequently, `ACCEPT_ON_ALL_PASS` **does** still escalate when any scheduled judge abstains, because
`ABSTAIN` is not `PASS` at the individual level. That stricter cascade rule and the meaning of
consensus are genuinely separate concerns, and neither is changed by the other.

Required tests — from the brief, with the tier policy named explicitly in each cascade case, since
the outcome depends entirely on which policy the tier uses:

1. `Consensus(PASS, ABSTAIN)` → `PASS`;
2. `Consensus(PASS, PASS, ABSTAIN)` → `PASS`;
3. `Consensus(FAIL, ABSTAIN)` → `FAIL`;
4. `Consensus(ABSTAIN, ABSTAIN)` → `ABSTAIN`;
5. `CascadedJury`: T0 passes, T1 has `PASS + ABSTAIN` **and T1 is `FINAL_TIER`** → overall passes,
   because the tier's aggregate is consulted;
6. `CascadedJury`: T0 passes, T1 has `PASS + ABSTAIN` **and T1 is `ACCEPT_ON_ALL_PASS`** → escalates
   to T2, because `allPassed` is false at the individual level. This is the pair that makes the
   aggregate-versus-individual distinction observable;
7. `CascadedJury`: T0 passes, T1 all `ABSTAIN` under each tier policy.

### [DELTA-3] `ErrorPolicy` gains `PROPAGATE`; `IGNORE` is kept and *fixed* — **decided**

*Source: `ddd-review.md` Warning 4, whose recommendation is overruled by research evidence.*

> **Corrected.** `ddd-review.md` Warning 4 proposed removing `IGNORE` because it is behaviourally
> identical to `TREAT_AS_ABSTAIN`. That inference was wrong: the duplication is an **implementation
> defect, not evidence the policy is unnecessary**. The enum has documented "skip in vote counting"
> since 0.1.0 and the implementation simply never did it. My own characterization test had already
> marked the behaviour `INTENDED` — the removal proposal contradicted evidence I had gathered myself.

Established prior art treats "exclude from the population" as a first-class, distinct policy:

| System | Behaviour |
|---|---|
| Ragas | failed evaluations become `NaN`; aggregation uses `nanmean`, excluding rather than failing them |
| Inspect | an explicit *unscored* result stays recorded but is excluded from metrics and reducers |
| DeepEval | distinguishes a missing/not-applicable metric from an ignored execution error, preserving the latter as errored |
| Snorkel | fault-tolerant labelling converts an execution failure to an abstention — no emitted label, not a negative label |

**Four explicit policies**, `PROPAGATE` as the default for all five strategies:

| Policy | Meaning |
|---|---|
| `PROPAGATE` *(new, default)* | an `ERROR` produces an aggregate `ERROR` |
| `TREAT_AS_FAIL` | the errored judgment participates as a `FAIL` |
| `TREAT_AS_ABSTAIN` | it participates as an `ABSTAIN`; the conversion is preserved in aggregation reasoning and accounting |
| `IGNORE` | removed **entirely** from the aggregation population — numerator, denominator, vote count, and weight — while the original `ERROR` is retained in `Verdict.individual` for audit and diagnostics |

Implementation rules:

- **`IGNORE` is implemented by filtering, never by mapping `ERROR` to `Judgment.abstain(...)`.** The
  current mapping approach is what collapsed the two policies in the first place.
- **Weighted aggregation** must remove an ignored judgment's weight and normalize over the remaining
  eligible weight — not merely zero its contribution while consuming its weight.
- **All-ignored** returns `ABSTAIN` with reasoning naming the cause, e.g.
  `"No eligible judgments; 2 errors ignored"`.
- **All-errors-treated-as-abstentions** returns `ABSTAIN` with *different* reasoning, e.g.
  `"All 2 judgments abstained because of evaluation errors"`.
- **Tests must distinguish `IGNORE` from `TREAT_AS_ABSTAIN` through effective-population counts and
  reasoning**, including cases where the final `PASS`/`FAIL`/`ABSTAIN` status coincides. A test that
  only asserts the final status — as my characterization test did — cannot tell them apart and is
  what let the defect survive.

### [DELTA-4] Two judges report a caught exception as `FAIL`; both become `ERROR` — **decided**

*Source: catch-block audit across all modules; resolved by the maintainer.*

The governing rule, stated once and applied everywhere:

```text
FAIL  = evaluation completed and the subject did not satisfy it
ERROR = evaluation could not be completed
```

Applied:

| Situation | Status |
|---|---|
| command runs, returns a disallowed exit code or output | `FAIL` |
| command cannot be started, or execution infrastructure throws | `ERROR` |
| file is read, content does not match | `FAIL` |
| file cannot be read | `ERROR` |

The audit found this is **conformance to an existing convention, not a new policy**: 7 of the 9 catch
blocks that build a judgment already return `Judgment.error(...)`. Only two deviate.

| Already `ERROR` (7) | `FAIL` on caught exception (2) |
|---|---|
| `SupersetDiffJudge`, `ClassVersionJudge`, `FileComparisonJudge`, `TextFileJudge`, `JavaSemanticJudge`, `XmlSemanticJudge`, `MavenSemanticJudge` | `CommandJudge:160`, `FileContentJudge:109` |

Both outliers are fixed, and each gets a **focused test proving the boundary** — one case that
completes with a negative result (`FAIL`) and one that cannot complete (`ERROR`) — so the distinction
is pinned per class rather than assumed.

### [DELTA-4a] A missing JaCoCo report is `ABSTAIN`, not `FAIL` — **decided**

*Initially deferred as ambiguous; the deferral did not survive review.*

`CoverageImprovementJudge:109` and `CoveragePreservationJudge:85` return
`Judgment.fail("No JaCoCo report found in workspace — coverage cannot be verified")`. There is no
reading under which this is a completed negative finding: **a missing report says nothing about
coverage.** The judge measures coverage; with no report it has no measurement. Absence of evidence
is not evidence of a negative result.

The codebase already settles it. In the *same method*, a few lines apart, two missing inputs are
classified oppositely:

```java
CoverageImprovementJudge.java:91   Judgment.abstain("No baselineCoverage in metadata")   // input absent → ABSTAIN
CoverageImprovementJudge.java:109  Judgment.fail("No JaCoCo report found …")             // input absent → FAIL
```

All 20 `Judgment.abstain(...)` sites across the modules are "a required input is absent", including
the exact analogues `SupersetDiffJudge`'s `"No files in reference directory"` and
`ClassVersionJudge`'s `"No .class files found in target/classes"`. These two guards are the outliers.

**`ABSTAIN`, not `ERROR`**: nothing threw. `JaCoCoReportParser.parse` returned normally, reporting
absence — which is precisely `JudgmentStatus.ABSTAIN`'s documented "insufficient information", not
`ERROR`'s "encountered an error during evaluation".

Two existing tests pin the old behaviour and will flip: `CoverageImprovementJudgeTest.noReportReturnsFail`
and `CoveragePreservationJudgeTest.noReportReturnsFail`. Both are characterization of the code rather
than evidence of a considered decision — the names restate the implementation — but the flip is a
deliberate semantic change and is recorded as one, with the tests renamed to
`noReportReturnsAbstain`.

> **Downstream consequence, stated because it is a real behaviour change.** Under the old code, a
> workspace with no coverage report failed a coverage gate. Under the new code it abstains, and per
> [DELTA-1] `ABSTAIN` is excluded from numeric denominators — so a jury of one coverage judge now
> yields a no-result `ABSTAIN` verdict rather than `FAIL`. Anyone relying on "no report ⇒ gate
> closed" must add an explicit judge asserting the report exists. Called out in the consumer handoff.

### [DELTA-10] Zero eligible weight yields `ABSTAIN` instead of a `NaN` score — **decided**

*Source: design review; confirmed by execution against current `main`.*

`WeightedAverageStrategy` computes `weightedSum / weightSum` with no guard. When every resolved weight
is zero the result is `0.0 / 0.0` = `NaN`, and — because every IEEE 754 comparison against `NaN` is
false — `NumericalScore`'s validation `value < min || value > max` **does not trip**. A `NaN` score is
constructed successfully. Verified by running it:

```text
score value = NaN  isNaN=true
status      = FAIL
reasoning   = Weighted average: NaN (threshold: 0.50, result: fail)
```

So today a jury with all-zero weights emits a `FAIL` whose reasoning literally reads `NaN`, and whose
score then fails any downstream gate comparison — failing closed, but on garbage rather than on a
finding.

Under the new invariants a non-finite score is refused at construction, so this path would *throw* —
worse than what it replaces. But "always `ABSTAIN`" is also wrong, because it conflates two genuinely
different situations. **An invalid weight configuration is a caller error and must fail loudly; a
valid configuration whose usable weight disappears after filtering is a runtime no-result.**

| Case | Outcome |
|---|---|
| any weight negative, `NaN`, or infinite | **reject** — `IllegalArgumentException`, invalid configuration |
| all weights explicitly zero (`inputWeight == 0`) | **reject** — `IllegalArgumentException`, invalid configuration: no judge can influence the result |
| `inputWeight > 0`, `eligibleWeight > 0` | weighted mean as normal |
| `inputWeight > 0` but `eligibleWeight == 0` — every positive-weight judge abstained or was ignored | **`ABSTAIN`**, reasoning naming the cause, evidence showing `inputWeight > 0` and `eligibleWeight == 0` |
| no eligible judgments at all | `ABSTAIN`, per the existing no-result rule |
| a `Judgment` constructed directly with a `NaN` score | rejected by the compact constructor via `Double.isFinite` |

An individual zero weight stays legal — it means "this judge does not count". Only a configuration in
which *nothing* counts is rejected.

#### Empty weights no longer delegate to `AverageVotingStrategy`

Today `WeightedAverageStrategy` short-circuits:

```java
if (weights == null || weights.isEmpty()) {
    return new AverageVotingStrategy().aggregate(judgments, weights);
}
```

That must go. With the evidence block in place, the delegation would stamp
`"strategy": "average"` onto a verdict the caller produced with `WeightedAverageStrategy` — the
evidence would misidentify the strategy, which is precisely the drift the block exists to prevent.

Instead, `WeightedAverageStrategy` resolves missing weights to `1.0` internally and computes
normally. The numeric result is unchanged — equal weights reduce to the simple mean, and both
strategies now exclude abstentions from their denominator — so only the attribution changes. The
characterization assertion on the *value* still holds; the assertion on the delegation does not, and
is rewritten.

Each row of the table above gets its own test. This closes both the `NaN` defect and the
configuration-versus-no-result distinction. All of it is pre-existing latent behaviour surfaced by
the redesign rather than introduced by it, and is recorded as a deliberate semantic change.

### [DELTA-5] Degenerate range `max == min` is now refused

*Source: handoff §2.2 "Refuse `max <= min`" versus current `NumericalScore.normalized()`.*

Current code special-cases it: returns `1.0` if `value == max`, else `0.0`. The handoff refuses it.
These conflict; the handoff wins, but it is a silent behaviour change worth recording rather than
discovering. No in-repo judge constructs a degenerate range, so blast radius is nil.

### [DELTA-6] The label→score policy gets an owner; `Scores` is deleted as dead code

*Source: `ddd-review.md` Warning 1.*

`Scores.toNormalized(score, categoryMap)` is the correct domain service and is referenced **only by
its own unit test** — verified by grep across all modules. All four numeric strategies carry a
private, lossier copy that drops the `categoryMap` and hardcodes categorical → `0.0`.

Proposal: `LabelJudgmentClassifier` — which already owns `categories()` — also owns an optional
label→normalized-score map, and records the mapped score on the `Judgment` at classification time.
Downstream aggregation then needs no policy at all. New builder method:

```java
LabelJudgmentClassifier.builder()
    .pass("excellent", 1.0)     // status + declared normalized score
    .pass("good", 0.6)
    .fail("poor", 0.0)
    .build();
```

Existing `.pass(label)` / `.fail(label)` / `.abstain(label)` / `.map(label, status)` keep working and
record no score. Unmatched output stays `ABSTAIN` with no score and no label — there is no valid
label, so none is asserted. Then delete `Scores` and `ScoreType`.

### [DELTA-7] `@JsonInclude(NON_NULL)` on `Judgment`

The handoff requires absent optionals to be omitted rather than emitted as `null`, and §7 requires
tests asserting exact JSON. A plain record emits `"score":null`. The alternatives are a per-consumer
global `ObjectMapper` setting (leaks into their unrelated types) or this annotation.
`jackson-databind` is already a compile dependency of `agent-judge-core`, and `@JsonInclude` is not
a polymorphic annotation, so the §8 non-goal is not engaged.

### [DELTA-9] The live `Throwable` leaves the model entirely — **decided**

*Source: `ddd-review.md` Warning 6; resolved by the maintainer, overriding handoff §2.4 and §3.4.*

The handoff permits retaining the exception "through the existing in-process metadata convention".
That is rejected. A `Judgment` is a simple result value, not an exception transport, and a live
`Throwable` drags in stack frames, causal chains, suppressed exceptions, Java-specific class
identity, and unpredictable serialization — none of which is judgment data. It is diagnostic *process*
state, and it belongs in the log at the point of catch.

**Removed:** `Judgment.error(String, Throwable)`, the `Throwable error()` accessor, and the `"error"`
metadata key convention. No `ErrorInfo`, no error-code enum, no stack-trace field, no polymorphic
error object.

**Kept:** the distinction that matters —

```text
FAIL  = the judge completed and rejected the subject
ERROR = the judge could not complete the evaluation
```

Resulting contract: machines classify on `status == ERROR`; humans read `reasoning`; operators read
the original exception from logs. No exception object crosses the wire.

```java
// construction — log where caught, then record a readable explanation
catch (Exception ex) {
    logger.error("Judge invocation failed", ex);
    return Judgment.error("Judge invocation timed out");
}

public static Judgment error(String reasoning) {
    return Judgment.withStatus(JudgmentStatus.ERROR).because(reasoning).build();
}

// consumption — predicate replaces the Throwable accessor
public boolean hasError() {
    return status == JudgmentStatus.ERROR;
}
```

Where `ex.getMessage()` could leak sensitive detail, judges pass a sanitized message instead.
Machine-readable error *classification* is deferred until a concrete consumer requires it.

#### Scope of the portability guarantee — stated precisely

Removing the `Throwable` makes the **declared fields** of `Judgment` wire-safe. It does **not** make
every `Judgment` portable, and this document must not claim otherwise: `metadata` remains
`Map<String, Object>` and can still hold a `Duration`, an arbitrary Java object, or indeed another
`Throwable` put there by a caller.

The guarantee is therefore:

> `status`, `score`, `label`, `reasoning`, and `checks` are unconditionally serializable. A
> **serializable `Judgment`** additionally requires every `metadata` value to belong to the project's
> JSON-compatible value algebra: a **string**, a **finite number**, a **boolean**, a **list**, or a
> **string-keyed map**, recursively composed of those. Judges that place richer objects in `metadata`
> produce judgments that are valid in-process but not portable.

**`null` is excluded from the algebra**, and deliberately so. An earlier revision listed it, which was
self-contradictory: `Map.copyOf` rejects null keys and values outright, so a metadata map containing
one cannot be constructed in the first place. This also matches the project's existing no-null
direction — absence is expressed by omitting the key, exactly as absent `score` and `label` are
expressed by omitting the property. Non-finite numbers are excluded for the same reason they are
excluded from `score`: `NaN` and `±∞` have no valid JSON representation.

All aggregation-evidence values defined in §4 are integers, finite doubles, or strings, and satisfy
this constraint.

> **Immutability is shallow for caller-supplied values.** `Map.copyOf(metadata)` copies the map, not
> the objects inside it, so a mutable object a judge places in `metadata` remains mutable through
> `judgment.metadata().get(key)`. This is pre-existing and is not fixed here — deep-copying arbitrary
> values is neither cheap nor well-defined. What *is* guaranteed is that the reserved `aggregation`
> block this design introduces is itself deeply immutable, and that is tested.

> **Known follow-up, deliberately not in scope.** `Judgment.elapsed()` has the same shape of problem
> — it blind-casts a `metadata` entry to `Duration`, and a raw `Duration` does not serialize cleanly
> under plain `jackson-databind`. The handoff's non-goals fence off redesigning `metadata`, so
> `elapsed()` is left as-is and recorded in the consumer handoff as a named follow-up, along with the
> question of whether `metadata` should be narrowed to the value algebra above in a later act.

### [DELTA-8] `reasoning(String)` is removed outright, not deprecated

The handoff permits `reasoning(String)` as a deprecated bridge for one release. Every in-repo caller
is being migrated anyway, and every external consumer must recompile against the new `Judgment`
regardless — so the bridge buys nothing and preserves two spellings for a vocabulary the redesign is
trying to make univocal. `because(String)` only.

---

## 4. Aggregation semantics, restated per strategy

Current behaviour is from the characterization test. "New" is what this proposal implements.

In every strategy, `ABSTAIN` is **not a vote**: it leaves the population entirely, taking its weight
with it. All five gain an `ErrorPolicy` defaulting to `PROPAGATE`.

| Strategy | Reads | ABSTAIN | Nothing eligible | Aggregate carries |
|---|---|---|---|---|
| `Majority` | `status` | excluded from counts *(unchanged)* | `ABSTAIN` *(unchanged)* | status only, no score |
| `Consensus` | `status` **(was `score`)** | **excluded; consensus over applicable judges** (was: counted as a fail vote) | `ABSTAIN` **(was `FAIL`)** | status only, no score |
| `Average` | `effectiveScore()` | excluded from numerator **and denominator** **(was: 0.0, counted)** | `ABSTAIN` **(was `FAIL` scored 0.0)** | normalized mean |
| `WeightedAverage` | `effectiveScore()` | excluded, **weight removed and remaining weight renormalized** **(was: 0.0, weight consumed)** | `ABSTAIN` | normalized weighted mean |
| `Median` | `effectiveScore()` | excluded **(was: 0.0, participated)** | `ABSTAIN` | normalized median |

Three consequences worth stating plainly:

1. **All five strategies gain an `ErrorPolicy`, defaulting to `PROPAGATE`.** Only `Majority` has one
   today, defaulting to `TREAT_AS_FAIL`; the other four silently scored errors as `0.0`. `PROPAGATE`
   is the consistent reading of the `FAIL`-vs-`ERROR` contract: an errored judge must not silently
   become a negative finding it never made. This **changes `Majority`'s default** from
   `TREAT_AS_FAIL`, which is a deliberate semantic change and is recorded as one.
2. **`Majority` and `Consensus` emit no score.** They aggregate outcomes, not quantities; under the
   new model, manufacturing `1.0`/`0.0` would re-introduce exactly the duplication being removed.
   Vote counts and participation/error counts stay as aggregation *evidence* in `reasoning` and
   `metadata`, and `effectiveScore()` remains the derived `1.0`/`0.0` compatibility projection.
3. **The aggregate must report its effective population, under stable keys.** Exactly **three**
   mechanisms remove a judgment from the voting/numeric population — `ABSTAIN`, `ERROR` under
   `TREAT_AS_ABSTAIN`, and `ERROR` under `IGNORE`. `TREAT_AS_FAIL` keeps the judgment *in* the
   population as a `FAIL`, and `PROPAGATE` short-circuits aggregation entirely rather than removing
   anything. (An earlier revision of this document said "three of the four policies", which was
   simply wrong.)

   Because removal now has several causes, the aggregate must say how many judgments were eligible
   and why the rest were not — under key names that do not vary between strategies. Unstable or
   strategy-local keys would leave `IGNORE` and `TREAT_AS_ABSTAIN` observationally identical, which
   is the defect that produced [DELTA-3].

   **These keys live in a reserved `aggregation` namespace**, not at the top level of `metadata`.
   `metadata` is an open extension plane that callers write to freely; a flat `passCount` would
   eventually collide with a caller's own key, and the collision would be silent. Nesting also keeps
   the whole block trivially strippable by a consumer that does not want it.

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

   **Universal — every strategy emits all of these:**

   | Key | Meaning |
   |---|---|
   | `strategy` | which strategy produced the aggregate |
   | `errorPolicy` | which policy was applied to errored judgments |
   | `inputCount` | how many judgments were submitted |
   | `eligibleCount` | how many actually contributed to the reduction |
   | `explicitAbstainCount` | arrived with `status == ABSTAIN`; excluded as not applicable |
   | `errorCount` | arrived with `status == ERROR` |
   | `ignoredErrorCount` | errors removed from the population under `IGNORE` |
   | `errorsTreatedAsAbstainCount` | errors converted to non-votes under `TREAT_AS_ABSTAIN` |
   | `errorsTreatedAsFailCount` | errors that participated as `FAIL` under `TREAT_AS_FAIL` |

   `explicitAbstainCount` is deliberately named to distinguish a judge's *own* abstention from an
   error converted into one — those are different facts that the previous `abstainCount` conflated.

   **Strategy-specific — describing the actual reduction:**

   | Strategy | Additional keys |
   |---|---|
   | `Majority`, `Consensus` | `passCount`, `failCount` |
   | `WeightedAverage` | `inputWeight`, `eligibleWeight` |
   | `Average`, `Median` | none beyond `eligibleCount` |

   `passCount`/`failCount` are deliberately **not** emitted by the numeric strategies: those count no
   votes, and emitting zeros would read as real counts rather than as "not applicable here". The
   aggregate's own status and optional score already live on the `Judgment` and are not duplicated
   into this block.

   `inputWeight` is the sum of resolved weights **before** eligibility filtering; `eligibleWeight` is
   the sum **after**. If eligible judgments exist but `eligibleWeight` is zero, the strategy returns
   `ABSTAIN` rather than dividing by zero — see [DELTA-10].

   **Key names are contract constants**, declared once in an `AggregationEvidence` type in the `jury`
   package rather than as scattered string literals, and asserted literally in tests because
   consumers will depend on them. `AggregationEvidence` is a **utility/factory that produces an
   immutable, JSON-compatible `Map<String, Object>`** — an `AggregationEvidence` *object* is never
   placed into `metadata`, since that would violate the value algebra declared below. The map it
   returns is deeply immutable: `Map.copyOf(metadata)` on the `Judgment` is only a *shallow* copy, so
   the nested block must be immutable in its own right or a caller could mutate it through
   `judgment.metadata().get("aggregation")`.

   `strategy` and `errorPolicy` are **lower-camel-case** tokens — `majority`, `consensus`, `average`,
   `weightedAverage`, `median`; `propagate`, `treatAsFail`, `treatAsAbstain`, `ignore` — consistent
   with the `JudgmentStatus` wire-name convention. Like `JudgmentStatus`, `ErrorPolicy` carries
   **explicit stable token fields**; the tokens are never derived mechanically from enum constant
   names, so renaming a constant cannot silently alter the published contract.

   **This normalises `VotingStrategy.getName()`**, which currently returns the inconsistent set
   `"majority"`, `"Consensus"`, `"AverageVoting"`, `"WeightedAverage"`, `"MedianVoting"`. Usage was
   checked across `agent-judge`, `agent-workflow`, and `agentworks-pr-review`: the existing values are
   pinned only by `agent-judge`'s own tests, with no known production consumer. Its Javadoc is
   strengthened to state the contract:

   > Returns the stable lower-camel-case identifier used in diagnostics and aggregation evidence.

   Recorded in the consumer handoff as a small public-API change.

   #### Reserving the `aggregation` key

   Two mechanisms, because the first alone is not a guarantee:

   1. **Aggregation strategies never copy input metadata.** An aggregate `Judgment`'s metadata
      contains exactly the evidence block and nothing else. A voting strategy therefore cannot
      overwrite a caller's key, because it never sees one.
   2. **`aggregation` is a reserved key.** `Judgment.Builder.metadata("aggregation", …)` throws
      `IllegalArgumentException`. Without this, any judge could attach an unrelated `aggregation`
      value that a consumer reading `metadata.aggregation` would misparse as evidence.

   > The obvious test — a caller writing a top-level `passCount` — demonstrates only that nesting
   > works, which is true by construction. It does **not** test reservation. The real collision is a
   > caller supplying the key `aggregation` itself, and that is what must be tested.

---

## 5. Decisions — all resolved

| # | Decision | Resolution |
|---|---|---|
| **D1** | ~~How should the error be represented on the wire?~~ | **DECIDED — drop the live `Throwable` entirely.** `status = ERROR` plus human-readable `reasoning`; `boolean hasError()` replaces the `Throwable` accessor; the exception is logged where caught. No error fields, no hierarchy, until a concrete machine-readable classification requirement exists. See [DELTA-9]. |
| **D2** | ~~`Consensus` and `ABSTAIN`?~~ | **DECIDED — `ABSTAIN` is not a vote; exclude it and compute consensus over the applicable judges.** `(PASS, ABSTAIN)` → `PASS`; `(FAIL, ABSTAIN)` → `FAIL`; `(PASS, FAIL)` → `ABSTAIN`. Grounded in `plans/inbox/abstain-aware-consensus.md` and a real consumer. No `ConsensusPolicy`. Truth table in [DELTA-2]. |
| **D3** | ~~Remove `ErrorPolicy.IGNORE`?~~ | **DECIDED — keep it and fix it.** Four policies: `PROPAGATE` (new default for all five strategies), `TREAT_AS_FAIL`, `TREAT_AS_ABSTAIN`, `IGNORE`. `IGNORE` filters from the population — numerator, denominator, vote count, weight — retaining the `ERROR` in `Verdict.individual`. See [DELTA-3]. |
| **D4** | ~~Caught exception → `ERROR` instead of `FAIL`?~~ | **DECIDED — yes, for `CommandJudge` and `FileContentJudge`,** the only two of nine catch sites that deviate from the convention. Focused boundary tests in each class. JaCoCo guard explicitly excluded pending separate adjudication. See [DELTA-4]. |
| **D5** | ~~Keep `reasoning(String)` as a deprecated bridge?~~ | **DECIDED — no.** Everyone recompiles anyway; two spellings undercut the vocabulary fix. `because(String)` only. |
| **D6** | ~~Should `Majority`/`Consensus` store the vote ratio as `score`?~~ | **DECIDED — no.** Vote counts and participation/error counts stay as aggregation evidence in `reasoning`/`metadata`; `effectiveScore()` remains the derived `1.0`/`0.0` compatibility projection. Storing the ratio would make a configured `0.8` mean "quality ≥ 0.8" under `Average` but "80% of judges agreed" under `Majority`. |
| **D7** | ~~`@JsonInclude(NON_NULL)` on `Judgment`?~~ | **DECIDED — yes.** Required to meet the "omitted, never null" contract without pushing global mapper config onto every consumer. |
| **D8** | ~~Enum casing on the wire?~~ | **DECIDED — lower case: `pass`, `fail`, `abstain`, `error`.** Explicit stable wire-name fields, not `name().toLowerCase()`. Parsing exact and case-sensitive; upper case refused. See §2. |

---

## 6. Blast radius

Production files touched, by module — from the full inventory, not an estimate:

| Module | Files | Nature |
|---|---|---|
| `agent-judge-core` | 14 | `Judgment` + `JudgmentStatus` + 5 strategies + `ErrorPolicy` rewritten; 6 score types deleted; `FileContentJudge` semantic (D4); 2 other `fs` judges mechanical |
| `agent-judge-exec` | 4 | `CommandJudge` semantic (D4); both coverage judges semantic (DELTA-4a); 1 mechanical |
| `agent-judge-file` | 5 | mechanical |
| `agent-judge-rag` | 3 | mechanical |
| `agent-judge-llm` | 2 | mechanical + Javadoc example |
| `agent-judge-ai-core` | 1 | `LabelJudgmentClassifier` — new score-mapping API (DELTA-6) |
| `agent-judge-spring-ai`, `-langchain4j`, `-koog`, `-agent-client` | 0 production | context-builder bridges only; tests touched |

Test files: 20 reference the old score types and migrate with the API. The 80% line / 75% branch
JaCoCo gate on `agent-judge-core` must still hold.

**Out of scope this session, per your instruction:** no edits to `agent-workflow` or
`agentworks-pr-review`. Both get a written migration handoff instead. One item for it is already
known and is a genuine bug rather than a rename — `agent-workflow`'s `JudgeGate.extractScore` and
`TieredGate.extractScore` call `NumericalScore.value()`, the **raw** value, and compare it to a
normalized gate threshold (`ddd-review.md` Warning 2). It is latent only because no current judge
uses a non-unit range.

---

## 7. Verification plan

Per handoff §7, plus what the DDD review added:

- **Value invariants** — the full §2 table, asserted **through the canonical constructor** as well as
  the builders, since the constructor is the path a staged builder cannot police: score absent /
  `0.0` / `1.0` / interior; reject negative, `>1`, NaN, ±∞; reject a non-null score on `ERROR` and on
  `ABSTAIN`; reject a label on `ERROR`; **accept** a label on `ABSTAIN`; label absent / non-blank;
  reject blank label; reject null status, reasoning, checks, metadata; reject blank reasoning on
  `ERROR` and `ABSTAIN` while permitting it elsewhere; `checks` and `metadata` immutable.
- **Fluent API** — `verdict(true|false)` derives only the status and no score; `passing()`/`failing()`;
  `passingAt` below / above / **exactly at** threshold (`>=` passes); raw-range normalization;
  invalid ranges rejected including `max == min` (DELTA-5); explicit status on a scored judgment
  restricted to `PASS`/`FAIL`; `classified(...)` unbuildable before `.as(...)`; abstain/error carry
  no manufactured score; direct factories delegate to the same invariants.
- **Aggregation** — the full matrix in §4 for all five strategies: boolean-only, numeric-only, mixed,
  labelled with and without a declared numeric mapping, abstain among valid, error among valid,
  nothing eligible, empty input.
- **Consensus truth table** — the six cases enumerated in [DELTA-2], including the two `CascadedJury`
  cases (T1 with `PASS + ABSTAIN`; T1 all `ABSTAIN`).
- **Error policies** — all four constants × all five strategies. `IGNORE` and `TREAT_AS_ABSTAIN` must
  be distinguished by the **reserved evidence block** of §4 (`eligibleCount`, `ignoredErrorCount`,
  `errorsTreatedAsAbstainCount`) and by reasoning, including the cases where their final status
  coincides; a status-only assertion is explicitly insufficient. Weighted aggregation must be shown
  to renormalize over `eligibleWeight` after an `IGNORE`, not merely to zero a contribution while
  consuming its weight.
- **Evidence block** — every strategy emits all nine universal keys under `metadata.aggregation` with
  correct values; `Majority`/`Consensus` additionally emit `passCount`/`failCount`;
  `WeightedAverage` emits `inputWeight`/`eligibleWeight`; `Average`/`Median` emit neither set. Key
  names asserted literally against the `AggregationEvidence` constants.
- **Namespace reservation** — `Judgment.Builder.metadata("aggregation", …)` throws; and an aggregate's
  metadata is shown to contain the evidence block alone, proving strategies copy no input metadata. A
  top-level `passCount` test is explicitly *not* sufficient: it demonstrates nesting, which is true by
  construction, not reservation.
- **Evidence immutability** — mutating the map returned by `judgment.metadata().get("aggregation")`
  fails. `Map.copyOf` is shallow, so this needs its own assertion rather than inheriting the
  `Judgment`-level copy.
- **Weight configuration** — one test per row of [DELTA-10]: negative, `NaN`, and infinite weights
  rejected; all-zero weights rejected as invalid configuration; a single zero weight accepted;
  `inputWeight > 0` with `eligibleWeight == 0` yielding `ABSTAIN` with the evidence to prove it; and
  empty weights computing in-strategy with `"strategy": "weightedAverage"` in the evidence rather
  than delegating to `average`.
- **FAIL-vs-ERROR boundary** — a focused pair in `CommandJudge` and `FileContentJudge`: one case that
  completes with a negative result (`FAIL`), one that cannot complete (`ERROR`).
- **Serialization** — the four exact JSON documents in §2 with order pinned by `@JsonPropertyOrder`;
  absent optionals omitted; no type metadata; round-trip deserialization; lower-case wire names
  serialized and parsed exactly, with upper case and unknown values both rejected; a test asserting
  an `ERROR` judgment carries no exception object anywhere in its projection; and a test documenting
  that a judgment whose `metadata` holds a non-JSON-safe value is **not** portable, so the boundary
  of the guarantee is executable rather than prose.
- **Characterization migration** — `VotingStrategyCharacterizationTest` is rewritten against the new
  API. It holds **35 characterization cases (test methods): 16 `INTENDED`, 19 `DEFECT`.** (An earlier revision of this document
  said 14 and 13, which was wrong and did not even sum to 35.) All 19 `DEFECT` cases flip by
  design. Of the 16 `INTENDED`, **exactly one flips**:

  | Disposition | Count | Test names |
  |---|---|---|
  | Holds unchanged | 13 | `numericOnly`, `booleanOnly`, `mixed`, `emptyRejected`, `oddCount`, `evenCount`, `weightsByIndex`, `absentWeightDefaultsToOne`, `unanimousPass`, `majorityWins`, `allAbstain`, `tiePolicy`, `errorTreatedAsFail` |
  | **Flips** — delegation removed per [DELTA-10]; the value assertion (`0.5`) survives, the delegation assertion is rewritten to require in-strategy computation and `"strategy": "weightedAverage"` evidence | 1 | `emptyWeightsDelegate` |
  | Holds but is **insufficient** — asserts only the final status, so it cannot distinguish `IGNORE` from `TREAT_AS_ABSTAIN`; strengthened with evidence-block assertions rather than flipped | 1 | `errorIgnored` |
  | Holds, but its reasoning-string assertion (`"1 passed, 0 failed"`) is coupled to wording that changes when the evidence block lands — test maintenance, not a semantic change | 1 | `abstainExcluded` |

  **Coverage gap, not a reclassification.** `Majority`'s `ErrorPolicy` default moving from
  `TREAT_AS_FAIL` to `PROPAGATE` per [DELTA-3] flips **no** existing case: `errorTreatedAsFail` and
  `errorIgnored` both pass the policy *explicitly*, and no case exercises the default constructor
  with an `ERROR` input. The default being changed therefore has **no characterization coverage at
  all**. It is closed by Phase 0 below.

  Every flip and the gap above are recorded in the implementation record as deliberate semantic
  changes, listed separately from mechanical API migration. The record must state, in these terms:
  **one `INTENDED` case flips (`emptyWeightsDelegate`), and one newly characterized current behaviour
  deliberately changes (`Majority`'s default error policy).**

### Phase 0 — COMPLETE: baseline established before production change

This protocol was executed against untouched production code and preserved in **a5b15f8** before the
migration in **dc6ca2d**:

1. Add a case exercising `new MajorityVotingStrategy()` — the **default** constructor — with an
   `ERROR` judgment among the inputs.
2. Assert that current `main` produces `FAIL`, demonstrating the implicit `TREAT_AS_FAIL` default.
3. Run it green against the unmodified implementation.
4. **Preserve that checkpoint in its own commit**, before deleting any score type or touching any
   strategy. Completed in a5b15f8.
5. Only then begin the migration. After the default changes, flip the assertion to `ERROR`.

**Label it `BASELINE`, not `INTENDED`.** The existing behaviour is demonstrable but there is no
evidence it was deliberately chosen — no test, no Javadoc, and a constructor comment that says merely
"safest default". Marking it `INTENDED` would assert a design intent nobody recorded.

This created a third marker and a 36th case:

| Marker | Cases | Meaning |
|---|---|---|
| `INTENDED` | 16 | behaviour the redesign must preserve |
| `DEFECT` | 19 | behaviour the redesign deliberately changes |
| `BASELINE` | 1 | current behaviour, demonstrable but never deliberately chosen; changed with evidence rather than assumption |
