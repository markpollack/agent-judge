/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.agentclient;

import java.nio.file.Path;
import java.util.Map;
import java.util.function.Supplier;

import io.github.markpollack.agents.client.AgentClientResponse;
import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.jury.Jury;
import io.github.markpollack.judge.verdict.Verdict;
import io.github.markpollack.judge.judgment.Judgment;

/**
 * One-liner convenience methods for evaluating AgentClient CLI-agent results with
 * agent-judge.
 * <p>
 * AgentClient wraps CLI-delegated agents (Claude Code, Codex, Gemini CLI, Amazon Q,
 * etc.). This evaluator bridges their output into {@link AgentExecutionEvidence} for
 * evaluation by ordinary {@link Judge} or {@link Jury} instances.
 * <p>
 * The bridge uses {@code Supplier<AgentClientResponse>} to keep process execution inside
 * AgentClient. Provider configuration, session management, approval modes, and CLI
 * lifecycle remain AgentClient's responsibility.
 * <p>
 * Usage: Executable examples are maintained in the Agent Judge Tutorial:
 * https://github.com/markpollack/agent-judge-tutorial.
 *
 * @author Mark Pollack
 * @since 0.10.0
 */
public final class AgentClientEvaluator {

	private AgentClientEvaluator() {
	}

	/**
	 * Execute a CLI agent and evaluate the result with a judge.
	 * @param goal task sent to the agent
	 * @param workspace directory in which the agent executes
	 * @param call agent invocation
	 * @param judge judge applied to the captured execution
	 * @return the judge result
	 */
	public static Judgment evaluate(String goal, Path workspace, Supplier<AgentClientResponse> call,
			java.util.function.Function<AgentExecutionEvidence, Judge> judge) {
		return evaluate(goal, workspace, call, judge, Map.of());
	}

	/**
	 * Execute a CLI agent and evaluate the result with a judge, attaching extra metadata.
	 * @param goal task sent to the agent
	 * @param workspace directory in which the agent executes
	 * @param call agent invocation
	 * @param judge judge applied to the captured execution
	 * @param extraMetadata caller-supplied context metadata
	 * @return the judge result
	 */
	public static Judgment evaluate(String goal, Path workspace, Supplier<AgentClientResponse> call,
			java.util.function.Function<AgentExecutionEvidence, Judge> judge, Map<String, Object> extraMetadata) {
		AgentExecutionEvidence context = AgentClientEvidence.execute(goal, workspace, call, extraMetadata);
		return java.util.Objects.requireNonNull(judge.apply(context)).judge();
	}

	/**
	 * Execute a CLI agent and evaluate the result with a jury.
	 * @param goal task sent to the agent
	 * @param workspace directory in which the agent executes
	 * @param call agent invocation
	 * @param jury jury applied to the captured execution
	 * @return the jury verdict
	 */
	public static Verdict evaluateJury(String goal, Path workspace, Supplier<AgentClientResponse> call,
			java.util.function.Function<AgentExecutionEvidence, Jury> jury) {
		return evaluateJury(goal, workspace, call, jury, Map.of());
	}

	/**
	 * Execute a CLI agent and evaluate the result with a jury, attaching extra metadata.
	 * @param goal task sent to the agent
	 * @param workspace directory in which the agent executes
	 * @param call agent invocation
	 * @param jury jury applied to the captured execution
	 * @param extraMetadata caller-supplied context metadata
	 * @return the jury verdict
	 */
	public static Verdict evaluateJury(String goal, Path workspace, Supplier<AgentClientResponse> call,
			java.util.function.Function<AgentExecutionEvidence, Jury> jury, Map<String, Object> extraMetadata) {
		AgentExecutionEvidence context = AgentClientEvidence.execute(goal, workspace, call, extraMetadata);
		return java.util.Objects.requireNonNull(jury.apply(context)).vote();
	}

}
