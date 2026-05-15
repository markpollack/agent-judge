package io.github.markpollack.judge.koog;

import ai.koog.agents.core.agent.AIAgent;
import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.context.JudgmentContext;
import io.github.markpollack.judge.jury.Jury;
import io.github.markpollack.judge.jury.Verdict;
import io.github.markpollack.judge.result.Judgment;

/**
 * One-liner convenience methods for evaluating Koog agents with agent-judge.
 * <p>
 * Usage:
 * <pre>{@code
 * Judgment result = KoogSupport.evaluate(agent, "build a REST API", myJudge);
 * Verdict verdict = KoogSupport.evaluate(agent, "build a REST API", myJury);
 * }</pre>
 *
 * @author Mark Pollack
 * @since 0.10.0
 */
public final class KoogSupport {

	private KoogSupport() {
	}

	/**
	 * Run a Koog agent and evaluate the result with a single judge.
	 * @param agent the Koog agent to execute
	 * @param input the input (goal) to send to the agent
	 * @param judge the judge to evaluate the result
	 * @return the judgment
	 */
	public static Judgment evaluate(AIAgent<String, String> agent, String input, Judge judge) {
		JudgmentContext context = KoogJudgmentContextBuilder.from(agent, input);
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
		JudgmentContext context = KoogJudgmentContextBuilder.from(agent, input);
		return jury.vote(context);
	}

}
