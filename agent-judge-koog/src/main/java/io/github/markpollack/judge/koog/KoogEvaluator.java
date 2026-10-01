/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.koog;

import java.util.Map;

import ai.koog.agents.core.agent.AIAgent;
import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.completion.CompletionEvidence;
import io.github.markpollack.judge.jury.Jury;
import io.github.markpollack.judge.jury.Verdict;
import io.github.markpollack.judge.judgment.Judgment;

/**
 * One-liner convenience methods for evaluating Koog agents with agent-judge.
 * <p>
 * The bridge converts a Koog agent execution into a {@link CompletionEvidence} and runs
 * the provided {@link Judge} or {@link Jury} against it. Koog takes an agent object
 * because {@link AIAgent} is a concrete type with a synchronous {@code run()} method.
 * <p>
 * Usage: Executable examples are maintained in the Agent Judge Tutorial:
 * https://github.com/markpollack/agent-judge-tutorial.
 *
 * @author Mark Pollack
 * @since 0.10.0
 */
public final class KoogEvaluator {

	private KoogEvaluator() {
	}

	/**
	 * Run a Koog agent and evaluate the result with a single judge.
	 * @param agent the Koog agent to execute
	 * @param input the input (goal) to send to the agent
	 * @param judge the judge to evaluate the result
	 * @return the judgment
	 */
	public static Judgment evaluate(AIAgent<String, String> agent, String input,
			java.util.function.Function<CompletionEvidence, Judge> judge) {
		return evaluate(agent, input, judge, Map.of());
	}

	/**
	 * Run a Koog agent and evaluate the result with a single judge, attaching extra
	 * metadata.
	 * @param agent the Koog agent to execute
	 * @param input the input (goal) to send to the agent
	 * @param judge the judge to evaluate the result
	 * @param extraMetadata additional metadata to attach to the CompletionEvidence (e.g.,
	 * run ID, experiment tag, dataset row index)
	 * @return the judgment
	 */
	public static Judgment evaluate(AIAgent<String, String> agent, String input,
			java.util.function.Function<CompletionEvidence, Judge> judge, Map<String, Object> extraMetadata) {
		CompletionEvidence context = KoogCompletionEvidenceBuilder.from(agent, input, extraMetadata);
		return java.util.Objects.requireNonNull(judge.apply(context)).judge();
	}

	/**
	 * Run a Koog agent and evaluate the result with a jury.
	 * @param agent the Koog agent to execute
	 * @param input the input (goal) to send to the agent
	 * @param jury the jury to evaluate the result
	 * @return the verdict
	 */
	public static Verdict evaluateJury(AIAgent<String, String> agent, String input,
			java.util.function.Function<CompletionEvidence, Jury> jury) {
		return evaluateJury(agent, input, jury, Map.of());
	}

	/**
	 * Run a Koog agent and evaluate the result with a jury, attaching extra metadata.
	 * @param agent the Koog agent to execute
	 * @param input the input (goal) to send to the agent
	 * @param jury the jury to evaluate the result
	 * @param extraMetadata additional metadata to attach to the CompletionEvidence
	 * @return the verdict
	 */
	public static Verdict evaluateJury(AIAgent<String, String> agent, String input,
			java.util.function.Function<CompletionEvidence, Jury> jury, Map<String, Object> extraMetadata) {
		CompletionEvidence context = KoogCompletionEvidenceBuilder.from(agent, input, extraMetadata);
		return java.util.Objects.requireNonNull(jury.apply(context)).vote();
	}

}
