# Design: Agent Judge

> **Reviewed**: 2026-08-03
> **Status**: Current architecture; the normalized-Judgment migration is implemented and verified
> against a clean full reactor
> **Normative judgment detail**: design-normalized-judgment.md
> **Execution plan**: roadmap.md

## Design Goals

1. Keep evaluation semantics independent of agent runtimes and model providers.
2. Represent judgment outcomes without contradictory or manufactured facts.
3. Make aggregation policy explicit, testable, and explainable.
4. Support workspace and artifact evidence as first-class evaluation inputs.
5. Keep framework integrations thin and replaceable.
6. Permit AI-backed judges without coupling the core to one model SDK.
7. Produce values that can cross process and language boundaries predictably.

## System Boundary

Agent Judge begins after an agent execution has produced, or is able to produce, evidence. It ends
with a Judgment or Verdict.

    Native response / workspace / execution evidence
                         |
                  runtime adapter
                         |
                  JudgmentContext
                         |
          Judge or Jury evaluation policy
                         |
                Judgment / Verdict

Agent execution, persistence, visualization, and narrative tracing are outside this repository.

## Core Dependency Boundary

The historical claim that agent-judge-core has zero external dependencies is false for the current
implementation. Core currently depends on:

- Jackson Databind for configuration and JSON projection support;
- SLF4J API for logging.

The optional Reactor dependency was removed in 0.14 along with ReactiveJudge, which had no
implementation, test, caller, or sample in this repository. A reactive consumer can wrap Judge
directly; a dedicated adapter can be added if a real consumer asks for one. AsyncJudge is unaffected
because it uses only JDK CompletableFuture.

The enforced architectural property is narrower and more useful:

> agent-judge-core has no dependency on an agent framework, model provider, dependency-injection
> container, or hosted evaluation service.

A future dependency-reduction effort may be valuable, but it is not a current fact or a prerequisite
for framework neutrality.

## Core Value Model

### JudgmentContext

JudgmentContext is the immutable input to a Judge. It carries:

- goal;
- workspace;
- start time and execution duration;
- optional agent output;
- ExecutionStatus;
- optional execution error;
- extensible metadata.

Metadata is the adapter and domain-extension surface. Keys intended for public consumption should be
declared as constants and documented. Narrative traces do not enter metadata by default.

### Judgment

Judgment is an immutable result containing:

| Field | Contract |
|---|---|
| status | Required PASS, FAIL, ABSTAIN, or ERROR |
| score | Optional finite normalized number in [0,1] |
| label | Optional non-blank classification |
| reasoning | Human-readable explanation; required and non-blank for ABSTAIN/ERROR |
| checks | Immutable detailed findings |
| metadata | Immutable top-level map subject to the documented value algebra |

Status, score, and label are independent facts. Construction invariants forbid:

- score on ABSTAIN or ERROR;
- label on ERROR;
- NaN, infinity, or out-of-range score;
- blank label;
- blank reasoning for ABSTAIN or ERROR;
- null status, reasoning, checks, or metadata.

Intent-specific staged factories prevent incomplete score or classification construction. The compact
record constructor remains the enforcement boundary for all construction paths.

### Status Semantics

| Status | Meaning |
|---|---|
| PASS | The judge completed and accepted the subject |
| FAIL | The judge completed and rejected the subject |
| ABSTAIN | The judge reached no applicable finding |
| ERROR | The judge could not complete because evaluation failed |

A caught execution exception normally maps to ERROR. Absence of optional evidence normally maps to
ABSTAIN. Neither is silently converted into FAIL.

### Scores

A stored score is already normalized to [0,1]. Raw-range values must be normalized at construction.

effectiveScore() is a derived compatibility view:

- stored score when present;
- 1.0 for status-only PASS;
- 0.0 for status-only FAIL;
- absent for ABSTAIN and ERROR.

Consumers must not use effectiveScore() when the distinction between an actual quality measurement and
a status projection matters.

### Labels

