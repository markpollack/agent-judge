package io.github.markpollack.judge.langchain4j;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.Result;
import io.github.markpollack.judge.completion.CompletionStatus;
import io.github.markpollack.judge.completion.CompletionEvidence;

/**
 * Bridges a LangChain4j {@link Result} to a {@link CompletionEvidence}.
 * <p>
 * Extracts aggregates and per-call inputs ({@code tokenUsage}, {@code toolExecutions},
 * {@code sources}) into metadata because judges legitimately reason about them.
 * Narrative/trace data ({@code intermediateResponses}) is NOT extracted — that is
 * cognitive observability owned by agent-journal.
 *
 * @author Mark Pollack
 * @since 0.10.0
 */
public final class LangChain4jCompletionEvidenceBuilder {

	private LangChain4jCompletionEvidenceBuilder() {
	}

	/**
	 * Build a {@link CompletionEvidence} from a pre-computed LangChain4j {@link Result}.
	 * @param <T> result content type
	 * @param result the LangChain4j result to wrap
	 * @param goal the task description that produced this result
	 * @param startedAt when execution began
	 * @param executionTime how long execution took
	 * @return a fully populated CompletionEvidence
	 */
	public static <T> CompletionEvidence from(Result<T> result, String goal, Instant startedAt,
			Duration executionTime) {
		return from(result, goal, startedAt, executionTime, Map.of());
	}

	/**
	 * Build a context with caller metadata. Native response facts remain authoritative if
	 * a caller supplies a colliding key.
	 * @param <T> result content type
	 * @param result the LangChain4j result to wrap
	 * @param goal the task description that produced this result
	 * @param startedAt when execution began
	 * @param executionTime how long execution took
	 * @param extraMetadata additional caller-owned context metadata
	 * @return a fully populated CompletionEvidence
	 */
	public static <T> CompletionEvidence from(Result<T> result, String goal, Instant startedAt, Duration executionTime,
			Map<String, Object> extraMetadata) {
		Map<String, Object> metadata = new HashMap<>(extraMetadata);
		TokenUsage usage = result.tokenUsage();
		if (usage != null) {
			metadata.put(LangChain4jMetadataKeys.TOKEN_USAGE, usage);
			if (usage.inputTokenCount() != null) {
				metadata.put(LangChain4jMetadataKeys.USAGE_INPUT_TOKENS, usage.inputTokenCount());
			}
			if (usage.outputTokenCount() != null) {
				metadata.put(LangChain4jMetadataKeys.USAGE_OUTPUT_TOKENS, usage.outputTokenCount());
			}
		}
		if (result.toolExecutions() != null && !result.toolExecutions().isEmpty()) {
			metadata.put(LangChain4jMetadataKeys.TOOL_EXECUTIONS, result.toolExecutions());
		}
		if (result.sources() != null && !result.sources().isEmpty()) {
			metadata.put(LangChain4jMetadataKeys.SOURCES, result.sources());
		}
		if (result.finishReason() != null) {
			metadata.put(LangChain4jMetadataKeys.FINISH_REASON, result.finishReason().name());
		}
		if (result.finalResponse() != null) {
			if (result.finalResponse().id() != null && !result.finalResponse().id().isEmpty()) {
				metadata.put(LangChain4jMetadataKeys.RESPONSE_ID, result.finalResponse().id());
			}
			if (result.finalResponse().modelName() != null && !result.finalResponse().modelName().isEmpty()) {
				metadata.put(LangChain4jMetadataKeys.MODEL, result.finalResponse().modelName());
			}
		}

		String output = result.content() != null ? result.content().toString() : null;
		CompletionStatus status = mapFinishReason(result.finishReason());

		CompletionEvidence.Builder builder = CompletionEvidence.builder()
			.request(goal)
			.status(status)
			.startedAt(startedAt)
			.elapsedTime(executionTime)
			.metadata(metadata);

		if (output != null) {
			builder.response(output);
		}

		return builder.build();
	}

	/**
	 * Execute a function, capture the result, and build a {@link CompletionEvidence}.
	 * @param <T> result content type
	 * @param goal the task description
	 * @param serviceCall the LangChain4j service invocation
	 * @return a fully populated CompletionEvidence
	 */
	public static <T> CompletionEvidence execute(String goal, Function<String, Result<T>> serviceCall) {
		return execute(goal, serviceCall, Map.of());
	}

	/**
	 * Execute a function, capture the result, and build a {@link CompletionEvidence} with
	 * extra metadata.
	 * @param <T> result content type
	 * @param goal the task description
	 * @param serviceCall the LangChain4j service invocation
	 * @param extraMetadata additional metadata to attach (e.g., run ID, experiment tag)
	 * @return a fully populated CompletionEvidence
	 */
	public static <T> CompletionEvidence execute(String goal, Function<String, Result<T>> serviceCall,
			Map<String, Object> extraMetadata) {
		Instant startedAt = Instant.now();
		try {
			Result<T> result = serviceCall.apply(goal);
			if (result == null) {
				Duration elapsed = Duration.between(startedAt, Instant.now());
				return CompletionEvidence.builder()
					.request(goal)
					.status(CompletionStatus.FAILED)
					.startedAt(startedAt)
					.elapsedTime(elapsed)
					.error(new NullPointerException("Service call returned null Result"))
					.metadata(extraMetadata)
					.build();
			}
			Duration elapsed = Duration.between(startedAt, Instant.now());
			return from(result, goal, startedAt, elapsed, extraMetadata);
		}
		catch (Exception ex) {
			Duration elapsed = Duration.between(startedAt, Instant.now());
			return CompletionEvidence.builder()
				.request(goal)
				.status(CompletionStatus.FAILED)
				.startedAt(startedAt)
				.elapsedTime(elapsed)
				.error(ex)
				.metadata(extraMetadata)
				.build();
		}
	}

	/**
	 * Map LangChain4j finish reasons to execution status.
	 * <p>
	 * Note: {@code LENGTH} maps to {@code SUCCESS} because the model completed its
	 * response (albeit truncated). However, judges evaluating a truncated answer may
	 * produce misleading verdicts. Callers should check
	 * {@code metadata.get("langchain4j.finishReason")} and consider ABSTAIN logic for
	 * LENGTH responses.
	 */
	private static CompletionStatus mapFinishReason(FinishReason reason) {
		if (reason == null) {
			return CompletionStatus.UNKNOWN;
		}
		return switch (reason) {
			case STOP -> CompletionStatus.SUCCESS;
			case LENGTH -> CompletionStatus.SUCCESS;
			case TOOL_EXECUTION -> CompletionStatus.SUCCESS;
			case CONTENT_FILTER -> CompletionStatus.REFUSED;
			case OTHER -> CompletionStatus.UNKNOWN;
		};
	}

}
