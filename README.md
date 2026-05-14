# Agent Judge

Agent-agnostic evaluation framework with deterministic, command, and LLM judges. Compose judges into juries with configurable voting strategies. Zero coupling to any specific agent implementation.

## Modules

| Module | Description |
|--------|-------------|
| `agent-judge-core` | Core Judge API and abstractions (zero external deps) |
| `agent-judge-exec` | Command execution judges (build, test, coverage) |
| `agent-judge-llm` | LLM-powered evaluation via Spring AI |
| `agent-judge-file` | File comparison judges (Java AST, Maven POM, XML, text) |

## Maven Central

```xml
<dependency>
    <groupId>io.github.markpollack</groupId>
    <artifactId>agent-judge-core</artifactId>
    <version>0.9.2</version>
</dependency>
```

Additional modules:

```xml
<artifactId>agent-judge-exec</artifactId>
<artifactId>agent-judge-llm</artifactId>
<artifactId>agent-judge-file</artifactId>
```

## Quick Example

```java
// Deterministic judge
Judge buildJudge = BuildSuccessJudge.maven("compile");

// Jury with voting
Jury jury = SimpleJury.builder()
    .name("eval")
    .judge(buildJudge)
    .judge(FileExistsJudge.of("target/classes/App.class"))
    .votingStrategy(VotingStrategy.majority())
    .build();

Verdict verdict = jury.vote(context);
```

## Documentation

Full API reference, built-in judges, jury system, and voting strategies:
https://lab.pollack.ai/projects/agent-judge

## Licensing

This project originated from earlier Apache-licensed work in the Spring AI Community.

Beginning with version 0.9.2, new development is licensed under the Business Source License 1.1 (BSL).

Historical Apache-licensed portions remain available under their original terms. See [LICENSE](LICENSE) and [LICENSE-APACHE.txt](LICENSE-APACHE.txt) for details.
