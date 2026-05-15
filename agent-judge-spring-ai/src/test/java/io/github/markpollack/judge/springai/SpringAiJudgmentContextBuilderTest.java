package io.github.markpollack.judge.springai;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import io.github.markpollack.judge.context.ExecutionStatus;
import io.github.markpollack.judge.context.JudgmentContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpringAiJudgmentContextBuilderTest {

	private ChatResponse successResponse() {
		AssistantMessage message = new AssistantMessage("Spring Boot simplifies application development.");
		ChatGenerationMetadata genMeta = ChatGenerationMetadata.builder().finishReason("stop").build();
		Generation generation = new Generation(message, genMeta);
		ChatResponseMetadata meta = ChatResponseMetadata.builder()
			.id("resp-123")
			.model("gpt-4o")
			.usage(new DefaultUsage(100, 50))
			.build();
		return new ChatResponse(List.of(generation), meta);
	}

	@Test
	void shouldBuildContextFromSuccessResponse() {
		ChatResponse response = successResponse();
		Instant startedAt = Instant.now();

		JudgmentContext context = SpringAiJudgmentContextBuilder.from(response, "Explain Spring Boot", startedAt,
				Duration.ofSeconds(2));

		assertThat(context.goal()).isEqualTo("Explain Spring Boot");
		assertThat(context.status()).isEqualTo(ExecutionStatus.SUCCESS);
		assertThat(context.agentOutput()).isPresent().hasValue("Spring Boot simplifies application development.");
		assertThat(context.metadata()).containsEntry(SpringAiMetadataKeys.RESPONSE_ID, "resp-123");
		assertThat(context.metadata()).containsEntry(SpringAiMetadataKeys.MODEL, "gpt-4o");
		assertThat(context.metadata()).containsEntry(SpringAiMetadataKeys.FINISH_REASON, "stop");
		assertThat(context.metadata()).containsEntry(SpringAiMetadataKeys.USAGE_PROMPT_TOKENS, 100);
		assertThat(context.metadata()).containsEntry(SpringAiMetadataKeys.USAGE_COMPLETION_TOKENS, 50);
		assertThat(context.metadata()).containsEntry(SpringAiMetadataKeys.USAGE_TOTAL_TOKENS, 150);
	}

	@Test
	void shouldMapContentFilterToRefused() {
		AssistantMessage message = new AssistantMessage("");
		ChatGenerationMetadata genMeta = ChatGenerationMetadata.builder().finishReason("content_filter").build();
		Generation generation = new Generation(message, genMeta);
		ChatResponse response = new ChatResponse(List.of(generation));

		JudgmentContext context = SpringAiJudgmentContextBuilder.from(response, "Generate something", Instant.now(),
				Duration.ofSeconds(1));

		assertThat(context.status()).isEqualTo(ExecutionStatus.REFUSED);
	}

	@Test
	void shouldMapLengthToSuccessWithCaveat() {
		AssistantMessage message = new AssistantMessage("Truncated response...");
		ChatGenerationMetadata genMeta = ChatGenerationMetadata.builder().finishReason("length").build();
		Generation generation = new Generation(message, genMeta);
		ChatResponse response = new ChatResponse(List.of(generation));

		JudgmentContext context = SpringAiJudgmentContextBuilder.from(response, "Long question", Instant.now(),
				Duration.ofSeconds(5));

		assertThat(context.status()).isEqualTo(ExecutionStatus.SUCCESS);
		assertThat(context.metadata()).containsEntry(SpringAiMetadataKeys.FINISH_REASON, "length");
	}

	@Test
	void shouldMapNullFinishReasonToUnknown() {
		AssistantMessage message = new AssistantMessage("Some response");
		Generation generation = new Generation(message);
		ChatResponse response = new ChatResponse(List.of(generation));

		JudgmentContext context = SpringAiJudgmentContextBuilder.from(response, "Test", Instant.now(),
				Duration.ofSeconds(1));

		assertThat(context.status()).isEqualTo(ExecutionStatus.UNKNOWN);
	}

	@Test
	void shouldCaptureExceptionFromSupplier() {
		JudgmentContext context = SpringAiJudgmentContextBuilder.execute("Fail please", () -> {
			throw new RuntimeException("Connection refused");
		});

		assertThat(context.status()).isEqualTo(ExecutionStatus.FAILED);
		assertThat(context.error()).isPresent()
			.hasValueSatisfying(e -> assertThat(e.getMessage()).isEqualTo("Connection refused"));
	}

	@Test
	void shouldHandleNullChatResponse() {
		JudgmentContext context = SpringAiJudgmentContextBuilder.from(null, "Test", Instant.now(),
				Duration.ofSeconds(1));

		assertThat(context.status()).isEqualTo(ExecutionStatus.UNKNOWN);
		assertThat(context.agentOutput()).isEmpty();
	}

	@Test
	void shouldHandleNullSupplierResult() {
		JudgmentContext context = SpringAiJudgmentContextBuilder.execute("Test", () -> null);

		assertThat(context.status()).isEqualTo(ExecutionStatus.FAILED);
		assertThat(context.error()).isPresent();
	}

	@Test
	void shouldIncludeExtraMetadata() {
		ChatResponse response = successResponse();

		JudgmentContext context = SpringAiJudgmentContextBuilder.from(response, "Test", Instant.now(),
				Duration.ofSeconds(1), Map.of("run.id", "exp-42"));

		assertThat(context.metadata()).containsEntry("run.id", "exp-42");
		assertThat(context.metadata()).containsEntry(SpringAiMetadataKeys.MODEL, "gpt-4o");
	}

}
