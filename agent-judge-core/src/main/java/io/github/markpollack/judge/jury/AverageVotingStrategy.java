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
 * Average voting strategy: the mean of the applicable judges' assessments.
 *
 * <p>
 * A numeric strategy. It reduces over {@link Judgment#effectiveScore()}, which yields an
 * explicit score where the judge measured one and {@code 1.0}/{@code 0.0} for a Boolean
 * {@code PASS}/{@code FAIL}.
 * </p>
 *
 * <p>
 * Abstentions leave the population entirely — excluded from both the numerator and the
 * denominator, because "no assessment" is not the assessment zero. Errors are governed by
 * {@link ErrorPolicy} (default {@code PROPAGATE}) and exclusions by
 * {@link NotApplicablePolicy} (default {@code REFUSE}). If nothing is eligible the result is
 * {@code ABSTAIN} rather than a manufactured failing score, or {@code NOT_APPLICABLE} when
 * every input was an exclusion this strategy was configured to honour.
 * </p>
 *
 * <p>
 * The judgment passes if the mean reaches the configured threshold, which defaults to
 * {@link #DEFAULT_THRESHOLD}. That default is a convention rather than a derivation: it knows
 * nothing about the scale your judges score on. Prefer a bar derived from the rubric that
 * produced the scores.
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
public class AverageVotingStrategy implements VotingStrategy {

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

	private final NotApplicablePolicy notApplicablePolicy;

	/**
	 * Create an average strategy with the default error policy.
	 */
	public AverageVotingStrategy() {
		this(DEFAULT_THRESHOLD, ErrorPolicy.PROPAGATE);
	}

	/**
	 * Create an average strategy with a custom error policy.
	 * @param errorPolicy policy for handling errors
	 * @throws IllegalArgumentException if {@code errorPolicy} is null
	 */
	public AverageVotingStrategy(ErrorPolicy errorPolicy) {
		this(DEFAULT_THRESHOLD, errorPolicy);
	}

	/**
	 * Create an average strategy with a caller-supplied acceptance bar.
	 * @param threshold the normalized bar the average must reach, in {@code [0.0, 1.0]}
	 * @throws IllegalArgumentException if the threshold is not a finite value in
	 * {@code [0.0, 1.0]}
	 * @since 0.16.0
	 */
	public AverageVotingStrategy(double threshold) {
		this(threshold, ErrorPolicy.PROPAGATE);
	}

	/**
	 * Create an average strategy with a caller-supplied acceptance bar and error policy.
	 * @param threshold the normalized bar the average must reach, in {@code [0.0, 1.0]}
	 * @param errorPolicy policy for handling errors
	 * @throws IllegalArgumentException if the threshold is not a finite value in
	 * {@code [0.0, 1.0]}, or if {@code errorPolicy} is null
	 * @since 0.16.0
	 */
	public AverageVotingStrategy(double threshold, ErrorPolicy errorPolicy) {
		this(threshold, errorPolicy, NotApplicablePolicy.REFUSE);
	}

	/**
	 * Create an average strategy with a caller-supplied acceptance bar and both policies.
	 * @param threshold the normalized bar the average must reach, in {@code [0.0, 1.0]}
	 * @param errorPolicy policy for handling errors
	 * @param notApplicablePolicy policy for handling excluded judgments
	 * @throws IllegalArgumentException if the threshold is not a finite value in
	 * {@code [0.0, 1.0]}, or if either policy is null
	 * @since 0.17.0
	 */
	public AverageVotingStrategy(double threshold, ErrorPolicy errorPolicy,
			NotApplicablePolicy notApplicablePolicy) {
		if (notApplicablePolicy == null) {
			throw new IllegalArgumentException("notApplicablePolicy must not be null");
		}
		this.notApplicablePolicy = notApplicablePolicy;
		if (!Double.isFinite(threshold)) {
			throw new IllegalArgumentException("threshold must be finite, but was " + threshold);
		}
		if (threshold < 0.0 || threshold > 1.0) {
			throw new IllegalArgumentException("threshold must be between 0.0 and 1.0, but was " + threshold);
		}
		if (errorPolicy == null) {
			throw new IllegalArgumentException("errorPolicy must not be null");
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
		AggregationPopulation population = AggregationPopulation.resolve(judgments, this.errorPolicy,
				this.notApplicablePolicy);

		if (population.hasPolicyExit()) {
			return population.policyExitAggregate(getName());
		}
		if (population.isEmpty()) {
			return population.noResult(getName(), Map.of());
		}

		// Every eligible judgment is PASS or FAIL, so effectiveScore is always present.
		double average = population.eligible()
			.stream()
			.mapToDouble(j -> j.effectiveScore().orElseThrow())
			.average()
			.orElseThrow();

		Judgment aggregate = (average >= this.threshold ? Judgment.builder().pass() : Judgment.builder().fail())
			.score(average)
			.reasoning(String.format("Average score: %.2f across %d applicable judge(s) (threshold: %.2f, result: %s)",
					average, population.eligible().size(), this.threshold, average >= this.threshold ? "pass" : "fail"))
			.build();
		return AggregationEvidence.attach(aggregate,
				population.evidence(getName()).put(AggregationEvidence.THRESHOLD, this.threshold).build());
	}

	@Override
	public String getName() {
		return "average";
	}

	/**
	 * Declares the error policy and the threshold.
	 * @return the declared description
	 * @since 0.17.0
	 */
	@Override
	public StrategyDescription describe() {
		return StrategyDescription.declared(this, this.errorPolicy, this.notApplicablePolicy, this.threshold,
				Map.of());
	}

	/** {@inheritDoc} */
	@Override
	public NotApplicablePolicy notApplicablePolicy() {
		return this.notApplicablePolicy;
	}


}
