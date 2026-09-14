# Agent Judge

Agent Judge is the portable verification layer for JVM agent systems.
It evaluates agent output and workspace evidence with the same judges, juries, and policies whether the result came from Spring AI, LangChain4j, Koog, AgentClient, or a custom runtime.

Judges answer one question: did this execution satisfy its goal, and what evidence supports that conclusion?

## Research foundations

Agent Judge evaluates whether work satisfies an explicit definition of done, using executable evidence and independent checks—not whether it resembles one reference answer.

[Read the research foundations](https://lab.pollack.ai/docs/agent-judge/research-foundations) for the motivating migration experiment, five design principles, their current implementation in Agent Judge, and the complete source map.

## Result model

Every `Judgment` records a required outcome—`PASS`, `FAIL`, `ABSTAIN`, `NOT_APPLICABLE`, or `ERROR`—plus an optional normalized score, an optional classification label, and an optional countable reason code.
These are independent facts: an abstention is not a failing vote, an error is not a negative finding, and a status-only pass does not manufacture a stored score.

The difference between the last three outcomes is what a denominator does with them.
`ABSTAIN` means the question applied and has no answer yet; `NOT_APPLICABLE` means the question should not have been asked, so the criterion is excluded from the denominator and counted separately; `ERROR` means the instrument never reached a finding.
A judge may only exclude a subject where it declared in advance that it can—an undeclared exclusion is contained as an error rather than honoured.

Every `ERROR` carries a `JudgmentReasonCode`, because an instrument failure nobody can count is a failure nobody fixes.
A failure the library's own machinery produced is never converted into a rejection of the subject, under any error policy.

Result metadata is recursively immutable and restricted to ordinary JSON-compatible values.
Token usage preserves independently reported input, output, reasoning, cache-creation, cache-read, and total quantities; pricing is a downstream derivation.

A `Verdict` records where each judgment sat (`seats`) and what produced its aggregate (`decision`), so a stored composite result can be read correctly without knowing how the jury was built.
Composite juries return complete ordered execution evidence in `Verdict.compositeAttempts()`.
Each named attempt says whether its parent could use what the stage returned, and contains exactly one returned child verdict or one stable code-only failure, so
callers can distinguish a negative finding from a stage that did not execute successfully—even when a later stage succeeds.
`CompositePaths.flatten(verdict)` derives deterministic RFC 6901 paths across nested meta-juries and
cascades without placing paths or runtime exceptions in the wire result.

## Modules

| Module | Responsibility |
|---|---|
| `agent-judge-core` | `JudgmentContext`, `Judgment`, judges, juries, verdicts, and voting strategies |
| `agent-judge-ai-core` | Framework-neutral prompt, model, and classifier infrastructure for AI-backed judges |
| `agent-judge-exec` | Command, build, class-version, and coverage judges |
| `agent-judge-file` | Java, Maven, XML, and text semantic comparison |
| `agent-judge-llm` | Spring AI-backed semantic judging |
| `agent-judge-rag` | Faithfulness, contextual relevance, and hallucination judges |
| `agent-judge-spring-ai` | Evaluated-side `ChatResponse` bridge |
| `agent-judge-langchain4j` | Evaluated-side `Result<T>` bridge |
| `agent-judge-koog` | Evaluated-side Koog `AIAgent` bridge |
| `agent-judge-agent-client` | Evaluated-side AgentClient bridge and AgentClient judging backend |

`agent-judge-core` is framework-neutral, not dependency-free.
It uses Jackson Databind, SLF4J API, and compile-scope JSpecify annotations; no core dependency is an agent framework, model provider, dependency-injection container, or hosted evaluation service.

## Install

```xml
<dependency>
    <groupId>io.github.markpollack</groupId>
    <artifactId>agent-judge-core</artifactId>
    <version>0.14.0</version>
</dependency>
```

Add only the judge-family and runtime-bridge modules your application needs.
All published modules use the same version.

## Quick start

This example is maintained as compiled source in [Tutorial module 04](https://github.com/markpollack/agent-judge-tutorial/blob/main/module-04-simple-jury/src/main/java/io/github/markpollack/judge/tutorial/module04/SimpleJuryDemo.java):

```java
Path workspace = Path.of("test-workspace");
String controllerPath = "src/main/java/com/example/HelloController.java";

JudgmentContext context = JudgmentContext.builder()
    .goal("Add a HelloController class")
    .workspace(workspace)
    .status(ExecutionStatus.SUCCESS)
    .startedAt(Instant.now())
    .executionTime(Duration.ofSeconds(5))
    .build();

Judge fileExists = Judges.named(
    new FileExistsJudge(controllerPath),
    "file-exists", "Controller file created");

Judge hasMethod = Judges.named(
    new FileContentJudge(controllerPath, "hello",
        FileContentJudge.MatchMode.CONTAINS),
    "has-method", "Contains hello method");

Judge hasPom = Judges.named(
    new FileExistsJudge("pom.xml"),
    "has-pom", "Maven project file exists");

SimpleJury majorityJury = SimpleJury.builder()
    .judge(fileExists, 1.0)
    .judge(hasMethod, 1.0)
    .judge(hasPom, 1.0)
    .votingStrategy(new MajorityVotingStrategy())
    .parallel(true)
    .build();

Verdict majorityVerdict = majorityJury.vote(context);

System.out.println("Overall: " + majorityVerdict.aggregated().status());
```

## Executable examples and documentation

The [Agent Judge Tutorial](https://github.com/markpollack/agent-judge-tutorial) is the canonical executable sample repository.
Its ten credential-free Maven modules cover core judging, composition, juries, custom judges, model-backed judges, Koog, and LangChain4j.

The narrative guides—writing judges, describing juries, the normalized-`Judgment` handoff, and the migration guides—live on the documentation site.
This repository keeps the code, the API Javadoc, and the release notes.

- [Getting started](https://lab.pollack.ai/docs/agent-judge/getting-started)
- [Documentation](https://lab.pollack.ai/docs/agent-judge)
- [Tutorial source](https://github.com/markpollack/agent-judge-tutorial)
- [0.17 release notes](RELEASE_NOTES_0.17.0.md)
- [0.16 release notes](RELEASE_NOTES_0.16.0.md)

## License

The current source tree is licensed under the Business Source License 1.1 with the project-specific terms in [LICENSE](LICENSE).
Releases through 0.9.1, published under the former `org.springaicommunity` coordinates, were licensed under [Apache License 2.0](LICENSE-APACHE.txt); 0.9.2 is the first release under the Business Source License.
Previously published Apache-licensed releases retain their original terms; `LICENSE-APACHE.txt` is preserved only as that release-history record and is not a second license for the current source tree.
