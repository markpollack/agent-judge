# Migrating to the 0.18 domain API

This source line intentionally redesigns the earlier 0.18 candidate. It offers no Java
compatibility aliases. Modern portable results use [schemaVersion 3](portable-results-v3.md);
V2 documents are unsupported. Historical unversioned interpretation remains separate.

| Earlier concept | Current API |
|---|---|
| Requirement-owned policy / `under` | Pure Requirement; acceptance on assertion configuration or after evidence |
| PolicyBinding | AcceptancePolicy lambda; optional `Policies.recorded` provenance |
| Acceptance / ApplicationDecision | AcceptanceDecision / retained AcceptanceExecution |
| USE_ASSESSMENT | RELY (positive or negative Judgment) |
| Assessment / Proposition | Finding / BooleanFinding |
| NumericAssessment / Category | NumericFinding / CategoryFinding |
| Certainty / Distribution | Confidence / ProbabilityDistribution |
| EvaluationProvenance | Provenance |
| Verdict.aggregated / decision | Verdict.judgment / provenance |
| Decision / DecisionKind / DecisionBasis | VerdictProvenance and its kind/basis |
| VerdictReading | RequirementOutcome: SATISFIED, VIOLATED, UNRESOLVED, NOT_APPLICABLE, NOT_ASSESSED |
| SemanticAssertions / SemanticAssertion / SemanticAssertionError | RequirementAssertions / staged AssertJ / RequirementAssertionError |
| `Judge<RequirementEvidence<Requirement<S>,E>>` | Ordinary `Judge<E>` or explicit `Judge<RequirementEvidence<S,E>>` |
| STOP_ON_USABLE_ASSESSMENT | STOP_ON_RELIED_JUDGMENT |

`result` is split into `judgment`, `acceptance` and `provenance`. Concrete `fs` judges move
to `agent-judge-file`. The obsolete JudgeSpec is deleted. Wire helpers use `serialization`.

There is no universal JudgmentContext replacement. File existence/content, command/build,
class-version and workspace requirement audits take `Path`. File and directory comparisons
use `FileComparison` and `DirectoryComparison`. Coverage takes `CoverageComparison`, with a
numeric baseline. Class-version expectations are constructor configuration. RAG uses
`RagEvidence`; response bridges use `CompletionEvidence`; AgentClient execution combines a
workspace with completion in `AgentExecutionEvidence`. Jev keeps selected `JevEvidence`.
No required input is obtained from arbitrary metadata keys.

`DeterministicJudge<E>`, `LLMJudge<E>` and `ModelBackedJudge<E>` preserve the selected evidence
type. ModelBackedJudge requires explicit `.variables(...)` rendering. CompletionVariables
is an optional helper for request/response templates. JudgeModelResponse reports backend
completion with a typed `completed` component, separate from incidental telemetry.

For AssertJ use `judgedBy(Judge<E>)` or `judgedBy(Jury<E>)`. When an evaluator needs the
Requirement, use `judgedByRequirement(...)`; its pair type is `RequirementEvidence<S,E>`.
Both produce the same evidence → optional policy → isSatisfied progression. See the
[compiled experience examples](agent-judge-assertj/src/test/java/io/github/markpollack/judge/assertj/AssertJApiExperienceTest.java).
