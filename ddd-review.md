# DDD Design Review Report

> **Date**: 2026-08-03
> **Project**: agent-judge (`/home/mark/projects/agent-judge`)
> **Scope**: the judgment / score / aggregation domain only — `io.github.markpollack.judge.result`,
> `io.github.markpollack.judge.score`, `io.github.markpollack.judge.jury`, plus the judgment-producing
> seams in `judge.ai` (classifiers) and the `fs` / `exec` / `file` / `llm` / `rag` judges. Judge
> discovery, prompt templating, sandboxed execution, and the model-abstraction layer are out of scope.
> **Files Reviewed**:
> - `agent-judge-core/.../result/Judgment.java`, `JudgmentStatus.java`, `Check.java`
> - `agent-judge-core/.../score/Score.java`, `BooleanScore.java`, `NumericalScore.java`,
>   `CategoricalScore.java`, `ScoreType.java`, `Scores.java`
> - `agent-judge-core/.../jury/VotingStrategy.java`, `MajorityVotingStrategy.java`,
>   `AverageVotingStrategy.java`, `WeightedAverageStrategy.java`, `MedianVotingStrategy.java`,
>   `ConsensusStrategy.java`, `Verdict.java`, `ErrorPolicy.java`, `TiePolicy.java`, `TierPolicy.java`
> - `agent-judge-ai-core/.../ai/LabelJudgmentClassifier.java`
> - judgment construction sites in `fs/`, `exec/`, `coverage/`, `file/`, `llm/`, `rag/`
> - `agent-judge-core/.../jury/VotingStrategyCharacterizationTest.java` (written for this review)
> **Reviewer**: Bud-DDD Review Agent
> **Reference repositories**: `{{REPOS_ROOT}}` was not substituted and no repo root exists under
> `/home/mark/projects/bud-ddd/`. Repository cross-referencing skipped; all findings cite expert
> documents only, per the review protocol.

## Executive Summary

`agent-judge` models a genuinely well-chosen domain. The ubiquitous language — a **Judge** renders a
**Judgment** containing **Checks**; a **Jury** of judges votes to produce a **Verdict**; a judge may
**abstain**; a **CascadedJury** escalates through **tiers** — is a coherent legal metaphor that a
domain expert would recognise on sight, and it is used consistently in type names, method names, and
documentation. The library is correctly built from immutable records with no persistence, no
transactions, and no entity lifecycle, which is the right tactical weight for what it is.

The critical problem is confined to one place: the `Score` sealed hierarchy and its relationship to
`JudgmentStatus`. `Score` is not a domain polymorphism. It is a **type code attached to a
representation** — `Score`'s only member is `type()`, which returns a `ScoreType` enum that merely
restates the Java class. No call site pattern-matches it exhaustively; every call site immediately
downcasts it back to a `double`. Worse, the hierarchy encodes facts that overlap and can contradict:
`BooleanScore(true)` duplicates `JudgmentStatus.PASS`, and the builder happily constructs a judgment
that is simultaneously `PASS` and `BooleanScore(false)`. And the absence of an assessment is encoded
as the assessment zero — `Judgment.abstain(...)` and `Judgment.error(...)` both attach
`BooleanScore(false)` — so a judge that declines to evaluate silently casts a maximally negative vote
in every numeric strategy. Characterization tests written for this review confirm all three
behaviours against the current code.

Two further findings compound this. First, the aggregation vocabulary is not univocal: `Majority`
counts `status`, while `Consensus` counts `score`, so the single word "vote" means two incompatible
things inside one bounded context — `ConsensusStrategy` returns `FAIL` for two unanimously *passing*
judgments that happen to carry no score. Second, the one legitimate domain service that owns
score normalization, `Scores.toNormalized`, is **dead production code**: it is referenced only by its
own unit test, while all four numeric strategies privately reimplement a lossier copy of it that
drops the category-mapping policy entirely. The proposed redesign — `status` + optional normalized
`score` + optional `label`, with a single `effectiveScore()` — is the correct correction, and it is a
domain correction rather than a serialization convenience. My recommendations below tighten it in
five places rather than replace it.

## Strengths

