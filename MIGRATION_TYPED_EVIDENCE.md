# Migrating to the 0.18 domain API

This candidate deliberately revises the earlier 0.18 API. There are no deprecated Java aliases. Current typed storage uses [schemaVersion 4](portable-results-v4.md); V2/V3 are explicitly refused. Historical unversioned diagnostics remain separate.

| Earlier surface | Current API |
|---|---|
| `Judge<RequirementEvidence<S,E>>` | `RequirementJudge<S,E>.judge(Requirement<S>,E)` |
| Requirement-aware Jury encoded as a Judge | `RequirementJury<S,E>.vote(Requirement<S>,E)` returning a full Verdict |
| Requirement-bound rendering | `JevJudge.rendering(Function<S,String>)`; actual requirement supplied per call |
| `AcceptancePolicy.decide(Judgment)` | `Policy.decide(Verdict)` |
| AcceptanceDecision / AcceptanceAction | PolicyDecision / PolicyAction |
| AppliedPolicy / PolicyApplication on Judgment | Verdict-level `PolicyResult`: NotRequested, Decided, Failed |
| AssertionResult / AcceptanceExecution | Core `EvaluationResult(Verdict, PolicyResult)` |
| Public Interpretation / reading outcome | Derived `Verdict.conclusion()`; read-only `VerdictReport` |
| ErrorPolicy / NotApplicablePolicy / TiePolicy / TierPolicy | ErrorHandling / ExclusionHandling / TieBreakRule / RoutingRule |
| STOP_ON_RELIED_JUDGMENT | STOP_ON_CONCLUSIVE; application policy runs after composition |
| `judgedByRequirement` / `withAcceptancePolicy` | `judgedBy(RequirementJudge or RequirementJury)` / `withPolicy` |
| Default RELY, nullable override | No policy requested unless explicitly supplied; no trailing null |
| Repeated fluent terminals reexecute | A completed fluent stage caches its EvaluationResult |
| Jury exposes nullable voting strategy | VotingJury exposes strategy; CascadedJury exposes tiers; common Jury exposes neither |

Requirement remains pure; identity, revision, native specification and source are preserved per Verdict node. All-of specifications own child rosters. `Assignments` owns evaluators and typed evidence selectors; `.validate()` checks complete coverage and freezes the plan before calls. A child Jury retains its entire record.

Packages now separate `judgment`, `requirement`, `jury`, `policy`, `evaluation`, `reporting`, and `serialization`. Historical diagnostic types live under `serialization.diagnostics`; they are not domain conclusion objects. The earlier `result` package and `JudgeSpec` remain removed; concrete filesystem judges live in `agent-judge-file`.

Typed evidence remains specific to each family: `Path`, `FileComparison`, `DirectoryComparison`, `CoverageComparison`, `RagEvidence`, `CompletionEvidence`, `AgentExecutionEvidence`, and `JevEvidence`. `ModelBackedJudge<E>` uses explicit variable rendering. No universal context or required magic metadata replaces these types.

[Executable examples](agent-judge-assertj/src/test/java/io/github/markpollack/judge/assertj/AssertJApiExperienceTest.java) and [package diagrams](domain-model.md) show the current construction and execution grammar. Frozen V2/V3 resources and release notes remain historical evidence, not current examples.
