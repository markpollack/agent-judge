package io.github.markpollack.judge.langchain4j;

import java.util.function.Function;

import dev.langchain4j.service.Result;
import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.jury.Jury;
import io.github.markpollack.judge.jury.Verdict;
import io.github.markpollack.judge.result.Judgment;

/**
 * One-liner convenience methods for evaluating LangChain4j results with agent-judge.
 * <p>
 * Usage:
 * <pre>{@code
 * // With a pre-computed Result
 * Judgment result = LangChain4jSupport.evaluate(lcResult, "Summarize the document", judge);
 *
 * // With a service call (timing captured automatically)
 * Judgment result = LangChain4jSupport.evaluate("Summarize the document", assistant::chat, judge);
 * }</pre>
 *
 * @author Mark Pollack
 * @since 0.10.0
 */
public final class LangChain4jSupport {

	private LangChain4jSupport() {
	}

	/**
	 * Execute a LangChain4j service call and evaluate with a judge.
	 * @param goal the input to send to the service
	 * @param serviceCall the LangChain4j service invocation
	 * @param judge the judge to evaluate the result
	 * @return the judgment
	 */
	public static <T> Judgment evaluate(String goal, Function<String, Result<T>> serviceCall, Judge judge) {
		return judge.judge(LangChain4jJudgmentContextBuilder.execute(goal, serviceCall));
	}

	/**
	 * Execute a LangChain4j service call and evaluate with a jury.
	 * @param goal the input to send to the service
	 * @param serviceCall the LangChain4j service invocation
	 * @param jury the jury to evaluate the result
	 * @return the verdict
	 */
	public static <T> Verdict evaluate(String goal, Function<String, Result<T>> serviceCall, Jury jury) {
		return jury.vote(LangChain4jJudgmentContextBuilder.execute(goal, serviceCall));
	}

}
