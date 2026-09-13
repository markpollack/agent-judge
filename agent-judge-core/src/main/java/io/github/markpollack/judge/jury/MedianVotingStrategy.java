/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury;

import java.util.List;
import java.util.Map;

import io.github.markpollack.judge.description.StrategyDescription;
import io.github.markpollack.judge.result.Judgment;

/**
 * Median voting strategy: the middle assessment among the applicable judges.
 *
 * <p>
 * A numeric strategy, robust to outliers. It reduces over
 * {@link Judgment#effectiveScore()}, which yields an explicit score where the judge
 * measured one and {@code 1.0}/{@code 0.0} for a Boolean {@code PASS}/{@code FAIL}.
 * </p>
 *
 * <p>
 * Abstentions leave the population entirely rather than participating as zero — under the
 * old behaviour two abstentions could drag the median to zero and flip the verdict. Errors
 * are governed by {@link ErrorPolicy} (default {@code PROPAGATE}). If nothing is eligible
 * the result is {@code ABSTAIN}.
 * </p>
 *
 * <p>
 * For an even number of eligible judgments the median is the mean of the two middle values.
 * The judgment passes if the median is greater than or equal to the acceptance
 * threshold, which defaults to {@link #DEFAULT_THRESHOLD}.
 * </p>
 *
 * <p>
 * Example usage:
 * </p>
 * Executable examples are maintained in the Agent Judge Tutorial: https://github.com/markpollack/agent-judge-tutorial.
 *
 * @author Mark Pollack
 * @since 0.1.0
 */
public class MedianVotingStrategy implements VotingStrategy {

	/**
	 * The acceptance bar applied when the caller does not state one.
	 * <p>
	 * 0.5 is the convention across the evaluation ecosystem and is retained as the
	 * default so existing behaviour is unchanged. It is a convention, not a derivation: it
	 * knows nothing about the scale your judges score on. Prefer a threshold derived from
	 * the rubric that produced the scores, supplied through the threshold constructor.
	 * </p>
	 *
	 * @since 0.16.0
	 */
	public static final double DEFAULT_THRESHOLD = 0.5;

	private final double threshold;

	private final ErrorPolicy errorPolicy;

	/**
	 * Create a median strategy with the default error policy.
	 */
	public MedianVotingStrategy() {
		this(DEFAULT_THRESHOLD, ErrorPolicy.PROPAGATE);
	}

	/**
	 * Create a median strategy with a custom error policy.
	 * @param errorPolicy policy for handling errors
	 */
	public MedianVotingStrategy(ErrorPolicy errorPolicy) {
		this(DEFAULT_THRESHOLD, errorPolicy);
	}

	/**
	 * Create a median strategy with a caller-supplied acceptance bar.
	 * @param threshold the normalized bar the median must reach, in {@code [0.0, 1.0]}
	 * @throws IllegalArgumentException if the threshold is not a finite value in
	 * {@code [0.0, 1.0]}
	 * @since 0.16.0
	 */
	public MedianVotingStrategy(double threshold) {
		this(threshold, ErrorPolicy.PROPAGATE);
	}

	/**
	 * Create a median strategy with a caller-supplied acceptance bar and error policy.
	 * @param threshold the normalized bar the median must reach, in {@code [0.0, 1.0]}
	 * @param errorPolicy policy for handling errors
	 * @throws IllegalArgumentException if the threshold is not a finite value in
	 * {@code [0.0, 1.0]}
	 * @since 0.16.0
	 */
	public MedianVotingStrategy(double threshold, ErrorPolicy errorPolicy) {
		if (!Double.isFinite(threshold)) {
			throw new IllegalArgumentException("threshold must be finite, but was " + threshold);
		}
		if (threshold < 0.0 || threshold > 1.0) {
			throw new IllegalArgumentException("threshold must be between 0.0 and 1.0, but was " + threshold);
		}
		this.threshold = threshold;
		this.errorPolicy = errorPolicy;
	}

	/**
	 * The acceptance bar this strategy applies.
	 * @return the normalized threshold
	 * @since 0.16.0
	 */
	public double getThreshold() {
		return this.threshold;
	}

	@Override
	public Judgment aggregate(List<Judgment> judgments, Map<String, Double> weights) {
		AggregationPopulation population = AggregationPopulation.resolve(judgments, this.errorPolicy);

		if (population.propagateError()) {
			return population.propagatedError(getName());
		}
		if (population.isEmpty()) {
			return population.noResult(getName(), Map.of());
		}

		// Every eligible judgment is PASS or FAIL, so effectiveScore is always present.
		double[] scores = population.eligible()
			.stream()
			.mapToDouble(j -> j.effectiveScore().orElseThrow())
			.sorted()
			.toArray();

		int size = scores.length;
		double median = (size % 2 == 0) ? (scores[size / 2 - 1] + scores[size / 2]) / 2.0 : scores[size / 2];

		Judgment aggregate = (median >= this.threshold ? Judgment.builder().pass() : Judgment.builder().fail())
			.score(median)
			.reasoning(String.format("Median score: %.2f across %d applicable judge(s) (threshold: %.2f, result: %s)",
					median, size, this.threshold, median >= this.threshold ? "pass" : "fail"))
			.build();
		return AggregationEvidence.attach(aggregate,
				population.evidence(getName()).put(AggregationEvidence.THRESHOLD, this.threshold).build());
	}

	@Override
	public String getName() {
		return "median";
	}

	/**
	 * Declares the error policy and the threshold.
	 * @return the declared description
	 * @since 0.17.0
	 */
	@Override
	public StrategyDescription describe() {
		return StrategyDescription.declared(this, this.errorPolicy, this.threshold, Map.of());
	}

}
