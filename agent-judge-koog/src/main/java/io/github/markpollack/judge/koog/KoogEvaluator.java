package io.github.markpollack.judge.koog;

import java.util.Map;

import ai.koog.agents.core.agent.AIAgent;
import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.context.JudgmentContext;
import io.github.markpollack.judge.jury.Jury;
import io.github.markpollack.judge.jury.Verdict;
import io.github.markpollack.judge.result.Judgment;

/**
 * One-liner convenience methods for evaluating Koog agents with agent-judge.
 * <p>
 * The bridge converts a Koog agent execution into a {@link JudgmentContext} and runs
 * the provided {@link Judge} or {@link Jury} against it. Koog takes an agent object
 * because {@link AIAgent} is a concrete type with a synchronous {@code run()} method.
 * <p>
 * Usage:
 * <pre>{@code
 * Judgment result = KoogEvaluator.evaluate(agent, "build a REST API", myJudge);
 * Verdict verdict = KoogEvaluator.evaluate(agent, "build a REST API", myJury);
 *
 * // With extra metadata for run tagging
 * Judgment tagged = KoogEvaluator.evaluate(agent, "build a REST API", myJudge,
 *     Map.of("run.id", "exp-42", "dataset.row", 7));
 * }</pre>
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
	public static Judgment evaluate(AIAgent<String, String> agent, String input, Judge judge) {
		return evaluate(agent, input, judge, Map.of());
	}

	/**
	 * Run a Koog agent and evaluate the result with a single judge, attaching extra
	 * metadata.
	 * @param agent the Koog agent to execute
	 * @param input the input (goal) to send to the agent
	 * @param judge the judge to evaluate the result
	 * @param extraMetadata additional metadata to attach to the JudgmentContext (e.g.,
	 * run ID, experiment tag, dataset row index)
	 * @return the judgment
	 */
	public static Judgment evaluate(AIAgent<String, String> agent, String input, Judge judge,
			Map<String, Object> extraMetadata) {
		JudgmentContext context = KoogJudgmentContextBuilder.from(agent, input, extraMetadata);
		return judge.judge(context);
	}

	/**
	 * Run a Koog agent and evaluate the result with a jury.
	 * @param agent the Koog agent to execute
	 * @param input the input (goal) to send to the agent
	 * @param jury the jury to evaluate the result
	 * @return the verdict
	 */
	public static Verdict evaluate(AIAgent<String, String> agent, String input, Jury jury) {
		return evaluate(agent, input, jury, Map.of());
	}

	/**
	 * Run a Koog agent and evaluate the result with a jury, attaching extra metadata.
	 * @param agent the Koog agent to execute
	 * @param input the input (goal) to send to the agent
	 * @param jury the jury to evaluate the result
	 * @param extraMetadata additional metadata to attach to the JudgmentContext
	 * @return the verdict
	 */
	public static Verdict evaluate(AIAgent<String, String> agent, String input, Jury jury,
			Map<String, Object> extraMetadata) {
		JudgmentContext context = KoogJudgmentContextBuilder.from(agent, input, extraMetadata);
		return jury.vote(context);
	}

}
