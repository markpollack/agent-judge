package io.github.markpollack.judge.ai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.github.markpollack.judge.ai.model.JudgeModelResponse;
import io.github.markpollack.judge.result.Judgment;
import io.github.markpollack.judge.result.JudgmentStatus;

/**
 * Label-based classification using exact normalized matching (trim + lowercase).
 *
 * <p>Maps a model response's text to a {@link JudgmentStatus} via declared label mappings.
 * Uses exact matching after normalization — substring matching is intentionally avoided
 * because "irrelevant" contains "relevant".
 *
 * <p>When no label matches, returns {@link JudgmentStatus#ABSTAIN} with the raw judge
 * output preserved in metadata. No label is recorded in that case: there is no valid
 * classification to assert.
 *
 * <h2>Numeric meaning of a label</h2>
 * <p>A label is not implicitly a score. {@code relevant}, {@code poor}, or {@code excellent}
 * has numeric meaning only under a declared policy, and this classifier is where that policy
 * belongs — it already owns the vocabulary. Declare a score alongside a label and it is
 * recorded on the judgment; otherwise the judgment carries a label and no score, and
 * downstream aggregation needs no category mapping at all.
 *
 * <pre>{@code
 * LabelJudgmentClassifier.builder()
 *     .pass("excellent", 1.0)
 *     .pass("good", 0.6)
 *     .fail("poor", 0.0)
 *     .build();
 * }</pre>
 *
 * @author Mark Pollack
 * @since 0.10.0
 * @see JudgmentClassifiers
 */
public final class LabelJudgmentClassifier implements JudgmentClassifier {

	private final Map<String, JudgmentStatus> mapping;

	private final Map<String, Double> scores;

	private LabelJudgmentClassifier(Map<String, JudgmentStatus> mapping, Map<String, Double> scores) {
		this.mapping = Map.copyOf(mapping);
		this.scores = Map.copyOf(scores);
	}

	/**
	 * Create a pass/fail classifier with the given labels.
	 * @param passLabel the label that maps to PASS
	 * @param failLabel the label that maps to FAIL
	 * @return a new classifier
	 */
	public static LabelJudgmentClassifier passFail(String passLabel, String failLabel) {
		return new LabelJudgmentClassifier(
				Map.of(normalize(passLabel), JudgmentStatus.PASS, normalize(failLabel), JudgmentStatus.FAIL), Map.of());
	}

	@Override
	public Judgment classify(JudgeModelResponse response) {
		String raw = response.text();
		String normalized = normalize(raw);

		JudgmentStatus status = mapping.get(normalized);
		if (status == null) {
			// Nothing matched, so there is no classification to assert: no label, no score.
			Judgment.Builder builder = Judgment.abstaining()
				.because("Judge output did not match any label: " + raw)
				.metadata("rawJudgeOutput", raw);
			addResponseMetadata(builder, response);
			return builder.build();
		}

		Judgment.Builder builder = Judgment.classified(normalized)
			.as(status)
			.because(raw)
			.metadata("rawJudgeOutput", raw);

		Double declaredScore = scores.get(normalized);
		if (declaredScore != null) {
			builder.withNormalizedScore(declaredScore);
		}

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

	/**
	 * Return the declared normalized score for a label, if the policy declares one.
	 * @param label the label, normalized or otherwise
	 * @return the declared score, or null when the label carries no numeric meaning
	 */
	public Double scoreFor(String label) {
		return scores.get(normalize(label));
	}

	private static String normalize(String value) {
		return value.strip().toLowerCase(Locale.ROOT);
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private final Map<String, JudgmentStatus> mapping = new LinkedHashMap<>();

		private final Map<String, Double> scores = new HashMap<>();

		public Builder pass(String label) {
			return map(label, JudgmentStatus.PASS);
		}

		/**
		 * Map a label to PASS and declare its normalized score.
		 * @param label the label
		 * @param normalizedScore the declared score, in [0.0, 1.0]
		 * @return this builder
		 */
		public Builder pass(String label, double normalizedScore) {
			return map(label, JudgmentStatus.PASS, normalizedScore);
		}

		public Builder fail(String label) {
			return map(label, JudgmentStatus.FAIL);
		}

		/**
		 * Map a label to FAIL and declare its normalized score.
		 * @param label the label
		 * @param normalizedScore the declared score, in [0.0, 1.0]
		 * @return this builder
		 */
		public Builder fail(String label, double normalizedScore) {
			return map(label, JudgmentStatus.FAIL, normalizedScore);
		}

		public Builder abstain(String label) {
			return map(label, JudgmentStatus.ABSTAIN);
		}

		public Builder map(String label, JudgmentStatus status) {
			this.mapping.put(normalize(label), status);
			return this;
		}

		/**
		 * Map a label to a status and declare its normalized score.
		 * @param label the label
		 * @param status the status; must be PASS or FAIL, since ABSTAIN and ERROR
		 * represent no completed measurement
		 * @param normalizedScore the declared score, in [0.0, 1.0]
		 * @return this builder
		 */
		public Builder map(String label, JudgmentStatus status, double normalizedScore) {
			if (status != JudgmentStatus.PASS && status != JudgmentStatus.FAIL) {
				throw new IllegalArgumentException(
						status + " represents no completed measurement, so label '" + label + "' cannot declare a score");
			}
			if (!Double.isFinite(normalizedScore) || normalizedScore < 0.0 || normalizedScore > 1.0) {
				throw new IllegalArgumentException("Score for label '" + label
						+ "' must be finite and between 0.0 and 1.0, but was " + normalizedScore);
			}
			this.mapping.put(normalize(label), status);
			this.scores.put(normalize(label), normalizedScore);
			return this;
		}

		public LabelJudgmentClassifier build() {
			if (mapping.isEmpty()) {
				throw new IllegalStateException("At least one label mapping is required");
			}
			List<String> inconsistent = new ArrayList<>();
			scores.keySet().forEach(label -> {
				JudgmentStatus status = mapping.get(label);
				if (status != JudgmentStatus.PASS && status != JudgmentStatus.FAIL) {
					inconsistent.add(label);
				}
			});
			if (!inconsistent.isEmpty()) {
				throw new IllegalStateException(
						"Labels declare a score but map to a status that carries none: " + inconsistent);
			}
			return new LabelJudgmentClassifier(mapping, scores);
		}

	}

}
