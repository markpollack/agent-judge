# Agent Judge 0.17.0

Teaches the result format to say **"this criterion does not apply here"** without that being
mistaken for "I could not decide", to **name the cause of every instrument failure in a countable
way**, to **stop charging its own failures to the subject**, and to record enough structure that a
stored composite result can be **read correctly on its own**.

This is one coherent change to the result format. It is a breaking change for every Java consumer
and for every reader of stored results.

## ⚠️ A requirements judge with no requirements no longer passes

`EarsJudge.create` and `Rfc2119Judge.create` reject an empty roster, and the rollup refuses one
independently.

A roster is a denominator, and a conjunctive rollup over an empty one is vacuously true: nothing
failed, nothing was unestablished, so the implementation "satisfies the specification". Released
0.16.0 returns `PASS` for that, and the result is indistinguishable in every stored field from a
specification that was genuinely met.

**A stored `PASS` with a `criteriaTotal` or `constraintsTotal` of `0` identifies a run affected
before this fix.** Parsing is unchanged: a document that matches nothing still yields an empty list,
because that is a fact about the document. The judge is what refuses.

## `NOT_APPLICABLE` is its own status

Five statuses now, and the difference between the last three is what a denominator does with them:

| Status | The question | A denominator |
|---|---|---|
| `PASS` | asked, answered yes | counted, in the numerator |
| `FAIL` | asked, answered no | counted, against the subject |
| `ABSTAIN` | applied, undecided | counted; no vote cast |
| `NOT_APPLICABLE` | should not have been asked | **excluded**, and counted separately |
| `ERROR` | never reached | excluded from the subject denominator |

`ABSTAIN` no longer means "not applicable" anywhere in the documentation, and the "not applicable"
wording has been removed from every strategy Javadoc that used it to justify excluding abstentions.
Missing evidence is still `ABSTAIN`.

`fromWire` stays exact and throws on an unknown name, and the compile break in exhaustive switches
over `JudgmentStatus` is intended.

### Exclusion must be declared in advance

Exclusion is the one outcome that removes a judge from its own denominator, which makes it the one
outcome an instrument could use to dodge a question it does not like the look of. So:

- `JudgeMetadata` gains `notApplicableWhen`: the condition under which this judge may exclude.
- `Judges.notApplicableCapability(Judge)` is the one lookup, and it walks wrapper chains outward in,
  so a deduplicating rename cannot strip a capability off the judge underneath.
- A `SimpleJury` refuses a capable seat under a strategy that refuses exclusions, and `Juries.meta`
  refuses a possibly-excluding member, both at **construction**.
- At vote time, an exclusion from an undeclared seat becomes `ERROR undeclared_not_applicable`, a
  judge-origin error the configured `ErrorPolicy` governs.
- `Jury.aggregateMayBeNotApplicable()` is the conservative bound a parent checks; it defaults to
  `false`, so an opaque jury makes no pre-spend guarantee and is checked at runtime.

### `NotApplicablePolicy`

`REFUSE` (the default), `EXCLUDE`, `TREAT_AS_FAIL`. Every built-in strategy takes one, and
`VotingStrategy.notApplicablePolicy()` declares it so composition can be validated before anything
runs.

### Conditional criteria in the requirements judges

`EarsCriterion` and `Rfc2119Constraint` gain an optional applicability clause. The prompt offers
`NOT_APPLICABLE` only for criteria that carry one, naming them; an exclusion of an unconditional
criterion, or one with no reason, is a protocol error rather than a finding, and is never counted as
an authorized exclusion. An excluded criterion is not a `Check`. Both judges declare the capability,
naming the ids.

## Every ERROR names its cause

`Judgment` gains `reasonCode`, a closed vocabulary in two families:

- **instrument** codes — `judge_failed`, `judge_metadata_unreadable`, `judge_reported`,
  `undeclared_not_applicable`, `errors_propagated`, `not_applicable_refused`, `aggregation_failed`,
  `stage_failed`, `no_tier_decided` — **required** on every `ERROR`;
