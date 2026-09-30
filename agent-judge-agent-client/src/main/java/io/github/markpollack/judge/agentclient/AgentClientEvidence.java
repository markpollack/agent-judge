package io.github.markpollack.judge.agentclient;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

import io.github.markpollack.agents.client.AgentClientResponse;
import io.github.markpollack.agents.model.AgentResponseMetadata;
import io.github.markpollack.judge.completion.CompletionStatus;
import io.github.markpollack.judge.completion.CompletionEvidence;

/**
 * Bridges an {@link AgentClientResponse} from CLI-delegated agent execution to a
 * {@link CompletionEvidence}.
 * <p>
 * The bridge does not own runtime semantics — provider configuration, session management,
 * approval modes, and CLI process lifecycle remain in AgentClient. This module only
 * adapts execution results for evaluation.
 *
 * @author Mark Pollack
 * @since 0.10.0
 */
public final class AgentClientEvidence {

	private AgentClientEvidence() {
	}

	/**
	 * Build a {@link CompletionEvidence} from a pre-computed {@link AgentClientResponse}.
	 * @param response the agent client response
	 * @param goal the task description that was sent to the agent
	 * @param workspace the working directory where the agent executed
	 * @return a fully populated CompletionEvidence
	 */
	public static AgentExecutionEvidence from(AgentClientResponse response, String goal, Path workspace) {
		return from(response, goal, workspace, Map.of());
	}

	/**
	 * Build a {@link CompletionEvidence} from a pre-computed {@link AgentClientResponse}
	 * with extra metadata.
	 * @param response the agent client response
	 * @param goal the task description
	 * @param workspace the working directory
	 * @param extraMetadata additional metadata to attach (e.g., run ID, experiment tag)
	 * @return a fully populated CompletionEvidence
	 */
	public static AgentExecutionEvidence from(AgentClientResponse response, String goal, Path workspace,
			Map<String, Object> extraMetadata) {
		Map<String, Object> metadata = new HashMap<>(extraMetadata);
		Duration duration = Duration.ZERO;
		CompletionStatus status = CompletionStatus.UNKNOWN;
		String output = null;

		if (response != null) {
			output = response.getResult();
			String finishReason = safeGetFinishReason(response);
			status = mapFinishReason(finishReason, response.isSuccessful());

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

			if (finishReason != null) {
				metadata.put(AgentClientMetadataKeys.FINISH_REASON, finishReason);
			}
		}

		CompletionEvidence.Builder builder = CompletionEvidence.builder()
			.request(goal)
			.status(status)
			.startedAt(Instant.now())
			.elapsedTime(duration)
			.metadata(metadata);

		if (output != null && !output.isEmpty()) {
			builder.response(output);
		}

		return new AgentExecutionEvidence(workspace, builder.build());
	}

	/**
	 * Execute an agent call via a Supplier, capture the result, and build a
	 * {@link CompletionEvidence}.
	 * @param goal the task description
	 * @param workspace the working directory
	 * @param call the agent execution (typically {@code () -> agentClient.run(goal)})
	 * @return a fully populated CompletionEvidence
	 */
	public static AgentExecutionEvidence execute(String goal, Path workspace, Supplier<AgentClientResponse> call) {
		return execute(goal, workspace, call, Map.of());
	}

	/**
	 * Execute an agent call via a Supplier with extra metadata.
	 * @param goal the task description
	 * @param workspace the working directory
	 * @param call the agent execution
	 * @param extraMetadata additional metadata to attach
	 * @return a fully populated CompletionEvidence
	 */
	public static AgentExecutionEvidence execute(String goal, Path workspace, Supplier<AgentClientResponse> call,
			Map<String, Object> extraMetadata) {
		Instant startedAt = Instant.now();
		try {
			AgentClientResponse response = call.get();
			CompletionEvidence context = from(response, goal, workspace, extraMetadata).completion();
			Duration measured = Duration.between(startedAt, Instant.now());
			// Use measured time if response metadata didn't provide duration
			Duration effective = (context.elapsedTime() != null && !context.elapsedTime().isZero())
					? context.elapsedTime() : measured;
			return new AgentExecutionEvidence(workspace,
					CompletionEvidence.builder()
						.request(context.request())
						.status(context.status())
						.startedAt(startedAt)
						.elapsedTime(effective)
						.response(context.response())
						.metadata(context.metadata())
						.build());
		}
		catch (Exception ex) {
			Duration elapsed = Duration.between(startedAt, Instant.now());
			return new AgentExecutionEvidence(workspace,
					CompletionEvidence.builder()
						.request(goal)
						.status(CompletionStatus.FAILED)
						.startedAt(startedAt)
						.elapsedTime(elapsed)
						.error(ex)
						.metadata(extraMetadata)
						.build());
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

	private static CompletionStatus mapFinishReason(String finishReason, boolean successful) {
		if (finishReason == null || finishReason.isBlank()) {
			return successful ? CompletionStatus.SUCCESS : CompletionStatus.UNKNOWN;
		}
		return switch (finishReason.toUpperCase(Locale.ROOT)) {
			case "SUCCESS", "COMPLETE" -> CompletionStatus.SUCCESS;
			case "ERROR", "FAILED" -> CompletionStatus.FAILED;
			case "TIMEOUT" -> CompletionStatus.TIMEOUT;
			case "CANCELLED", "CANCELED" -> CompletionStatus.CANCELLED;
			case "REFUSED", "CONTENT_FILTER" -> CompletionStatus.REFUSED;
			default -> successful ? CompletionStatus.SUCCESS : CompletionStatus.UNKNOWN;
		};
	}

}
