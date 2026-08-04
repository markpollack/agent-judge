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
import io.github.markpollack.judge.result.JudgmentStatus;

/**
 * Consensus voting strategy: every applicable judge must reach the same verdict.
 *
 * <p>
 * A status-counting strategy. It reads {@link Judgment#status()}, the outcome of record.
 * </p>
 *
 * <p>
 * {@link JudgmentStatus#ABSTAIN} means "not applicable to this run" — it is not a vote at
 * all, so it is excluded from the population and consensus is computed over the applicable
 * judges. A judge added precisely because it cannot evaluate every case must not be able to
 * break unanimity by declining.
 * </p>
 *
 * <table border="1">
 * <caption>Outcomes</caption>
 * <tr><th>Inputs</th><th>Result</th></tr>
 * <tr><td>all PASS</td><td>PASS</td></tr>
 * <tr><td>all FAIL</td><td>FAIL</td></tr>
 * <tr><td>PASS + ABSTAIN</td><td>PASS</td></tr>
 * <tr><td>FAIL + ABSTAIN</td><td>FAIL</td></tr>
 * <tr><td>PASS + FAIL</td><td>FAIL — unanimity was not reached</td></tr>
 * <tr><td>all ABSTAIN</td><td>ABSTAIN</td></tr>
 * <tr><td>any ERROR</td><td>per {@link ErrorPolicy}, default PROPAGATE</td></tr>
 * </table>
 *
 * <p>
 * Disagreement yields {@code FAIL}: every applicable judge completed, but the unanimity
 * requirement was not met. The reasoning and pass/fail evidence distinguish disagreement
 * from unanimous failure. {@code ABSTAIN} remains reserved for a population with no
 * applicable finding.
 * </p>
 *
 * <p>
 * Note this is independent of {@link CascadedJury} escalation, which inspects a tier's
 * <em>individual</em> judgments rather than its aggregate. A tier using
 * {@link TierPolicy#ACCEPT_ON_ALL_PASS} still escalates when any judge abstains.
 * </p>
 *
 * <p>
 * Example usage:
 * </p>
 * <pre>{@code
 * VotingStrategy strategy = new ConsensusStrategy();
 * Judgment result = strategy.aggregate(judgments, Map.of());
 * }</pre>
 *
 * @author Mark Pollack
 * @since 0.1.0
 */
public class ConsensusStrategy implements VotingStrategy {

	private final ErrorPolicy errorPolicy;

	/**
	 * Create a consensus strategy with the default error policy.
	 */
	public ConsensusStrategy() {
		this(ErrorPolicy.PROPAGATE);
	}

	/**
	 * Create a consensus strategy with a custom error policy.
	 * @param errorPolicy policy for handling errors
	 */
	public ConsensusStrategy(ErrorPolicy errorPolicy) {
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

		int eligibleCount = population.eligible().size();
		int passCount = (int) population.eligible().stream().filter(j -> j.status() == JudgmentStatus.PASS).count();
		int failCount = (int) population.eligible().stream().filter(j -> j.status() == JudgmentStatus.FAIL).count();

		JudgmentStatus status;
		String reasoning;
		if (passCount == eligibleCount) {
			status = JudgmentStatus.PASS;
			reasoning = String.format("Unanimous consensus: all %d applicable judge(s) passed", eligibleCount);
		}
		else if (failCount == eligibleCount) {
			status = JudgmentStatus.FAIL;
			reasoning = String.format("Unanimous consensus: all %d applicable judge(s) failed", eligibleCount);
		}
		else {
			status = JudgmentStatus.FAIL;
			reasoning = String.format("No consensus: %d passed, %d failed among %d applicable judge(s)", passCount,
					failCount, eligibleCount);
		}

		Judgment aggregate = switch (status) {
			case PASS -> Judgment.builder().pass().reasoning(reasoning).build();
			case FAIL -> Judgment.builder().fail().reasoning(reasoning).build();
			case ABSTAIN, ERROR -> throw new IllegalStateException("Consensus produced an unexpected status: " + status);
		};
		return AggregationEvidence.attach(aggregate, population.evidence(getName())
				.put(AggregationEvidence.PASS_COUNT, passCount)
				.put(AggregationEvidence.FAIL_COUNT, failCount)
				.build());
	}

	@Override
	public String getName() {
		return "consensus";
	}

}
