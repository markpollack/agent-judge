package io.github.markpollack.judge.ai;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;

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
				Map.of(normalizeLabel(passLabel), JudgmentStatus.PASS, normalizeLabel(failLabel), JudgmentStatus.FAIL),
				Map.of());
	}

	@Override
	public Judgment classify(JudgeModelResponse response) {
		String raw = response.text();
		String normalized = normalize(raw);

		JudgmentStatus status = mapping.get(normalized);
		if (status == null) {
			// Nothing matched, so there is no classification to assert: no label, no score.
			Judgment.EnrichmentBuilder builder = Judgment.builder().abstain()
				.reasoning("Judge output did not match any label: " + raw)
				.metadata("rawJudgeOutput", raw);
			addResponseMetadata(builder, response);
			return builder.build();
		}

		Double declaredScore = scores.get(normalized);
		Judgment.EnrichmentBuilder builder = switch (status) {
			case PASS -> finding(Judgment.builder().pass(), normalized, raw, declaredScore);
			case FAIL -> finding(Judgment.builder().fail(), normalized, raw, declaredScore);
			case ABSTAIN -> Judgment.builder()
				.abstain()
				.reasoning(raw)
				.label(normalized)
				.metadata("rawJudgeOutput", raw);
			case ERROR -> throw new IllegalStateException("A recognized classification label cannot map to ERROR");
		};

		addResponseMetadata(builder, response);
		return builder.build();
	}

	private static Judgment.FindingBuilder finding(Judgment.FindingBuilder builder, String label, String raw,
			Double score) {
		builder.label(label).reasoning(raw).metadata("rawJudgeOutput", raw);
		return score == null ? builder : builder.score(score);
	}

	private static void addResponseMetadata(Judgment.EnrichmentBuilder builder, JudgeModelResponse response) {
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
	 * @return the declared score, or empty when the label carries no numeric meaning
	 */
	public OptionalDouble scoreFor(String label) {
		Double score = scores.get(normalize(label));
		return score == null ? OptionalDouble.empty() : OptionalDouble.of(score);
	}

	private static String normalize(String value) {
		return value.strip().toLowerCase(Locale.ROOT);
	}

	private static String normalizeLabel(String label) {
		String normalized = normalize(label);
		if (normalized.isBlank()) {
			throw new IllegalArgumentException("label must be non-blank");
		}
		return normalized;
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

		private Builder map(String label, JudgmentStatus status) {
			String normalized = normalizeLabel(label);
			this.mapping.put(normalized, status);
			this.scores.remove(normalized);
			return this;
		}

		private Builder map(String label, JudgmentStatus status, double normalizedScore) {
			if (!Double.isFinite(normalizedScore) || normalizedScore < 0.0 || normalizedScore > 1.0) {
				throw new IllegalArgumentException("Score for label '" + label
						+ "' must be finite and between 0.0 and 1.0, but was " + normalizedScore);
			}
			String normalized = normalizeLabel(label);
			this.mapping.put(normalized, status);
			this.scores.put(normalized, normalizedScore);
			return this;
		}

		public LabelJudgmentClassifier build() {
			if (mapping.isEmpty()) {
				throw new IllegalStateException("At least one label mapping is required");
			}
			return new LabelJudgmentClassifier(mapping, scores);
		}

	}

}
