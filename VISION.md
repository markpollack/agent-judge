# Vision: Agent Judge

> Frameworks are vertical; evaluation is horizontal.

> **Reviewed**: 2026-08-03
> **Current release**: 0.13.0
> **Current development line**: 0.14.0-SNAPSHOT

## End State

Agent Judge is the portable verification layer for JVM agent systems: teams define evaluation logic
once, run it against in-process frameworks or CLI-delegated agents, and retain the same judges,
juries, thresholds, and evidence when the runtime changes.

The library answers one primary question:

> Did this agent execution satisfy the goal, and what evidence supports that conclusion?

It does not attempt to explain every internal step taken by the agent, host an experiment-management
platform, or replace agent runtimes.

## Who It Is For

- JVM teams evaluating output from Spring AI, LangChain4j, Koog, AgentClient, or custom runtimes.
- Teams using coding agents that need workspace-aware verification such as builds, coverage, file
  comparison, and semantic review.
- Platform and engineering leaders who want evaluation assets to survive a framework or model change.
- Agent-framework authors who need a reusable evaluation layer without importing another framework.

## The Problem

Evaluation programs are long-lived assets. Agent runtimes, models, providers, and procurement choices
change faster than the rules used to decide whether work is acceptable. If evaluation is embedded in
one framework's lifecycle or one vendor's response type, changing the runtime also forces teams to
rewrite their quality gates.

Agent Judge separates:

- the source of an execution result;
- the evidence made available for evaluation;
- the judges that interpret that evidence;
- the jury policy that aggregates findings;
- the resulting verdict.

This separation is the product.

## Product Promise

### Portable evaluation

Framework and runtime adapters convert native response types into JudgmentContext. Judges and juries
operate on that shared context and do not depend on the producing framework.

### Explicit outcomes

A Judgment records three independent facts:

- required status: PASS, FAIL, ABSTAIN, or ERROR;
- optional normalized score in [0,1];
- optional classification label.

ABSTAIN is not a failing vote. ERROR is not a negative finding. A score is not a second status.

### Cost-aware composition

CascadedJury supports cheap deterministic checks before expensive model-backed checks. Voting
strategies make aggregation policy explicit rather than hiding it in application code.

### Workspace-aware verification

JudgmentContext can carry a workspace, execution status, output, timing, and metadata. This enables
build, coverage, class-version, file, AST, Maven, XML, and other artifact-oriented judges that cannot
be expressed as input/output similarity alone.

### Inspectable evidence

Judgments and aggregate verdicts carry reasoning, checks, and structured aggregation evidence.
Consumers can distinguish unanimous failure, disagreement, abstention, and execution error.

### Deployable in constrained environments

Agent Judge is a library, not a hosted control plane. It supports local, CI, offline, and
air-gapped use without requiring evaluation data to leave the environment.

## Architecture Principle

Framework-neutral does not mean dependency-free. The core currently uses general-purpose JSON and
logging libraries, but it has no dependency on an agent framework or model provider. The durable
boundary is framework independence, not a dependency-count slogan.

    Spring AI     LangChain4j     Koog     AgentClient     Custom runtime
        \              |           |           /               /
         \             |           |          /               /
          -------- framework/runtime adapters ----------------
                               |
                        JudgmentContext
                               |
                 Judges -> Juries -> Verdicts

The adapter changes when the runtime changes. Evaluation policy does not.

## Distinctive Capabilities

- CascadedJury for tiered, fail-fast, cost-aware evaluation.
- Majority, consensus, average, weighted-average, and median aggregation.
- Explicit error policies with observable aggregation evidence.
- Deterministic, execution, file-semantic, LLM, and RAG judge families.
- Evaluated-side adapters for Spring AI, LangChain4j, Koog, and AgentClient.
- Judging-side abstractions through JudgeModel and ModelBackedJudge.
- Workspace and artifact awareness for coding-agent and repository workflows.

## Current Product State

Released through 0.13.0:

- ten Maven modules;
- framework/runtime bridges for Spring AI, LangChain4j, Koog, and AgentClient;
- AI-core abstractions for model-backed judges;
- RAG judges;
- Maven Central publication;
- documentation covering the module and adapter model;
- CI dependency-vulnerability gates.

