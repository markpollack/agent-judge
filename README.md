# Agent Judge

Framework-neutral evaluation layer for AI agent output across Spring AI, LangChain4j, Koog, and CLI agents.

Judges are like unit tests for your agent: executable checks that decide whether an agent output satisfies a goal. You wouldn't ship application code without tests or assertions; agents need the same discipline.

```
        Spring AI     LangChain4j     Koog     AgentClient
            |              |           |             |
            v              v           v             v
-----------------------------------------------------------------
                         Agent Judge
                 horizontal evaluation layer
-----------------------------------------------------------------
   Build checks, file checks, AST comparison, coverage,
   tool-use metadata checks, RAG faithfulness, hallucination,
   LLM-as-judge, juries, cascaded juries
```

## Modules

**Judge families:**

| Module | Description |
|--------|-------------|
| `agent-judge-core` | Core Judge API, juries, scoring (zero external deps) |
| `agent-judge-exec` | Build, shell, and coverage judges |
| `agent-judge-file` | AST, POM, XML, and text comparison judges |
| `agent-judge-llm` | LLM-powered judges (Spring AI ChatClient) |
| `agent-judge-rag` | RAG evaluation: faithfulness, hallucination, relevance |

**Framework/runtime bridges:**

| Module | Description |
|--------|-------------|
| `agent-judge-spring-ai` | Adapts `ChatResponse` output |
| `agent-judge-langchain4j` | Adapts `Result<T>` output |
| `agent-judge-koog` | Adapts `AIAgent` output |
| `agent-judge-agent-client` | Adapts CLI-agent responses |

## Install

```xml
<dependency>
    <groupId>io.github.markpollack</groupId>
    <artifactId>agent-judge-core</artifactId>
    <version>0.10.0</version>
</dependency>
```

Start with `agent-judge-core`, then add only the modules you need.

`agent-judge-llm` uses Spring AI to call an LLM *as a judge*; `agent-judge-spring-ai` adapts Spring AI `ChatResponse` output *for evaluation*.

## Quick Example

Abbreviated — imports omitted:

```java
JudgmentContext context = JudgmentContext.builder()
    .goal("Verify the project builds and includes a README")
    .workspace(Path.of("."))
    .build();

Judge fileCheck = new FileExistsJudge("README.md");
Judge buildCheck = BuildSuccessJudge.maven("compile");

SimpleJury jury = SimpleJury.builder()
    .judge(fileCheck)
    .judge(buildCheck, 2.0)
    .votingStrategy(new MajorityVotingStrategy())
    .build();

Verdict verdict = jury.vote(context);
```

With a framework bridge, runtime output is adapted into the same evaluation layer:

```java
Verdict verdict = KoogEvaluator.evaluate(
    agent, "Add a REST controller", jury);
```

## Documentation

Full docs — getting started, tutorials, built-in judges, jury system, API reference:

[lab.pollack.ai/projects/agent-judge](https://lab.pollack.ai/projects/agent-judge)

## License

This project originated from earlier Apache-licensed work in the Spring AI Community.

Beginning with version 0.9.2, new development is licensed under the Business Source License 1.1 (BSL). Internal enterprise use is welcome; commercial redistribution or competing hosted/managed offerings require permission.

Historical Apache-licensed portions remain available under their original terms. See [LICENSE](LICENSE) and [LICENSE-APACHE.txt](LICENSE-APACHE.txt) for details.
