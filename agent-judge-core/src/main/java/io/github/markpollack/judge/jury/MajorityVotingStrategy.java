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
import java.util.Locale;
import java.util.Map;

import io.github.markpollack.judge.result.Judgment;
import io.github.markpollack.judge.result.JudgmentStatus;

/**
 * Majority voting strategy: the outcome held by most applicable judges wins.
 *
 * <p>
 * A status-counting strategy. It reads {@link Judgment#status()}, the outcome of record,
 * and does not consult scores — a judge that passed casts one pass vote regardless of how
 * confidently it passed.
 * </p>
 *
 * <p>
 * Edge cases are governed by explicit policies rather than buried conditionals:
 * </p>
 * <ul>
 * <li>Ties: resolved by {@link TiePolicy} (default {@code FAIL}).</li>
 * <li>Errors: resolved by {@link ErrorPolicy} (default {@code PROPAGATE}).</li>
 * <li>Abstentions: excluded — a judge that does not apply casts no vote.</li>
 * <li>Nothing eligible: {@code ABSTAIN}, with evidence naming the cause.</li>
 * </ul>
 *
 * <p>
 * The aggregate carries no score. A majority verdict's meaning is its outcome; the vote
 * counts are evidence and live in the {@link AggregationEvidence} block, not in a
 * manufactured number a threshold could mistake for a quality assessment.
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
public class MajorityVotingStrategy implements VotingStrategy {

	private final TiePolicy tiePolicy;

	private final ErrorPolicy errorPolicy;

	/**
	 * Create majority voting strategy with default policies.
	 */
	public MajorityVotingStrategy() {
		this(TiePolicy.FAIL, ErrorPolicy.PROPAGATE);
	}

	/**
	 * Create majority voting strategy with custom policies.
	 * @param tiePolicy policy for handling ties
	 * @param errorPolicy policy for handling errors
	 */
	public MajorityVotingStrategy(TiePolicy tiePolicy, ErrorPolicy errorPolicy) {
		this.tiePolicy = tiePolicy;
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

		int passCount = (int) population.eligible().stream().filter(j -> j.status() == JudgmentStatus.PASS).count();
		int failCount = (int) population.eligible().stream().filter(j -> j.status() == JudgmentStatus.FAIL).count();

		JudgmentStatus status;
		String reasoning;
		if (passCount == failCount) {
			status = switch (this.tiePolicy) {
				case PASS -> JudgmentStatus.PASS;
				case FAIL -> JudgmentStatus.FAIL;
				case ABSTAIN -> JudgmentStatus.ABSTAIN;
			};
			reasoning = String.format("Tie vote: %d passed, %d failed (tie resolved as %s)", passCount, failCount,
					this.tiePolicy.name().toLowerCase(Locale.ROOT));
		}
		else {
			boolean majorityPass = passCount > failCount;
			status = majorityPass ? JudgmentStatus.PASS : JudgmentStatus.FAIL;
			reasoning = String.format("Majority vote: %d passed, %d failed (majority %s)", passCount, failCount,
					majorityPass ? "pass" : "fail");
		}

		Judgment aggregate = switch (status) {
			case PASS -> Judgment.builder().pass().reasoning(reasoning).build();
			case FAIL -> Judgment.builder().fail().reasoning(reasoning).build();
			case ABSTAIN -> Judgment.builder().abstain().reasoning(reasoning).build();
			case ERROR -> throw new IllegalStateException("Majority cannot produce ERROR after population resolution");
		};
		return AggregationEvidence.attach(aggregate, population.evidence(getName())
				.put(AggregationEvidence.PASS_COUNT, passCount)
				.put(AggregationEvidence.FAIL_COUNT, failCount)
				.build());
	}

	@Override
	public String getName() {
		return "majority";
	}

}
