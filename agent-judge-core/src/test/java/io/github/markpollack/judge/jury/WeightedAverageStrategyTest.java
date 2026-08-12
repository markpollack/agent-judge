/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
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
 * Tests for {@link WeightedAverageStrategy}.
 *
 * @author Mark Pollack
 * @since 0.1.0
 */
class WeightedAverageStrategyTest {

	@Test
	void shouldCalculateWeightedAverage() {
		WeightedAverageStrategy strategy = new WeightedAverageStrategy();

		List<Judgment> judgments = List.of(passJudgment(0.8), // weight 0.3
				passJudgment(0.6) // weight 0.7
		);

		Map<String, Double> weights = Map.of("0", 0.3, "1", 0.7);

		Judgment result = strategy.aggregate(judgments, weights);

		// (0.8 * 0.3 + 0.6 * 0.7) / (0.3 + 0.7) = (0.24 + 0.42) / 1.0 = 0.66
		assertThat(result.status()).isEqualTo(JudgmentStatus.PASS);
				double score = result.score();
		assertThat(score).isCloseTo(0.66, org.assertj.core.data.Offset.offset(0.01));
	}

	@Test
	void shouldNormalizeWeights() {
		WeightedAverageStrategy strategy = new WeightedAverageStrategy();

		List<Judgment> judgments = List.of(passJudgment(0.8), passJudgment(0.6));

		// Weights don't sum to 1.0 - should be normalized
		Map<String, Double> weights = Map.of("0", 3.0, "1", 7.0);

		Judgment result = strategy.aggregate(judgments, weights);

		// (0.8 * 3.0 + 0.6 * 7.0) / (3.0 + 7.0) = (2.4 + 4.2) / 10.0 = 0.66
		double score = result.score();
		assertThat(score).isCloseTo(0.66, org.assertj.core.data.Offset.offset(0.01));
	}

	@Test
	void shouldHandleMissingWeights() {
		WeightedAverageStrategy strategy = new WeightedAverageStrategy();

		List<Judgment> judgments = List.of(passJudgment(0.8), passJudgment(0.6), passJudgment(0.4));

		// Only provide weight for first judgment - others default to 1.0
		Map<String, Double> weights = Map.of("0", 2.0);

		Judgment result = strategy.aggregate(judgments, weights);

		// (0.8 * 2.0 + 0.6 * 1.0 + 0.4 * 1.0) / (2.0 + 1.0 + 1.0) = (1.6 + 0.6 + 0.4) /
		// 4.0 = 0.65
		double score = result.score();
		assertThat(score).isCloseTo(0.65, org.assertj.core.data.Offset.offset(0.01));
	}

	@Test
	void emptyWeightsResolveToOneAndComputeInStrategy() {
		WeightedAverageStrategy strategy = new WeightedAverageStrategy();

		List<Judgment> judgments = List.of(passJudgment(0.8), passJudgment(0.6));

		Judgment result = strategy.aggregate(judgments, Map.of());

		// DELTA-10: equal weights reduce to the simple mean, but the computation stays in
		// this strategy rather than delegating to AverageVotingStrategy, so the evidence
		// names the strategy the caller actually chose.
		double score = result.score();
		assertThat(score).isEqualTo(0.7);
		assertThat(evidence(result)).containsEntry(AggregationEvidence.STRATEGY, "weightedAverage");
	}

	@Test
	void nullWeightsResolveToOneAndComputeInStrategy() {
		WeightedAverageStrategy strategy = new WeightedAverageStrategy();

		List<Judgment> judgments = List.of(passJudgment(0.8), passJudgment(0.6));

		Judgment result = strategy.aggregate(judgments, null);

		double score = result.score();
		assertThat(score).isEqualTo(0.7);
		assertThat(evidence(result)).containsEntry(AggregationEvidence.STRATEGY, "weightedAverage");
	}

	@Test
	void shouldHandleBooleanVerdicts() {
		WeightedAverageStrategy strategy = new WeightedAverageStrategy();

		List<Judgment> judgments = List.of(booleanPass("Judge 1"), // 1.0
				booleanFail("Judge 2") // 0.0
		);

		Map<String, Double> weights = Map.of("0", 0.7, "1", 0.3);

		Judgment result = strategy.aggregate(judgments, weights);

		// (1.0 * 0.7 + 0.0 * 0.3) / (0.7 + 0.3) = 0.7 / 1.0 = 0.7
		double score = result.score();
		assertThat(score).isEqualTo(0.7);
	}

	@Test
	void shouldHandleZeroWeight() {
		WeightedAverageStrategy strategy = new WeightedAverageStrategy();

		List<Judgment> judgments = List.of(passJudgment(0.8), passJudgment(0.2));

		// Second judgment has zero weight - should be ignored
		Map<String, Double> weights = Map.of("0", 1.0, "1", 0.0);

		Judgment result = strategy.aggregate(judgments, weights);

		// (0.8 * 1.0 + 0.2 * 0.0) / (1.0 + 0.0) = 0.8 / 1.0 = 0.8
		double score = result.score();
		assertThat(score).isEqualTo(0.8);
	}

