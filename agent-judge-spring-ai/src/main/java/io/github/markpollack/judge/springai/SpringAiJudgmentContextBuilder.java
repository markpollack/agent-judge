package io.github.markpollack.judge.springai;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import io.github.markpollack.judge.context.ExecutionStatus;
import io.github.markpollack.judge.context.JudgmentContext;

/**
 * Bridges a Spring AI {@link ChatResponse} to a {@link JudgmentContext}.
 * <p>
 * This is the <strong>evaluated-side</strong> bridge — it converts Spring AI agent output
 * into a JudgmentContext for evaluation by ordinary judges and juries. It is NOT the
 * judging-side LLM dependency (that is {@code agent-judge-llm} which uses
 * {@code spring-ai-client-chat}).
 * <p>
 * Extracts output text, finish reason, independently available token quantities,
 * model name, response ID, and tool-call indicators into metadata. Tool call details
 * are best-effort — detailed tool execution traces belong to agent-journal / advisor
 * instrumentation. A total token count is not emitted because Spring AI may derive it
 * from prompt and completion counts rather than preserve provider-reporting provenance.
 *
 * @author Mark Pollack
 * @since 0.10.0
 */
public final class SpringAiJudgmentContextBuilder {

	private SpringAiJudgmentContextBuilder() {
	}

	/**
	 * Build a {@link JudgmentContext} from a pre-computed {@link ChatResponse}.
	 * @param response Spring AI response
	 * @param goal task description
	 * @param startedAt execution start time
	 * @param executionTime elapsed execution time
	 * @return populated judgment context
	 */
	public static JudgmentContext from(ChatResponse response, String goal, Instant startedAt,
			Duration executionTime) {
		return from(response, goal, startedAt, executionTime, Map.of());
	}

	/**
	 * Build a {@link JudgmentContext} from a pre-computed {@link ChatResponse} with extra
	 * metadata.
	 * @param response Spring AI response
	 * @param goal task description
	 * @param startedAt execution start time
	 * @param executionTime elapsed execution time
	 * @param extraMetadata additional context metadata
	 * @return populated judgment context
	 */
	public static JudgmentContext from(ChatResponse response, String goal, Instant startedAt, Duration executionTime,
			Map<String, Object> extraMetadata) {
		Map<String, Object> metadata = new HashMap<>(extraMetadata);
		String output = null;
		ExecutionStatus status = ExecutionStatus.UNKNOWN;
		String finishReason = null;

		if (response != null) {
			Generation result = response.getResult();
			if (result != null && result.getOutput() != null) {
				output = result.getOutput().getText();
				if (result.getOutput().hasToolCalls()) {
					metadata.put(SpringAiMetadataKeys.HAS_TOOL_CALLS, true);
					metadata.put(SpringAiMetadataKeys.TOOL_CALLS, result.getOutput().getToolCalls());
				}
			}
			if (result != null && result.getMetadata() != null) {
				finishReason = result.getMetadata().getFinishReason();
				if (finishReason != null) {
					metadata.put(SpringAiMetadataKeys.FINISH_REASON, finishReason);
				}
			}
			status = mapFinishReason(finishReason);
			metadata.put(SpringAiMetadataKeys.HAS_TOOL_CALLS,
					metadata.getOrDefault(SpringAiMetadataKeys.HAS_TOOL_CALLS, response.hasToolCalls()));

			extractResponseMetadata(response, metadata);
		}

		JudgmentContext.Builder builder = JudgmentContext.builder()
			.goal(goal)
			.status(status)
			.startedAt(startedAt)
			.executionTime(executionTime)
			.metadata(metadata);

		if (output != null && !output.isEmpty()) {
			builder.agentOutput(output);
		}

		return builder.build();
	}

