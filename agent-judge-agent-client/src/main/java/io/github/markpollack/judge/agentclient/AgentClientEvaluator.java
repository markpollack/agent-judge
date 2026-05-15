package io.github.markpollack.judge.agentclient;

import java.nio.file.Path;
import java.util.Map;
import java.util.function.Supplier;

import io.github.markpollack.agents.client.AgentClientResponse;
import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.context.JudgmentContext;
import io.github.markpollack.judge.jury.Jury;
import io.github.markpollack.judge.jury.Verdict;
import io.github.markpollack.judge.result.Judgment;

/**
 * One-liner convenience methods for evaluating AgentClient CLI-agent results with
 * agent-judge.
 * <p>
 * AgentClient wraps CLI-delegated agents (Claude Code, Codex, Gemini CLI, Amazon Q, etc.).
 * This evaluator bridges their output into {@link JudgmentContext} for evaluation by
 * ordinary {@link Judge} or {@link Jury} instances.
 * <p>
 * The bridge uses {@code Supplier<AgentClientResponse>} to keep process execution inside
 * AgentClient. Provider configuration, session management, approval modes, and CLI
 * lifecycle remain AgentClient's responsibility.
 * <p>
 * Usage:
 * <pre>{@code
 * Judgment result = AgentClientEvaluator.evaluate(
 *     "Fix the build", workspace,
 *     () -> agentClient.run("Fix the build"),
 *     myJudge);
 * }</pre>
 *
 * @author Mark Pollack
 * @since 0.10.0
 */
public final class AgentClientEvaluator {

	private AgentClientEvaluator() {
	}

	/**
	 * Execute a CLI agent and evaluate the result with a judge.
	 */
	public static Judgment evaluate(String goal, Path workspace, Supplier<AgentClientResponse> call, Judge judge) {
		return evaluate(goal, workspace, call, judge, Map.of());
	}

	/**
	 * Execute a CLI agent and evaluate the result with a judge, attaching extra metadata.
	 */
	public static Judgment evaluate(String goal, Path workspace, Supplier<AgentClientResponse> call, Judge judge,
			Map<String, Object> extraMetadata) {
		JudgmentContext context = AgentClientJudgmentContextBuilder.execute(goal, workspace, call, extraMetadata);
		return judge.judge(context);
	}

	/**
	 * Execute a CLI agent and evaluate the result with a jury.
	 */
	public static Verdict evaluate(String goal, Path workspace, Supplier<AgentClientResponse> call, Jury jury) {
		return evaluate(goal, workspace, call, jury, Map.of());
	}

	/**
	 * Execute a CLI agent and evaluate the result with a jury, attaching extra metadata.
	 */
	public static Verdict evaluate(String goal, Path workspace, Supplier<AgentClientResponse> call, Jury jury,
			Map<String, Object> extraMetadata) {
		JudgmentContext context = AgentClientJudgmentContextBuilder.execute(goal, workspace, call, extraMetadata);
		return jury.vote(context);
	}

}
