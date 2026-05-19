package io.github.markpollack.judge.ai;

/**
 * Convenience factory for common {@link JudgmentClassifier} configurations.
 *
 * @author Mark Pollack
 * @since 0.10.0
 */
public final class JudgmentClassifiers {

	private JudgmentClassifiers() {
	}

	/**
	 * Create a pass/fail classifier with the given labels.
	 * @param passLabel the label that maps to PASS
	 * @param failLabel the label that maps to FAIL
	 * @return a new classifier
	 */
	public static JudgmentClassifier passFail(String passLabel, String failLabel) {
		return LabelJudgmentClassifier.passFail(passLabel, failLabel);
	}

}
