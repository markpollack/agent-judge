# Agent Judge 0.16.0

Adds **requirements judges** that run a written specification back against the implementation,
**two non-compensatory aggregation strategies**, and stops the file judges passing on things they
never examined.

## ⚠️ Behaviour change — the file judges no longer fail open

`FileExistsJudge` and `FileContentJudge` previously returned `PASS` for inputs they had not
actually examined. Each of these is now refused:

| Input | Before | 0.16.0 |
|---|---|---|
| empty path | could pass | refused |
| `.` | could pass | refused |
| a directory | could pass | refused |
| absolute path outside the workspace | could pass | refused |
| parent traversal (`../`) | could pass | refused |

**If you have suites quietly passing on a mistyped or empty path, they will start failing. That is
the fix working.**

This is the failure mode judges are most prone to, and it is worth stating plainly: a JUnit test
*has* an oracle, but a judge *is* the oracle. Nothing checks the checker. A test that cannot run
reports a failure; **a judge that examines nothing still returns a status**, and `PASS` on an
unexamined thing is indistinguishable from `PASS` on a verified one.

## Requirements judges — `agent-judge-ai-core`

New. These take a document somebody wrote *before the code existed* — acceptance criteria,
architectural constraints — and answer whether the implementation satisfies it.

| Type | Purpose |
|---|---|
| `EarsCriterion` | One acceptance criterion, parsed from the specification |
| `EarsJudge` | Answers every acceptance criterion |
| `Rfc2119Constraint` | One architectural constraint, with its RFC 2119 keyword |
| `Rfc2119Judge` | Answers every architectural constraint |
| `Observation` | Non-binding evidence noticed while establishing a judgment |

**The document supplies the roster, and that changes the aggregation.** Elsewhere a jury samples a
population and an `ABSTAIN` is dropped as not applicable. Here the roster is fixed: the document
says the requirement applies, so *"could not be established"* is not *"does not apply."*

```
any ERROR        -> ERROR
else any FAIL    -> FAIL
else any ABSTAIN -> ABSTAIN
else             -> PASS
```

⚠️ **Fifty-one criteria established and one unsettled is `ABSTAIN`, not `PASS`.** The specification
has not been shown to hold — a different statement from having been shown to fail, and both
different from success. Know this before composing these with anything else: a strategy that drops
abstentions will silently convert *unverified* into *fine*.

Both judges also now report what they actually did at `INFO`, so a run is auditable without a
debugger.

## Two non-compensatory strategies — `agent-judge-core`

`AverageVotingStrategy` is **compensatory**: a high score on one criterion offsets a low score on
another. Right when criteria trade off — a slower solution that is markedly clearer may be better.

Two additions for when they do not trade off:

- **`ConjunctiveStrategy`** — reduces `effectiveScore()` with `min`. A single low assessment decides
  the aggregate and nothing lifts it. Correctness is not offset by elegance.
- **`AllMustPassStrategy`** — the same conjunction over *outcomes*. Every applicable judgment must
  be `PASS`; a mixed jury is a rejection. Use it as a gate, where the question is admissibility
  rather than quality.

`AggregationEvidence` records how a strategy reached its result.

**Averaging a jury whose criteria are independently necessary is the most common way a rubric
reports a healthy number for unusable work.** If any single criterion failing should sink the
result, an average will not say so — it reports the mean and looks reasonable doing it.

## `Judges` combinators

The Boolean boundary of the `Judges` combinators is pinned rather than widened, so composing them
no longer quietly changes how non-Boolean statuses are treated.

## Documentation

A practitioner's manual for constructing judges, covering the decisions a default otherwise makes
for you: the question, the denominator, how the pass mark is derived, compensatory versus
conjunctive aggregation, and what gets persisted.

One rule from it worth repeating here: **aggregate at read time, never at write time.** Parts cannot
be recovered from an aggregate — once you store the mean, the individual judgments are gone.

## Dependencies

`agent-client` moves **0.29.3 → 0.30.0**, the current GA. No other dependency changes.
