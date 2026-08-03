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
 * The judgment passes if the median is greater than or equal to 0.5.
 * </p>
 *
 * <p>
 * Example usage:
 * </p>
 * <pre>{@code
 * VotingStrategy strategy = new MedianVotingStrategy();
 * Judgment result = strategy.aggregate(judgments, Map.of());
 * }</pre>
 *
 * @author Mark Pollack
 * @since 0.1.0
 */
public class MedianVotingStrategy implements VotingStrategy {

	private static final double THRESHOLD = 0.5;

	private final ErrorPolicy errorPolicy;

	/**
	 * Create a median strategy with the default error policy.
	 */
	public MedianVotingStrategy() {
		this(ErrorPolicy.PROPAGATE);
	}

	/**
	 * Create a median strategy with a custom error policy.
	 * @param errorPolicy policy for handling errors
	 */
	public MedianVotingStrategy(ErrorPolicy errorPolicy) {
		this.errorPolicy = errorPolicy;
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

		return Judgment.scored(median)
			.passingAt(THRESHOLD)
			.because(String.format("Median score: %.2f across %d applicable judge(s) (threshold: %.2f, result: %s)",
					median, size, THRESHOLD, median >= THRESHOLD ? "pass" : "fail"))
			.aggregationEvidence(population.evidence(getName()).build())
			.build();
	}

	@Override
	public String getName() {
		return "median";
	}

}
