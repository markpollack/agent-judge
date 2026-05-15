package io.github.markpollack.judge.koog;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import ai.koog.agents.core.agent.AIAgent;
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
		Instant startedAt = Instant.now();
		try {
			String output = agent.run(input);
			Duration elapsed = Duration.between(startedAt, Instant.now());
			return JudgmentContext.builder()
				.goal(input)
				.status(ExecutionStatus.SUCCESS)
				.startedAt(startedAt)
				.executionTime(elapsed)
				.agentOutput(output)
				.metadata("koog.agentId", agent.getId())
				.build();
		}
		catch (Exception ex) {
			Duration elapsed = Duration.between(startedAt, Instant.now());
			return JudgmentContext.builder()
				.goal(input)
				.status(ExecutionStatus.FAILED)
				.startedAt(startedAt)
				.executionTime(elapsed)
				.error(ex)
				.metadata("koog.agentId", agent.getId())
				.build();
		}
	}

}
