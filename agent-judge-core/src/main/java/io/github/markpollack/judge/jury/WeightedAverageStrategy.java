/*
 * Copyright 2024 Spring AI Community
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.markpollack.judge.jury;

import java.util.List;
import java.util.Map;

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
 * <p>
 * The judgment passes if the weighted mean is greater than or equal to 0.5.
 * </p>
 *
 * <p>
 * Example usage:
 * </p>
 * <pre>{@code
 * Map<String, Double> weights = Map.of("0", 0.3, "1", 0.7);
 * VotingStrategy strategy = new WeightedAverageStrategy();
 * Judgment result = strategy.aggregate(judgments, weights);
 * }</pre>
 *
 * @author Mark Pollack
 * @since 0.1.0
 */
public class WeightedAverageStrategy implements VotingStrategy {

	private static final double THRESHOLD = 0.5;

	private final ErrorPolicy errorPolicy;

	/**
	 * Create a weighted average strategy with the default error policy.
	 */
	public WeightedAverageStrategy() {
		this(ErrorPolicy.PROPAGATE);
	}

	/**
	 * Create a weighted average strategy with a custom error policy.
	 * @param errorPolicy policy for handling errors
	 */
	public WeightedAverageStrategy(ErrorPolicy errorPolicy) {
		this.errorPolicy = errorPolicy;
	}

	@Override
	public Judgment aggregate(List<Judgment> judgments, Map<String, Double> weights) {
		AggregationPopulation population = AggregationPopulation.resolve(judgments, this.errorPolicy);

		double[] resolved = resolveWeights(population.inputCount(), weights);
		double inputWeight = 0.0;
		for (double weight : resolved) {
			inputWeight += weight;
		}
		if (inputWeight == 0.0) {
			throw new IllegalArgumentException(
					"All weights are zero, so no judge could influence the result; check the weight configuration");
		}

		if (population.propagateError()) {
			return population.propagatedError(getName());
		}

		double weightedSum = 0.0;
		double eligibleWeight = 0.0;
		for (int i = 0; i < population.eligible().size(); i++) {
			double weight = resolved[population.eligibleIndices().get(i)];
			// Every eligible judgment is PASS or FAIL, so effectiveScore is always present.
			weightedSum += population.eligible().get(i).effectiveScore().orElseThrow() * weight;
			eligibleWeight += weight;
		}

		Map<String, Object> weightEvidence = Map.of(AggregationEvidence.INPUT_WEIGHT, inputWeight,
				AggregationEvidence.ELIGIBLE_WEIGHT, eligibleWeight);

		if (population.isEmpty() || eligibleWeight == 0.0) {
			return population.noResult(getName(), weightEvidence);
		}

		double weightedAverage = weightedSum / eligibleWeight;

		return Judgment.scored(weightedAverage)
			.passingAt(THRESHOLD)
			.because(String.format(
					"Weighted average: %.2f across %d applicable judge(s) (threshold: %.2f, result: %s)",
					weightedAverage, population.eligible().size(), THRESHOLD,
					weightedAverage >= THRESHOLD ? "pass" : "fail"))
			.aggregationEvidence(population.evidence(getName())
				.put(AggregationEvidence.INPUT_WEIGHT, inputWeight)
				.put(AggregationEvidence.ELIGIBLE_WEIGHT, eligibleWeight)
				.build())
			.build();
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

}
