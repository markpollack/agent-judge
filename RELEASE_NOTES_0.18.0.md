# Agent Eval 0.18.0 — unreleased

Agent Eval separates what an evaluator observed from the application's decision to rely
on it. Configure actual requirements, evidence and runtime collaborators before executing
a ready Judge or Jury; retain the complete result for assertions, policy and storage.

The current source version is **0.18.0-SNAPSHOT**, built with Java 21. These are draft
release notes, not an announcement of Maven Central availability. Repository identity,
group `io.github.markpollack`, artifact prefix `agent-judge-` and Java package prefix
`io.github.markpollack.judge` remain unchanged.

## Configured execution and native requirements

`Judge.judge()` returns a Judgment and `Jury.vote()` returns a complete Verdict, with no
replacement input argument. Typed construction binds requirements and evidence. Pure
`Requirement<S>` values retain identity, revision, specification and source; they own no
runtime or policy. Deterministic rules need no invented requirement.

`EvalRuntime<Q,A>` is the typed native execution port. Generated `EvalModel` specializes
that port using EvalModelRequest/Response/Options, EvalMessage and EvalRole. Spring AI
and Agent Client supply adapters; tools, workspace, credentials and deadlines belong to
the configured native harness.

Generated RFC2119/EARS rosters enter the backend once for a selected roster. Internal
model/tool calls remain distinct. Structured native rosters, including Jev, execute per
item. Jev retains native probabilities, confidence, distributions and provider declarations;
those declarations do not certify accuracy or a universal reliance threshold.

## Complete results and independent reliance

Judgments retain optional findings, support, Checks, actual requirements and native facts.
Verdicts retain complete original opinions, children, attempts, declared coverage, seat
weights, captured voting-rule configuration, provenance and invocation owners. Rejected
returns keep complete originals separately from their treatment and expected input.

Ordinary panels reduce independent opinions. Explicit AllOf and native rosters establish
required coverage; an uncertain required item cannot be filtered out to manufacture PASS.
One declared usable member preserves its complete semantic identity.

`Verdict.conclusion()` derives PASS, FAIL, INCONCLUSIVE or NOT_APPLICABLE. Application
`Policy` receives the whole original usable Verdict and decides RELY, ABSTAIN or ESCALATE.
RELY can trust a negative conclusion; withholding never rewrites that conclusion.
`EvaluationResult` retains the Verdict with NotRequested, Decided or Failed policy application,
including optional policy attribution. Live AssertJ stages cache results or thrown failures;
retained reads, reports and assertions execute no producer or policy.

Cancellation, interruption, fatal errors and preservation-limit failures propagate. Ordinary
instrument failures remain distinguishable from subject violations; unavailable facts stay
absent rather than becoming zero.

## Intentional migration breaks

Recompile 0.17 consumers. Replace input-at-execution calls with configured builders, old
result/composition imports with `judgment`/`verdict`/`voting`/`jury` packages, and old model types
with EvalModel types. Policy is a separate whole-Verdict operation. See
[the migration guide](MIGRATION_0.18.md) for the human API/module inventory.

Result/evaluation storage uses **V6**; descriptions use **V3**. Add
`agent-judge-json-jackson2` explicitly. Use `VerdictCodec`, or
`NativeRequirementCodecs.codec()` for RFC2119/EARS. Low-level Jackson callers explicitly
register `ResultJson.module()` and trusted native/custom specification codecs; ordinary
POJO binding is not the storage contract.

Current typed readers refuse older result versions. Preserve their bytes and labels.
V5 archival reading uses `c0ae61dda4a65e9278485cb528925d71f27a1007`; V4 uses
`7387aab1bf9d3bd56e4d9a932f2f40e2978d676d`, which refuses V2/V3.
No verified typed V2/V3 reader pin is supplied. See [the V6 contract](portable-results-v6.md).

## Module and tutorial inventory

The source reactor contains **14 JAR modules plus agent-judge-parent**: core,
json-jackson2, jev, assertions, assertj, exec, file, llm, koog, langchain4j, rag,
ai-core, agent-client and spring-ai. All use matching versions. Add JSON and assertion
support explicitly; no AgentWorks BOM or broader consumer migration is implied.

The [configured PetClinic tutorial](https://github.com/markpollack/agent-judge-tutorial/tree/861231ccf96d650c14fa62a14ddb5bc1181ced19/case-studies/spec-driven-petclinic)
uses actual RFC2119/EARS factories and archived responses: RFC13 retains 5 PASS/8 FAIL →
FAIL; EARS6 → PASS; EARS52 retains 51 PASS/1 ABSTAIN → INCONCLUSIVE. Credential-free
replay proves caller behavior and retention, not new inference or evaluator accuracy.
The separate fundamentals and judge-junit path stays on released 0.17.0.

## License and availability

Jackson 2 uses BOM 2.22.3 and Jackson 3 uses BOM 3.2.3. The Jackson 2 BOM manages
the shared `jackson-annotations:2.22` dependency. Main, sources, Javadoc and attached
test JARs include the current root LICENSE through the build configuration.

Project-specific [Business Source License terms](LICENSE) remain in force. Earlier releases
retain the terms shipped with them. Final dependency/license/security checks, certification,
signing and publication remain prerequisites to a release. These notes supply no certification
or publication receipt.
