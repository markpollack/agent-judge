package io.github.markpollack.judge.langchain4j;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.tool.ToolExecution;
import dev.langchain4j.service.tool.ToolExecutionResult;
import io.github.markpollack.judge.context.ExecutionStatus;
import io.github.markpollack.judge.context.JudgmentContext;
import io.github.markpollack.judge.conformance.JudgmentContextConformance;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link LangChain4jJudgmentContextBuilder}.
 */
class LangChain4jJudgmentContextBuilderTest {

	@Test
	void shouldBuildContextFromResult() {
		Result<String> result = Result.<String>builder()
			.content("The document discusses AI evaluation")
			.finishReason(FinishReason.STOP)
			.tokenUsage(new TokenUsage(100, 50))
			.toolExecutions(List.of())
			.build();

		JudgmentContext context = LangChain4jJudgmentContextBuilder.from(result, "Summarize the document",
				Instant.now(), Duration.ofSeconds(2));

		assertThat(context.goal()).isEqualTo("Summarize the document");
		assertThat(context.status()).isEqualTo(ExecutionStatus.SUCCESS);
		assertThat(context.agentOutput()).isPresent().hasValue("The document discusses AI evaluation");
		assertThat(context.metadata()).containsKey("langchain4j.tokenUsage");
		assertThat(context.metadata()).containsEntry("langchain4j.finishReason", "STOP");
		JudgmentContextConformance.assertSuccessful(context, "Summarize the document",
				"The document discusses AI evaluation");
	}

	@Test
	void shouldExposeStableFinalResponseIdentityAndIndependentUsage() {
		ChatResponse finalResponse = ChatResponse.builder()
			.aiMessage(AiMessage.from("Done"))
			.id("lc4j-response-1")
			.modelName("gpt-5")
			.finishReason(FinishReason.STOP)
			.build();
		Result<String> result = Result.<String>builder()
			.content("Done")
			.finishReason(FinishReason.STOP)
			.tokenUsage(new TokenUsage(100, 50))
			.finalResponse(finalResponse)
			.build();

		JudgmentContext context = LangChain4jJudgmentContextBuilder.from(result, "Finish", Instant.now(),
				Duration.ofMillis(5));

		assertThat(context.metadata())
			.containsEntry("langchain4j.responseId", "lc4j-response-1")
			.containsEntry("langchain4j.model", "gpt-5")
			.containsEntry("langchain4j.usage.inputTokens", 100)
			.containsEntry("langchain4j.usage.outputTokens", 50)
			.doesNotContainKey("langchain4j.usage.totalTokens");
	}

	@Test
	void shouldKeepNativeFactsAuthoritativeOverExtraMetadata() {
		JudgmentContext context = LangChain4jJudgmentContextBuilder.execute("Hello",
				goal -> Result.<String>builder().content("Hi").finishReason(FinishReason.STOP).build(),
				Map.of("langchain4j.finishReason", "SPOOFED", "run.id", "run-1"));

		assertThat(context.metadata())
			.containsEntry("langchain4j.finishReason", "STOP")
			.containsEntry("run.id", "run-1");
	}

	@Test
	void shouldMapContentFilterToRefused() {
		Result<String> result = Result.<String>builder()
			.content("")
			.finishReason(FinishReason.CONTENT_FILTER)
			.build();

		JudgmentContext context = LangChain4jJudgmentContextBuilder.from(result, "Generate something",
				Instant.now(), Duration.ofSeconds(1));

		assertThat(context.status()).isEqualTo(ExecutionStatus.REFUSED);
	}

	@Test
	void shouldCaptureToolExecutionsInMetadata() {
		ToolExecution toolExec = ToolExecution.builder()
			.request(dev.langchain4j.agent.tool.ToolExecutionRequest.builder()
				.name("searchWeb")
				.arguments("{\"query\": \"agent evaluation\"}")
				.build())
			.result(ToolExecutionResult.builder().resultText("Found 5 results").build())
			.invocationContext(InvocationContext.builder().build())
			.build();

		Result<String> result = Result.<String>builder()
			.content("Based on search results...")
			.finishReason(FinishReason.STOP)
			.toolExecutions(List.of(toolExec))
			.build();

		JudgmentContext context = LangChain4jJudgmentContextBuilder.from(result, "Search and summarize",
				Instant.now(), Duration.ofSeconds(3));

		assertThat(context.metadata()).containsKey("langchain4j.toolExecutions");
		@SuppressWarnings("unchecked")
		List<ToolExecution> tools = (List<ToolExecution>) context.metadata().get("langchain4j.toolExecutions");
		assertThat(tools).hasSize(1);
	}

	@Test
	void shouldExecuteServiceCallAndCaptureContext() {
		JudgmentContext context = LangChain4jJudgmentContextBuilder.execute("Hello", goal -> Result.<String>builder()
			.content("Hi there!")
			.finishReason(FinishReason.STOP)
			.build());

		assertThat(context.goal()).isEqualTo("Hello");
		assertThat(context.status()).isEqualTo(ExecutionStatus.SUCCESS);
		assertThat(context.agentOutput()).isPresent().hasValue("Hi there!");
		assertThat(context.startedAt()).isNotNull();
		assertThat(context.executionTime()).isGreaterThanOrEqualTo(Duration.ZERO);
	}

	@Test
	void shouldCaptureExceptionFromServiceCall() {
		RuntimeException failure = new RuntimeException("Service unavailable");
		JudgmentContext context = LangChain4jJudgmentContextBuilder.execute("Fail please", goal -> {
			throw failure;
		});

		assertThat(context.status()).isEqualTo(ExecutionStatus.FAILED);
		assertThat(context.error()).isPresent()
			.hasValueSatisfying(e -> assertThat(e.getMessage()).isEqualTo("Service unavailable"));
		JudgmentContextConformance.assertFailed(context, "Fail please", failure);
	}

}
