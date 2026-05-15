package io.github.markpollack.judge.langchain4j;

import java.util.Map;
import java.util.function.Function;

import dev.langchain4j.service.Result;
import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.jury.Jury;
import io.github.markpollack.judge.jury.Verdict;
import io.github.markpollack.judge.result.Judgment;

/**
 * One-liner convenience methods for evaluating LangChain4j results with agent-judge.
 * <p>
 * The bridge accepts a {@code Function<String, Result<T>>} rather than a specific service
 * object because LangChain4j AI Services are dynamic proxies — there is no common interface
 * to bind to the way Koog's {@code AIAgent} exists. Any {@code assistant::chat} method
 * reference works.
 * <p>
 * Usage:
 * <pre>{@code
 * // With a service call (timing captured automatically)
 * Judgment result = LangChain4jEvaluator.evaluate("Summarize the document", assistant::chat, judge);
 *
 * // With extra metadata for run tagging
 * Judgment tagged = LangChain4jEvaluator.evaluate("Summarize", assistant::chat, judge,
 *     Map.of("run.id", "exp-42"));
 * }</pre>
 *
 * @author Mark Pollack
 * @since 0.10.0
 */
public final class LangChain4jEvaluator {

	private LangChain4jEvaluator() {
	}

	/**
	 * Execute a LangChain4j service call and evaluate with a judge.
	 * @param goal the input to send to the service
	 * @param serviceCall the LangChain4j service invocation
	 * @param judge the judge to evaluate the result
	 * @return the judgment
	 */
	public static <T> Judgment evaluate(String goal, Function<String, Result<T>> serviceCall, Judge judge) {
		return evaluate(goal, serviceCall, judge, Map.of());
	}

	/**
	 * Execute a LangChain4j service call and evaluate with a judge, attaching extra
	 * metadata.
	 * @param goal the input to send to the service
	 * @param serviceCall the LangChain4j service invocation
	 * @param judge the judge to evaluate the result
	 * @param extraMetadata additional metadata to attach (e.g., run ID, experiment tag)
	 * @return the judgment
	 */
	public static <T> Judgment evaluate(String goal, Function<String, Result<T>> serviceCall, Judge judge,
			Map<String, Object> extraMetadata) {
		return judge.judge(LangChain4jJudgmentContextBuilder.execute(goal, serviceCall, extraMetadata));
	}

	/**
	 * Execute a LangChain4j service call and evaluate with a jury.
	 * @param goal the input to send to the service
	 * @param serviceCall the LangChain4j service invocation
	 * @param jury the jury to evaluate the result
	 * @return the verdict
	 */
	public static <T> Verdict evaluate(String goal, Function<String, Result<T>> serviceCall, Jury jury) {
		return evaluate(goal, serviceCall, jury, Map.of());
	}

	/**
	 * Execute a LangChain4j service call and evaluate with a jury, attaching extra
	 * metadata.
	 * @param goal the input to send to the service
	 * @param serviceCall the LangChain4j service invocation
	 * @param jury the jury to evaluate the result
	 * @param extraMetadata additional metadata to attach
	 * @return the verdict
	 */
	public static <T> Verdict evaluate(String goal, Function<String, Result<T>> serviceCall, Jury jury,
			Map<String, Object> extraMetadata) {
		return jury.vote(LangChain4jJudgmentContextBuilder.execute(goal, serviceCall, extraMetadata));
	}

}