- the **subject** code `subject_empty` — optional on a `FAIL`. A `FAIL` with no code is an uncoded
  rejection, by deliberate choice.

`errors_propagated` must carry its origin: a non-empty `errorCodeCounts` block naming the terminal
causes it propagated, flattened through nested wrappers. `Judgment.propagatedError` builds it
atomically, and construction refuses the code without it.

### Machinery failure never supplies rejection evidence

An error from a configured judge is the error policy's business. An error from the library's own
composition or reduction is not, and is **never** converted into a failing contribution — *not even
under* `TREAT_AS_FAIL`, which propagates instead. Charging a broken reduction to the subject would
produce a rejection indistinguishable from a real one in every stored field.

## Containment, and one cascade rule

- A strategy that throws, returns null, or returns an aggregate it was not entitled to produce now
  yields an `ERROR aggregation_failed` verdict marked undecided, with every judge's own result
  intact. Previously it discarded the whole jury and, inside a cascade, the enclosing tier.
  `Error` is not caught.
- A meta-jury member that throws, returns an undecided verdict, or excludes itself without declaring
  it may, is a **stage failure**: excluded from strategy input, its actual verdict kept on the
  attempt, the aggregate `ERROR stage_failed`.
- A cascade tier that did not produce a usable determination but had already established a genuine
  individual `FAIL` **stops** — `REJECT_ON_ANY_FAIL` only. `ACCEPT_ON_ALL_PASS` escalates past it,
  because a broken reduction cannot demonstrate that a subject is fine.
- When it stops that way, no `FAIL` and no score is manufactured. The rejection is carried by the
  *decision*; the root aggregate stays a machinery error.

## New verdict structure

| Surface | Change |
|---|---|
| `Verdict` | `+ seats`, `+ decision` — seven components |
| `CompositeAttempt` | `+ disposition`, `+ dispositionReason` |
| `AggregationEvidence` | `+ notApplicablePolicy`, `notApplicableCount`, `notApplicableTreatedAsFailCount`, `errorCodeCounts` |
| `SimpleJury.Builder` | `+ requireDeclaredNames()` (opt-in) |
| Description | `notApplicableWhen`, `aggregateMayBeNotApplicable`, strategy `notApplicablePolicy`; `descriptionVersion` **2** |

`Seat` records where each judgment sat and under what key, so the ordered list and the keyed map can
be joined without guessing; only a `DECLARED` key is an identity. `Decision` says what produced the
aggregate — this jury's own reduction, a named direct tier, or nothing at all — so a copied outcome
is no longer indistinguishable from a computed one.

`requireDeclaredNames()` is opt-in and worth turning on wherever a verdict is stored: it rejects
positional seats, duplicate declared names, and the collision that makes it necessary — a judge
declaring the name `Judge#2`, which is exactly the key an unnamed second seat takes.

## Reading stored results

New construction is strict, and reading old data is a separate decision. The live types refuse an
incomplete result by name — no decision, no seats, no status, an errored judgment with no code, an
attempt with no disposition, a propagated error whose origin is missing or unrecognised. A reader
lenient enough to load a 0.14 document is lenient enough to accept a 0.17 result that lost a
required fact in transit, and it would accept it silently.

The 0.14 conformance fixtures are byte-identical and are now read only through private historical
shapes.

## Migrating

`agent-experiment`'s `RecordedJudgmentStatus` must learn `not_applicable` **before** any run that can
emit it. Python readers must raise on an unknown status rather than defaulting it. Counts are stored
and rates are derived, so a reader that persisted a pass rate should recompute it.

A legacy `ABSTAIN` carrying the label `not_applicable` is **never** reinterpreted automatically. A
deliberate re-curation records that it reclassified.

The full 0.17 migration guide lives on the documentation site.
