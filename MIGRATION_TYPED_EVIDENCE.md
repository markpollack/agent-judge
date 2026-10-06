# Configured execution migration

This guide covers intermediate 0.18 development APIs. For migration from released 0.17,
use [MIGRATION_0.18.md](MIGRATION_0.18.md). Configure real inputs before obtaining a ready producer.

| Previous API | Current construction/execution |
|---|---|
| `Judge<E>.judge(e)` | `Judge.judge()` after typed `.evidence(e).build()` |
| `Jury<E>.vote(e)` | `Jury.vote()` after configuration |
| `RequirementJudge<S,E>` | `JudgeRecipe<S,E>.requirement(actual)` → typed evidence → ready Judge |
| `RequirementJury<S,E>` | `JuryRecipe<S,E>.requirement(actual)` → typed evidence → ready Jury |
| Unconfigured deterministic constructors | Evidence value/supplier supplied at construction |
| Native Jev requirement adapter | Actual public Jev/RFC2119/EARS Judge with a typed JevRuntime protocol |
| Intrinsic Judge exclusion metadata used as permission | Immutable `JudgeSeat.named(...).notApplicableWhen(...)` |
| `assertThatEvidence(e).judgedBy(j)` | `assertThat(configuredJudgeOrJury)` |
| Retained `.withPolicy(...)` / `.isSatisfied()` | Explicit `Evaluations.apply(verdict, policy)` / read-only conclusion assertions |
| AllOf `prepared.vote(e)` | `prepared.evidence(e).build().vote()` |

A ready ordinary Jury accepts mixed independent requirements/evidence without inventing a shared parent. Explicit AllOf assignments retain complete child Verdicts and validated coverage. Actual Requirements remain pure values; their identity/revision/source/native specification travel on original Judgments.

Generated native rosters retain one investigative call for the whole roster. Typed structured rosters require real common or per-requirement evidence and report one execution per item. Names identify seats; requirement ids identify inputs. Neither is reconstructed from naming conventions.

Retained results use [V6](portable-results-v6.md), with description V3. Current codecs refuse V2/V3/V4/V5. Use `NativeRequirementCodecs.codec()` for exact RFC2119/EARS reconstruction, or register a pure `SpecificationCodec` factory. Frozen artifacts remain unchanged; archival reading uses the baseline code and original semantics.
