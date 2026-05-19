package io.github.markpollack.judge.ai;

import io.github.markpollack.judge.ai.model.JudgeModelResponse;
import io.github.markpollack.judge.result.Judgment;

/**
 * Maps an AI judge's model response into a {@link Judgment}.
 *
 * <p>Vocabulary:
 * <ul>
 *   <li><em>agent output</em> — the output being evaluated</li>
 *   <li><em>judge output</em> — response from the AI judge backend</li>
 *   <li><em>judgment</em> — normalized domain object (Score + Status + reasoning)</li>
 * </ul>
 *
 * <p>Simple classifiers inspect only {@code response.text()}; richer classifiers
 * may use structured output data from {@code response.metadata()}.
 *
 * @author Mark Pollack
 * @since 0.10.0
 * @see LabelJudgmentClassifier
 * @see JudgmentClassifiers
 */
@FunctionalInterface
public interface JudgmentClassifier {

	/**
	 * Classify a judge model response into a judgment.
	 * @param response the judge model response
	 * @return the classified judgment
	 */
	Judgment classify(JudgeModelResponse response);

}