	@Test
	void shouldFailWhenWeightedAverageBelowThreshold() {
		WeightedAverageStrategy strategy = new WeightedAverageStrategy();

		List<Judgment> judgments = List.of(passJudgment(0.8), // low weight
				failJudgment(0.2) // high weight
		);

		Map<String, Double> weights = Map.of("0", 0.2, "1", 0.8);

		Judgment result = strategy.aggregate(judgments, weights);

		// (0.8 * 0.2 + 0.2 * 0.8) / (0.2 + 0.8) = (0.16 + 0.16) / 1.0 = 0.32 < 0.5
		assertThat(result.status()).isEqualTo(JudgmentStatus.FAIL);
		double score = result.score();
		assertThat(score).isCloseTo(0.32, org.assertj.core.data.Offset.offset(0.01));
	}

	@Test
	void shouldHandleExactThreshold() {
		WeightedAverageStrategy strategy = new WeightedAverageStrategy();

		List<Judgment> judgments = List.of(passJudgment(1.0), failJudgment(0.0));

		// Equal weights → (1.0 + 0.0) / 2 = 0.5
		Map<String, Double> weights = Map.of("0", 1.0, "1", 1.0);

		Judgment result = strategy.aggregate(judgments, weights);

		// 0.5 >= 0.5 → PASS
		assertThat(result.status()).isEqualTo(JudgmentStatus.PASS);
		double score = result.score();
		assertThat(score).isEqualTo(0.5);
	}

	// ==================== Edge Cases ====================

	@Test
	void emptyJudgmentListShouldThrowException() {
		WeightedAverageStrategy strategy = new WeightedAverageStrategy();

		assertThatThrownBy(() -> strategy.aggregate(List.of(), Map.of())).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("empty");
	}

	@Test
	void nullJudgmentListShouldThrowException() {
		WeightedAverageStrategy strategy = new WeightedAverageStrategy();

		assertThatThrownBy(() -> strategy.aggregate(null, Map.of())).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void singleJudgmentShouldReturnItsScore() {
		WeightedAverageStrategy strategy = new WeightedAverageStrategy();

		Judgment result = strategy.aggregate(List.of(passJudgment(0.8)), Map.of("0", 5.0));

		// Single judgment with any weight → same score
		double score = result.score();
		assertThat(score).isEqualTo(0.8);
	}

	@Test
	void allZeroWeightsAreRejectedAsInvalidConfiguration() {
		WeightedAverageStrategy strategy = new WeightedAverageStrategy();

		List<Judgment> judgments = List.of(passJudgment(0.8), passJudgment(0.6));

		Map<String, Double> weights = Map.of("0", 0.0, "1", 0.0);

		// DELTA-10: this test previously pinned the defect as correct behaviour — 0/0
		// produced NaN, which slipped past range validation because every comparison
		// against NaN is false, yielding a FAIL whose reasoning read "NaN". A weight map
		// in which no judge can influence the result is a caller error, so it now fails
		// loudly at the call rather than producing a garbage score.
		assertThatThrownBy(() -> strategy.aggregate(judgments, weights))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("All weights are zero");
	}

	/**
	 * DELTA-10: distinct from the case above. Here the configuration is valid — some
	 * judge carries positive weight — but every positively weighted judge abstained, so
	 * there is genuinely nothing to average. That is a runtime no-result, not a caller
	 * error, and it abstains rather than throwing.
	 */
	@Test
	void positiveInputWeightButNoEligibleWeightAbstains() {
		WeightedAverageStrategy strategy = new WeightedAverageStrategy();

		Judgment result = strategy.aggregate(List.of(Judgment.abstain("Cannot evaluate"), passJudgment(0.8)),
				Map.of("0", 1.0, "1", 0.0));

		assertThat(result.status()).isEqualTo(JudgmentStatus.ABSTAIN);
		assertThat(result.score()).isNull();
		assertThat(evidence(result)).containsEntry(AggregationEvidence.INPUT_WEIGHT, 1.0)
			.containsEntry(AggregationEvidence.ELIGIBLE_WEIGHT, 0.0);
	}

	// ==================== Metadata Tests ====================

	@Test
	void nameIsTheStableTokenUsedInEvidence() {
		WeightedAverageStrategy strategy = new WeightedAverageStrategy();

		// DELTA-3: names are stable lower-camel-case tokens, so the identifier used in
		// diagnostics is the same one recorded in the aggregation evidence.
		assertThat(strategy.getName()).isEqualTo("weightedAverage");
		assertThat(evidence(strategy.aggregate(List.of(passJudgment(0.8)), Map.of())))
			.containsEntry(AggregationEvidence.STRATEGY, "weightedAverage");
	}


	@SuppressWarnings("unchecked")
	private static Map<String, Object> evidence(Judgment judgment) {
		return (Map<String, Object>) judgment.metadata().get(Judgment.AGGREGATION_KEY);
	}

}
