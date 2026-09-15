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

### A judge whose metadata cannot be read

A `JudgeWithMetadata` whose `metadata()` returned `null` or threw used to escape `SimpleJury.vote()`:
the seat's name was read outside the failure handling, so every other judge's result was lost and an
enclosing cascade recorded the tier as `JURY_EXECUTION_FAILED`.

The jury now reads every seat's key once, on the caller's thread and before any judge runs. Such a
seat becomes `ERROR judge_metadata_unreadable`, keyed `Judge#N`, naming its position and the failure,
for the `ErrorPolicy` to resolve — and the judge does not run, so it cannot exercise a capability the
jury was never built with. `describe()` refuses the seat loudly and by name, including a `NamedJudge`
whose wrapped judge's metadata cannot be read, and `Juries.fromJudges` fails at construction naming
the position.

## Configuration is refused at construction, not at the first vote

Cheap configuration mistakes are now build-time errors. Each of these used to fail later, after
judges had already run, or not at all:

| Was | Now |
|---|---|
| `null` `ErrorPolicy` — every aggregation threw `NullPointerException` out of `vote()`, even when every judge passed | `IllegalArgumentException("errorPolicy must not be null")` from all ten strategy constructors |
| ⚠️ `null` `TiePolicy` — **failed only at the first tie**, so a jury could run for months and then break on one input | `MajorityVotingStrategy` refuses it at construction |
| `null` `NotApplicablePolicy` | refused at construction |
| A `NaN` or infinite judge weight — `NaN < 0` is false, so the sign check let both through | `SimpleJury.Builder` rejects any non-finite weight |

An invalid threshold is still reported first. The no-argument constructors already passed
`PROPAGATE` (and `TiePolicy.FAIL`) and are unchanged.

**This is a behaviour change, not only a message change:** a jury that was constructed with a `null`
`TiePolicy` and never tied used to build and run. It now fails to build.

### Weighted average survives an overflowing weight total

`WeightedAverageStrategy` threw when finite weights summed past `Double.MAX_VALUE`: the total became
`Infinity`, the score `NaN`, and the infinite evidence value was then refused by `Judgment`
construction.

Scores lie in `[0, 1]` and weights are non-negative, so any overflow shows up as an infinite total,
and only then are the eligible weights rescaled by a power of two before averaging. **Every total
that fits runs the original arithmetic, so results that worked before are bit-identical** — pinned by
a table captured from the unmodified sources and by a seeded differential test against the old
arithmetic.

**Recorded residual:** an overflowing total is reported in the aggregation evidence as
`Double.MAX_VALUE`. `inputWeight` and `eligibleWeight` are therefore a saturating view, not an exact
sum, and a reader must not treat `Double.MAX_VALUE` there as a measured total.

## A jury can be described before it votes

A verdict records what a jury did; nothing recorded what it was configured to do, so a jury that
scored with fewer judges than it lists could not be caught by comparing the two.

`Jury.describe()` and `VotingStrategy.describe()` return that structure before any vote, from a new
`@NullMarked` description package. `SimpleJury`, `CascadedJury`, `MetaJury` and all seven strategies
describe themselves; a consumer jury or strategy that does not override is described as **opaque** or
**undeclared**, never as empty. `Judges.describe(Judge)` looks through `NamedJudge` — which now
exposes `delegate()` — and reports both layers of metadata, so the `DETERMINISTIC` label
`Juries.fromJudges` puts on a renamed judge no longer hides its real type. Each seat pairs position,
verdict key, key source and weight.

A judge declares its configuration only by implementing `ConfiguredJudge`, and the portable form keeps
*undeclared* and *declared-empty* distinct with an explicit flag. `ModelBackedJudge` declares its
prompt template name, a SHA-256 of the template text, its missing-variable policy and its classifier's
implementation identity; it deliberately declares no model, because a `JudgeModel` does not state which
model it will call and any value would be a guess.