	/**
	 * Execute a Spring AI call via a Supplier, capture the result, and build a
	 * {@link JudgmentContext}.
	 * @param goal task description
	 * @param call Spring AI invocation
	 * @return populated judgment context
	 */
	public static JudgmentContext execute(String goal, Supplier<ChatResponse> call) {
		return execute(goal, call, Map.of());
	}

	/**
	 * Execute a Spring AI call via a Supplier with extra metadata.
	 * @param goal task description
	 * @param call Spring AI invocation
	 * @param extraMetadata additional context metadata
	 * @return populated judgment context
	 */
	public static JudgmentContext execute(String goal, Supplier<ChatResponse> call,
			Map<String, Object> extraMetadata) {
		Instant startedAt = Instant.now();
		try {
			ChatResponse response = call.get();
			Duration elapsed = Duration.between(startedAt, Instant.now());
			if (response == null) {
				return JudgmentContext.builder()
					.goal(goal)
					.status(ExecutionStatus.FAILED)
					.startedAt(startedAt)
					.executionTime(elapsed)
					.error(new NullPointerException("ChatClient call returned null ChatResponse"))
					.metadata(extraMetadata)
					.build();
			}
			return from(response, goal, startedAt, elapsed, extraMetadata);
		}
		catch (Exception ex) {
			Duration elapsed = Duration.between(startedAt, Instant.now());
			return JudgmentContext.builder()
				.goal(goal)
				.status(ExecutionStatus.FAILED)
				.startedAt(startedAt)
				.executionTime(elapsed)
				.error(ex)
				.metadata(extraMetadata)
				.build();
		}
	}

	private static void extractResponseMetadata(ChatResponse response, Map<String, Object> metadata) {
		try {
			ChatResponseMetadata meta = response.getMetadata();
			if (meta == null) {
				return;
			}
			if (meta.getId() != null && !meta.getId().isEmpty()) {
				metadata.put(SpringAiMetadataKeys.RESPONSE_ID, meta.getId());
			}
			if (meta.getModel() != null && !meta.getModel().isEmpty()) {
				metadata.put(SpringAiMetadataKeys.MODEL, meta.getModel());
			}
			Usage usage = meta.getUsage();
			if (usage != null) {
				if (usage.getPromptTokens() != null) {
					metadata.put(SpringAiMetadataKeys.USAGE_PROMPT_TOKENS, usage.getPromptTokens());
				}
				if (usage.getCompletionTokens() != null) {
					metadata.put(SpringAiMetadataKeys.USAGE_COMPLETION_TOKENS, usage.getCompletionTokens());
				}
				if (usage.getCacheWriteInputTokens() != null) {
					metadata.put(SpringAiMetadataKeys.USAGE_CACHE_CREATION_TOKENS, usage.getCacheWriteInputTokens());
				}
				if (usage.getCacheReadInputTokens() != null) {
					metadata.put(SpringAiMetadataKeys.USAGE_CACHE_READ_TOKENS, usage.getCacheReadInputTokens());
				}
			}
		}
		catch (Exception ex) {
			// Null-safe: don't let metadata extraction failure break the bridge
		}
	}

	/**
	 * Map Spring AI finish reasons (provider-specific strings) to ExecutionStatus.
	 * <p>
	 * Note: "length" maps to SUCCESS but represents a truncated response. Callers should
	 * check {@code metadata.get(SpringAiMetadataKeys.FINISH_REASON)} and consider ABSTAIN
	 * logic for length-truncated responses.
	 */
	private static ExecutionStatus mapFinishReason(String reason) {
		if (reason == null) {
			return ExecutionStatus.UNKNOWN;
		}
		return switch (reason.toLowerCase()) {
			case "stop" -> ExecutionStatus.SUCCESS;
			case "tool_calls" -> ExecutionStatus.SUCCESS;
			case "length" -> ExecutionStatus.SUCCESS;
			case "content_filter" -> ExecutionStatus.REFUSED;
			default -> ExecutionStatus.UNKNOWN;
		};
	}

}
