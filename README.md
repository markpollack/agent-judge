# Agent Judge

Agent Judge is a Java library for verifying what an AI agent did: judges check executable evidence against a goal, and juries combine their judgments into a verdict.
It is for teams building JVM agents, and it works the same whether the result came from Spring AI, LangChain4j, Koog, AgentClient or a custom runtime.

Judges answer one question: did this execution satisfy its goal, and what evidence supports that conclusion?
They check an explicit definition of done with executable evidence and independent checks, not resemblance to one reference answer.
The [research foundations](https://lab.pollack.ai/docs/agent-judge/research-foundations) explain why.

## Install

Requires Java 21.

```xml
<dependency>
    <groupId>io.github.markpollack</groupId>
    <artifactId>agent-judge-core</artifactId>
    <version>0.17.0</version>
</dependency>
```

Add only the judge-family and runtime-bridge modules you need; all modules share one version.
The `io.github.markpollack:agentworks-bom` also manages it.

## Quick start

A deterministic judge and a scored judge, both required, so the jury is conjunctive:

```java
JudgmentContext context = JudgmentContext.builder()
    .goal("Add a HelloController class")
    .workspace(Path.of("my-project"))
    .status(ExecutionStatus.SUCCESS)
    .startedAt(Instant.now())
    .executionTime(Duration.ofSeconds(5))
    .build();

// A deterministic judge checks evidence in the workspace.
Judge controllerExists = Judges.named(
    new FileExistsJudge("src/main/java/com/example/HelloController.java"),
    "controller-exists", "Controller file created");

// A scored judge states its pass mark; the score is kept beside the outcome.
Judge coverage = Judges.named(
    ctx -> Judgment.scored(0.82).passingAt(0.80).reasoning("82% line coverage").build(),
    "coverage", "Line coverage at least 80%");

SimpleJury jury = SimpleJury.builder()
    .judge(controllerExists)
    .judge(coverage)
    .votingStrategy(new AllMustPassStrategy())
    .build();

Verdict verdict = jury.vote(context);
System.out.println("Overall: " + verdict.aggregated().status());
verdict.individualByName().forEach((name, judgment) ->
    System.out.println(name + ": " + judgment.status() + " score=" + judgment.score()));
```

Voting strategies such as `MajorityVotingStrategy` and `ConsensusStrategy` are for several independent estimates of the same property.
[Tutorial module 08](https://github.com/markpollack/agent-judge-tutorial/blob/main/module-08-jury/src/main/java/io/github/markpollack/judge/tutorial/module08/JuryDemo.java) shows when to use them.

## Result model

Every `Judgment` records a required outcome, which is `PASS`, `FAIL`, `ABSTAIN`, `NOT_APPLICABLE` or `ERROR`.
It can also carry a normalized score, a classification label and a countable reason code.
These are independent facts. An abstention is not a failing vote, an error is not a negative finding, and a status-only pass stores no score.

`ABSTAIN` means the question applied and has no answer yet.
`NOT_APPLICABLE` means the question should not have been asked, so the criterion is excluded from the denominator and counted separately.
`ERROR` means the instrument never reached a finding, and it always carries a `JudgmentReasonCode`.
A failure of the library's own machinery is never turned into a rejection of the subject, under any error policy.

A `Verdict` records where each judgment sat and what produced the aggregate, so a stored result can be read without knowing how the jury was built.
[Interpreting verdicts](https://lab.pollack.ai/docs/agent-judge/interpreting-verdicts) covers seats, decisions and composite juries.

## Modules

| Module | Responsibility |
|---|---|
| `agent-judge-core` | `JudgmentContext`, `Judgment`, judges, juries, verdicts and voting strategies |
| `agent-judge-ai-core` | Framework-neutral prompt, model and classifier support for AI-backed judges |
| `agent-judge-exec` | Command, build, class-version and coverage judges |
| `agent-judge-file` | Java, Maven, XML and text semantic comparison |
| `agent-judge-llm` | Spring AI-backed semantic judges |
| `agent-judge-rag` | Faithfulness, contextual-relevance and hallucination judges |
| `agent-judge-spring-ai` | Evaluated-side `ChatResponse` bridge |
| `agent-judge-langchain4j` | Evaluated-side `Result<T>` bridge |
| `agent-judge-koog` | Evaluated-side Koog `AIAgent` bridge |
| `agent-judge-agent-client` | Evaluated-side AgentClient bridge and AgentClient judging backend |

`agent-judge-core` depends only on Jackson Databind, the SLF4J API and JSpecify annotations.
It pulls in no agent framework, model provider, dependency-injection container or hosted evaluation service.

## Documentation

- [Documentation](https://lab.pollack.ai/docs/agent-judge), starting with [Getting started](https://lab.pollack.ai/docs/agent-judge/getting-started)
- [Agent Judge Tutorial](https://github.com/markpollack/agent-judge-tutorial): credential-free, runnable Maven modules
- [Releases](https://github.com/markpollack/agent-judge/releases) and the [0.17.0 release notes](RELEASE_NOTES_0.17.0.md)
- API Javadoc ships with each release on Maven Central

## Building from source

```bash
./mvnw clean verify
```

[AGENTS.md](AGENTS.md) lists the other build profiles and the project's hard rules.

## License

The current source tree is licensed under the Business Source License 1.1 with the project-specific terms in [LICENSE](LICENSE).
Releases through 0.9.1, published under the former `org.springaicommunity` coordinates, were licensed under [Apache License 2.0](LICENSE-APACHE.txt); 0.9.2 is the first release under the Business Source License.
Previously published Apache-licensed releases retain their original terms; `LICENSE-APACHE.txt` is preserved only as that release-history record and is not a second license for the current source tree.
