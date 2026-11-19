# Agent Eval Agent Instructions

Agent Eval is a Java 21 library of configured judges, juries and retained verdicts that evaluate requirements over evidence. It was formerly Agent Judge; the repository, groupId, artifact IDs and Java packages keep that name.

## AI Development Laws

> Reality first. Evidence sufficient to trust it. Infrastructure only when necessary.

1. **Maintain Contact With Reality.** Prefer the shortest credible path to observing real application behavior and completing the assigned engineering objective. Use established development environments, dependencies, tools and workflows unless the objective specifically requires isolation. Treat reproducibility and evidence integrity as constraints on the assigned work. Distinguish apparatus failure from failure of the engineering objective. Do not spend more effort perfecting the apparatus than learning from the experiment.
2. **Objective Is Sovereign.** The assigned objective, acceptance criteria and scope govern the work. Do not substitute infrastructure improvements or unrelated engineering for the requested outcome.
3. **Discovery Is Not Authorization.** Discovering an unrelated problem does not authorize fixing it. Report it or request authorization when necessary.
4. **Do Not Confuse Measurement With the Objective.** Measurement, recording, qualification and evaluation support real engineering work. Their failures must not automatically invalidate independently established results or trigger unrelated infrastructure projects.
5. **Stop When Done.** Once the authorized objective and acceptance criteria are satisfied, report the result and stop. Do not add speculative hardening or new work.

The shortest credible path must satisfy the actual acceptance contract, required tests, honest evidence, pinned experimental inputs and safety requirements. Experimental isolation and independent evaluation apply where the owning contract requires them.

For missing Maven release artifacts, first investigate the established dependency installation procedure. A private Maven cache needs a concrete requirement from the assigned objective.

## Steward

Private planning and control state lives in `/home/mark/projects/agent-judge-steward`. Read its
`BINDING.md` before planning or executing work.

## Build and test

- Gate before every commit: `./mvnw clean verify`. Use `./mvnw`, never `mvn`.
- Release health: `./mvnw -o javadoc:aggregate` and `./mvnw -o -Prelease -Dgpg.skip=true clean package`.
- Model-backed integration tests: `./mvnw -Pfailsafe verify` (needs `ANTHROPIC_API_KEY`, `OPENAI_API_KEY`).
- Dependency CVE audit: `./mvnw -Powasp verify`.

## Modules

| Module | Holds |
|---|---|
| `agent-judge-core` | Pure Requirements, configured `Judge`/`Jury`, Judgment, voting, retained Verdict, policy and reporting |
| `agent-judge-json-jackson2` | Explicit Jackson 2 converters, strict V6 result/evaluation storage and archival diagnostics |
| `agent-judge-assertions`, `-assertj` | Retained assertions and staged cached execution |
| `agent-judge-jev` | Typed structured Jev runtime |
| `agent-judge-ai-core` | Generated EvalModel protocol, native RFC2119/EARS requirements and capture |
| `agent-judge-exec` | Command, build, class-version and coverage judges |
| `agent-judge-file` | Java, Maven, XML and text semantic comparison |
| `agent-judge-llm` | Spring AI-backed semantic judges |
| `agent-judge-rag` | Faithfulness, contextual-relevance and hallucination judges |
| `agent-judge-spring-ai`, `-langchain4j`, `-koog` | Evaluated-side response evidence bridges |
| `agent-judge-agent-client` | Evaluated-side AgentClient bridge and AgentClient judging backend |
| `adapter-conformance` | Shared test sources compiled into every bridge module; not a Maven module |

## Architecture

- Dependencies point at `agent-judge-core`. `llm` and `agent-client` build on `ai-core`; `rag` builds on `llm`.
- `agent-judge-core` uses JDK facilities, Jackson annotations and JSpecify; JSON engines live outside core. No agent
  framework, model provider, DI container or hosted evaluation service may enter it.
- A `Judgment` has one required outcome (`PASS`, `FAIL`, `ABSTAIN`, `NOT_APPLICABLE`, `ERROR`) plus
  optional findings, support, checks and provenance, as independent facts. `ABSTAIN` and `ERROR` are not failures.
- A `Verdict` is the stored wire result. It carries seats, decision and composite attempts, never
  serialized runtime exceptions. In-memory failures retain their original causes.

- Execution is configured `Judge.judge()` / `Jury.vote()`. Pure Requirements own no runtime or policy.
- A Policy decides reliance from the complete original Verdict. Retained reads execute no producer or policy.
- Results/evaluations use strict V6; descriptions use V3. Older typed versions are explicitly refused.

## Hard rules

- Library code must not depend on the thread context classloader, and must resolve external inputs
  eagerly on the caller's thread. Juries run judges on pool threads whose context classloader is
  not the application's, so a lazy lookup fails only in production.
- Contain ordinary judge/instrument failures as ERROR while preserving available originals and native facts.
  Cancellation, interruption, fatal errors and `PreservationLimitException` must propagate, including
  asynchronous wrappers; never convert these into a negative subject finding or truncate their originals.
- A failure of the library's own machinery stays `ERROR` under every error policy, never `FAIL`.
  Otherwise a broken instrument reads as a rejected subject and consumers count it as a finding.
- Do not add SBOM generation to any POM. The release pipeline produces the certified per-module
  SBOMs from the graph a consumer resolves; in-POM output would describe the wrong graph.
- Javadoc errors break the release build. Run `./mvnw -o javadoc:aggregate` before committing API
  changes.
- Java changes follow `/home/mark/projects/agento-forge/guides/java-library-quality.md`.
- The source is under a customized Business Source License (`LICENSE`); `LICENSE-APACHE.txt` is
  release history only. Do not relicense files or add Apache headers.
- This repository is public. Commit messages carry no AI attribution, and no private planning or
  steward state goes into tracked files.

## Docs

- Documentation: https://lab.pollack.ai/docs/agent-judge
- Executable tutorial: https://github.com/markpollack/agent-judge-tutorial
- Releases: https://github.com/markpollack/agent-judge/releases and `RELEASE_NOTES_*.md`
- API source of truth: the Javadoc in the source. When the docs site disagrees, the code wins.

## When the preferred path hits a separate defect

If the preferred path in a work order runs into a defect outside its scope, aim for the smallest change that can ship
with the full gate green:

1. Where a quick check can establish it, check whether the defect also occurs on the baseline.
2. If a smaller change still meets the work order's acceptance criteria and passes the full gate, continue within the
   existing authorization, and record the separate defect with its owning repository.
3. Bring the Owner a decision instead if proceeding would change the acceptance criteria, weaken the gate, or expand
   the work into another repository.
