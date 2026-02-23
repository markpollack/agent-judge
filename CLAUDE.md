# CLAUDE.md

## CRITICAL: Running Claude CLI Subprocesses from Claude Code

Claude Code 2.1.39+ blocks nested `claude` invocations via process tree detection. Any command that spawns `claude` CLI as a subprocess will fail silently with empty output.

**Use `~/scripts/claude-run.sh`** to run builds that invoke Claude CLI:
```bash
~/scripts/claude-run.sh ./mvnw test -pl some-module -Dtest="SomeIT"
```
The script uses `systemd-run` to escape the process tree. Works for Maven/Gradle builds where claude is invoked indirectly via the SDK. See `~/scripts/claude-run.sh` for details.

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build and Development Commands

### Basic Commands
- `./mvnw clean compile` - Compile all modules
- `./mvnw clean test` - Run unit tests
- `./mvnw clean verify` - Run full build including tests
- `./mvnw clean install` - Install artifacts to local repository

### Code Quality
- Code formatting is enforced via `spring-javaformat-maven-plugin`
- Formatting validation runs during the `validate` phase
- Use Spring's Java code formatting conventions

### MANDATORY: Java Formatting Before Commits
- **ALWAYS run `./mvnw spring-javaformat:apply` before any commit**
- CI will fail if formatting violations are found

### Git Commit Guidelines
- **NEVER add Claude Code attribution** in commit messages
- Keep commit messages clean and professional

## Architecture Overview

### Multi-Module Maven Project Structure

```
spring-ai-judge/
├── spring-ai-judge-core/     # Core Judge API (zero external deps)
├── spring-ai-judge-exec/     # Command execution judges (zt-exec)
├── spring-ai-judge-llm/      # LLM-powered judges (spring-ai-client-chat)
├── spring-ai-judge-agent/    # Agent-as-judge (bridge interface)
├── spring-ai-judge-advisor/  # AgentClient advisors (spring-ai-agent-client)
├── spring-ai-judge-bom/      # BOM for dependency management
└── plans/                    # Project roadmap
```

### Key Design Patterns

**Core Abstraction Hierarchy:**
- `Judge` - Core functional interface for all judges
- `DeterministicJudge` - Rule-based evaluation base class
- `LLMJudge` - Template method pattern for LLM-powered evaluation
- `AgentJudge` - Delegates evaluation to AI agents

**Result Chain:**
- `Judgment` - Contains Score, status, reasoning, and checks
- `Score` - Sealed interface (BooleanScore, NumericalScore, CategoricalScore)
- `JudgmentContext` - Builder pattern for evaluation context

**Jury System:**
- `Jury` - Interface for multi-judge voting
- `SimpleJury` - Parallel execution with flexible voting strategies
- `CascadedJury` - Sequential tiered execution with policy-based stop/escalate. Each tier is a `TierConfig` (name, judges, `TierPolicy`). Policies: `REJECT_ON_ANY_FAIL` (fail-fast guardrail), `ACCEPT_ON_ALL_PASS` (consensus gate), `FINAL_TIER` (always runs, provides final verdict). `Verdict.subVerdicts()` gives per-tier execution trace.
- `VotingStrategy` - Interface for aggregation (majority, weighted, consensus)
- `Verdict` - Aggregated + individual judgments

### Package Structure
- `org.springaicommunity.judge.*` - Core Judge API
- `org.springaicommunity.judge.context` - Judgment context
- `org.springaicommunity.judge.result` - Judgment results
- `org.springaicommunity.judge.score` - Score types
- `org.springaicommunity.judge.jury` - Jury voting system
- `org.springaicommunity.judge.exec` - Execution judges
- `org.springaicommunity.judge.llm` - LLM-powered judges
- `org.springaicommunity.judge.agent` - Agent-as-judge
- `org.springaicommunity.judge.advisor` - AgentClient advisors

### Bridge Interface Pattern
The `spring-ai-judge-agent` module uses `JudgeAgentClient` as a bridge interface, allowing users to adapt their agent clients without hard dependencies:

```java
public interface JudgeAgentClient {
    JudgeAgentResponse execute(String goal, Path workspace);
}
```

## Process Management

### Use zt-exec for Process Execution
The `spring-ai-judge-exec` module uses zt-exec for robust process management:

```java
import org.zeroturnaround.exec.ProcessExecutor;

ProcessResult result = new ProcessExecutor()
    .command("mvn", "test")
    .timeout(5, TimeUnit.MINUTES)
    .readOutput(true)
    .execute();
```

## Testing

- Unit tests for all core abstractions
- Integration tests using mocked dependencies
- JaCoCo coverage enforced (80% line, 75% branch)