- **Ubiquitous Language (Evans; Rayner).** Judge / Judgment / Verdict / Jury / Check / abstain /
  tier / cascade is a single coherent metaphor carried consistently through type names, method names,
  Javadoc, and tests. Evans' test — "would a domain expert recognise these names?" — passes
  comfortably. `TierPolicy.REJECT_ON_ANY_FAIL` / `ACCEPT_ON_ALL_PASS` / `FINAL_TIER` name the
  *business* rule, not a control-flow mechanism.
- **Value Objects done properly (Evans; Vernon; Kerr).** `Judgment`, `Check`, and `Verdict` are Java
  records with defensive `List.copyOf` / `Map.copyOf` in their compact constructors. They are
  immutable, structurally equal, and replaceable — exactly Vernon's characterisation of a Value
  Object, and exactly Kerr's point that Value Objects are functional constructs adopted into OOP.
- **Side-Effect-Free Functions (Evans, Supple Design).** `VotingStrategy.aggregate` is a pure
  function of `(judgments, weights)`. Strategies hold no mutable state. This is why the redesign is
  cheap to verify — every behaviour under review is reachable from a pure call.
- **Correct tactical weight (Khononov).** No repositories, no aggregates, no factories-for-their-own-
  sake, no event sourcing. The domain has no persisted lifecycle, so none of that apparatus is
  warranted, and the codebase does not reach for it. This is the complexity-matching heuristic
  applied correctly, and it should be preserved through the migration.
- **Standalone core (Evans, Standalone Classes).** `agent-judge-core` has essentially no external
  dependencies. Provider integrations live in separate modules behind their own bridges. The
  dependency direction is correct: the domain depends on nothing.
- **Explicit policy as first-class enums.** `ErrorPolicy`, `TiePolicy`, and `TierPolicy` lift
  aggregation decisions out of buried conditionals into named domain choices. The redesign should
  extend this pattern, not abandon it (see Suggestion 3).

## Critical Issues

### Issue 1 — `Score` is a type code, not a domain polymorphism

- **Principle violated**: Conceptual Contours and Standalone Classes (Evans, Supple Design);
  complexity matching (Khononov).
- **Location**: `score/Score.java`, `score/ScoreType.java`, and all five strategies in `jury/`.
- **Problem**: `Score`'s entire published behaviour is `ScoreType type()`, which returns an enum that
  restates the implementing class — the textbook "type code plus subclass" duplication. The sealed
  interface advertises exhaustive pattern matching, but no production call site exhausts it. Every
  one is an `instanceof` ladder ending in a silent `return 0.0` default:

  ```java
  if (score instanceof BooleanScore bs) { return bs.value() ? 1.0 : 0.0; }
  else if (score instanceof NumericalScore ns) { return ns.normalized(); }
  return 0.0;                      // CategoricalScore and null land here, silently
  ```

  Evans' Conceptual Contours asks whether the decomposition follows meaningful domain seams. This one
  follows *representational* seams — bool / number / string — which is a Java-type distinction, not a
  domain distinction. The domain distinctions that actually matter (did the judge assess? on what
  scale? under what classification policy?) are not what the hierarchy separates.
- **Impact**: Four separate private reimplementations of the same normalization, each with its own
  silent-zero fallback; a sealed interface whose one guarantee (exhaustiveness) is unused; and a
  `ScoreType` enum that must be kept in sync with the class hierarchy for no benefit.
- **Recommendation**: Retire `Score`, `BooleanScore`, `NumericalScore`, `CategoricalScore`, and
  `ScoreType`. Replace with three independent, directly-modelled facts on `Judgment`: required
  `status`, optional normalized `score`, optional `label`. This is the handoff's proposal and it is
  correct.
- **Reference**: Evans, *Supple Design* — "Decompose design elements so that the boundaries align
  with meaningful domain concepts rather than arbitrary technical divisions"; Khononov — "Using a
  Domain Model for a simple CRUD application is over-engineering."

### Issue 2 — One fact stored twice, with no invariant keeping the copies consistent

- **Principle violated**: Assertions (Evans, Supple Design); Value Objects are self-validating
  (Vernon, ch. 6).
