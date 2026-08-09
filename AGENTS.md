# Agent Judge Agent Instructions

## Steward Binding

Agentic planning and review state is authoritative in the private checkout
`/home/mark/projects/agent-judge-steward`. Before planning or executing a roadmap step, read its
`BINDING.md`, `plans/VISION.md`, `plans/DESIGN.md`, and `plans/ROADMAP.md`.

This public repository owns code, tests, Maven builds, releases, public documentation, and shipped
contracts. This `AGENTS.md` file is the tracked bridge to the private steward. The ignored `plans/`
transition inventory here is not planning authority and remains only until the current closure round
ends. The current action is declared only in the steward's `AGENTS.md`; do not cache transient
steward state in this file.

## Build and Development Commands

- `./mvnw clean compile` - Compile all modules
- `./mvnw clean test` - Run unit tests
- `./mvnw clean verify` - Run full build including tests
- `./mvnw clean install` - Install artifacts to local repository

### Git Commit Guidelines
- **NEVER add Claude Code attribution** in commit messages
- Keep commit messages clean and professional

## Architecture Overview

### Coordinates

- **GroupId**: `io.github.markpollack`
- **Version**: `0.14.0-SNAPSHOT`
- **License**: Customized source license; see `LICENSE`
- **Repo**: `github.com/markpollack/agent-judge`

### Multi-Module Maven Project Structure

```
agent-judge/
├── agent-judge-core/           # Framework-neutral Judge/Jury API
├── agent-judge-ai-core/        # AI-backed judge infra (JudgeModel, PromptTemplate, Classifier)
├── agent-judge-exec/           # Command execution judges (agent-sandbox)
├── agent-judge-file/           # File comparison judges (JavaParser, Maven Model)
├── agent-judge-llm/            # LLM-powered judges (Spring AI ChatClient) + SpringAiJudgeModel
├── agent-judge-rag/            # RAG evaluation judges (faithfulness, relevance, hallucination)
├── agent-judge-spring-ai/      # Spring AI ChatResponse evaluated-side bridge
├── agent-judge-langchain4j/    # LangChain4j Result<T> bridge
├── agent-judge-koog/           # Koog AIAgent bridge
├── agent-judge-agent-client/   # AgentClient CLI-agent bridge + AgentClientJudgeModel
└── plans/                      # Project roadmap (gitignored)
```

### Key Design Patterns

**Core Abstraction Hierarchy:**
- `Judge` - Core functional interface for all judges
- `DeterministicJudge` - Rule-based evaluation base class
- `LLMJudge` - Template method pattern for LLM-powered evaluation
- `ModelBackedJudge` - Composed builder for AI-backed judges (no subclassing)

**Result Chain:**
- `Judgment` - Required status, optional normalized score, optional label, reasoning, checks, and metadata
- `JudgmentStatus` - PASS, FAIL, ABSTAIN, ERROR with stable lower-case wire names
- Scores are normalized to `[0,1]` before storage; the former sealed Score hierarchy was removed in 0.14
- `JudgmentContext` - Builder pattern for evaluation context

**Jury System:**
- `Jury` - Interface for multi-judge voting
- `SimpleJury` - Parallel execution with flexible voting strategies
- `CascadedJury` - Sequential tiered execution with policy-based stop/escalate. Each tier is a `TierConfig` (name, judges, `TierPolicy`). Policies: `REJECT_ON_ANY_FAIL` (fail-fast guardrail), `ACCEPT_ON_ALL_PASS` (consensus gate), `FINAL_TIER` (always runs, provides final verdict). `Verdict.subVerdicts()` gives per-tier execution trace.
- `VotingStrategy` - Interface for aggregation (majority, weighted, consensus, median)
- `Verdict` - Aggregated + individual judgments

