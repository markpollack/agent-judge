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

import org.junit.jupiter.api.Test;
import io.github.markpollack.judge.result.Judgment;
import io.github.markpollack.judge.result.JudgmentStatus;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static io.github.markpollack.judge.JudgeTestFixtures.*;

/**
 * Tests for {@link ConsensusStrategy}.
 *
 * @author Mark Pollack
 * @since 0.1.0
 */
class ConsensusStrategyTest {

	@Test
	void shouldPassWhenUnanimousPass() {
		ConsensusStrategy strategy = new ConsensusStrategy();

		List<Judgment> judgments = List.of(booleanPass("Judge 1"), booleanPass("Judge 2"), booleanPass("Judge 3"));

		Judgment result = strategy.aggregate(judgments, Map.of());

		assertThat(result.status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(result.score()).isNull();
		assertThat(result.effectiveScore()).hasValue(1.0);
		assertThat(result.reasoning()).contains("Unanimous consensus");
		assertThat(result.reasoning()).contains("all 3 applicable judge(s) passed");
	}

	@Test
	void shouldFailWhenUnanimousFail() {
		ConsensusStrategy strategy = new ConsensusStrategy();

		List<Judgment> judgments = List.of(booleanFail("Judge 1"), booleanFail("Judge 2"), booleanFail("Judge 3"));

		Judgment result = strategy.aggregate(judgments, Map.of());

		assertThat(result.status()).isEqualTo(JudgmentStatus.FAIL);
		assertThat(result.score()).isNull();
		assertThat(result.effectiveScore()).hasValue(0.0);
		assertThat(result.reasoning()).contains("Unanimous consensus");
		assertThat(result.reasoning()).contains("all 3 applicable judge(s) failed");
	}

	/**
	 * Disagreement fails the unanimity requirement. Evidence and reasoning distinguish it
	 * from unanimous failure without overloading ABSTAIN.
	 */
	@Test
	void disagreementFailsAndIsDistinguishedFromUnanimousFailByEvidence() {
		ConsensusStrategy strategy = new ConsensusStrategy();

		Judgment disagreed = strategy
			.aggregate(List.of(booleanPass("Judge 1"), booleanPass("Judge 2"), booleanFail("Judge 3")), Map.of());
		Judgment unanimouslyFailed = strategy
			.aggregate(List.of(booleanFail("Judge 1"), booleanFail("Judge 2")), Map.of());

		assertThat(disagreed.status()).isEqualTo(JudgmentStatus.FAIL);
		assertThat(disagreed.reasoning()).contains("No consensus").contains("2 passed, 1 failed");
		assertThat(unanimouslyFailed.status()).isEqualTo(JudgmentStatus.FAIL);
		assertThat(unanimouslyFailed.reasoning()).contains("Unanimous consensus");
		assertThat(evidence(disagreed)).containsEntry(AggregationEvidence.PASS_COUNT, 2)
			.containsEntry(AggregationEvidence.FAIL_COUNT, 1);
	}

	@Test
	void shouldHandleSingleJudge() {
		ConsensusStrategy strategy = new ConsensusStrategy();

		Judgment result = strategy.aggregate(List.of(booleanPass("Only judge")), Map.of());

		// Single judge → unanimous → pass
		assertThat(result.status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(result.reasoning()).contains("all 1 applicable judge(s) passed");
	}

	@Test
	void shouldReachConsensusOverScoredJudgments() {
		ConsensusStrategy strategy = new ConsensusStrategy();

		List<Judgment> judgments = List.of(passJudgment(0.8), passJudgment(0.9), passJudgment(0.6));

		Judgment result = strategy.aggregate(judgments, Map.of());

		// DELTA-1: the scores are along for the ride. Consensus reads status, so what makes
		// this unanimous is that all three judges passed, not where their scores sit.
		assertThat(result.status()).isEqualTo(JudgmentStatus.PASS);
	}

	@Test
	void unanimousFailAmongScoredJudgments() {
		ConsensusStrategy strategy = new ConsensusStrategy();

		List<Judgment> judgments = List.of(failJudgment(0.3), failJudgment(0.2), failJudgment(0.1));

		Judgment result = strategy.aggregate(judgments, Map.of());

		assertThat(result.status()).isEqualTo(JudgmentStatus.FAIL);
		assertThat(result.reasoning()).contains("all 3 applicable judge(s) failed");
	}

	@Test
	void shouldHandleMixedNumericalAndBoolean() {
		ConsensusStrategy strategy = new ConsensusStrategy();

		List<Judgment> judgments = List.of(booleanPass("Judge 1"), passJudgment(0.8), booleanPass("Judge 3"));

		Judgment result = strategy.aggregate(judgments, Map.of());

		// A scored judgment and a status-only one are substitutable here: both declare PASS.
		assertThat(result.status()).isEqualTo(JudgmentStatus.PASS);
	}

	@Test
	void disagreementAcrossScoredAndStatusOnlyJudgmentsFails() {
		ConsensusStrategy strategy = new ConsensusStrategy();

		List<Judgment> judgments = List.of(booleanPass("Judge 1"), failJudgment(0.3), booleanPass("Judge 3"));

		Judgment result = strategy.aggregate(judgments, Map.of());

		assertThat(result.status()).isEqualTo(JudgmentStatus.FAIL);
		assertThat(result.reasoning()).contains("No consensus");
		assertThat(result.reasoning()).contains("2 passed, 1 failed");
	}

	@Test
	void lowScoresDoNotOverrideAPassingStatus() {
		ConsensusStrategy strategy = new ConsensusStrategy();

		// Both judges passed while scoring at the bottom of the range. The removed
		// implementation thresholded the score at 0.5 and would have called this a
		// unanimous FAIL; reading status is what makes it a PASS.
		List<Judgment> judgments = List.of(
				Judgment.builder().pass().score(0.1).reasoning("low but ok").build(),
				Judgment.builder().pass().score(0.2).reasoning("low but ok").build());

		Judgment result = strategy.aggregate(judgments, Map.of());

		assertThat(result.status()).isEqualTo(JudgmentStatus.PASS);
	}

	/**
	 * DELTA-2: an abstention is not a vote. It leaves the population rather than
	 * counting as a fail, so it cannot break the unanimity of the applicable judges.
	 * This is the case a judge that legitimately does not apply to every run depends on.
	 */
	@Test
	void abstentionDoesNotBreakUnanimity() {
		ConsensusStrategy strategy = new ConsensusStrategy();

		Judgment result = strategy.aggregate(
				List.of(booleanPass("Judge 1"), Judgment.abstain("Cannot evaluate"), booleanPass("Judge 3")), Map.of());

		// Was FAIL: the abstention's absent score fell through to a fail vote.
		assertThat(result.status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(result.reasoning()).contains("all 2 applicable judge(s) passed");
		assertThat(evidence(result)).containsEntry(AggregationEvidence.ELIGIBLE_COUNT, 2)
			.containsEntry(AggregationEvidence.EXPLICIT_ABSTAIN_COUNT, 1);
	}

	/**
	 * DELTA-2: an abstention alongside a fail is still a unanimous fail among the
	 * applicable judges — previously this reported "Unanimous consensus" only because
	 * the abstention had been miscounted as a second fail vote.
	 */
	@Test
	void abstentionAlongsideFailIsUnanimousFail() {
		ConsensusStrategy strategy = new ConsensusStrategy();

		Judgment result = strategy.aggregate(List.of(booleanFail("Judge 1"), Judgment.abstain("Cannot evaluate")),
				Map.of());

		assertThat(result.status()).isEqualTo(JudgmentStatus.FAIL);
		assertThat(result.reasoning()).contains("all 1 applicable judge(s) failed");
	}

	@Test
	void shouldIgnoreWeightsParameter() {
		ConsensusStrategy strategy = new ConsensusStrategy();

		List<Judgment> judgments = List.of(booleanPass("Judge 1"), booleanPass("Judge 2"));

		// Weights are ignored in ConsensusStrategy
		Map<String, Double> weights = Map.of("0", 100.0, "1", 1.0);

		Judgment result = strategy.aggregate(judgments, weights);

		// Unanimous pass regardless of weights
		assertThat(result.status()).isEqualTo(JudgmentStatus.PASS);
	}

	// ==================== Edge Cases ====================

	@Test
	void emptyJudgmentListShouldThrowException() {
		ConsensusStrategy strategy = new ConsensusStrategy();

		assertThatThrownBy(() -> strategy.aggregate(List.of(), Map.of())).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("empty");
	}

	@Test
	void nullJudgmentListShouldThrowException() {
		ConsensusStrategy strategy = new ConsensusStrategy();

		assertThatThrownBy(() -> strategy.aggregate(null, Map.of())).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void oneMajorityIsNotConsensus() {
		ConsensusStrategy strategy = new ConsensusStrategy();

		// 2 pass, 1 fail → no consensus
		List<Judgment> judgments = List.of(booleanPass("Judge 1"), booleanPass("Judge 2"), booleanFail("Judge 3"));

		Judgment result = strategy.aggregate(judgments, Map.of());

		assertThat(result.status()).isEqualTo(JudgmentStatus.FAIL);
	}

	@Test
	void oneMajorityFailIsNotConsensus() {
		ConsensusStrategy strategy = new ConsensusStrategy();

		// 2 fail, 1 pass → no consensus
		List<Judgment> judgments = List.of(booleanFail("Judge 1"), booleanFail("Judge 2"), booleanPass("Judge 3"));

		Judgment result = strategy.aggregate(judgments, Map.of());

		// A fail-leaning majority still fails the unanimity requirement.
		assertThat(result.status()).isEqualTo(JudgmentStatus.FAIL);
		assertThat(result.reasoning()).contains("No consensus");
	}

	// ==================== Metadata Tests ====================

	/**
	 * DELTA-3: strategy names are stable lower-camel-case tokens, so the identifier in
	 * diagnostics is the same one recorded in the aggregation evidence.
	 */
	@Test
	void nameIsTheStableTokenUsedInEvidence() {
		ConsensusStrategy strategy = new ConsensusStrategy();

		assertThat(strategy.getName()).isEqualTo("consensus");
		assertThat(evidence(strategy.aggregate(List.of(booleanPass("Judge 1")), Map.of())))
			.containsEntry(AggregationEvidence.STRATEGY, "consensus");
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> evidence(Judgment judgment) {
		return (Map<String, Object>) judgment.metadata().get(Judgment.AGGREGATION_KEY);
	}

}