- **Location**: `result/Judgment.java` — the compact constructor and `Judgment.Builder`.
- **Problem**: A boolean outcome is recorded twice, once in `JudgmentStatus` and once in
  `BooleanScore`, and nothing enforces agreement. The compact constructor copies the collections and
  validates nothing else — it does not even require `status` to be non-null. Both of these compile
  and run today (confirmed by `VotingStrategyCharacterizationTest.FactoryScores`):

  ```java
  Judgment.builder().score(new BooleanScore(false)).status(PASS).build();  // contradiction
  Judgment.builder().reasoning("no status").build();                       // status == null
  ```

  A null `status` is not hypothetical: `ConsensusStrategy` and `MajorityVotingStrategy` both branch
  on `status` without a null guard.
- **Impact**: The value object cannot be trusted by its own consumers, so each consumer re-derives
  what it needs from whichever field it happens to trust — which is precisely the divergence
  documented in Issue 4.
- **Recommendation**: Make `status` required and validated in the compact constructor. Remove the
  boolean score entirely — `JudgmentStatus` already carries that fact. Replace the free-for-all
  builder with intent-specific entry points (`verdict(boolean)`, `scored(...)`, `classified(...)`,
  `abstaining()`, `error(...)`) that derive coupled fields once, at the point where intent is known.
- **Reference**: Vernon, *IDDD* ch. 6 — a Value Object is "self-validating"; Evans, *Assertions* —
  "Assertions on arguments and internal state improve correctness by disallowing invalid state to
  propagate."

### Issue 3 — "No assessment" is modelled as the assessment zero

- **Principle violated**: Make implicit concepts explicit (Evans; Kerr — model with types so invalid
  states are unrepresentable).
- **Location**: `result/Judgment.java` `abstain(String)` and `error(String, Throwable)`; consumed in
  `AverageVotingStrategy`, `MedianVotingStrategy`, `WeightedAverageStrategy`, `ConsensusStrategy`.
- **Problem**: Both factories attach `BooleanScore(false)`. `ABSTAIN` means *"I decline to assess
  this"*; `0.0` means *"I assessed it and it is as bad as possible."* These are different domain
  facts and the model cannot tell them apart. The consequences are characterized and confirmed:

  | Input | Current result | Domain-correct result |
  |---|---|---|
  | `Average(pass, abstain)` | `0.5` — abstention voted 0 and stayed in the denominator | `1.0` — abstention excluded |
  | `Median(1.0, abstain, abstain)` | `0.0`, verdict flips to `FAIL` | `1.0`, verdict `PASS` |
  | `Average(pass, error)` | numerically identical to `Average(pass, real 0.0)` | error must not be silently scored |
  | `Average(abstain, abstain)` | `FAIL` scored `0.0` | explicit no-result outcome |

  Kerr's guidance is directly on point: an absent value must be *representable as absent*, so the
  compiler forces every consumer to state a policy for it.
- **Impact**: An abstaining judge — one added specifically because it *cannot* assess a given case —
  silently casts the strongest possible negative vote. In a `SimpleJury` with an `ABSTAIN`-heavy
  configuration this inverts verdicts. This is a correctness defect in the product's core subdomain,
  not a modelling nicety.
- **Recommendation**: Make `score` optional (`@Nullable Double` / `OptionalDouble` view) and leave it
  absent for `ABSTAIN` and `ERROR`. Centralise the Boolean-to-numeric view in one derived
  `effectiveScore()` returning `OptionalDouble.empty()` for `ABSTAIN`/`ERROR`. Then require each
  strategy to state its missing-value policy explicitly rather than inheriting a silent zero.
- **Reference**: Kerr — "wrap primitives in domain-meaningful types… making invalid states
  unrepresentable"; Evans, *Refactoring Toward Deeper Insight* — making an implicit concept explicit.

### Issue 4 — "Vote" has two incompatible meanings inside one bounded context

