package io.github.markpollack.judge.springai;

import java.util.Map;
import java.util.function.Supplier;

import org.springframework.ai.chat.model.ChatResponse;

import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.context.JudgmentContext;
import io.github.markpollack.judge.jury.Jury;
import io.github.markpollack.judge.jury.Verdict;
import io.github.markpollack.judge.result.Judgment;

/**
 * One-liner convenience methods for evaluating Spring AI ChatResponse output with
 * agent-judge.
 * <p>
 * This is the <strong>evaluated-side</strong> bridge. {@code agent-judge-llm} uses Spring
 * AI {@code ChatClient} for the judging side. This evaluator converts Spring AI agent
 * output into {@link JudgmentContext} for evaluation by ordinary judges and juries.
 * <p>
 * Uses {@code Supplier<ChatResponse>} because Spring AI {@code ChatClient} calls don't
 * take the goal as an argument at call time — the goal is baked into the prompt/call
 * chain before invocation.
 * <p>
 * Usage:
 * Executable examples are maintained in the Agent Judge Tutorial: https://github.com/markpollack/agent-judge-tutorial.
 *
 * @author Mark Pollack
 * @since 0.10.0
 */
public final class SpringAiEvaluator {

	private SpringAiEvaluator() {
	}

	/**
	 * Execute a Spring AI call and evaluate the result with a judge.
	 * @param goal task description
	 * @param call Spring AI invocation
	 * @param judge judge to apply
	 * @return the judgment
	 */
	public static Judgment evaluate(String goal, Supplier<ChatResponse> call, Judge judge) {
		return evaluate(goal, call, judge, Map.of());
	}

	/**
	 * Execute a Spring AI call and evaluate the result with a judge, attaching extra
	 * metadata.
	 * @param goal task description
	 * @param call Spring AI invocation
	 * @param judge judge to apply
	 * @param extraMetadata additional context metadata
	 * @return the judgment
	 */
	public static Judgment evaluate(String goal, Supplier<ChatResponse> call, Judge judge,
			Map<String, Object> extraMetadata) {
		JudgmentContext context = SpringAiJudgmentContextBuilder.execute(goal, call, extraMetadata);
		return judge.judge(context);
	}

	/**
	 * Execute a Spring AI call and evaluate the result with a jury.
	 * @param goal task description
	 * @param call Spring AI invocation
	 * @param jury jury to apply
	 * @return the verdict
	 */
	public static Verdict evaluate(String goal, Supplier<ChatResponse> call, Jury jury) {
		return evaluate(goal, call, jury, Map.of());
	}

	/**
	 * Execute a Spring AI call and evaluate the result with a jury, attaching extra
	 * metadata.
	 * @param goal task description
	 * @param call Spring AI invocation
	 * @param jury jury to apply
	 * @param extraMetadata additional context metadata
	 * @return the verdict
	 */
	public static Verdict evaluate(String goal, Supplier<ChatResponse> call, Jury jury,
			Map<String, Object> extraMetadata) {
		JudgmentContext context = SpringAiJudgmentContextBuilder.execute(goal, call, extraMetadata);
		return jury.vote(context);
	}

}