A label records a completed classification. It can coexist with PASS, FAIL, or ABSTAIN. ABSTAIN with
a label supports classifications whose declared meaning is abstention. ERROR carries no label because
classification did not complete.

## Wire Contract

Judgment serializes as a flat JSON product:

- stable lower-case status names: pass, fail, abstain, error;
- case-sensitive parsing;
- absent score and label omitted, not emitted as null;
- stable field names;
- pinned presentation order for deterministic examples;
- no polymorphic type metadata;
- no Throwable transport.

Field order is presentational rather than semantic. Consumers must parse by field name.

The metadata portability guarantee is conditional: metadata values must themselves be JSON-safe.
Judgment does not attempt to serialize arbitrary live Java objects.

## Judge Model

Judge is a functional interface from JudgmentContext to Judgment. DeterministicJudge and specialized
judge families provide reuse, but composition is preferred over deep inheritance.

Judges may emit detailed Check values and metadata. They must use status according to the table above;
they must not manufacture a score merely to satisfy an aggregator.

## Jury Model

Jury evaluates multiple judges and returns a Verdict containing:

- aggregate Judgment;
- individual Judgments;
- configured weights;
- sub-verdicts for cascaded tiers.

SimpleJury executes a flat set. CascadedJury executes tiers using TierPolicy:

- REJECT_ON_ANY_FAIL;
- ACCEPT_ON_ALL_PASS;
- FINAL_TIER.

Cascade decisions read individual statuses where specified. Aggregate consensus semantics do not erase
the underlying individual outcomes.

## Aggregation

Five strategies are provided:

- majority;
- consensus;
- average;
- weightedAverage;
- median.

ABSTAIN is removed from the eligible population. ERROR is handled through ErrorPolicy on every
strategy:

| Policy | Effect |
|---|---|
| PROPAGATE | Aggregate becomes ERROR; default |
| TREAT_AS_FAIL | Error participates as a failing finding |
| TREAT_AS_ABSTAIN | Error is converted into abstention accounting |
| IGNORE | Error is removed from the eligible population |

IGNORE and TREAT_AS_ABSTAIN may produce the same final status but remain distinguishable through
aggregation evidence.

Consensus computes over applicable votes:

- PASS + PASS = PASS;
- FAIL + FAIL = FAIL;
- PASS + FAIL = ABSTAIN;
- explicit ABSTAIN does not vote;
- no eligible votes = ABSTAIN.

WeightedAverage rejects negative, non-finite, or all-zero configured weights. A valid configuration
whose eligible weight becomes zero after filtering returns ABSTAIN.

### Aggregation Evidence

Every aggregate contains an immutable map under reserved metadata.aggregation. It records stable
strategy and policy identifiers plus population and error-accounting facts. Strategy-specific keys add
vote counts or weight totals.

The aggregation key is reserved at Judgment construction. Strategies do not copy arbitrary input
metadata into the aggregate.

## Module Architecture

Current development contains ten modules:

    agent-judge-core
      |
      +-- agent-judge-ai-core
      |
      +-- agent-judge-exec
      +-- agent-judge-file
      +-- agent-judge-llm
      |     +-- agent-judge-rag
      |
      +-- agent-judge-spring-ai
      +-- agent-judge-langchain4j
      +-- agent-judge-koog
      +-- agent-judge-agent-client

### Core and AI Infrastructure

agent-judge-core owns evaluation values and orchestration.

agent-judge-ai-core owns framework-neutral AI-backed judge infrastructure:

    JudgmentContext
       -> JudgmentVariables
       -> JudgePromptTemplate
       -> JudgeModelRequest
       -> JudgeModel
       -> JudgeModelResponse
       -> JudgmentClassifier
       -> Judgment

ModelBackedJudge composes those parts for single-shot model-backed evaluation. Multi-step AI judges
should be expressed as workflows that ultimately produce a Judgment.

### Judge Families

- agent-judge-exec: command, build, class-version, and coverage evidence.
- agent-judge-file: Java, Maven, XML, and text semantic comparison.
- agent-judge-llm: Spring AI judging backend and semantic LLM judges.
- agent-judge-rag: faithfulness, contextual relevance, and hallucination judges.

