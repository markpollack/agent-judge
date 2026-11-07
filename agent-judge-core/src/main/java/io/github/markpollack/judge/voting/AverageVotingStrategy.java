/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.voting;

import java.util.List;
import java.util.Map;

import io.github.markpollack.judge.judgment.Judgment;

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
 * denominator, because "no assessment" is not the finding zero. Errors are governed by
 * {@link ErrorHandling} (default {@code PROPAGATE}) and exclusions by
 * {@link ExclusionHandling} (default {@code REFUSE}). If nothing is eligible the result
 * is {@code ABSTAIN} rather than a manufactured failing score, or {@code NOT_APPLICABLE}
 * when every input was an exclusion this strategy was configured to honour.
 * </p>
 *
 * <p>
 * The judgment passes if the mean reaches the configured threshold, which defaults to
 * {@link #DEFAULT_THRESHOLD}. That default is a convention rather than a derivation: it
 * knows nothing about the scale your judges score on. Prefer a bar derived from the
 * rubric that produced the scores.
 * </p>
 *
 * <p>
 * Example usage:
 * </p>
 * Executable examples are maintained in the Agent Judge Tutorial:
 * https://github.com/markpollack/agent-judge-tutorial.
 *
 * @author Mark Pollack
 * @since 0.1.0
 */
public class AverageVotingStrategy extends ThresholdStrategy {

	/**
	 * The acceptance bar applied when the caller does not state one.
	 * <p>
	 * 0.5 is the convention across the evaluation ecosystem and is retained as the
	 * default so existing behaviour is unchanged. It is a convention, not a derivation:
	 * it knows nothing about the scale your judges score on. Prefer a threshold derived
	 * from the rubric that produced the scores, supplied through the threshold
	 * constructor.
	 * </p>
	 *
	 * @since 0.16.0
	 */
	public static final double DEFAULT_THRESHOLD = 0.5;




	/**
	 * Create an average strategy with the default error policy.
	 */
	public AverageVotingStrategy() {
		this(DEFAULT_THRESHOLD, ErrorHandling.PROPAGATE);
	}

	/**
	 * Create an average strategy with a custom error policy.
	 * @param errorPolicy policy for handling errors
	 * @throws IllegalArgumentException if {@code errorPolicy} is null
	 */
	public AverageVotingStrategy(ErrorHandling errorPolicy) {
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
		this(threshold, ErrorHandling.PROPAGATE);
	}

	/**
	 * Create an average strategy with a caller-supplied acceptance bar and error policy.
	 * @param threshold the normalized bar the average must reach, in {@code [0.0, 1.0]}
	 * @param errorPolicy policy for handling errors
	 * @throws IllegalArgumentException if the threshold is not a finite value in
	 * {@code [0.0, 1.0]}, or if {@code errorPolicy} is null
	 * @since 0.16.0
	 */
	public AverageVotingStrategy(double threshold, ErrorHandling errorPolicy) {
		this(threshold, errorPolicy, ExclusionHandling.REFUSE);
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
	public AverageVotingStrategy(double threshold, ErrorHandling errorPolicy, ExclusionHandling notApplicablePolicy) {
		super(threshold, errorPolicy, notApplicablePolicy);
	}


	@Override
	public Judgment aggregate(List<Ballot> ballots) {
		AggregationPopulation population = population(ballots);
		Judgment exit = Populations.exit(population, getName());
		if (exit != null) {
			return exit;
		}

		// Every eligible judgment is PASS or FAIL, so effectiveScore is always present.
		double average = population.eligible()
			.stream()
			.mapToDouble(j -> j.effectiveScore().orElseThrow())
			.average()
			.orElseThrow();

		return Populations.thresholded(population, getName(), "Average", average, getThreshold());
	}

	@Override
	public String getName() {
		return "average";
	}



}