- **Principle violated**: Ubiquitous Language — one term, one meaning (Evans; Khononov: "each
  ubiquitous language term should have exactly one meaning").
- **Location**: `jury/MajorityVotingStrategy.java` (counts `status`) versus
  `jury/ConsensusStrategy.java` (counts `score`).
- **Problem**: Both classes implement `VotingStrategy`, both describe themselves in Javadoc as
  counting judges' votes, and each reads a *different field* to decide what a judge voted. Because
  `ConsensusStrategy.toBoolean` falls through to `false` for a null or categorical score, two
  judgments that both declare `JudgmentStatus.PASS` aggregate to `FAIL`:

  ```java
  consensus.aggregate(List.of(statusOnly(PASS), statusOnly(PASS)), Map.of()).status(); // FAIL
  ```

  The same fall-through makes a categorical judgment a fail vote, and makes an `ABSTAIN` a fail vote
  that can *manufacture* unanimity — `consensus(fail, abstain)` reports "Unanimous consensus".
  Separately, `MajorityVotingStrategy`'s Javadoc claims it converts numerical scores to boolean at a
  0.5 threshold; it does not. The evidence is a `THRESHOLD` constant and a `NumericalScore` import
  that are both unused. The documentation describes a model the code abandoned.
- **Impact**: `VotingStrategy` is the interface the whole jury system is polymorphic over. If its
  implementations disagree about what a judgment *is*, the abstraction is not substitutable, and
  swapping strategies — the entire point of the interface — silently changes meaning.
- **Recommendation**: Define one authoritative reading of a judgment's numeric contribution
  (`effectiveScore()`), express every strategy in terms of it, and state in `VotingStrategy`'s
  contract that `status` is the outcome of record and `score` refines it. Delete the stale Javadoc,
  the unused constant, and the unused import.
- **Reference**: Khononov, *Bounded Contexts* — "Since software doesn't cope well with ambiguity,
  each ubiquitous language term should have exactly one meaning."

## Warnings

### Warning 1 — The category-mapping policy is homeless, and its only home is dead code

- **Principle violated**: Domain Service should be the single home for logic that belongs to no value
  object (Evans, Vernon); Standalone Classes.
- **Location**: `score/Scores.java` versus the four private `toNormalized` / `toBoolean` methods in
  `jury/`.
- **Problem**: `Scores.toNormalized(Score, Map<String, Double> categoryMap)` is the correct shape for
  this domain service: normalization *including* the caller-declared label-to-number policy. It is
  referenced by `ScoresTest` and by nothing else in production — verified by grep across all modules.
  Every voting strategy instead carries a private copy that omits the `categoryMap` parameter and
  hardcodes categorical scores to `0.0`. The domain concept "a category's numeric meaning is a
  declared policy" therefore exists in the model, is unit-tested, and is unreachable.
- **Impact**: Four copies of one rule; the only copy that is correct is the one nobody calls; and
  categorical judges are silently scored zero everywhere in production.
- **Recommendation**: Give the label-to-score mapping an owner — the classifier that declares the
  labels (`LabelJudgmentClassifier` already owns `categories()`). Have it record the mapped
  normalized score on the `Judgment` at classification time, so no downstream aggregator needs the
  policy at all. Then delete `Scores`.
- **Related**: Issue 1, Issue 4.

### Warning 2 — `NumericalScore` publishes two indistinguishable numbers, and a live consumer picked the wrong one

- **Principle violated**: Intention-Revealing Interfaces (Evans, Supple Design).
- **Location**: `score/NumericalScore.java` (`value()` versus `normalized()`); consumer
  `agent-workflow/workflow-flows/.../JudgeGate.java:90` and `TieredGate.java:71`.
- **Problem**: `NumericalScore` exposes the raw `value()` and the derived `normalized()` side by
  side, with names that give the call site no clue which one a threshold comparison wants. The
  downstream consumer chose wrong:

  ```java
  // agent-workflow JudgeGate.extractScore — reviewed read-only, not modified
  if (score instanceof NumericalScore ns) { return ns.value(); }   // raw, not normalized
  ```

  That value is then compared against a normalized gate threshold. For any judge using
  `NumericalScore.outOfTen(...)`, a score of `8.5` clears a `0.7` gate for the wrong reason, and a
  raw `0-100` scale clears every gate unconditionally. No current in-repo judge uses a non-unit
  range, so the defect is latent rather than firing — but the API is what invited it.
- **Impact**: The interface makes the wrong call easier to write than the right one, in the one place
  the number is actually used to make a decision.
- **Recommendation**: Normalize once, at construction, and publish exactly one number. Keep the raw
  value and its range in metadata or typed judge output when review-relevant, never as a second
  number with equal standing on the same object. Flag this explicitly in the consumer migration
  handoff as a semantic fix, not a mechanical rename.
- **Related**: Issue 1.

### Warning 3 — `ConsensusStrategy` collapses "unanimously failed" and "no consensus" into one outcome

- **Principle violated**: Making implicit concepts explicit (Evans); Conceptual Contours.
- **Location**: `jury/ConsensusStrategy.java`.
- **Problem**: The strategy computes `consensus` and `pass` separately, writes the distinction into
  the reasoning string ("Unanimous consensus" versus "No consensus"), and then throws it away by
  returning `FAIL` for both. The one fact a consensus strategy exists to report — *did the judges
  agree?* — survives only as prose.
- **Impact**: A caller cannot programmatically distinguish "every judge agreed this fails" from "the
  judges could not agree", which are different business situations with different escalation paths —
  exactly the distinction `CascadedJury`'s tiering is built to act on.
- **Recommendation**: Return `FAIL` on unanimous failure and `ABSTAIN` (no-result) on disagreement,
  or add an explicit policy enum in the style of the existing `TiePolicy`. Record whichever choice is
  made as a deliberate semantic change.
- **Related**: Issue 4, Warning 5.

### Warning 4 — `ErrorPolicy.IGNORE` and `ErrorPolicy.TREAT_AS_ABSTAIN` are the same behaviour

- **Principle violated**: Ubiquitous Language — distinct terms must denote distinct concepts (Evans).
- **Location**: `jury/ErrorPolicy.java`, applied in `MajorityVotingStrategy.applyErrorPolicy`.
- **Problem**: The enum documents `IGNORE` as "skip in vote counting" and `TREAT_AS_ABSTAIN` as
  "treat as neutral". The implementation maps both to `Judgment.abstain(...)`, differing only in the
  reasoning string. Two named domain policies, one behaviour.
- **Impact**: A user choosing `IGNORE` for its documented meaning gets `TREAT_AS_ABSTAIN`. Since
  all-abstain yields `ABSTAIN`, an all-error jury under `IGNORE` returns `ABSTAIN` rather than the
  documented "skip" semantics, and the difference is invisible.
- **Recommendation**: Make `IGNORE` genuinely remove the judgment from the population before counting
  — numerator, denominator, vote count, and weight — while retaining the original `ERROR` in
  `Verdict.individual` for audit.
- **Related**: Issue 4.

> **Correction (post-review).** This warning originally offered a second option — collapse the two
> constants — and argued that once Issue 3 is fixed they become "genuinely equivalent, in which case
> collapsing is the honest fix". **That was wrong**, and the design proposal initially followed it.
> The duplication is an implementation defect, not evidence the policy is unnecessary. "Exclude from
> the population" and "participate as an abstention" are distinct policies with distinct accounting,
> and established prior art treats them as such: Ragas (`NaN` + `nanmean`), Inspect (explicit
> *unscored*, excluded from metrics and reducers), DeepEval (missing metric versus ignored execution
> error), Snorkel (abstention as no emitted label). The enum has documented "skip in vote counting"
> since 0.1.0; the implementation simply never did it. Note also that the characterization test
> written for this review marked the behaviour `INTENDED` — the collapse recommendation contradicted
> evidence gathered in the same pass, because the test asserted only the final status and so could
> not distinguish the two policies. Tests for these policies must assert effective-population counts
> and reasoning, not just `PASS`/`FAIL`/`ABSTAIN`. Resolved in `design-normalized-judgment.md`
> [DELTA-3].

### Warning 5 — No modelled "no result" outcome

- **Principle violated**: Making implicit concepts explicit (Evans); Assertions.
- **Location**: all four numeric strategies in `jury/`.
- **Problem**: `JudgmentStatus` offers `PASS | FAIL | ABSTAIN | ERROR`, all of which describe *a
  judge's* stance. Aggregation has an additional outcome with no name: *nothing scorable was
  submitted*. Today that case is manufactured as `FAIL` with score `0.0`
  (`Average(abstain, abstain)` → `FAIL`), which asserts a substantive negative finding the jury never
  made. Once `ABSTAIN`/`ERROR` correctly carry no score, this case stops being an edge case and
  becomes routine, and dividing by an empty denominator becomes reachable.
- **Impact**: A jury that could not evaluate is indistinguishable from a jury that evaluated and
  rejected — the same conflation as Issue 3, one level up in the composition.
- **Recommendation**: Reuse `ABSTAIN` at the verdict level with reasoning that names the cause. Do
  **not** add a fifth enum constant: `ABSTAIN` already means "no assessment was made", and the
  aggregate is itself a `Judgment`, so the existing vocabulary covers it. Assert explicitly rather
  than dividing by zero.
- **Related**: Issue 3, Warning 3.

### Warning 6 — `metadata` is an untyped bag holding two real domain concepts and one unserializable object

- **Principle violated**: Making implicit concepts explicit (Evans); Standalone Classes.
- **Location**: `result/Judgment.java` — `Map<String, Object> metadata`, `elapsed()`, `error()`.
- **Problem**: Two accessors reach into the map and blind-cast: `elapsed()` casts to `Duration`,
  `error()` casts to `Throwable`. "How long the judge took" and "what went wrong" are genuine,
  recurring domain concepts on every judgment; they are modelled as string-keyed `Object` entries
  with a cast at the read site. The `Throwable` case is worse than untidy — a `Throwable` graph
  carries stack frames, suppressed exceptions, and causal chains, so any durable or wire projection
  of a `Judgment` either fails or serializes an unbounded object graph. This is the concrete reason
  the downstream consumer declined to reuse this type and hand-rolled its own DTO
  (`workflow-spec/.../envelope/Judgment.java` — reviewed read-only), which independently arrived at
  exactly the target shape: `String memberId, @Nullable Double score, …`.
- **Impact**: The extension plane, which is a legitimate design choice, is being used to carry
  first-class concepts. That blocks the Open-Host Service / Published Language role this type must
  play for `agent-workflow` (Khononov, context mapping): a published language cannot contain
  `Object`.
- **Recommendation**: Keep `metadata` as the open extension plane, but give the error its own safe,
  declared representation for the wire (message and type, not the `Throwable` graph), per handoff
  §2.4. Retaining the live `Throwable` in in-process metadata is fine and useful; what must not
  happen is a projection that tries to serialize it. Treat `elapsed` as a separate question and do
  not redesign it in this act — the handoff's non-goals explicitly fence off `Check` and metadata.
- **Related**: Issue 2, Issue 3.

## Suggestions

### Suggestion 1 — Adopt the three-fact `Judgment` as proposed, and validate it in the compact constructor

The handoff's `status` + optional `score` + optional `label` shape is the right correction. Put every
invariant in the compact constructor so the record cannot be built invalid by *any* route (builder,
static factory, or direct `new`): `status` non-null, `reasoning` non-null, `score` finite and within
`[0.0, 1.0]` when present, `label` non-blank when present. Vernon's self-validating Value Object
means validation lives with the value, not with one preferred construction path.

**Related**: Issue 1, Issue 2.

### Suggestion 2 — Let the staged builders carry the invariants the type cannot

`Judgment.scored(0.82)` returning a stage with no `build()` — forcing `.passingAt(threshold)` or
`.withStatus(...)` — is Evans' Intention-Revealing Interfaces applied at the type level, and it is the
strongest part of the proposal: it makes "a score without a stated outcome policy" *unrepresentable*
rather than merely discouraged. This is Kerr's "make invalid states unrepresentable" in a language
without sum types. Keep it. Do the same for `classified(label)` requiring `.as(status)`.

**Related**: Issue 2, Issue 3.

### Suggestion 3 — Make each strategy's missing-value policy a named domain choice, not a code comment

The existing `ErrorPolicy` / `TiePolicy` / `TierPolicy` enums are the right precedent: aggregation
decisions are business policy and deserve names. When `ABSTAIN` and `ERROR` stop carrying a score,
every numeric strategy needs an explicit answer for both. Prefer extending the existing policy enums
over inventing a parallel mechanism, and prefer one shared default (`ABSTAIN` excluded from the
denominator; `ERROR` governed by `ErrorPolicy`; no scorable input yields an explicit no-result) over
per-strategy improvisation.

**Related**: Issue 3, Issue 4, Warning 4, Warning 5.

### Suggestion 4 — Resolve `Judgment.pass()` versus `Judgment.pass(String)`

The instance predicate `pass()` and the static factory `pass(String)` share a name while doing
categorically different things — one is a side-effect-free query, the other constructs a value.
Evans separates queries from commands precisely to avoid this. The handoff's `passing()` / `failing()`
naming for the fluent entry points is the right resolution and should be adopted as stated; it also
explains *why* the asymmetric name exists, which is worth a Javadoc line so a future reader does not
"fix" it back into a collision.

**Related**: Issue 2.

### Suggestion 5 — Name `agent-judge` the upstream of a Published Language, and design the wire shape as such

`agent-workflow` consumes `Judgment` and `Verdict` across a repository boundary. In Khononov's
context-mapping terms this is an upstream Open-Host Service relationship, and the JSON projection is
its Published Language. That reframes the serialization work: the flat `{status, score?, label?}`
shape is not a Jackson accommodation, it is the published contract, and it should be pinned by tests
in `agent-judge` itself (exact JSON, optionals omitted rather than `null`, no polymorphic type
metadata) rather than discovered downstream. The fact that the consumer independently invented
`@Nullable Double score` is strong evidence the proposed shape is the right one.

**Related**: Issue 1, Warning 2, Warning 6.

### Suggestion 6 — Do not add DDD apparatus this domain does not have

Stated explicitly so a future review does not over-fire: there are no Aggregates, Aggregate Roots,
Repositories, Domain Events, or Factories-as-pattern here, and there should not be. Nothing in this
domain has identity, a lifecycle, or a transactional consistency boundary — a `Judgment` is a value
produced by a pure function and never mutated. By Khononov's decision tree the aggregation rules are
complex enough to warrant a **Domain Model** of value objects plus stateless domain services, and
nothing beyond that. Adding aggregates or repositories here would be exactly the over-engineering
his complexity-matching heuristic warns against.

**Related**: none.

## Expert-Specific Notes

### Evans Lens

Supple Design is where this model is weakest and where the redesign pays off most.
**Intention-Revealing Interfaces**: `NumericalScore.value()` versus `normalized()` misled a real
consumer (Warning 2); `Judgment.pass()` versus `pass(String)` overloads a query and a factory
(Suggestion 4). **Assertions**: `Judgment`'s compact constructor validates collection immutability
and nothing else, permitting null status and self-contradictory judgments (Issue 2).
**Conceptual Contours**: `Score`'s subtypes divide along Java representation rather than domain
meaning (Issue 1). **Standalone Classes**: `CategoricalScore` drags a copy of the classifier's entire
`allowedValues` list into every judgment value — a dependency inessential to the concept of "this
judge said `relevant`", and one that must then be serialized or stripped at every boundary.
On the positive side, **Side-Effect-Free Functions** is exemplary: aggregation is pure throughout,
which is why this redesign is verifiable by characterization rather than by inspection.
Ubiquitous Language is a genuine strength with one sharp exception — "vote" (Issue 4).

### Vernon Lens

The four aggregate rules do not apply: there is no aggregate, no consistency boundary, and no
transaction in this domain, and inventing one would be a mistake. Vernon's chapter 6 treatment of
Value Objects is the applicable lens, and it produces one clear finding: a Value Object must be
**self-validating**, and `Judgment` is not (Issue 2). Vernon's guidance to "favor Value Objects over
Entities when the choice is not clear" is already followed. His preference for typed identity values
over generic primitives argues mildly for keeping `label` a declared, classifier-owned vocabulary
rather than a free string — the compact constructor's non-blank check is the pragmatic floor here,
and given that labels arrive from LLM output at runtime, a full value type is not warranted.

### Khononov Lens

Subdomain classification for this library: the **judgment and aggregation semantics are the core
subdomain** — they are what `agent-judge` exists to get right and what distinguishes it from calling
an LLM directly. The provider bridges (`spring-ai`, `langchain4j`, `koog`, `agent-client`) are
**supporting**; process execution and JSON parsing are **generic** and correctly delegated to
`agent-sandbox` and Jackson. This classification has a direct consequence for the migration: the core
subdomain is exactly where a *silent* defect is least acceptable, which is why Issue 3's abstention
bug outranks every stylistic finding in this report.

On complexity matching, the current design is over-engineered in one axis and under-engineered in
another simultaneously — a combination his heuristics predict when a pattern is chosen for its type-
system appeal rather than the domain's shape. The sealed hierarchy is a sophisticated pattern serving
a domain that needs three scalar fields (over-engineered); the invariants that genuinely are complex
— what abstention means to a numeric average, what happens when nothing is scorable — are absent
(under-engineered). The redesign moves complexity from where it is decorative to where it is load-
bearing.

On coupling: `Judgment` scores high on all three of his dimensions with respect to `agent-workflow` —
high **integration strength** (the consumer reads the score to make gate decisions), high **distance**
(separate repository, separate release), and currently high **volatility** (this change).
`BALANCE = (STRENGTH XOR DISTANCE) OR NOT VOLATILITY` is unsatisfied, which is the formal statement of
why this migration hurts. The remedy is to reduce strength by publishing a narrow, stable contract
(Suggestion 5) and then let volatility fall.

### Brandolini Lens

Not applicable, and deliberately so. There are no domain events in this model and none are warranted:
`agent-judge` is a synchronous, in-process evaluation library, and its "events" (`JudgmentRecorded`
and friends) properly belong to the orchestrating workflow context, where they already exist. Adding
past-tense event types here would push orchestration concerns into a library whose value is being
embeddable. The one EventStorming-adjacent observation worth recording: `TierPolicy` describes a
policy in Brandolini's exact sense — "whenever X happens, we do Y" — and naming it as such is a small
win for the ubiquitous language that the codebase already achieved.

### Spring Ecosystem Lens

Spring Modulith is not applicable: `agent-judge-core` is a dependency-free library, not a Spring
application with module boundaries to verify, and its module separation is already enforced by Maven
reactor structure — a stronger boundary than Modulith's runtime verification. jMolecules annotations
(`@ValueObject`) would document intent, but they add a dependency to a module whose stated selling
point is "zero external dependencies", so the trade-off is not worth it. The dependency direction is
already correct: `agent-judge-core` depends on nothing domain-relevant, and Spring AI appears only in
the `llm` and `spring-ai` bridge modules. Nothing to change here.

## Appendix: Review Checklist

| # | Criterion | Status | Note |
|---|---|---|---|
| 1 | Ubiquitous Language consistency | **WARN** | Strong overall; "vote" is ambiguous (Issue 4); `pass()` overloaded (Suggestion 4) |
| 2 | Bounded Context integrity | **WARN** | Upstream/downstream with `agent-workflow` is real but unnamed; no published language (Suggestion 5) |
| 3 | Aggregate design (Vernon's four rules) | **N/A** | No aggregates; none warranted (Suggestion 6) |
| 4 | Entity vs Value Object classification | **PASS** | All records, immutable, structurally equal, correctly Value Objects |
| 4b | Value Object self-validation | **FAIL** | `Judgment` permits null status and contradictory score/status (Issue 2) |
| 4c | Primitives wrapped in domain types | **WARN** | Over-applied: `Score` wraps primitives that want to *be* primitives (Issue 1) |
| 5 | Domain Events | **N/A** | Synchronous library; events belong to the workflow context |
| 6 | Repository design | **N/A** | No persistence in this domain |
| 7 | Service design | **FAIL** | `Scores` is the correct domain service and is dead code; logic duplicated ×4 (Warning 1) |
| 8 | Subdomain classification / complexity match | **FAIL** | Over-engineered representation, under-engineered invariants (Khononov lens) |
| 9a | Supple Design — Intention-Revealing Interfaces | **FAIL** | `value()`/`normalized()` misled a live consumer (Warning 2) |
| 9b | Supple Design — Side-Effect-Free Functions | **PASS** | Aggregation is pure throughout |
| 9c | Supple Design — Assertions | **FAIL** | Invariants absent from the compact constructor (Issue 2) |
| 9d | Supple Design — Conceptual Contours | **FAIL** | `Score` splits on representation, not domain meaning (Issue 1) |
| 9e | Supple Design — Standalone Classes | **WARN** | `CategoricalScore` carries the classifier's allowed-values list (Evans lens) |
| 10 | Architecture / dependency direction | **PASS** | Core depends on nothing; integrations isolated in bridge modules |
| 11 | Documentation matches model | **FAIL** | `MajorityVotingStrategy` Javadoc describes threshold logic the code does not implement (Issue 4) |
| 12 | Dead code in the domain | **FAIL** | `Scores`, `ScoreType`, unused `THRESHOLD` constant and `NumericalScore` import (Warning 1, Issue 4) |
