/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.markpollack.judge.result.Judgment;
import io.github.markpollack.judge.result.JudgmentStatus;

import static io.github.markpollack.judge.JudgeTestFixtures.passJudgment;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The acceptance bar on the three numeric strategies is the caller's to state.
 *
 * <p>
 * Each of these previously carried {@code private static final double THRESHOLD = 0.5} with
 * no constructor taking one, so a jury's PASS/FAIL was decided by a number the caller never
 * chose, on a normalized scale whose meaning is the caller's. The value itself is the
 * ecosystem convention and is retained as {@code DEFAULT_THRESHOLD}; what was missing was
 * the affordance to override it.
 * </p>
 *
 * @author Mark Pollack
 * @since 0.16.0
 */
class NumericStrategyThresholdTest {

	private static final List<Judgment> SCORES = List.of(passJudgment(0.6), passJudgment(0.6));

	@Test
	void defaultRemainsTheInheritedConventionOnEveryNumericStrategy() {
		assertThat(AverageVotingStrategy.DEFAULT_THRESHOLD).isEqualTo(0.5);
		assertThat(MedianVotingStrategy.DEFAULT_THRESHOLD).isEqualTo(0.5);
		assertThat(WeightedAverageStrategy.DEFAULT_THRESHOLD).isEqualTo(0.5);

		assertThat(new AverageVotingStrategy().getThreshold()).isEqualTo(0.5);
		assertThat(new MedianVotingStrategy().getThreshold()).isEqualTo(0.5);
		assertThat(new WeightedAverageStrategy().getThreshold()).isEqualTo(0.5);
		assertThat(new AverageVotingStrategy(ErrorPolicy.IGNORE).getThreshold()).isEqualTo(0.5);
	}

	@Test
	void aStatedBarChangesTheOutcomeOnTheSameScores() {
		// 0.6 clears the inherited 0.5 and misses a deliberately chosen 0.8.
		assertThat(new AverageVotingStrategy().aggregate(SCORES, Map.of()).status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(new AverageVotingStrategy(0.8).aggregate(SCORES, Map.of()).status()).isEqualTo(JudgmentStatus.FAIL);

		assertThat(new MedianVotingStrategy().aggregate(SCORES, Map.of()).status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(new MedianVotingStrategy(0.8).aggregate(SCORES, Map.of()).status()).isEqualTo(JudgmentStatus.FAIL);

		assertThat(new WeightedAverageStrategy().aggregate(SCORES, Map.of()).status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(new WeightedAverageStrategy(0.8).aggregate(SCORES, Map.of()).status())
			.isEqualTo(JudgmentStatus.FAIL);
	}

	@Test
	void theBarIsRecordedInTheEvidenceSoAStoredVerdictCanBeAudited() {
		assertThat(aggregation(new AverageVotingStrategy(0.8).aggregate(SCORES, Map.of())))
			.containsEntry(AggregationEvidence.THRESHOLD, 0.8);
		assertThat(aggregation(new MedianVotingStrategy(0.25).aggregate(SCORES, Map.of())))
			.containsEntry(AggregationEvidence.THRESHOLD, 0.25);
		assertThat(aggregation(new WeightedAverageStrategy(0.9).aggregate(SCORES, Map.of())))
			.containsEntry(AggregationEvidence.THRESHOLD, 0.9);
	}

	@Test
	void reasoningNamesTheBarThatWasApplied() {
		assertThat(new AverageVotingStrategy(0.8).aggregate(SCORES, Map.of()).reasoning())
			.contains("threshold: 0.80");
	}

	@Test
	void errorPolicyStillComposesWithAStatedBar() {
		List<Judgment> withError = List.of(passJudgment(0.6), Judgment.error("model unavailable"));

		assertThat(new AverageVotingStrategy(0.8, ErrorPolicy.IGNORE).aggregate(withError, Map.of()).status())
			.isEqualTo(JudgmentStatus.FAIL);
		assertThat(new AverageVotingStrategy(0.5, ErrorPolicy.IGNORE).aggregate(withError, Map.of()).status())
			.isEqualTo(JudgmentStatus.PASS);
	}

	@Test
	void anOutOfRangeBarIsRejectedAtConstruction() {
		assertThatThrownBy(() -> new AverageVotingStrategy(1.5)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new MedianVotingStrategy(-0.1)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new WeightedAverageStrategy(Double.NaN)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("finite");
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> aggregation(Judgment judgment) {
		return (Map<String, Object>) judgment.metadata().get(Judgment.AGGREGATION_KEY);
	}

}
