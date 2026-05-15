package io.github.markpollack.judge.langchain4j;

import java.util.List;

import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.tool.ToolExecution;
import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.context.JudgmentContext;
import io.github.markpollack.judge.result.Judgment;
import io.github.markpollack.judge.result.JudgmentStatus;
import io.github.markpollack.judge.score.BooleanScore;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Demo test showing the end-to-end LangChain4j evaluation flow.
 * This is the CI-runnable version of the samples/langchain4j-evaluation/ README.
 */
class LangChain4jEvaluationDemoTest {

	@Test
	void langchain4jResultEvaluatedWithJudge() {
		// A simple judge that checks the answer is non-empty and mentions the question topic
		Judge relevanceCheck = (JudgmentContext ctx) -> {
			String output = ctx.agentOutput().orElse("");
			boolean relevant = !output.isEmpty() && output.toLowerCase().contains("spring boot");
			return Judgment.builder()
				.score(new BooleanScore(relevant))
				.status(relevant ? JudgmentStatus.PASS : JudgmentStatus.FAIL)
				.reasoning(relevant ? "Answer addresses Spring Boot" : "Answer does not address Spring Boot")
				.build();
		};

		// Simulate a LangChain4j service call that returns a Result
		Judgment judgment = LangChain4jEvaluator.evaluate("What is Spring Boot?", goal -> Result.<String>builder()
			.content("Spring Boot is a framework that simplifies creating production-ready Spring applications.")
			.finishReason(FinishReason.STOP)
			.tokenUsage(new TokenUsage(50, 30))
			.toolExecutions(List.of())
			.build(), relevanceCheck);

		assertThat(judgment.status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(judgment.reasoning()).isEqualTo("Answer addresses Spring Boot");
	}

	@Test
	void langchain4jResultWithToolExecutionsPreservesMetadata() {
		Judge anyJudge = (JudgmentContext ctx) -> {
			// Verify tool executions are accessible in metadata
			@SuppressWarnings("unchecked")
			List<ToolExecution> tools = (List<ToolExecution>) ctx.metadata().get("langchain4j.toolExecutions");
			boolean hasTools = tools != null && !tools.isEmpty();
			return Judgment.builder()
				.score(new BooleanScore(hasTools))
				.status(hasTools ? JudgmentStatus.PASS : JudgmentStatus.FAIL)
				.reasoning(hasTools ? "Tool executions captured" : "No tool executions found")
				.build();
		};

		ToolExecution toolExec = ToolExecution.builder()
			.request(dev.langchain4j.agent.tool.ToolExecutionRequest.builder()
				.name("searchDocs")
				.arguments("{\"query\": \"Spring Boot\"}")
				.build())
			.result("Found 3 relevant docs")
			.build();

		Judgment judgment = LangChain4jEvaluator.evaluate("Search for Spring Boot docs",
				goal -> Result.<String>builder()
					.content("Based on the docs...")
					.finishReason(FinishReason.STOP)
					.toolExecutions(List.of(toolExec))
					.build(),
				anyJudge);

		assertThat(judgment.status()).isEqualTo(JudgmentStatus.PASS);
	}

}
