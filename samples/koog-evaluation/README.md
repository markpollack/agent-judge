# Koog Evaluation Sample

Demonstrates evaluating a JetBrains Koog agent with agent-judge.

## Java

```java
import ai.koog.agents.core.agent.AIAgent;
import io.github.markpollack.judge.koog.KoogEvaluator;
import io.github.markpollack.judge.rag.FaithfulnessJudge;
import io.github.markpollack.judge.result.Judgment;

// Build a Koog agent (your existing agent code)
var agent = AIAgent.builder()
    .promptExecutor(promptExecutor)
    .llmModel(model)
    .<String, String>functionalStrategy("assistant", (ctx, input) -> {
        // Your agent logic here
        return ctx.llm().writeSession().requestLLM(input).content();
    })
    .build();

// One-liner evaluation
Judgment judgment = KoogEvaluator.evaluate(agent, "Explain dependency injection", faithfulnessJudge);

System.out.println(judgment.status());    // PASS or FAIL
System.out.println(judgment.reasoning()); // Why
```

## Kotlin

```kotlin
import ai.koog.agents.core.agent.AIAgent
import io.github.markpollack.judge.koog.KoogEvaluator
import io.github.markpollack.judge.rag.FaithfulnessJudge

// Build agent with Kotlin DSL
val agent = AIAgent.builder()
    .promptExecutor(promptExecutor)
    .llmModel(model)
    .functionalStrategy<String, String>("assistant") { ctx, input ->
        ctx.llm().writeSession().requestLLM(input).content()
    }
    .build()

// Same Java bridge, callable from Kotlin
val judgment = KoogEvaluator.evaluate(agent, "Explain dependency injection", faithfulnessJudge)

println("${judgment.status()}: ${judgment.reasoning()}")
```

## Dependencies

```xml
<dependencies>
    <dependency>
        <groupId>io.github.markpollack</groupId>
        <artifactId>agent-judge-koog</artifactId>
        <version>0.10.0</version>
    </dependency>
    <dependency>
        <groupId>io.github.markpollack</groupId>
        <artifactId>agent-judge-rag</artifactId>
        <version>0.10.0</version>
    </dependency>
    <dependency>
        <groupId>ai.koog</groupId>
        <artifactId>koog-agents-jvm</artifactId>
        <version>0.8.0</version>
    </dependency>
</dependencies>
```
