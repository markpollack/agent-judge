/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.voting;

import java.util.Map;

/**
 * The configuration shared by the built-in score strategies: a normalized pass threshold
 * and both policies, validated once, and declared the same way.
 */
abstract class ThresholdStrategy implements VotingStrategy {

	private final double threshold;

	private final ErrorHandling errorPolicy;

	private final ExclusionHandling notApplicablePolicy;

	ThresholdStrategy(double threshold, ErrorHandling errorPolicy, ExclusionHandling notApplicablePolicy) {
		if (notApplicablePolicy == null) {
			throw new IllegalArgumentException("notApplicablePolicy must not be null");
		}
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
		this.notApplicablePolicy = notApplicablePolicy;
	}

	/**
	 * The normalized bar the reduced score must reach.
	 * @return the threshold, in {@code [0.0, 1.0]}
	 */
	public double getThreshold() {
		return this.threshold;
	}

	final ErrorHandling errorPolicy() {
		return this.errorPolicy;
	}

	/**
	 * The eligible population of these ballots under this strategy's policies.
	 * @param ballots the seats' ballots
	 * @return the resolved population
	 */
	final AggregationPopulation population(java.util.List<Ballot> ballots) {
		return Populations.of(ballots, this.errorPolicy, this.notApplicablePolicy);
	}

	/**
	 * Declares the error policy and the threshold.
	 * @return the declared description
	 * @since 0.17.0
	 */
	@Override
	public StrategyDescription describe() {
		return StrategyDescription.declared(this, this.errorPolicy, this.notApplicablePolicy, this.threshold, Map.of());
	}

	/** {@inheritDoc} */
	@Override
	public ExclusionHandling exclusionHandling() {
		return this.notApplicablePolicy;
	}

}
