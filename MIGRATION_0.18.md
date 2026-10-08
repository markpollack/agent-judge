# Migrating from Agent Judge 0.17 to Agent Eval 0.18

This is an intentional source/API/wire break on the Java 21 source line. Released
artifacts are `0.18.0` on Maven Central. Keep repository, coordinate
and package prefixes, but recompile all adopting callers against matching module versions.

| 0.17 caller surface | 0.18 replacement |
|---|---|
| `Judge.judge(JudgmentContext)` / `Jury.vote(JudgmentContext)` | Configure evidence/workspace collaborators first; call `Judge.judge()` / `Jury.vote()` |
| Model plumbing using JudgeModel/Request/Response | EvalModel/Request/Response/Options and EvalMessage/EvalMessageRole; native adapters are SpringAiEvalModel and AgentClientEvalModel |
| Result imports in `result` and voting/attempt types in `jury` | `judgment` owns Judgment/Finding/Check; `verdict` owns Verdict/Seat/CompositeAttempt; `voting` owns Ballot and rules; `jury` owns composition |
| Binary Check assumptions and score/label-only copying | Preserve child Judgment statuses and complete optional finding/support/native facts |
| Requirement-roster Judge returning one rollup Judgment | EarsJury/Rfc2119Jury returns complete selected coverage; EarsJudge/Rfc2119Judge judges one actual requirement |
| Intrinsic exclusion metadata treated as seat permission | Declare local immutable JudgeSeat exclusion permission; whole-Jury permission derives from its description |
| Per-reader interpretation heuristics | Use validated Verdict semantics and `conclusion()`; keep application reliance separate |
| Jackson engine assumed from core | Explicit `agent-judge-json-jackson2`, strict VerdictCodec/native codec; low-level `ResultJson.module()` registration |
| Old typed result binding | Strict V6 result/evaluation codecs; preserve old bytes for their exact archival reader |

Pure requirements carry identity, revision, specification and source. Typed recipes supply
actual requirements and evidence before producing a ready Judge/Jury. Evidence suppliers
run once per direct execution; construction is inert. Keep runtime tools, permissions and
timeouts on the configured backend. Inspect [the README](README.md) and
[the AssertJ guide](agent-judge-assertj/README.md) for caller examples.

An ordinary ready-member panel combines independent opinions; it does not prove coverage
of a shared requirement. Explicit AllOf assignments validate constituent coverage before
execution. Generated native rosters enter once per whole selected roster; structured
runtimes execute per item. Retain complete Verdicts, refused originals and invocation owners.

Policy decides reliance from the complete original usable Verdict. RELY/ABSTAIN/ESCALATE
do not rewrite the conclusion. EvaluationResult retains the original and the separate policy
application. Live AssertJ stages cache execution and failures. Retained assertions/reports
execute nothing; `Evaluations.apply(retainedVerdict, policy)` explicitly requests a new decision.

Results/evaluations are V6 and descriptions remain V3. Current typed readers refuse older
results. Preserve old bytes/version tags. V5 archival pin: `c0ae61dda4a65e9278485cb528925d71f27a1007`.
V4 archival pin: `7387aab1bf9d3bd56e4d9a932f2f40e2978d676d` (refuses V2/V3).
No verified typed V2/V3 reader pin is supplied. Frozen historical vectors are not V6 vectors.
See [portable-results-v6.md](portable-results-v6.md) for limits and explicit codec registration.

There are 14 JAR modules plus the parent POM. Add `agent-judge-assertj` for fluent execution,
`agent-judge-json-jackson2` for storage, `agent-judge-ai-core` for native RFC/EARS and generated
protocols, and `agent-judge-jev` for structured Jev. Optional runtime bridges remain separate.
The [draft release notes](RELEASE_NOTES_0.18.0.md) enumerate all modules and behavioral changes.
No source or binary compatibility promise, automatic history conversion or suite-wide adoption
is made. The tutorial fundamentals remain on released 0.17.0 in their separate reactor.
