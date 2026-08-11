package io.github.markpollack.judge.koog;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import ai.koog.agents.core.agent.AIAgent;
import ai.koog.agents.core.agent.config.AIAgentConfig;
import ai.koog.prompt.llm.LLModel;
import io.github.markpollack.judge.context.ExecutionStatus;
import io.github.markpollack.judge.context.JudgmentContext;

/**
 * Bridges a Koog {@link AIAgent} execution to a {@link JudgmentContext}.
 * <p>
 * Wraps a single {@code agent.run(input)} call, capturing output, timing,
 * and exceptions. The bridge does not own an ExecutorService — threading
 * is configured by the user on {@code AIAgentConfig}.
 *
 * @author Mark Pollack
 * @since 0.10.0
 */
public final class KoogJudgmentContextBuilder {

	private KoogJudgmentContextBuilder() {
	}

	/**
	 * Run a Koog agent and capture the result as a {@link JudgmentContext}.
	 * @param agent the Koog agent to execute
	 * @param input the input (goal) to send to the agent
	 * @return a fully populated JudgmentContext
	 */
	public static JudgmentContext from(AIAgent<String, String> agent, String input) {
		return from(agent, input, Map.of());
	}

	/**
	 * Run a Koog agent and capture the result as a {@link JudgmentContext} with extra
	 * metadata.
	 * @param agent the Koog agent to execute
	 * @param input the input (goal) to send to the agent
	 * @param extraMetadata additional metadata to attach (e.g., run ID, experiment tag)
	 * @return a fully populated JudgmentContext
	 */
	public static JudgmentContext from(AIAgent<String, String> agent, String input,
			Map<String, Object> extraMetadata) {
		Instant startedAt = Instant.now();
		String agentId = safeGetId(agent);
		Map<String, Object> metadata = new HashMap<>(extraMetadata);
		metadata.put(KoogMetadataKeys.AGENT_ID, agentId);
		extractModelIdentity(agent, metadata);
		try {
			String output = agent.run(input);
			Duration elapsed = Duration.between(startedAt, Instant.now());
			return JudgmentContext.builder()
				.goal(input)
				.status(ExecutionStatus.SUCCESS)
				.startedAt(startedAt)
				.executionTime(elapsed)
				.agentOutput(output)
				.metadata(metadata)
				.build();
		}
		catch (Exception ex) {
			if (ex instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			Duration elapsed = Duration.between(startedAt, Instant.now());
			return JudgmentContext.builder()
				.goal(input)
				.status(ExecutionStatus.FAILED)
				.startedAt(startedAt)
				.executionTime(elapsed)
				.error(ex)
				.metadata(metadata)
				.build();
		}
	}

	private static String safeGetId(AIAgent<String, String> agent) {
		try {
			String id = agent.getId();
			return id != null && !id.isEmpty() ? id : "unknown";
		}
		catch (Exception ex) {
			return "unknown";
		}
	}

	private static void extractModelIdentity(AIAgent<String, String> agent, Map<String, Object> metadata) {
		try {
			AIAgentConfig config = agent.getAgentConfig();
			LLModel model = config != null ? config.getModel() : null;
			if (model == null) {
				return;
			}
			if (model.getProvider() != null && model.getProvider().getId() != null
					&& !model.getProvider().getId().isEmpty()) {
				metadata.put(KoogMetadataKeys.PROVIDER, model.getProvider().getId());
			}
			if (model.getId() != null && !model.getId().isEmpty()) {
				metadata.put(KoogMetadataKeys.MODEL, model.getId());
			}
		}
		catch (Exception ex) {
			// Model identity is optional and must not turn an otherwise valid execution into a failure.
		}
	}

}
