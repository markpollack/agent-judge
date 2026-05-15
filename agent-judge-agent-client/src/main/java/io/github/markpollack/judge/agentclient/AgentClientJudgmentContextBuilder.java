package io.github.markpollack.judge.agentclient;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import io.github.markpollack.agents.client.AgentClientResponse;
import io.github.markpollack.agents.model.AgentResponseMetadata;
import io.github.markpollack.judge.context.ExecutionStatus;
import io.github.markpollack.judge.context.JudgmentContext;

/**
 * Bridges an {@link AgentClientResponse} from CLI-delegated agent execution to a
 * {@link JudgmentContext}.
 * <p>
 * The bridge does not own runtime semantics — provider configuration, session management,
 * approval modes, and CLI process lifecycle remain in AgentClient. This module only
 * adapts execution results for evaluation.
 *
 * @author Mark Pollack
 * @since 0.10.0
 */
public final class AgentClientJudgmentContextBuilder {

	private AgentClientJudgmentContextBuilder() {
	}

	/**
	 * Build a {@link JudgmentContext} from a pre-computed {@link AgentClientResponse}.
	 * @param response the agent client response
	 * @param goal the task description that was sent to the agent
	 * @param workspace the working directory where the agent executed
	 * @return a fully populated JudgmentContext
	 */
	public static JudgmentContext from(AgentClientResponse response, String goal, Path workspace) {
		return from(response, goal, workspace, Map.of());
	}

	/**
	 * Build a {@link JudgmentContext} from a pre-computed {@link AgentClientResponse} with
	 * extra metadata.
	 * @param response the agent client response
	 * @param goal the task description
	 * @param workspace the working directory
	 * @param extraMetadata additional metadata to attach (e.g., run ID, experiment tag)
	 * @return a fully populated JudgmentContext
	 */
	public static JudgmentContext from(AgentClientResponse response, String goal, Path workspace,
			Map<String, Object> extraMetadata) {
		Map<String, Object> metadata = new HashMap<>(extraMetadata);
		Duration duration = Duration.ZERO;
		ExecutionStatus status = ExecutionStatus.UNKNOWN;
		String output = null;

		if (response != null) {
			output = response.getResult();
			status = response.isSuccessful() ? ExecutionStatus.SUCCESS : ExecutionStatus.FAILED;

			AgentResponseMetadata meta = safeGetMetadata(response);
			if (meta != null) {
				duration = meta.getDuration() != null ? meta.getDuration() : Duration.ZERO;
				if (meta.getModel() != null && !meta.getModel().isEmpty()) {
					metadata.put(AgentClientMetadataKeys.MODEL, meta.getModel());
				}
				if (meta.getSessionId() != null && !meta.getSessionId().isEmpty()) {
					metadata.put(AgentClientMetadataKeys.SESSION_ID, meta.getSessionId());
				}
			}

			String finishReason = safeGetFinishReason(response);
			if (finishReason != null) {
				metadata.put(AgentClientMetadataKeys.FINISH_REASON, finishReason);
			}
		}

		JudgmentContext.Builder builder = JudgmentContext.builder()
			.goal(goal)
			.workspace(workspace)
			.status(status)
			.startedAt(Instant.now())
			.executionTime(duration)
			.metadata(metadata);

		if (output != null && !output.isEmpty()) {
			builder.agentOutput(output);
		}

		return builder.build();
	}

	/**
	 * Execute an agent call via a Supplier, capture the result, and build a
	 * {@link JudgmentContext}.
	 * @param goal the task description
	 * @param workspace the working directory
	 * @param call the agent execution (typically {@code () -> agentClient.run(goal)})
	 * @return a fully populated JudgmentContext
	 */
	public static JudgmentContext execute(String goal, Path workspace, Supplier<AgentClientResponse> call) {
		return execute(goal, workspace, call, Map.of());
	}

	/**
	 * Execute an agent call via a Supplier with extra metadata.
	 * @param goal the task description
	 * @param workspace the working directory
	 * @param call the agent execution
	 * @param extraMetadata additional metadata to attach
	 * @return a fully populated JudgmentContext
	 */
	public static JudgmentContext execute(String goal, Path workspace, Supplier<AgentClientResponse> call,
			Map<String, Object> extraMetadata) {
		Instant startedAt = Instant.now();
		try {
			AgentClientResponse response = call.get();
			JudgmentContext context = from(response, goal, workspace, extraMetadata);
			Duration measured = Duration.between(startedAt, Instant.now());
			// Use measured time if response metadata didn't provide duration
			Duration effective = (context.executionTime() != null && !context.executionTime().isZero())
					? context.executionTime() : measured;
			return JudgmentContext.builder()
				.goal(context.goal())
				.workspace(context.workspace())
				.status(context.status())
				.startedAt(startedAt)
				.executionTime(effective)
				.agentOutput(context.agentOutput().orElse(null))
				.metadata(context.metadata())
				.build();
		}
		catch (Exception ex) {
			Duration elapsed = Duration.between(startedAt, Instant.now());
			return JudgmentContext.builder()
				.goal(goal)
				.workspace(workspace)
				.status(ExecutionStatus.FAILED)
				.startedAt(startedAt)
				.executionTime(elapsed)
				.error(ex)
				.metadata(extraMetadata)
				.build();
		}
	}

	private static AgentResponseMetadata safeGetMetadata(AgentClientResponse response) {
		try {
			return response.getMetadata();
		}
		catch (Exception ex) {
			return null;
		}
	}

	private static String safeGetFinishReason(AgentClientResponse response) {
		try {
			return response.getAgentResponse().getResult().getMetadata().getFinishReason();
		}
		catch (Exception ex) {
			return null;
		}
	}

}