Hidden classes record no name, and anonymous or local classes record only their enclosing top-level
class, so the portable form — validated through the same portable-value algebra `Judgment` uses, and
versioned by `descriptionVersion` — is byte-identical across JVM runs. No reflection is used, and
ArchUnit holds it.

**Count a cascade per tier, against `compositeAttempts`, and never also by its top-level aggregate.**

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

## Corrections found by the 0.17 code review

A bounded review of the merged candidate found eight defects that 1,134 passing tests did not. All
eight are fixed, and these are the parts a consumer sees:

| Surface | Change |
|---|---|
| `Judgment.propagatedError` | takes `Map<JudgmentReasonCode, Long>`. Origin counts are carried, merged and emitted in one `long` domain, bounded by the portable integer range; a count above 2³² used to be accepted at construction and silently reported as a smaller number by the next `PROPAGATE` reduction. A total that would leave the range now fails loudly and is contained |
| `Judgment.portableOriginCounts` | new. The one place the portable form of an origin count is decided, for a custom strategy writing the universal evidence keys itself |
| ⚠️ `JudgeMetadata` | refuses a blank name, and requires a non-null one. A blank name used to pass, the judge used to run, and seat construction then threw **outside containment** — discarding every other judge's result and collapsing the enclosing cascade tier. `Judges.named` refuses it through the same check. A judge that builds its metadata lazily is contained as unreadable metadata instead |
| `CompositeAttempt` | a disposition reason must describe the verdict the attempt holds: `CHILD_UNDECIDED` requires an undecided child, `UNDECLARED_NOT_APPLICABLE` requires a `NOT_APPLICABLE` aggregate, and `USED` refuses a child that decided nothing. Stored data carrying a false marker is refused where it is read |
| `SimpleJuryDescription`, `MetaJuryDescription` | gain `aggregateMayBeNotApplicable` as a component. The capability is stated by the jury rather than re-derived from the strategy description, which published a confident `false` for a jury whose custom strategy declared `EXCLUDE` only through `notApplicablePolicy()`. A description contradicting a strategy that *did* declare its policy is refused |
| Requirement judges | an incomplete audit is still an `ERROR`, but now keeps its sibling checks, its `criteriaTotal` / `constraintsTotal`, and any valid exclusions. An unanswered criterion is neither a `Check` nor an authorized exclusion |
| Composite juries | a child jury that returns `null` is a stage failure like one that threw, rather than a `NullPointerException` out of `vote()`. A meta-jury keeps its other members; a non-final cascade tier reaches its final tier |
| Parent reasoning | where no later tier explains the outcome, a cascade's `no_tier_decided` root and a meta-jury's `stage_failed` root name the stage whose exclusion was refused. A boundary rejection followed by a later selected tier is unchanged: the root reasoning stays that tier's |

The portable wire shapes are unchanged by these corrections, and all five conformance fixtures are
byte-identical.

## Migrating

`agent-experiment`'s `RecordedJudgmentStatus` must learn `not_applicable` **before** any run that can
emit it. Python readers must raise on an unknown status rather than defaulting it. Counts are stored
and rates are derived, so a reader that persisted a pass rate should recompute it.

A legacy `ABSTAIN` carrying the label `not_applicable` is **never** reinterpreted automatically. A
deliberate re-curation records that it reclassified.

`JudgeMetadata`'s three-argument constructor — `new JudgeMetadata(name, description, type)` — is
**retained**, and delegates to the canonical four-argument one with a `null` condition. A judge that
never excludes a subject needs **no change**: absence of `notApplicableWhen` is the statement that it
never excludes, not a blank or missing declaration, and it is what
`Judges.notApplicableCapability(Judge)` and the seat guard read. Declaring `notApplicableWhen` is the
deliberate opt-in, taken by naming the condition through the four-argument constructor. The name
validation above applies either way.

The full 0.17 migration guide lives on the documentation site.