### Package Structure
- `io.github.markpollack.judge` - Core Judge API
- `io.github.markpollack.judge.config` - Judge configuration
- `io.github.markpollack.judge.context` - Judgment context
- `io.github.markpollack.judge.result` - Judgment results
- `io.github.markpollack.judge.jury` - Jury voting system
- `io.github.markpollack.judge.fs` - File system judges (FileExistsJudge, FileContentJudge, SupersetDiffJudge)
- `io.github.markpollack.judge.exec` - Execution judges (BuildSuccessJudge, CommandJudge, ClassVersionJudge)
- `io.github.markpollack.judge.exec.util` - Maven build/test runners
- `io.github.markpollack.judge.coverage` - Coverage judges (CoveragePreservationJudge, CoverageImprovementJudge)
- `io.github.markpollack.judge.file` - File comparison judges (JavaSemanticJudge, MavenSemanticJudge, XmlSemanticJudge, TextFileJudge)
- `io.github.markpollack.judge.file.comparator` - Semantic comparators
- `io.github.markpollack.judge.llm` - LLM-powered judges (CorrectnessJudge, LLMJudge, SpringAiJudgeModel)
- `io.github.markpollack.judge.ai` - AI-backed judge infrastructure (ModelBackedJudge, JudgmentClassifier, LabelJudgmentClassifier)
- `io.github.markpollack.judge.ai.model` - Model abstraction (JudgeModel, JudgeModelRequest, JudgeModelResponse)
- `io.github.markpollack.judge.ai.prompt` - Prompt templates (JudgePromptTemplate, JudgeTemplateRenderer, TextSource)

### Process Execution

The `agent-judge-exec` module uses `agent-sandbox-core` (io.github.markpollack) for sandboxed process execution via the `Sandbox` interface.

### Key Dependencies

- Spring AI 2.0.0 / Spring Boot 4.0.7 / Spring Framework 7.0.2
- agent-sandbox-core 0.9.3 (for exec module)
- JavaParser 3.26.3 / Maven Model 3.9.6 (for file module)
- Java 21

## Testing

- Unit tests for all core abstractions
- JaCoCo coverage enforced in agent-judge-core (80% line, 75% branch)

## Java Quality Practices

The canonical Java engineering standard is
`/home/mark/projects/agento-forge/guides/java-library-quality.md`. Read its applicable section before
changing quality infrastructure. JSpecify annotations alone do not enforce nullness; `@NullMarked`
adoption must use a build-breaking checker such as NullAway at `ERROR` and must be validated with a
deliberate failing case.

## Documentation

Canonical project documents:
- `/home/mark/projects/agent-judge-steward/plans/VISION.md` - product purpose, boundaries, and outcome scoreboard
- `/home/mark/projects/agent-judge-steward/plans/DESIGN.md` - current architecture and compatibility policy
- `/home/mark/projects/agent-judge-steward/plans/ROADMAP.md` - sole active roadmap

Normalized-Judgment work (0.14 contract closure active; consumer migration not yet performed):
- `/home/mark/projects/agent-judge-steward/plans/reviews/aj-014-closure/` - frozen candidate, received review, and adjudication ledger
- `/home/mark/projects/agent-judge-steward/plans/learnings/LEARNINGS.md` - compact cross-step context
- `consumer-handoff-normalized-judgment.md` - 0.13 to 0.14 migration guide for consumers


Diataxis-based documentation at `~/projects/docs/docs/agent-judge/`.
Diataxis taxonomy reference: `~/projects/agento-forge/concepts/documentation-taxonomy.md`.
Diataxis research brief: `plans/inbox/diataxis-documentation-research-brief.md`.

Files:
- `getting-started.mdx` - Quick start guide
- `tutorial.mdx` - Build an evaluation pipeline
- `built-in-judges.mdx` - Catalog of all judges
- `jury-system.mdx` - Jury composition and voting strategies
- `custom-judge.mdx` - Writing custom judges
- `api-reference.mdx` - Complete type signatures
- `design-philosophy.mdx` - Design rationale
