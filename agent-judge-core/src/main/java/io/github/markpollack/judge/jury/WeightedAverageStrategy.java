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
 * Weighted average voting strategy: the mean of the applicable judges' assessments,
 * weighted by judge.
 *
 * <p>
 * A numeric strategy. It reduces over {@link Judgment#effectiveScore()}, which yields an
 * explicit score where the judge measured one and {@code 1.0}/{@code 0.0} for a Boolean
 * {@code PASS}/{@code FAIL}.
 * </p>
 *
 * <p>
 * Weights are keyed by judge position ({@code "0"}, {@code "1"}, ...) as strings. A missing
 * weight resolves to {@code 1.0}, so an empty weight map computes a simple mean — this
 * strategy does <em>not</em> delegate to {@link AverageVotingStrategy}, which would
 * misattribute the aggregation evidence to a strategy the caller did not use. Weights need
 * not sum to 1.0; they are normalized over the eligible population.
 * </p>
 *
 * <h2>Weight validation</h2>
 * <p>
 * An invalid weight configuration is a caller error and fails loudly; a valid configuration
 * whose usable weight disappears after filtering is a runtime no-result:
 * </p>
 * <ul>
 * <li>negative, NaN, or infinite weight — {@link IllegalArgumentException};</li>
 * <li>all weights explicitly zero — {@link IllegalArgumentException}, since no judge could
 * influence the result;</li>
 * <li>an individual zero weight — legal, meaning "this judge does not count";</li>
 * <li>positive input weight but zero eligible weight, because every positively weighted
 * judge abstained or was ignored — {@code ABSTAIN}, with the evidence to prove it.</li>
 * </ul>
 * <p>
 * Previously an all-zero configuration divided by zero and produced a {@code NaN} score,
 * which passed range validation because every IEEE 754 comparison against {@code NaN} is
 * false.
 * </p>
 *
 * <h2>Weight totals beyond the range of a double</h2>
 * <p>
 * Each weight must be finite, but the total need not fit in a {@code double}. When finite
 * weights sum past {@link Double#MAX_VALUE}, the eligible weights are rescaled by a power of
 * two before averaging, so the result is still their weighted mean. Previously the total
 * overflowed to {@code Infinity} and the aggregation threw. A total that fits is computed
 * exactly as before.
 * </p>
 * <p>
 * In the evidence, a weight total that exceeded the largest finite {@code double} is reported
 * as {@link Double#MAX_VALUE}, so the evidence remains a finite, portable number. A total
 * that fits is reported exactly.
 * </p>
 *
 * <p>
 * Abstentions leave the population entirely. Errors are governed by {@link ErrorPolicy}
 * (default {@code PROPAGATE}) and exclusions by {@link NotApplicablePolicy} (default
 * {@code REFUSE}); an exclusion honoured as a failure keeps its configured weight, so a
 * heavily weighted criterion that does not apply counts for as much as it would have.
 * </p>
 *
 * <p>
 * The judgment passes if the weighted mean reaches the configured threshold, which defaults to
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
public class WeightedAverageStrategy implements VotingStrategy {

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
	 * Create a weighted average strategy with the default error policy.
	 */
	public WeightedAverageStrategy() {
		this(DEFAULT_THRESHOLD, ErrorPolicy.PROPAGATE);
	}

	/**
	 * Create a weighted average strategy with a custom error policy.
	 * @param errorPolicy policy for handling errors
	 * @throws IllegalArgumentException if {@code errorPolicy} is null
	 */
	public WeightedAverageStrategy(ErrorPolicy errorPolicy) {
		this(DEFAULT_THRESHOLD, errorPolicy);
	}

	/**
	 * Create a weighted-average strategy with a caller-supplied acceptance bar.
	 * @param threshold the normalized bar the weighted average must reach, in {@code [0.0, 1.0]}
	 * @throws IllegalArgumentException if the threshold is not a finite value in
	 * {@code [0.0, 1.0]}
	 * @since 0.16.0
	 */
	public WeightedAverageStrategy(double threshold) {
		this(threshold, ErrorPolicy.PROPAGATE);
	}

	/**
	 * Create a weighted-average strategy with a caller-supplied acceptance bar and error policy.
	 * @param threshold the normalized bar the weighted average must reach, in {@code [0.0, 1.0]}
	 * @param errorPolicy policy for handling errors
	 * @throws IllegalArgumentException if the threshold is not a finite value in
	 * {@code [0.0, 1.0]}, or if {@code errorPolicy} is null
	 * @since 0.16.0
	 */
	public WeightedAverageStrategy(double threshold, ErrorPolicy errorPolicy) {
		this(threshold, errorPolicy, NotApplicablePolicy.REFUSE);
	}

	/**
	 * Create a weighted-average strategy with a caller-supplied acceptance bar and both
	 * policies.
	 * @param threshold the normalized bar the weighted average must reach, in {@code [0.0, 1.0]}
	 * @param errorPolicy policy for handling errors
	 * @param notApplicablePolicy policy for handling excluded judgments
	 * @throws IllegalArgumentException if the threshold is not a finite value in
	 * {@code [0.0, 1.0]}, or if either policy is null
	 * @since 0.17.0
	 */
	public WeightedAverageStrategy(double threshold, ErrorPolicy errorPolicy,
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

		double[] resolved = resolveWeights(population.inputCount(), weights);
		double inputWeight = 0.0;
		for (double weight : resolved) {
			inputWeight += weight;
		}
		if (inputWeight == 0.0) {
			throw new IllegalArgumentException(
					"All weights are zero, so no judge could influence the result; check the weight configuration");
		}

		if (population.hasPolicyExit()) {
			return population.policyExitAggregate(getName());
		}

		double weightedSum = 0.0;
		double eligibleWeight = 0.0;
		for (int i = 0; i < population.eligible().size(); i++) {
			double weight = resolved[population.eligibleIndices().get(i)];
			// Every eligible judgment is PASS or FAIL, so effectiveScore is always present.
			weightedSum += population.eligible().get(i).effectiveScore().orElseThrow() * weight;
			eligibleWeight += weight;
		}

		Map<String, Object> weightEvidence = Map.of(AggregationEvidence.INPUT_WEIGHT, portableTotal(inputWeight),
				AggregationEvidence.ELIGIBLE_WEIGHT, portableTotal(eligibleWeight));

		if (population.isEmpty() || eligibleWeight == 0.0) {
			return population.noResult(getName(), weightEvidence);
		}

		// Scores lie in [0.0, 1.0] and weights are not negative, so weightedSum never exceeds
		// eligibleWeight, and eligibleWeight never exceeds inputWeight: an overflow anywhere
		// shows up as an infinite total. Only then is the rescaled path taken. Any total that
		// fits in a double runs the original arithmetic unchanged, so its result is
		// bit-identical to what it was.
		double weightedAverage = Double.isInfinite(eligibleWeight) ? rescaledWeightedAverage(population, resolved)
				: weightedSum / eligibleWeight;

		Judgment aggregate = (weightedAverage >= this.threshold ? Judgment.builder().pass() : Judgment.builder().fail())
			.score(weightedAverage)
			.reasoning(String.format(
					"Weighted average: %.2f across %d applicable judge(s) (threshold: %.2f, result: %s)",
					weightedAverage, population.eligible().size(), this.threshold,
					weightedAverage >= this.threshold ? "pass" : "fail"))
			.build();
		return AggregationEvidence.attach(aggregate, population.evidence(getName())
			.put(AggregationEvidence.THRESHOLD, this.threshold)
			.put(AggregationEvidence.INPUT_WEIGHT, portableTotal(inputWeight))
			.put(AggregationEvidence.ELIGIBLE_WEIGHT, portableTotal(eligibleWeight))
			.build());
	}

	/**
	 * The weighted mean of the eligible judgments when their weight total overflows.
	 * <p>
	 * Each eligible weight is divided by the same power of two, chosen so that the largest
	 * becomes a value in {@code [1.0, 2.0)}. Scaling by a power of two is exact, so the
	 * weights keep their proportions and the totals stay finite.
	 * </p>
	 * @param population the resolved population, with at least one positive eligible weight
	 * @param resolved the resolved weights, indexed by submitted position
	 * @return the weighted mean, in {@code [0.0, 1.0]}
	 */
	private static double rescaledWeightedAverage(AggregationPopulation population, double[] resolved) {
		double largest = 0.0;
		for (int index : population.eligibleIndices()) {
			largest = Math.max(largest, resolved[index]);
		}
		int exponent = Math.getExponent(largest);
		double weightedSum = 0.0;
		double eligibleWeight = 0.0;
		for (int i = 0; i < population.eligible().size(); i++) {
			double weight = Math.scalb(resolved[population.eligibleIndices().get(i)], -exponent);
			weightedSum += population.eligible().get(i).effectiveScore().orElseThrow() * weight;
			eligibleWeight += weight;
		}
		return weightedSum / eligibleWeight;
	}

	/**
	 * A weight total as the evidence reports it: exact when it is finite, and
	 * {@link Double#MAX_VALUE} when the sum of finite weights exceeded it.
	 * @param total the computed total
	 * @return a finite, portable total
	 */
	private static double portableTotal(double total) {
		return Double.isInfinite(total) ? Double.MAX_VALUE : total;
	}

	private static double[] resolveWeights(int count, Map<String, Double> weights) {
		double[] resolved = new double[count];
		for (int i = 0; i < count; i++) {
			double weight = (weights == null) ? 1.0 : weights.getOrDefault(String.valueOf(i), 1.0);
			if (!Double.isFinite(weight)) {
				throw new IllegalArgumentException(
						String.format("Weight for judge %d must be finite, but was %s", i, weight));
			}
			if (weight < 0.0) {
				throw new IllegalArgumentException(
						String.format("Weight for judge %d must not be negative, but was %s", i, weight));
			}
			resolved[i] = weight;
		}
		return resolved;
	}

	@Override
	public String getName() {
		return "weightedAverage";
	}

	/**
	 * Declares the error policy and the threshold. Weights are the jury's configuration, not
	 * this strategy's, and appear on the jury's seats.
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
