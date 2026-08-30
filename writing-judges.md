# Writing a Judge

A practitioner's manual for constructing a judge for a specific purpose.

> **Scope.** [Design philosophy](https://lab.pollack.ai/docs/agent-judge/design-philosophy) explains what Agent
> Judge's types mean. This document is the other half: how to *choose* what your judge asks, where
> to put its pass mark, how to combine its parts, and how to prove it is capable of failing before
> you trust a number it produced. It is written for someone about to build a domain judge and for
> someone who inherited one.
>
> **Provenance is marked throughout.** `[OURS]` marks a recommendation derived from failures
> observed in production use of this library — it is our claim, not a citation. `[CORPUS]` marks
> guidance supported by a surveyed evaluation framework or paper, with the source named.
> `[OPEN]` marks a question where we could not find prior art and are reasoning from first
> principles. A stretched citation would be weaker than an honest `[OURS]`.

---

## 0. The failure this manual exists to prevent

Over one day of dogfooding, five separate defects were found in a working, tested, in-production
judge suite built on this library. Every one had been live for weeks. Not one had ever produced an
error, a warning, or a log line.

| # | Defect | What it looked like |
|---|---|---|
| 1 | A judge pinned to a model the vendor had retired returned `ERROR` on 7 of 7 items for ten weeks | The jury reported a plausible score from the *other* judge |
| 2 | Weights were keyed by array position because a `Judgment` carries no judge identity | The aggregate was still a number in range |
| 3 | A cross-reference check passed over 25 references its parser could not see | "All references resolve" |
| 4 | A "every critical finding has a location" check ran on reports with zero critical findings | "All critical findings carry a Location" |
| 5 | A rubric scored 7 criteria, recorded per-criterion pass/fail as `Check`s, then discarded them and formed the verdict from the **mean** | `{3,3,3,3,3,3,0} = 0.857` — a pass, with one criterion "missed entirely" |

All five share one **symptom**: a plausible number, nothing logged, and output that looked
*shorter* rather than *wrong*. They split into two **mechanisms**, and the distinction matters
because the fixes are unrelated.

### Shape 1 — absence renders as success (defects 1, 3, 4)

⭐ The judge produced a right-looking answer from **missing data**. A judge that cannot speak is
indistinguishable from a judge with nothing to add. A check with no input is indistinguishable from
a check whose input was correct.

> **The governing rule for shape 1.**
> **A judge must be able to distinguish "I checked and it was fine" from "I did not check."**
> If your judge cannot express that difference in its output, it will eventually report the second
> as the first.

Addressed in §2 (state the denominator), §3 (outcome vocabulary and the roster), and §7 (prove the
judge can fail).

### Shape 2 — a wrong number from complete data (defects 2, 5)

⭐ Here nothing was missing. Defect 2 **misattributed** present evidence: weights were keyed by array
position, so a correct set of judgments was combined against the wrong authority. Defect 5
**compensated across criteria**: a zero was fully visible in the output and the mean absorbed it.

> **The governing rule for shape 2.**
> **Evidence must keep its identity through aggregation, and aggregation must not let one criterion
> pay for another unless you decided it may.**

Addressed in §5 (conjunction, not mean) and §6.2 (give the judge an identity).

Neither shape is a discipline problem, and more careful review does not catch either — reviewers
read what is printed, and in shape 1 nothing is printed while in shape 2 what is printed is
plausible. Both are *design* problems, and the rest of this manual is the design response.

---

## 1. Decide what one question the judge asks

**Write the question down as a sentence, before writing any code.** If the sentence needs an "and",
you have two judges. `[OURS]`

Two judges from the same suite, measured seven times each against one byte-identical report:

| | Narrow judge | Open judge |
|---|---|---|
| Question | *"does this finding assert this specific problem?"* — one entailment question per answer-key entry | *"how good is this review?"* — open-ended rubric scoring |
| Anchored to | a committed answer key | a prose rubric |
| Model | Haiku-class | Sonnet-class |
| **Spread over 7 reps** | **0.0000** | **0.1429** |

The weaker model was the stable one.

⭐ **Variance is a property of the question, not of the model.** `[OURS]` Structure imposed *around*
the model is what produces determinism: decompose the judgement into narrow entailment checks,
anchor each to a committed expectation, and vote. Ask one open question and the noise stays.

> **Design instruction:** if a judge is noisy, the first move is to **decompose the question**, not
> to upgrade the model.

This is also the cheapest lever available. Decomposition costs you prompt engineering once;
upgrading the model costs you tokens on every run forever, and — as measured above — may buy nothing.

### Prefer evidence to opinion

Before writing a model-backed judge, ask whether a build, a test, a parser, or a bytecode check can
answer the question directly. `[CORPUS]` Tool-augmented judging improves judge performance in many
settings but does not replace an available deterministic check
([Findeis et al., ACL 2025](https://aclanthology.org/2025.acl-long.779/)). In this library that means
a `DeterministicJudge` or an execution judge in an early `CascadedJury` tier, with model-backed
judges reserved for criteria-based questions the tools cannot settle.

The five defects above are worth reading again with this in mind: three of the five were in
*deterministic* judges. Cheap and decisive does not mean correct.

---

## 2. State the denominator

> **A pass over an empty input set is not a pass. It is an abstention wearing a pass.** `[OURS]`

Defects 3 and 4 are the same line of code written twice:

```java
// WRONG — passes when `unresolved` is empty because nothing was ever parsed
checks.add(unresolved.isEmpty()
    ? Check.pass("related_resolve", "All Related references resolve")
    : Check.fail("related_resolve", "Unresolved: " + unresolved));
```

`unresolved.isEmpty()` is true in two completely different worlds: every reference resolved, or
there were no references. The check cannot tell you which, and neither can its message.

```java
// RIGHT — the denominator is in the outcome and in the message
int examined = review.crossReferences().size();
if (examined == 0) {
    checks.add(Check.fail("related_resolve", "no cross-references parsed — nothing was checked"));
    // or, if zero is legitimately expected for this subject, abstain the whole judge
}
else {
    checks.add(unresolved.isEmpty()
        ? Check.pass("related_resolve", unresolved.size() + " of " + examined + " unresolved")
        : Check.fail("related_resolve", unresolved + " of " + examined + " unresolved"));
}
```

**Rules.** `[OURS]`

1. **Every check states the size of the set it examined**, in its message, so `0 of 0 passed` can
   never be misread as `14 of 14 passed`.
2. **Prefer a positive count to an empty-failure-list.** `findingCount >= 1` is a real assertion;
   `missing.isEmpty()` is not. In the suite where defects 3 and 4 were found, one check in the same
   file already did this correctly — the pattern was present the whole time; two of its siblings
   just did not follow it.
3. **Decide, per check, what a zero denominator means**, and encode the decision:
   - the subject legitimately has none → the *judge* should `ABSTAIN` (see §3), not pass;
   - the subject should have had some → `FAIL`, loudly;
   - your parser could not see them → `ERROR`, because you did not evaluate.
4. **Write a denominator audit.** Run every check across your whole archive of past subjects and
   print, per subject, the size of the set behind each check. A zero in a column means that check
   told you nothing about that subject. This is cheap — the version that found defects 3 and 4 runs
   in 0.245 s and spends zero model tokens.

### Beware the parser your subject can outgrow

Defect 3 had a second lesson. The agent under evaluation changed markdown dialect between June and
August — `**Related:**` instead of `- **Related:**` — under the same prompt and the same corpus. The
parser required the leading bullet, so 25 references became invisible and the check passed over
nothing.

**A judge that parses free-form output is coupled to a dialect its subject is free to change.**
`[OURS]` Assert parse yield, not just parse success: a parser returning zero items from a
2,000-line document is a fact worth failing on.

---

## 3. Choose the outcome vocabulary deliberately

A `Judgment` carries four statuses. Most evaluation frameworks carry two. The extra two are the ones
that prevent §0's failure, and they are only useful if you assign them on purpose.

| Status | Means | Use it when |
|---|---|---|
| `PASS` | The judge evaluated and the subject satisfied the criterion | — |
| `FAIL` | The judge evaluated and the subject did not | — |
| `ABSTAIN` | The judge is **not applicable**, or lacks the evidence it needs | No security-sensitive file changed; the task is not a migration; no public API changed |
| `ERROR` | The judge **did not complete** | Model call failed, response unparseable, workspace missing, timeout |

The constructor enforces the semantics: `ABSTAIN` and `ERROR` cannot carry a score, `ERROR` cannot
carry a label, and both require non-blank reasoning. `effectiveScore()` returns empty for both,
because zero is a real assessment rather than the absence of one.

### The distinctions that matter

**`ERROR` is not a low score. It is not a number at all.** `[OURS]` It is silence, and silence is
indistinguishable from agreement unless something counts the judges that were supposed to speak.

**`ABSTAIN` is not a failing vote.** Any suite where not every judge applies to every subject needs
this: an `ApiCompatibilityJudge` on a change that touches no public API has nothing to say, and
saying `FAIL` would be a lie.

**Anything that can throw must be converted to a judgment at the point of invocation.** `[OURS]`
`ErrorPolicy` governs judgments whose status is `ERROR`. It cannot govern an exception. A new
failure mode that is not turned into an `ERROR` judgment is, by construction, invisible to every
policy this library offers. (`SimpleJury.invokeJudge` now does this conversion for you — a
throwing judge becomes an `ERROR` judgment naming the judge and the cause, rather than escaping and
collapsing its cascade tier. That fix is on `main` for 0.16 and is **not** in 0.15.2, so a judge
written against a released version may still be wrapping itself defensively; the wrapper becomes
redundant, not harmful.)

**Log the exception where you catch it.** The `Judgment` carries reasoning, not a `Throwable`, by
design. If you do not log at the catch site the diagnostic is gone.

### The trap: an errored judge and an abstaining judge render identically

This is defect 1, and it is the most expensive one. Under `ErrorPolicy.TREAT_AS_ABSTAIN` — and under
several downstream reporting conventions — a judge that could not run and a judge with nothing to
add produce the same visible output: nothing.

**Countermeasure: print the roster, not just the verdicts.** `[OURS]` The count that actually voted
is already published. Every voting strategy writes `AggregationEvidence` under the reserved
`aggregation` metadata key:

| Key | Meaning |
|---|---|
| `inputCount` | judges submitted |
| `eligibleCount` | judgments actually reduced over |
| `explicitAbstainCount` | judges that abstained on their own |
| `errorCount` | judges that errored |
| `ignoredErrorCount` / `errorsTreatedAsAbstainCount` / `errorsTreatedAsFailCount` | per-policy conversions |
| `passCount` / `failCount` | status-counting strategies |
| `inputWeight` / `eligibleWeight` | weighted strategies |

**Assert on these.** A batch report that prints only a score is not reporting the thing that failed
for ten weeks. A batch report that prints `inputCount=2, eligibleCount=1, errorCount=1` on every item
would have surfaced defect 1 on day one.

Do not add a second count surface that could disagree with this one. `[OURS]`

### "Every judge errored" is not "the jury abstained"

`AggregationPopulation` already distinguishes these in its reasoning text —
`"All 2 judge(s) abstained"` versus `"All 2 judgment(s) abstained because of evaluation errors"`
versus `"No eligible judgments; 2 error(s) ignored"` — and in the evidence counts.

**Do not claim the four-way outcome is unusual. It is not.** `[CORPUS]` A source pass over sixteen
Python evaluation frameworks found richer vocabularies than this library's in at least two:
[Inspect AI](https://inspect.aisi.org.uk/) carries a machine-readable `ScoreReason` that partitions
failure **by attribution** — `grader_failed` / `scoring_failed` for the measuring instrument versus
`refusal` / `invalid_response_format` / `no_response` for the model under test, a partition its own
source comments state deliberately — and `Score.unscored()` is preserved but excluded from metrics
and reducers. [Braintrust's `autoevals`](https://github.com/braintrustdata/autoevals) uses
`score=None` for "skipped" and has **deprecated** its `error` field because errors now propagate to
the caller.

⭐ **What survives is narrower and sharper: nobody publishes the composition.** `[OURS]` Inspect's
reduction carries no count of what it reduced over, and retains a `reason` only when it is identical
across every input — so a mean over three scorers where two returned `grader_failed` is
indistinguishable from a mean over three that actually voted, and the differing reason is dropped.
Weave's `auto_summarize` filters `None` and divides by the survivor count. Ragas uses a
`safe_nanmean`. Same shape three times, in three unrelated frameworks. **The evidence of instrument
failure is erased at aggregation** — which means AJ-17's failure mode is not our peculiarity, it is
the ecosystem default, present in the most carefully engineered framework in the set.

So the claim worth making is not "we have four statuses." It is: **this library publishes a
per-policy account of what its aggregation actually reduced over, and the surveyed frameworks do
not.** `[OURS]`

> ⚠️ **The mirrored conflation.** `[CORPUS]` DeepEval's `is_successful()` sets `success=False` when
> the *metric* errored. Ours makes absence look like success; theirs makes instrument failure look
> like a negative finding about the subject. Same conflation, opposite direction — and the second is
> the one that silently manufactures FAILs you will spend a day explaining. Whichever framework you
> use, find out which way it leans before you trust a batch.

Consume the distinction rather than re-deriving it. And decide explicitly what your gate does with
each:

| Aggregate outcome | Reasonable gate policy |
|---|---|
| All judges abstained, and abstention is expected for this subject | Not a rejection; report as *not exercised* |
| All judges abstained in a tier where abstention is **not** expected | Treat as a defect in the harness — this is what a guardrail tier looks like when it has stopped working |
| All judges errored | **Fail the batch loudly.** No verdict was reached |

⭐ **A judge that returns `ERROR` on every item must fail the batch, not abstain from it.** `[OURS]`

---

## 4. Place the threshold

Two thresholds in the suite from §0: one at `0.0`, one at `0.5`. Neither was derived from anything.

```java
Judgment.scored(recall).passingAt(0.0)     // cannot fail, by construction
Judgment.scored(quality).passingAt(0.5)    // 13 observed scores: 0.81 .. 0.95 — never binds
```

### The trap to name first

> ⚠️ **Setting the bar just under what a known-good run scored is fitting the bar to the data it
> must judge.** `[OURS]`

It is the single most tempting way to choose a threshold and it destroys the instrument. The bar
then encodes "what we have already achieved" and can never tell you that you have regressed below
acceptable, only that you have regressed below yourself.

### Derive the threshold from the rubric's own level semantics

⭐ **The rubric already states where the bar is. Use it.** `[OURS]`

```
0 = missed this dimension entirely, or got it fundamentally wrong
1 = superficial or partially wrong treatment
2 = correct assessment with minor gaps      ← the rubric's own acceptability line;
3 = expert-grade                              it already says "list a concern for every score below 2"
```

If a rubric level is written as a *sentence about acceptability*, the pass mark is the lowest level
whose sentence you are willing to ship. That is a threshold you can defend in a sentence, to a
reader who has never seen your score distribution.

**Practical procedure.** `[OURS]`

1. Write each scale level as a sentence describing the *work*, not a number.
2. Identify the lowest level you would accept in a delivered artifact. That is your per-criterion
   bar. If you cannot identify one, your levels are not written as acceptability statements — rewrite
   them before writing code.
3. Derive the judgment threshold from that bar (see §5 — usually as a conjunction, not a mean).
4. Record *why*, next to the constant. A threshold with no recorded derivation will be treated as
   arbitrary by the next person, and they will be right.
5. **Never revise the bar in response to a score you did not like** without recording the revision as
   a change in what the gate means, at a stated boundary, with both definitions written down. Results
   computed on either side of that boundary are not comparable.

**On observed scores.** They are legitimate input to *one* question — "does this bar ever bind?"
(§7) — and to nothing else. If your threshold has never rejected anything across your whole archive,
that is a fact about the threshold, not a compliment to your agent.

**The antecedent has a name: standard setting.** `[CORPUS]` Educational measurement has spent
fifty years on exactly this problem — deriving a cut score from item descriptors rather than from a
score distribution. The two mature methods are **Angoff** (each judge estimates, per item, the
probability that a *just-barely-qualified* candidate answers it correctly; the cut score is the mean
across judges) and **Bookmark** (judges place a bookmark in a difficulty-ordered booklet at the point
where a borderline candidate's mastery ends, with reported higher validity and tighter judge
agreement than Angoff).

⚠️ **No application of either to LLM-judge cut scores was found.** `[OPEN]` So the five-step
procedure above stays `[OURS]` — but it now has a borrowed antecedent, which is a better position
than either "novel" or "standard practice." If you are placing a bar and want more rigour than the
procedure gives you, Angoff's core move is the transferable one: reason about the *borderline
artifact*, not about the score distribution.

### Watch for thresholds you did not choose

The numeric voting strategies each carry a hardcoded pass mark:

```java
// AverageVotingStrategy, WeightedAverageStrategy, MedianVotingStrategy
private static final double THRESHOLD = 0.5;
```

If your jury uses one of these, **a 0.5 you never chose decides your aggregate's PASS/FAIL**, on a
scale whose meaning is yours. Either accept it deliberately, or read `Verdict.aggregated().score()`
and apply your own gate. `[OURS]`

This is not a defect peculiar to this library. `[CORPUS]` DeepEval ships `threshold=0.5` as the
default on roughly twenty metrics. An unchosen 0.5 is close to an ecosystem-wide default, so the
question "did anyone pick this number?" is worth asking of whatever tool you are holding.

---

## 5. Aggregate *inside* a judge: conjunction, not mean

This is defect 5, and it is the one with the sharpest lesson, because the code computed exactly the
right thing and then threw it away:

```java
boolean pass = score >= SCORE_PASS_THRESHOLD;            // per-criterion pass/fail...
checks.add(pass ? Check.pass(name, msg) : Check.fail(name, msg));   // ...recorded as Checks...
...
return Judgment.scored(sum / max).passingAt(passThreshold);   // ...then discarded for the average
```

```
{3,3,3,3,3,3,0}  →  18/21 = 0.857     passes any mean bar below 0.857,
                                       while one criterion is, in the rubric's own words,
                                       "missed entirely or got it fundamentally wrong"
```

⭐ **A mean lets a failed dimension pay for itself out of its siblings' surplus.** `[OURS]` This is
shape 2 from §0: the zero was never missing — it was fully visible in the judge's own `Check` list,
and the aggregation step spent it.

### The decision rule

| Use a **mean** when | Use a **conjunction** when |
|---|---|
| The criteria are genuinely interchangeable — more of one really does compensate for less of another | Each criterion names an independent property the artifact must have |
| You want a gradient for *ranking*, not a gate | You are deciding acceptability |
| No single criterion's floor is separately meaningful | The rubric states a per-criterion acceptability line |

⭐ **If your rubric says anything of the form "a score below X is a concern", it has already declared
a non-compensatory line, and a mean ignores it.** `[OURS]`

The replacement is `min` over criteria, which is a conjunction stated numerically:

```java
Y = 1  iff  artifacts exist
      AND   structure valid
      AND   min over criteria (score) >= 2
```

### Both rules have names, and you are choosing between them

⭐ `[CORPUS]` **Mean is *compensatory* scoring. `min`-over-criteria is *non-compensatory*, or
*conjunctive*, scoring.** Both are established in educational measurement, along with documented
hybrids for competence decisions. The field's own observation is that *compensatory* scoring is the
typical default in rubrics.

That reframes defect 5 completely, and for the better:

> **`{3,3,3,3,3,3,0} = 0.857` passing is not a bug in a compensatory model. It is what a
> compensatory model is *for*.** The defect was applying a compensatory rule to a rubric that had
> already declared a non-compensatory line — not the arithmetic itself.

So you are not inventing a rule here. You are picking the named one that matches your rubric's own
semantics, and the failure mode is picking it by default rather than by decision.

`[CORPUS]` The surveyed corpus supports keeping the aggregation policy explicit and separate from
the observations — [Inspect AI](https://inspect.aisi.org.uk/multiple-scorers.html) keeps scorer
results separate unless a reducer is chosen; [Braintrust](https://www.braintrust.dev/docs/evaluate/write-scorers)
retains separate scorer dimensions — but **no framework read at source level ships a conjunctive
rule**. ⚠️ [promptfoo](https://www.promptfoo.dev/docs/configuration/expected-outputs/) is the
sharpest evidence: a numeric test threshold **overrides** individual assertion pass/fail. That is
defect 5, shipped as designed behaviour, in a widely used tool. If you are on a framework that
averages, assume it is compensatory until you have read the code.

### Report the binding criterion

⭐ **Alongside the verdict, report *which* criterion sat closest to the bar.** `[OURS]` This is not
decoration — it is the diagnosis, and it points at the lever. A batch where one criterion always
binds tells you exactly where to intervene. A mean tells you nothing of the kind.

### What conjunction does not fix, and why that is correct

`min >= 2` is binary, so two runs that both clear it are both a pass and the gate cannot separate
them. **That is correct.** If `min >= 2` is genuinely the acceptability line, then a cheaper run that
clears it *is* better. Discomfort with "trading down to the floor" is a signal that the floor is in
the wrong place, not that the objective is wrong. `[OURS]`

### Make the `Check`s load-bearing

Nothing in this library derives a verdict from `checks`. A `Judgment` may carry `PASS` alongside
three failing `Check`s and the constructor will not object. Checks are evidence, not arithmetic —
**you** must close the loop:

```java
// Conjunction over the checks you just recorded — the structural judge pattern
boolean allPass = checks.stream().allMatch(Check::passed);
return Judgment.verdict(allPass)
    .reasoning(allPass ? "Structure valid"
        : checks.stream().filter(c -> !c.passed()).count() + " violation(s)")
    .checks(checks)
    .build();
```

**Guard it in a test.** Assert that a judgment carrying a failing check cannot be `PASS`. `[OURS]`
That test is three lines and it is the direct regression guard for defect 5.

---

## 6. Aggregate *across* judges: the jury

### Pick the strategy from the question, not from familiarity

| Strategy | Reduces over | Reach for it when |
|---|---|---|
| `ConsensusStrategy` | statuses | Every applicable judge must agree. Mixed applicable PASS/FAIL yields `ABSTAIN` — a reported disagreement, not a silent negative |
| `MajorityVotingStrategy` | statuses | Judges are noisy samples of one question; ties resolve by `TiePolicy` |
| `AverageVotingStrategy` / `MedianVotingStrategy` | `effectiveScore()` | The judges measure one quantity and you want its central tendency. Median if you expect outliers |
| `WeightedAverageStrategy` | `effectiveScore()` | As above, with judges of unequal authority — read §6.2 first |
| `CascadedJury` | tiers, in order | Cheap decisive checks should reject before an LLM tier incurs cost |

`[CORPUS]` The corpus supports keeping aggregation explicit and separate from the observations:
[Snorkel's `MajorityLabelVoter`](https://snorkel.readthedocs.io/en/master/packages/_autosummary/labeling/snorkel.labeling.model.baselines.MajorityLabelVoter.html)
defaults a tie to abstain — a split vote is indeterminate, not silently negative.

**Consensus reports a collective fact; it is not a gate.** `[OURS]` A downstream all-must-pass policy
may reject or escalate the same mixed jury. Keep the aggregation conclusion and the acceptance policy
in different places, and note that `CascadedJury` tier policies read `Verdict.individual()`, never
the aggregate — changing aggregation semantics does not change gate behavior.

### 6.1 Set the error policy on purpose

`ErrorPolicy` defaults to `PROPAGATE`, and the default is right: a judge that could not evaluate
should not be silently converted into a negative finding it never made.

| Policy | In the population? | Consequence |
|---|---|---|
| `PROPAGATE` (default) | aggregation short-circuits | One dead judge errors the whole item |
| `TREAT_AS_FAIL` | yes, as a `FAIL` | A dead judge rejects the subject |
| `TREAT_AS_ABSTAIN` | no — a non-vote | A dead judge is invisible in the score, visible only in the evidence |
| `IGNORE` | no — removed, weight included | Same, accounted differently |

⚠️ **`PROPAGATE` has a cost that is easy to discover the hard way.** `[OURS]` If two judges answer
*independent* questions and neither is the other's precondition, propagation discards a perfectly
good judgment from judge A because judge B could not parse its own model response. Observed exactly:
a `goldRecall` that had *passed* at 0.6 was thrown away because a sibling judge failed to parse. If
your tier holds independent questions, `PROPAGATE` is the wrong default *for that tier* — and the
right fix is usually to put independent questions in separate juries rather than to weaken the policy.

### 6.2 Give the judge an identity — and know where identity is missing

A `Judgment` carries `status`, `score`, `label`, `reasoning`, `checks`, `metadata` — and **no judge
identity**. This has three consequences you must design around. `[OURS]`

**Always name your judges.** `SimpleJury` resolves a name via `Judges.tryMetadata(judge)` and falls
back to `"Judge#" + (index + 1)` for anonymous lambdas. A named judge appears in
`Verdict.individualByName()`; an anonymous one appears as a position that changes when you insert a
judge above it.

```java
tier.judge(Judges.named(new DddReviewQualityJudge(), "dddQuality"));   // do this
tier.judge(ctx -> { ... });                                            // becomes "Judge#3"
```

**Know what identity does *not* reach.** `VotingStrategy.aggregate(List<Judgment>, Map<String,Double>)`
receives an ordered bag of anonymous verdicts. Position is the only handle it has, so
`WeightedAverageStrategy` keys weights by position (`"0"`, `"1"`, …) — documented behaviour, and the
best the method can do with the contract it was given. **The defect is in the contract, not the
method.**

In practice:

- Building a jury through `SimpleJury.builder().judge(judge, weight)` binds judge and weight at one
  call site, so moving that line moves its weight with it. The builder path is safer than it looks.
- The hazard is a weight map assembled anywhere else, and it is real in the serialized result:
  **`Verdict.weights()` is keyed by position while `Verdict.individualByName()` is keyed by name.**
  A consumer reading a stored verdict cannot join them, so it cannot say which judge carried which
  weight. That is the concrete cost today.
- **Persist the judge name yourself** into whatever run record you keep. Do not rely on ordinal
  position surviving a configuration change.

**Do not let identity loss be silent.** ⭐ A jury must never return a verdict computed from fewer
judges than it lists. `[OURS]` Two mechanisms previously allowed exactly that — a prompt template
resolved lazily through the thread-context classloader vanished on the pool worker that rendered it,
and a throwing judge escaped `SimpleJury` and collapsed its whole cascade tier. Both are fixed in the
library; the *discipline* they imply is yours: assert `eligibleCount == inputCount` wherever you
expect a full roster.

### 6.3 The combinators are boolean-era; read them before using them

`Judges.and`, `Judges.or`, `Judges.allOf`, and `Judges.anyOf` branch on `Judgment.pass()`, which is
`status == PASS`. They predate the four-status model, and their treatment of the other two statuses
is a hazard rather than a policy:

- `allOf` / `and` — an `ABSTAIN` or `ERROR` is "not pass", so it short-circuits and is returned as
  the composite result. The remaining judges never run.
- `anyOf` — if every judge abstains, none passes, and it returns `Judgment.fail("All checks failed")`.
  ⚠️ **That converts universal abstention into a `FAIL`** — precisely the conversion the rest of the
  library refuses to make.

There is no test coverage for `ABSTAIN` or `ERROR` through any of the four combinators. `[OURS]`
Until that changes, **compose abstaining judges with a jury and an explicit `ErrorPolicy`, not with
these combinators** — a jury's aggregation is status-aware and publishes its evidence.

---

## 7. Prove the judge can fail

⭐ **A quality plugin configured not to fail is not a gate.** `[OURS]` We learned that sentence about
a Javadoc build — a release profile carried `failOnError=false` and therefore shipped JARs missing
whatever failed to generate. It generalizes exactly to judges, and we did not apply it to judges
until five defects made us.

**Verify a newly enabled gate by watching it break on the defect it is meant to catch.** `[OURS]`
Everything in this section is a version of that sentence.

### 7.1 The degeneracy suite

Run these before trusting any number a judge produced. All are cheap; most cost zero model tokens.

**a. Can this judge return `FAIL` at all?**
Grep your own source for the tell-tales:

```
passingAt(0.0)         // cannot fail
passingAt(1.0)         // cannot pass, unless a perfect score is genuinely required
allMatch on an empty stream, isEmpty() on a failure list   // §2
```

`passingAt(0.0)` is not automatically a bug — a deliberately scoring-only instrument is a legitimate
design (§8) — but it must be *stated as such at the call site*, or the next reader will believe it
gates.

`[CORPUS]` The hazard is known, if not the detection. promptfoo hit it independently and guards it
in two places: it tests `typeof threshold === 'number'` rather than truthiness, specifically so that
a threshold of `0` is *honored* — noting in the code that `score >= 0` is always true — and it
separately guards a null or `NaN` threshold from "silently force-passing every assertion." So a
never-failing gate is a recognised ecosystem hazard, and threshold-zero is treated as a legitimate
deliberate mode. Our defect was not that the threshold was zero. It was that nobody had decided it.

⚠️ **No framework read at source level ships degeneracy detection** — no metamorphic testing, no
negative controls, no known-bad seeding as a built-in. `[OPEN]` The suite below is ours, and it is
cheap enough that its absence from the literature is more likely a gap in our reading than a gap in
the field. Treat it as "not found", not as "nobody has done this."

**b. Does this threshold ever bind?**
Replay every archived subject through the judge and count rejections. A threshold that has rejected
0 of *N* is not a gate; it is a formality. Record the count, not an impression.

```
13 observed quality scores    min 0.8095   max 0.9524
threshold                     0.5000       = 10/21 rubric points
runs that would have failed   0 of 13
```

**c. Negative controls.**
⭐ **A proof needs a negative control, or it degenerates into a naming check.** `[OURS]` Feed the
judge something you *know* is bad — a truncated artifact, a report with a criterion deliberately
gutted, an empty workspace — and assert `FAIL`, not merely "a lower score". `Judges.alwaysFail` and
`Judges.alwaysPass` are useful for the same purpose one level up, to prove your *jury* wiring
transmits a rejection.

**d. Watched red.**
Write the guard first and observe it failing against the unmodified code, then fix. A guard that has
never been seen red proves nothing about the defect it claims to cover. When a multi-module reactor
halts before your guard runs, `-Dmaven.test.failure.ignore=true` is the technique for observing the
whole red phase — that is a reusable method, not a weakened gate. `[OURS]`

**e. Denominator audit.** §2, run over your whole archive.

**f. Dated-asset audit.**
⭐ **A pinned model id is a dated asset.** `[OURS]` Defect 1 was a model id that was valid when
written and retired ten weeks before anyone noticed. The fix is not "check your model ids"; it is
**convert the silent runtime error into a loud build error**:

```java
/** Vendor-retired model ids. Add on announcement, not on discovery. */
private static final Set<String> RETIRED = Set.of("<retired-id>", ...);
```

Scan *source* for quoted model ids rather than asserting on one constant — the next dead pin will be
somewhere you are not currently looking, including in a judge nobody has written yet.

**g. Checks-versus-status guard.** §5: a judgment carrying a failing check must not be `PASS`.

**h. Roster guard.** §3: assert `eligibleCount == inputCount` where a full roster is expected.

### 7.2 Test every outcome path

A custom judge's test should cover its passing, failing, abstaining, and error paths where those
exist. Assert **status first**, then score, label, checks, and metadata. `[OURS]` Asserting a score
before checking status is how a `ClassCastException` on an `ERROR` return masked a real failure for
us once.

And: **tests can encode a regression.** `[OURS]` A regressed assertion labelled `PRESERVED` is how a
regression acquires the appearance of a contract. The display name is not evidence.

---

## 8. Ground truth: what gates, and what stays held out

### The tension

An answer key that gates makes the agent optimise for the key — Goodhart against your own test set —
and it stops being a valid measure of whether the work generalises. An answer key that never gates
leaves nothing gating on *correctness*, which is where the suite in §0 ended up: `Y = 1` meant
"the run produced a well-formed report", and the word *verified* in the batch metric did no work.

⭐ **The bug is not that the held-out key does not gate. It is that nothing else did either.** `[OURS]`

### The resolution we recommend

**Two instruments, two jobs.** `[OURS]`

| Instrument | Question | Gates? |
|---|---|---|
| Rubric conjunction (§5) | *Is this artifact acceptable on every dimension it claims to cover?* | **Yes.** Derived from the rubric's own level semantics, so it does not need the answer key |
| Held-out answer key | *Did it find the **right** problems?* | **No.** Reported alongside the verdict, never inside it |

This gets you a correctness-shaped gate without spending the key, because the gate's bar comes from
the rubric rather than from the key's contents. Report both; put only the first in the denominator of
any aggregate metric.

**Mark scoring-only instruments at the call site**, so `passingAt(0.0)` reads as a decision rather
than an oversight:

```java
return Judgment.scored(recall)
    .passingAt(0.0)   // scoring-only: held-out register, the instrument never rejects
```

### If you must split a key

⚠️ `[OPEN]` **This is the one question in this manual that is genuinely unresolved, and we are
saying so rather than filling it.** A source pass over sixteen Python evaluation frameworks found
nothing on tiered reference sets or minimum split sizes; the benchmark-construction and
contamination literature, where an answer would most plausibly live, has not been swept. Treat the
absence as *not searched*, not as *not there*. What we can say from our own experience:

- **Execution-validated ground truth is unusually strong and unusually scarce.** A key whose entries
  were each turned into shipped, running code is far better evidence than annotation agreement — and
  it cannot be manufactured on demand for entry #6. Treat its size as fixed and design around it.
- **A 5-entry key has a resolution of 1/5 = 0.2.** Splitting it 3/2 leaves a gating instrument whose
  every possible score is 0, ⅓, ⅔, or 1. Consider whether such an instrument can express the
  distinction you need before splitting (§9).
- **Contamination is a property of exposure, not of intent.** If the key's entries have ever appeared
  in a prompt, a knowledge base, or a system message the agent reads, the split is already spent.

### Calibrate the key's matcher, and let it disagree with you

`[OURS]` A recall judge is itself a judge, and it needs the same treatment. Ours earned its trust by
*disagreeing* with a hand-label — 3/5 against a human 5/5 — and adjudicating the disagreement by
required quotes found the **matcher right on one entry and the prompt wrong on another**. Both
outcomes were improvements. Practical rules that came out of that:

- **Require quotes.** A matcher that must cite the span it matched can be adjudicated after the fact;
  one that returns a boolean cannot.
- **Substring is not assertion.** Keyword matching was rejected before it was built, on exactly that
  ground.
- **Credit what the artifact *surfaces*, not what it mentions.** A claim buried inside another
  finding is not the same as a finding.
- **Voting fixes noise, not ambiguity.** Majority-of-3 removed run-to-run jitter and left a genuinely
  ambiguous entry still flipping. Document the borderline; do not vote harder at it.

---

## 9. Choose the scale's granularity

We measured a judge at `sd = 0.0000` across 7 repetitions and called it stable. The measurement was
real, but the number needs two qualifications before anyone leans on it.

**Zero variance on a coarse grid is not the same as stability.** `[OURS]` Flooring a demonstrably
noisy judge's outputs into the same few bands also produces `sd = 0.0000`; the values are sitting
inside a bin. On a 5-entry answer key the *resolution* is 1/5 = 0.2, which bounds the instrument far
more coarsely than its measured variance does. **Grain, not noise, was the binding constraint** —
and grain is a design choice you made, not a property you measured.

**Ceiling-bounded zero variance is weaker than mid-scale zero variance.** `[OURS]` A judge scoring
1.0 seven times out of seven cannot vary upward. A score pinned at a scale endpoint is a
floor/ceiling effect, and reporting its `sd` as evidence of stability overstates the claim.

### Practical rules

1. **State the resolution next to the variance, always.** `spread = 0.0000, resolution = 0.2` is an
   honest pair; either number alone is misleading. `[OURS]`
2. **Choose the scale from the distinctions you must make**, then check that the judge's run-to-run
   spread is smaller than one step. If the spread exceeds a step, the extra levels are decoration.
3. **A comparison band must be at least as wide as the judge's own measured spread**, or your
   ordering separates arms the instrument cannot distinguish. `[OURS]` For us: a measured spread of
   0.1429 means **two arms differing by less than 0.15 are not distinguishable**, and any ranking
   that separates them is reporting noise. Round the band up; do not round to the measurement.
4. **Measure the spread near the decision boundary, not near the ceiling.** A spread measured where
   the judge scores 0.86 says little about its behaviour at a pass mark of 0.50. A subject that
   *should* score near the threshold is the measurement that matters, and it is the one most suites
   are missing. `[OURS]`
5. **Coarse scales bound your thresholds too.** On a 0–3 per-criterion scale the only available bars
   are `>= 1`, `>= 2`, `>= 3`. There is nothing between "minor gaps" and "expert-grade" — accept that,
   or change the scale before you need the intermediate bar.
6. **Re-measure when the configuration changes.** Variance numbers describe the model, prompt, vote
   count, and tool configuration that produced them. Moving a judge from one model to another
   invalidates its measured spread; do not relabel the old number.

### The measurement literature got here first

⭐ `[CORPUS]` **Coarsening a continuous measurement destroys variance, and the cost is quantified.**
Cohen, *The Cost of Dichotomization*, **Applied Psychological Measurement 7:249–253 (1983)**:
dichotomizing one variable at its mean cuts the variance accounted for to `.647r²`; dichotomizing
both cuts it to `.405r²` — a power loss equivalent to discarding 38% and 60% of your cases
respectively. Extended by MacCallum, Zhang, Preacher & Rucker (2002). This is old, named, and
measured, and it applies directly to a rubric that floors an open judgement into four bands.

`[CORPUS]` **"Ceiling effect" is standard measurement vocabulary** — no coining needed. It is
exactly the right term for an `sd = 0.0000` observed at a score of 1.0.

⚠️ `[OURS]` **What the literature does *not* say is the inversion.** It warns that coarsening loses
*information*. It does not warn that coarsening manufactures a false **stability signal** in
evaluator QA — which is the failure we actually had. So rule 1 above stays ours: report variance next
to the scale's resolution *and* its ceiling, because the number alone will be read as stability by
someone who did not build the instrument. Stated as "not found," never as "nobody has written this."

> ⚠️ **A live threat to any zero-variance claim you make.** `[CORPUS]` There is an active literature
> on LLM judge self-inconsistency — *Rating Roulette* (arXiv 2510.27106), which names it intra-rater
> reliability; temperature effects (arXiv 2603.28304); *Coin Flip Judge* (arXiv 2606.13685). The
> field's working consensus is that LLM judges are self-**in**consistent. A zero-variance result of
> yours therefore needs *more* explanation than a noisy one, not less. Ours has one — the question
> was decomposed into narrow entailment checks and voted (§1), and the residual is bounded by grain
> and ceiling — and that explanation is the claim, not the `sd`.

Everything not cited above is ours, measured on one report, one codebase, and one register, with
`n = 7`.

---

## 10. Checklist

Before a judge is trusted:

**The question**
- [ ] The question is one sentence, with no "and"
- [ ] A deterministic check cannot answer it more cheaply
- [ ] If the judge is noisy, the question has been decomposed before the model was upgraded

**The evidence**
- [ ] Every check states the size of the set it examined
- [ ] Every check has a defined behaviour at a zero denominator
- [ ] Parse yield is asserted, not just parse success
- [ ] A denominator audit runs over the whole archive

**The outcomes**
- [ ] `ABSTAIN` is used for inapplicable, `ERROR` for could-not-complete
- [ ] Everything that can throw is converted to an `ERROR` judgment at the point of invocation
- [ ] The originating exception is logged where it is caught
- [ ] The batch report prints `inputCount` / `eligibleCount` / `errorCount`, not only a score
- [ ] "All errored" fails the batch; it does not abstain from it

**The threshold**
- [ ] Derived from the rubric's level semantics, not from observed scores
- [ ] The derivation is recorded next to the constant
- [ ] Replay says it has rejected at least one archived subject
- [ ] Any hardcoded `0.5` — in a numeric voting strategy here, or in a framework default elsewhere — is deliberate

**The aggregation**
- [ ] Compensatory (mean) versus non-compensatory (`min`) was decided against §5's table, not by default
- [ ] A judgment carrying a failing check cannot be `PASS`, and a test says so
- [ ] The binding criterion is reported alongside the verdict
- [ ] Every judge is `Judges.named(...)`
- [ ] `ErrorPolicy` is chosen per tier, and independent questions are not in a `PROPAGATE` tier
- [ ] `Judges.allOf`/`anyOf` are not used where judges can abstain

**The proof**
- [ ] Negative control: a known-bad subject produces `FAIL`
- [ ] Every guard has been observed red
- [ ] No pinned model id is retired, enforced by a source scan
- [ ] Passing, failing, abstaining, and error paths are all tested, status asserted first

**The measurement**
- [ ] Run-to-run spread is measured on a fixed subject, near the decision boundary
- [ ] Resolution is reported next to variance
- [ ] Comparison bands are at least as wide as the measured spread
- [ ] Variance is re-measured after any model or prompt change

---

## 11. What is ours, and what is not

Stated plainly, because "this part is ours" is a stronger claim than a stretched citation.

**Framework-level design principles** — evidence before opinion, judging requirements rather than
resemblance, staged escalation, composing independent perspectives, preserving uncertainty — are
grounded in published work and are documented with their sources in
[Research Foundations](https://lab.pollack.ai/docs/agent-judge/research-foundations). This manual
does not restate them.

**Ours, from observed production failures, not from the literature:**

- The two-shape failure taxonomy in §0 — *absence renders as success* and *a wrong number from
  complete data* — as one shared symptom with two unrelated fixes, and the countermeasures in §2, §3,
  §5, §6.2 and §7 mapped onto it
- The denominator rule, and the denominator audit as a standing artifact
- "A pass over an empty input set is an abstention wearing a pass"
- "An `ERROR` is not a low score; it is not a number at all"
- "All errored" must fail the batch rather than abstain from it
- Deriving a threshold from rubric level semantics, and naming bar-fitting as the trap
- Conjunction over mean when the rubric states a per-criterion acceptability line, and reporting the
  binding criterion as the diagnosis
- Two instruments, two jobs — a rubric conjunction gates, an execution-validated key stays held out
- "Variance is a property of the question, not the model" — decompose before upgrading
- Resolution reported next to variance; ceiling-bounded zero variance is weaker evidence
- Comparison bands at least as wide as measured spread
- A pinned model id is a dated asset, enforced by a source scan
- "A quality plugin configured not to fail is not a gate", generalized to judges
- "A proof needs a negative control or it degenerates into a naming check"
- Watched-red discipline for judge guards

**Named by prior art, and better for it — recommendations that are ours in application but not in
invention:**

| Recommendation | The name it already had |
|---|---|
| Conjunction over mean when the rubric declares a per-criterion line (§5) | **Compensatory vs. non-compensatory (conjunctive) scoring**, educational measurement. `{3,3,3,3,3,3,0}` passing is what a compensatory model is *for*; the defect was applying it to a rubric that had declared otherwise |
| Deriving a bar from rubric level semantics (§4) | **Standard setting** — Angoff and Bookmark. No application to LLM-judge cut scores was found, so the procedure stays ours with a borrowed antecedent |
| Reporting resolution beside variance (§9) | **Cohen, *The Cost of Dichotomization*, Applied Psychological Measurement 7:249–253 (1983)**; MacCallum, Zhang, Preacher & Rucker (2002). Coarsening's cost is quantified: `.647r²` and `.405r²` |
| `sd = 0.0000` at a scale endpoint is not stability (§9) | **Ceiling effect** — standard measurement vocabulary |

**Supported by our surveyed corpus**, with sources linked inline: that aggregation policy must be
explicit and separate from the observations (Inspect AI, Braintrust); that a split vote is
indeterminate rather than negative (Snorkel); that rejection belongs to the gate or test policy
rather than to the aggregation step (DeepEval, promptfoo). Also, as cautionary evidence rather than
as models to copy: that no framework read at source level ships a conjunctive rule or degeneracy
detection; that promptfoo's numeric test threshold overrides individual assertion pass/fail; that
DeepEval defaults `threshold=0.5` on roughly twenty metrics; that promptfoo independently guards
threshold-zero and NaN-threshold force-passing; and that DeepEval's `is_successful()` reports a
metric error as `success=False`.

**Explicitly refuted — claims we might have made and should not:**

- *"A four-way outcome vocabulary is unusual."* It is not. Inspect AI's `ScoreReason` partitions
  failure by attribution (instrument vs. subject) more finely than this library does, and Braintrust's
  `autoevals` has deprecated its error field in favour of propagation. What survives is the narrower
  claim: **the surveyed frameworks do not publish what their aggregation reduced over.**
- *"Reordering judges silently re-targets every weight."* Too strong for the `SimpleJury` builder
  path, which binds judge and weight at one call site. The real, present-day cost is §6.2's two
  unjoinable identity spaces.

**Open — searched and not found, or not searched. Stated as such:**

- Any application of standard-setting methods to LLM-judge cut scores (§4) — *searched, not found*
- Established practice for detecting a judge that cannot fail or a threshold that never binds (§7) —
  *searched, not found in sixteen frameworks; the suite is cheap enough that we suspect our reading
  more than the field*
- Minimum viable size for a gating versus held-out split of a small, execution-validated key (§8) —
  ⚠️ ***not adequately searched.*** The benchmark-construction and contamination literature was not
  swept. This is the one live decision this manual does not help you make
- Whether coarsening manufacturing a false *stability* signal in evaluator QA is written down
  anywhere (§9) — *searched, not found; the information-loss half is well covered, the inversion is
  not*

### On the depth of the survey

`[CORPUS]` claims here rest on a source pass over sixteen Python evaluation frameworks, of which
**six were read at mechanism level and ten were searched only**. Absence of a framework from a claim
means it was not checked, not that it lacks the behaviour. LangSmith's hosted evaluator service and
the Braintrust platform proper were not readable as source and any claim about them is doc-derived.
Phoenix's error path was not verified and is deliberately not characterised here.

---

## Related

- [Design philosophy](https://lab.pollack.ai/docs/agent-judge/design-philosophy) — what the types mean
- [Writing custom judges](https://lab.pollack.ai/docs/agent-judge/custom-judge) — the extension points
- [Jury system](https://lab.pollack.ai/docs/agent-judge/jury-system) — strategies and cascades
- [Research foundations](https://lab.pollack.ai/docs/agent-judge/research-foundations) — the five principles and their sources
- [Agent Judge Tutorial](https://github.com/markpollack/agent-judge-tutorial) — compiled, runnable examples