The 0.14.0 development line replaces the former Score hierarchy with the normalized Judgment model.
The core checkpoint is committed; full-reactor verification and consumer migration remain in
progress. See roadmap.md.

## Modules

| Layer | Module | Responsibility |
|---|---|---|
| Core | agent-judge-core | JudgmentContext, Judgment, Judge, Jury, Verdict, voting |
| AI infrastructure | agent-judge-ai-core | JudgeModel, prompt templates, classifiers, ModelBackedJudge |
| Judge family | agent-judge-exec | Commands, builds, class versions, coverage |
| Judge family | agent-judge-file | Java, Maven, XML, and text semantic comparison |
| Judge family | agent-judge-llm | Spring AI-backed semantic judging |
| Judge family | agent-judge-rag | Faithfulness, contextual relevance, hallucination |
| Adapter | agent-judge-spring-ai | Spring AI ChatResponse to JudgmentContext |
| Adapter | agent-judge-langchain4j | LangChain4j Result to JudgmentContext |
| Adapter | agent-judge-koog | Koog AIAgent execution to JudgmentContext |
| Adapter | agent-judge-agent-client | CLI-delegated AgentClient response and judging backend |

Samples demonstrate adapter use but are not published Maven modules.

## Outcome Scoreboard

Progress is measured by outcomes rather than document or commit count:

| Outcome | Evidence of completion |
|---|---|
| Runtime portability | The same judge/jury corpus runs through at least two independent adapters |
| Stable judgment semantics | Status, optional score, label, error, and abstention contracts are executable in tests |
| Full implementation integrity | Clean full-reactor verification and coverage gates pass |
| Consumer usability | A downstream consumer migrates without inspecting internal implementation |
| Evidence quality | Aggregate metadata explains population, error handling, weights, and vote counts |
| Release usability | Published artifacts, documentation, license metadata, and SBOM evidence agree |

## Near-Term Direction

1. Complete normalized-Judgment full-reactor verification.
2. Publish the consumer migration handoff and update downstream consumers separately.
3. Reconcile public documentation and examples for 0.14.
4. Complete the separate licensing/SBOM workstream.
5. Release 0.14 only after those gates are satisfied.

## Longer-Term Opportunities

These are candidates, not commitments:

- structured output for AI-backed judges;
- asynchronous or batch evaluation;
- additional quorum or threshold policies when demanded by real consumers;
- richer dataset orchestration through integration with a complementary experiment system;
- verdict visualization in a separate UI;
- optional trace enrichment from agent-journal.

Each opportunity requires an explicit consumer and stopping condition before it becomes roadmap work.

## Boundaries

### Agent Judge versus Agent Journal

Agent Judge owns evaluation inputs and findings. Agent Journal owns narrative traces of what happened
step by step. A judge may consume selected trace-derived facts through JudgmentContext metadata, but
Agent Judge does not become a trace store.

### Agent Judge versus Agent Runtime

An agent whose job is judging belongs with the runtime that executes agents. Agent Judge owns the
Judgment contract, judge composition, and adapters.

### Agent Judge versus Experiment Platform

Dataset management, run persistence, dashboards, and human-review workflows are separate products.
Agent Judge should integrate with them by emitting durable verdict data rather than embedding them.

## Non-Goals

- Owning agent execution or CLI lifecycle.
- Capturing private reasoning chains or full conversation histories.
- Providing a hosted evaluation service in this repository.
- Building framework-specific evaluation semantics into the core.
- Treating numeric scores as universal substitutes for categorical or status outcomes.
- Guaranteeing semantic equivalence between arbitrary evaluators that happen to return the same score.

## License Position

The project uses the customized source license contained in LICENSE. It must not be described in
machine-readable metadata as canonical SPDX BUSL-1.1 unless the text becomes identical to that
standard license. Historical Apache-licensed releases and files are recorded separately.

The licensing and SBOM implementation is a separate due-diligence workstream; it does not change the
product boundary described here.
