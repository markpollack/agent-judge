package io.github.markpollack.judge.langchain4j;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.service.Result;
import io.github.markpollack.judge.context.ExecutionStatus;
import io.github.markpollack.judge.context.JudgmentContext;

/**
 * Bridges a LangChain4j {@link Result} to a {@link JudgmentContext}.
 * <p>
 * Extracts aggregates and per-call inputs ({@code tokenUsage}, {@code toolExecutions},
 * {@code sources}) into metadata because judges legitimately reason about them.
 * Narrative/trace data ({@code intermediateResponses}) is NOT extracted — that is
 * cognitive observability owned by agent-journal.
 *
 * @author Mark Pollack
 * @since 0.10.0
 */
public final class LangChain4jJudgmentContextBuilder {

	private LangChain4jJudgmentContextBuilder() {
	}

	/**
	 * Build a {@link JudgmentContext} from a pre-computed LangChain4j {@link Result}.
	 * @param result the LangChain4j result to wrap
	 * @param goal the task description that produced this result
	 * @param startedAt when execution began
	 * @param executionTime how long execution took
	 * @return a fully populated JudgmentContext
	 */
	public static <T> JudgmentContext from(Result<T> result, String goal, Instant startedAt,
			Duration executionTime) {
		Map<String, Object> metadata = new HashMap<>();
		if (result.tokenUsage() != null) {
			metadata.put("langchain4j.tokenUsage", result.tokenUsage());
		}
		if (result.toolExecutions() != null && !result.toolExecutions().isEmpty()) {
			metadata.put("langchain4j.toolExecutions", result.toolExecutions());
		}
		if (result.sources() != null && !result.sources().isEmpty()) {
			metadata.put("langchain4j.sources", result.sources());
		}
		if (result.finishReason() != null) {
			metadata.put("langchain4j.finishReason", result.finishReason().name());
		}

		String output = result.content() != null ? result.content().toString() : null;
		ExecutionStatus status = mapFinishReason(result.finishReason());

		JudgmentContext.Builder builder = JudgmentContext.builder()
			.goal(goal)
			.status(status)
			.startedAt(startedAt)
			.executionTime(executionTime)
			.metadata(metadata);

		if (output != null) {
			builder.agentOutput(output);
		}

		return builder.build();
	}

	/**
	 * Execute a function, capture the result, and build a {@link JudgmentContext}.
	 * @param goal the task description
	 * @param serviceCall the LangChain4j service invocation
	 * @return a fully populated JudgmentContext
	 */
	public static <T> JudgmentContext execute(String goal, Function<String, Result<T>> serviceCall) {
		return execute(goal, serviceCall, Map.of());
	}

	/**
	 * Execute a function, capture the result, and build a {@link JudgmentContext} with
	 * extra metadata.
	 * @param goal the task description
	 * @param serviceCall the LangChain4j service invocation
	 * @param extraMetadata additional metadata to attach (e.g., run ID, experiment tag)
	 * @return a fully populated JudgmentContext
	 */
	public static <T> JudgmentContext execute(String goal, Function<String, Result<T>> serviceCall,
			Map<String, Object> extraMetadata) {
		Instant startedAt = Instant.now();
		try {
			Result<T> result = serviceCall.apply(goal);
			if (result == null) {
				Duration elapsed = Duration.between(startedAt, Instant.now());
				return JudgmentContext.builder()
					.goal(goal)
					.status(ExecutionStatus.FAILED)
					.startedAt(startedAt)
					.executionTime(elapsed)
					.error(new NullPointerException("Service call returned null Result"))
					.metadata(extraMetadata)
					.build();
			}
			Duration elapsed = Duration.between(startedAt, Instant.now());
			JudgmentContext context = from(result, goal, startedAt, elapsed);
			if (!extraMetadata.isEmpty()) {
				Map<String, Object> merged = new HashMap<>(context.metadata());
				merged.putAll(extraMetadata);
				context = JudgmentContext.builder()
					.goal(context.goal())
					.workspace(context.workspace())
					.status(context.status())
					.startedAt(context.startedAt())
					.executionTime(context.executionTime())
					.agentOutput(context.agentOutput().orElse(null))
					.metadata(merged)
					.build();
			}
			return context;
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

	private static ExecutionStatus mapFinishReason(FinishReason reason) {
		if (reason == null) {
			return ExecutionStatus.UNKNOWN;
		}
		return switch (reason) {
			case STOP -> ExecutionStatus.SUCCESS;
			case LENGTH -> ExecutionStatus.SUCCESS;
			case TOOL_EXECUTION -> ExecutionStatus.SUCCESS;
			case CONTENT_FILTER -> ExecutionStatus.REFUSED;
			case OTHER -> ExecutionStatus.UNKNOWN;
		};
	}

}
