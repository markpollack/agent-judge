package io.github.markpollack.judge.ai;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.github.markpollack.judge.ai.model.JudgeModelResponse;
import io.github.markpollack.judge.result.Judgment;
import io.github.markpollack.judge.result.JudgmentStatus;
import io.github.markpollack.judge.score.CategoricalScore;

/**
 * Label-based classification using exact normalized matching (trim + lowercase).
 *
 * <p>Maps a model response's text to a {@link JudgmentStatus} via declared label mappings.
 * Uses exact matching after normalization — substring matching is intentionally avoided
 * because "irrelevant" contains "relevant".
 *
 * <p>When no label matches, returns {@link JudgmentStatus#ABSTAIN} with the raw judge
 * output preserved in metadata.
 *
 * @author Mark Pollack
 * @since 0.10.0
 * @see JudgmentClassifiers
 */
public final class LabelJudgmentClassifier implements JudgmentClassifier {

	private final Map<String, JudgmentStatus> mapping;

	private LabelJudgmentClassifier(Map<String, JudgmentStatus> mapping) {
		this.mapping = Map.copyOf(mapping);
	}

	/**
	 * Create a pass/fail classifier with the given labels.
	 * @param passLabel the label that maps to PASS
	 * @param failLabel the label that maps to FAIL
	 * @return a new classifier
	 */
	public static LabelJudgmentClassifier passFail(String passLabel, String failLabel) {
		return new LabelJudgmentClassifier(
				Map.of(normalize(passLabel), JudgmentStatus.PASS, normalize(failLabel), JudgmentStatus.FAIL));
	}

	@Override
	public Judgment classify(JudgeModelResponse response) {
		String raw = response.text();
		String normalized = normalize(raw);

		JudgmentStatus status = mapping.get(normalized);
		if (status == null) {
			Judgment.Builder builder = Judgment.builder()
				.status(JudgmentStatus.ABSTAIN)
				.reasoning("Judge output did not match any label: " + raw)
				.metadata("rawJudgeOutput", raw);
			addResponseMetadata(builder, response);
			return builder.build();
		}

		Judgment.Builder builder = Judgment.builder()
			.status(status)
			.score(new CategoricalScore(normalized, categories()))
			.reasoning(raw)
			.metadata("rawJudgeOutput", raw);
		addResponseMetadata(builder, response);
		return builder.build();
	}

	private static void addResponseMetadata(Judgment.Builder builder, JudgeModelResponse response) {
		if (response.model() != null) {
			builder.metadata("model", response.model());
		}
		if (response.usage() != null) {
			builder.metadata("usage", response.usage());
		}
	}

	/**
	 * Return the ordered list of recognized category labels.
	 * @return the category labels
	 */
	public List<String> categories() {
		return List.copyOf(mapping.keySet());
	}

	private static String normalize(String value) {
		return value.strip().toLowerCase(Locale.ROOT);
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private final Map<String, JudgmentStatus> mapping = new HashMap<>();

		public Builder pass(String label) {
			this.mapping.put(normalize(label), JudgmentStatus.PASS);
			return this;
		}

		public Builder fail(String label) {
			this.mapping.put(normalize(label), JudgmentStatus.FAIL);
			return this;
		}

		public Builder abstain(String label) {
			this.mapping.put(normalize(label), JudgmentStatus.ABSTAIN);
			return this;
		}

		public Builder map(String label, JudgmentStatus status) {
			this.mapping.put(normalize(label), status);
			return this;
		}

		public LabelJudgmentClassifier build() {
			if (mapping.isEmpty()) {
				throw new IllegalStateException("At least one label mapping is required");
			}
			return new LabelJudgmentClassifier(mapping);
		}

	}

}
