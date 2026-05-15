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
		Instant startedAt = Instant.now();
		try {
			Result<T> result = serviceCall.apply(goal);
			Duration elapsed = Duration.between(startedAt, Instant.now());
			return from(result, goal, startedAt, elapsed);
		}
		catch (Exception ex) {
			Duration elapsed = Duration.between(startedAt, Instant.now());
			return JudgmentContext.builder()
				.goal(goal)
				.status(ExecutionStatus.FAILED)
				.startedAt(startedAt)
				.executionTime(elapsed)
				.error(ex)
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
			case CONTENT_FILTER -> ExecutionStatus.FAILED;
			case OTHER -> ExecutionStatus.UNKNOWN;
		};
	}

}
