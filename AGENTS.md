# Agent Judge Agent Instructions

Agent Judge is a Java 21 library of judges, juries and verdicts that verify agent output against executable evidence.

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
| `agent-judge-core` | `JudgmentContext`, `Judge`, `Judgment`, juries, voting strategies, `Verdict` |
| `agent-judge-ai-core` | Framework-neutral prompt, model and classifier support for AI-backed judges |
| `agent-judge-exec` | Command, build, class-version and coverage judges |
| `agent-judge-file` | Java, Maven, XML and text semantic comparison |
| `agent-judge-llm` | Spring AI-backed semantic judges |
| `agent-judge-rag` | Faithfulness, contextual-relevance and hallucination judges |
| `agent-judge-spring-ai`, `-langchain4j`, `-koog` | Evaluated-side bridges from each runtime into `JudgmentContext` |
| `agent-judge-agent-client` | Evaluated-side AgentClient bridge and AgentClient judging backend |
| `adapter-conformance` | Shared test sources compiled into every bridge module; not a Maven module |

## Architecture

- Dependencies point at `agent-judge-core`. `llm` and `agent-client` build on `ai-core`; `rag` builds on `llm`.
- `agent-judge-core` is framework-neutral: Jackson Databind, SLF4J API and JSpecify only. No agent
  framework, model provider, DI container or hosted evaluation service may enter it.
- A `Judgment` has one required outcome (`PASS`, `FAIL`, `ABSTAIN`, `NOT_APPLICABLE`, `ERROR`) plus
  optional score, label and reason code, as independent facts. `ABSTAIN` and `ERROR` are not failures.
- A `Verdict` is the stored wire result. It carries seats, decision and composite attempts, never
  runtime exceptions.

## Hard rules

- Library code must not depend on the thread context classloader, and must resolve external inputs
  eagerly on the caller's thread. Juries run judges on pool threads whose context classloader is
  not the application's, so a lazy lookup fails only in production.
- A judge's failure must not escape the jury that configured it; convert it to an `ERROR` judgment
  so `ErrorPolicy` governs it. An escaping exception discards every other judge in the jury and
  collapses the enclosing cascade tier.
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
