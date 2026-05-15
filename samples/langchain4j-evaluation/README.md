# LangChain4j Evaluation Sample

Demonstrates evaluating a LangChain4j AI Service result with agent-judge.

## Usage

```java
import dev.langchain4j.service.Result;
import io.github.markpollack.judge.langchain4j.LangChain4jSupport;
import io.github.markpollack.judge.rag.FaithfulnessJudge;
import io.github.markpollack.judge.result.Judgment;

// Your existing LangChain4j AI Service
interface Assistant {
    Result<String> chat(String message);
}

var assistant = AiServices.builder(Assistant.class)
    .chatModel(chatModel)
    .contentRetriever(retriever)
    .build();

// One-liner evaluation
Judgment judgment = LangChain4jSupport.evaluate(
    "What are the key features of Spring Boot?",
    assistant::chat,
    faithfulnessJudge
);

System.out.println(judgment.status());    // PASS or FAIL
System.out.println(judgment.reasoning()); // Why

// The JudgmentContext captures metadata automatically:
// - langchain4j.tokenUsage: aggregate token counts
// - langchain4j.toolExecutions: all tool calls
// - langchain4j.sources: retrieved RAG content
// - langchain4j.finishReason: STOP, LENGTH, etc.
```

## Dependencies

```xml
<dependencies>
    <dependency>
        <groupId>io.github.markpollack</groupId>
        <artifactId>agent-judge-langchain4j</artifactId>
        <version>0.10.0</version>
    </dependency>
    <dependency>
        <groupId>io.github.markpollack</groupId>
        <artifactId>agent-judge-rag</artifactId>
        <version>0.10.0</version>
    </dependency>
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j</artifactId>
        <version>1.0.0</version>
    </dependency>
</dependencies>
```
