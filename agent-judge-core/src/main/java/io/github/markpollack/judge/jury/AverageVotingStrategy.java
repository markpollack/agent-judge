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
 * {@link ErrorPolicy} (default {@code PROPAGATE}). If nothing is eligible the result is
 * {@code ABSTAIN} rather than a manufactured failing score.
 * </p>
 *
 * <p>
 * The judgment passes if the mean is greater than or equal to 0.5.
 * </p>
 *
 * <p>
 * Example usage:
 * </p>
 * <pre>{@code
 * VotingStrategy strategy = new AverageVotingStrategy();
 * Judgment result = strategy.aggregate(judgments, Map.of());
 * }</pre>
 *
 * @author Mark Pollack
 * @since 0.1.0
 */
public class AverageVotingStrategy implements VotingStrategy {

	private static final double THRESHOLD = 0.5;

	private final ErrorPolicy errorPolicy;

	/**
	 * Create an average strategy with the default error policy.
	 */
	public AverageVotingStrategy() {
		this(ErrorPolicy.PROPAGATE);
	}

	/**
	 * Create an average strategy with a custom error policy.
	 * @param errorPolicy policy for handling errors
	 */
	public AverageVotingStrategy(ErrorPolicy errorPolicy) {
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
		double average = population.eligible()
			.stream()
			.mapToDouble(j -> j.effectiveScore().orElseThrow())
			.average()
			.orElseThrow();

		Judgment aggregate = (average >= THRESHOLD ? Judgment.builder().pass() : Judgment.builder().fail())
			.score(average)
			.reasoning(String.format("Average score: %.2f across %d applicable judge(s) (threshold: %.2f, result: %s)",
					average, population.eligible().size(), THRESHOLD, average >= THRESHOLD ? "pass" : "fail"))
			.build();
		return AggregationEvidence.attach(aggregate, population.evidence(getName()).build());
	}

	@Override
	public String getName() {
		return "average";
	}

}
