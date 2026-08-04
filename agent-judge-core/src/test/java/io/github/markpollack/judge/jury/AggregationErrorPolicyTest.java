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
import java.util.stream.Stream;

import io.github.markpollack.judge.result.Judgment;
import io.github.markpollack.judge.result.JudgmentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cross-strategy contract tests for every {@link ErrorPolicy}.
 *
 * @author Mark Pollack
 */
class AggregationErrorPolicyTest {

	@ParameterizedTest(name = "{0} / {1}")
	@MethodSource("strategyPolicyMatrix")
	@DisplayName("all strategies account for mixed populations under every error policy")
	void mixedPopulationMatrix(String strategyName, ErrorPolicy policy) {
		VotingStrategy strategy = strategy(strategyName, policy);
		List<Judgment> judgments = List.of(Judgment.pass("ok"), Judgment.error("boom"),
				Judgment.abstain("not applicable"));

		Judgment result = strategy.aggregate(judgments, Map.of());
		Map<String, Object> evidence = evidence(result);

		JudgmentStatus expectedStatus = switch (policy) {
			case PROPAGATE -> JudgmentStatus.ERROR;
			case TREAT_AS_FAIL -> isStatusCounting(strategyName) ? JudgmentStatus.FAIL : JudgmentStatus.PASS;
			case TREAT_AS_ABSTAIN, IGNORE -> JudgmentStatus.PASS;
		};
		int expectedEligible = switch (policy) {
			case PROPAGATE -> 0;
			case TREAT_AS_FAIL -> 2;
			case TREAT_AS_ABSTAIN, IGNORE -> 1;
		};

		assertThat(result.status()).isEqualTo(expectedStatus);
		assertThat(evidence).containsEntry(AggregationEvidence.STRATEGY, strategyName)
			.containsEntry(AggregationEvidence.ERROR_POLICY, policy.token())
			.containsEntry(AggregationEvidence.INPUT_COUNT, 3)
			.containsEntry(AggregationEvidence.ELIGIBLE_COUNT, expectedEligible)
			.containsEntry(AggregationEvidence.EXPLICIT_ABSTAIN_COUNT, 1)
			.containsEntry(AggregationEvidence.ERROR_COUNT, 1)
			.containsEntry(AggregationEvidence.IGNORED_ERROR_COUNT, policy == ErrorPolicy.IGNORE ? 1 : 0)
			.containsEntry(AggregationEvidence.ERRORS_TREATED_AS_ABSTAIN_COUNT,
					policy == ErrorPolicy.TREAT_AS_ABSTAIN ? 1 : 0)
			.containsEntry(AggregationEvidence.ERRORS_TREATED_AS_FAIL_COUNT,
					policy == ErrorPolicy.TREAT_AS_FAIL ? 1 : 0);
	}

	@ParameterizedTest(name = "{0} / {1}")
	@MethodSource("strategyPolicyMatrix")
	@DisplayName("all strategies handle all-error populations under every error policy")
	void allErrorMatrix(String strategyName, ErrorPolicy policy) {
		Judgment result = strategy(strategyName, policy)
			.aggregate(List.of(Judgment.error("one"), Judgment.error("two")), Map.of());

		JudgmentStatus expectedStatus = switch (policy) {
			case PROPAGATE -> JudgmentStatus.ERROR;
			case TREAT_AS_FAIL -> JudgmentStatus.FAIL;
			case TREAT_AS_ABSTAIN, IGNORE -> JudgmentStatus.ABSTAIN;
		};

		assertThat(result.status()).isEqualTo(expectedStatus);
		assertThat(evidence(result)).containsEntry(AggregationEvidence.INPUT_COUNT, 2)
			.containsEntry(AggregationEvidence.ERROR_COUNT, 2)
			.containsEntry(AggregationEvidence.EXPLICIT_ABSTAIN_COUNT, 0);
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("strategyNames")
	@DisplayName("PROPAGATE evidence is invariant under input order")
	void propagateEvidenceIsOrderIndependent(String strategyName) {
		VotingStrategy strategy = strategy(strategyName, ErrorPolicy.PROPAGATE);
		Judgment errorThenAbstain = strategy
			.aggregate(List.of(Judgment.error("boom"), Judgment.abstain("not applicable")), Map.of());
		Judgment abstainThenError = strategy
			.aggregate(List.of(Judgment.abstain("not applicable"), Judgment.error("boom")), Map.of());

		assertThat(errorThenAbstain.status()).isEqualTo(JudgmentStatus.ERROR);
		assertThat(errorThenAbstain.reasoning()).isEqualTo(abstainThenError.reasoning());
		assertThat(evidence(errorThenAbstain)).isEqualTo(evidence(abstainThenError))
			.containsEntry(AggregationEvidence.EXPLICIT_ABSTAIN_COUNT, 1)
			.containsEntry(AggregationEvidence.ERROR_COUNT, 1);
	}

	@Test
	@DisplayName("aggregation evidence is immutable after attachment")
	void aggregationEvidenceIsImmutable() {
		Judgment result = new AverageVotingStrategy().aggregate(List.of(Judgment.pass("ok")), Map.of());

		assertThatThrownBy(() -> evidence(result).put(AggregationEvidence.INPUT_COUNT, 99))
			.isInstanceOf(UnsupportedOperationException.class);
	}

	private static Stream<Arguments> strategyPolicyMatrix() {
		return strategyNames().flatMap(name -> Stream.of(ErrorPolicy.values()).map(policy -> Arguments.of(name, policy)));
	}

	private static Stream<String> strategyNames() {
		return Stream.of("majority", "consensus", "average", "median", "weightedAverage");
	}

	private static VotingStrategy strategy(String name, ErrorPolicy policy) {
		return switch (name) {
			case "majority" -> new MajorityVotingStrategy(TiePolicy.FAIL, policy);
			case "consensus" -> new ConsensusStrategy(policy);
			case "average" -> new AverageVotingStrategy(policy);
			case "median" -> new MedianVotingStrategy(policy);
			case "weightedAverage" -> new WeightedAverageStrategy(policy);
			default -> throw new IllegalArgumentException("Unknown strategy: " + name);
		};
	}

	private static boolean isStatusCounting(String strategyName) {
		return strategyName.equals("majority") || strategyName.equals("consensus");
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> evidence(Judgment judgment) {
		return (Map<String, Object>) judgment.metadata().get(Judgment.AGGREGATION_KEY);
	}

}