### Framework and Runtime Adapters

All adapters follow the same design:

1. use a provided-scope runtime/framework dependency where practical;
2. convert a native response or supplied call into JudgmentContext;
3. expose a concise Judge/Jury evaluation convenience;
4. extract only facts judges can legitimately use;
5. avoid owning execution lifecycle or narrative observability;
6. test with mocks or stable fakes rather than requiring live providers.

Current adapters:

| Module | Native input |
|---|---|
| agent-judge-spring-ai | ChatResponse |
| agent-judge-langchain4j | Result<T> |
| agent-judge-koog | AIAgent execution |
| agent-judge-agent-client | AgentClientResponse and AgentClient judging backend |

## Metadata Boundary: Judge versus Journal

Adapters may extract aggregates and per-call facts such as model, token usage, tool calls, sources,
finish reason, session identifier, or response identifier.

They do not extract full intermediate conversations, private reasoning, or step-by-step execution
narrative. Those belong to agent-journal. Selected trace-derived facts can be attached by a caller
without creating a dependency between the projects.

## Extension Surfaces

Stable extension surfaces include:

- Judge implementations and lambdas;
- VotingStrategy;
- JudgeModel;
- JudgmentClassifier;
- JudgeTemplateRenderer;
- adapter-specific metadata constants;
- JudgmentContext metadata;
- workflow-backed judges that produce Judgment.

New extension points require demonstrated consumers. Do not add policy enums or generic abstraction
layers only because a future use can be imagined.

## Compatibility and Versioning

Agent Judge remains pre-1.0. Minor releases may contain source and binary breaks when correcting the
domain model, but every such break requires:

- characterization of previous behavior;
- an explicit design decision;
- a migration guide;
- release notes;
- a clean minor-version boundary.

Patch releases should remain source and binary compatible except for urgent security remediation that
cannot be delivered safely otherwise.

The normalized Judgment model is the intended contract beginning with 0.14.0. No stability promise is
made for the removed Score hierarchy. Integration modules continue to follow upstream framework
compatibility constraints.

Prompt text is not a stable API. Public metadata keys, wire field names, status wire names, and
strategy/policy identifiers are contracts and require deliberate migration when changed.

## Verification Gates

A design change is not complete until:

- a clean full Maven reactor passes;
- agent-judge-core satisfies 80% line and 75% branch coverage;
- serialization examples and round trips pass;
- status, score, label, error, abstention, and aggregation truth tables are executable;
- no stale API references remain in active source;
- provider modules actually execute their test suites;
- consumer-visible breaking changes have a written handoff.

Incremental compilation is not accepted as final evidence.

## Explicit Non-Goals

| Feature | Boundary |
|---|---|
| Agent execution lifecycle | Agent runtime or AgentClient |
| Agent-as-judge orchestration | Runtime layer using JudgeModel or workflow |
| Narrative trace storage | agent-journal |
| Dataset and experiment server | Complementary experiment product |
| Hosted UI | Separate visualization product |
| Arbitrary Java-object metadata portability | Caller responsibility |
| Semantic identity for equivalent judge programs | Not represented by Judgment |
| Framework-specific types in core | Adapter modules |

## Current Build Coordinates

- GroupId: io.github.markpollack
- Development version: 0.14.0-SNAPSHOT
- Latest release: 0.13.0
- Java: 21
- Spring AI: 2.0.0
- Spring Boot: 4.0.7
- Agent Sandbox: 0.9.3
- AgentClient: 0.20.0
- Repository: github.com/markpollack/agent-judge
- License: customized source license in LICENSE

## Related Documents

- VISION.md — product purpose and outcome scoreboard.
- roadmap.md — active execution and verification plan.
- design-normalized-judgment.md — detailed normalized Judgment contract.
- ddd-review.md — domain review evidence.
- vision-design-review.md — findings from the 2026-08-03 cold review.
