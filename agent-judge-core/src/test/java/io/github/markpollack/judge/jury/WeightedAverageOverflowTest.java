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

import static io.github.markpollack.judge.JudgeTestFixtures.booleanFail;
import static io.github.markpollack.judge.JudgeTestFixtures.booleanPass;
import static io.github.markpollack.judge.JudgeTestFixtures.failJudgment;
import static io.github.markpollack.judge.JudgeTestFixtures.passJudgment;
import static io.github.markpollack.judge.JudgeTestFixtures.simpleContext;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Finite weights whose total overflows a {@code double} still produce the weighted mean.
 *
 * <p>
 * Every weight here is individually legal: finite and not negative. Their total, however,
 * exceeds {@link Double#MAX_VALUE}. The ordinary arithmetic then divided {@code Infinity} by
 * {@code Infinity}, and the resulting {@code NaN} score, or the {@code Infinity} weight in the
 * evidence, was refused on the way into the {@link Judgment}. The exception escaped
 * {@code SimpleJury.vote()}.
 * </p>
 *
 * <p>
 * In the overflow case a weight total that exceeds the largest finite {@code double} is
 * reported in the evidence as {@link Double#MAX_VALUE}, so the evidence stays a finite,
 * portable number. A total that did not overflow is reported exactly.
 * </p>
 *
 * @author Mark Pollack
 * @since 0.17.0
 */
class WeightedAverageOverflowTest {

	private static final double MAX = Double.MAX_VALUE;

	private static final List<String> WEIGHTED_EVIDENCE_KEYS = List.of(AggregationEvidence.STRATEGY,
			AggregationEvidence.ERROR_POLICY, AggregationEvidence.NOT_APPLICABLE_POLICY,
			AggregationEvidence.INPUT_COUNT, AggregationEvidence.ELIGIBLE_COUNT,
			AggregationEvidence.EXPLICIT_ABSTAIN_COUNT, AggregationEvidence.NOT_APPLICABLE_COUNT,
			AggregationEvidence.ERROR_COUNT, AggregationEvidence.IGNORED_ERROR_COUNT,
			AggregationEvidence.ERRORS_TREATED_AS_ABSTAIN_COUNT, AggregationEvidence.ERRORS_TREATED_AS_FAIL_COUNT,
			AggregationEvidence.NOT_APPLICABLE_TREATED_AS_FAIL_COUNT, AggregationEvidence.ERROR_CODE_COUNTS,
			AggregationEvidence.THRESHOLD, AggregationEvidence.INPUT_WEIGHT, AggregationEvidence.ELIGIBLE_WEIGHT);

	@Test
	void twoJudgesWeightedMaxValueProduceTheirWeightedMean() {
		Judgment result = new WeightedAverageStrategy().aggregate(List.of(passJudgment(0.8), failJudgment(0.4)),
				Map.of("0", MAX, "1", MAX));

		assertThat(result.status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(result.score()).isCloseTo(0.6, within(1e-15));
		assertThat(evidence(result).keySet()).containsExactlyElementsOf(WEIGHTED_EVIDENCE_KEYS);
		assertThat(evidence(result)).containsEntry(AggregationEvidence.INPUT_WEIGHT, MAX)
			.containsEntry(AggregationEvidence.ELIGIBLE_WEIGHT, MAX)
			.containsEntry(AggregationEvidence.THRESHOLD, 0.5)
			.containsEntry(AggregationEvidence.INPUT_COUNT, 2)
			.containsEntry(AggregationEvidence.ELIGIBLE_COUNT, 2);
	}

	@Test
	void unequalOverflowingWeightsKeepTheirProportions() {
		// MAX : MAX/2 is 2 : 1, so a PASS and a FAIL average to 2/3.
		Judgment result = new WeightedAverageStrategy().aggregate(List.of(booleanPass("a"), booleanFail("b")),
				Map.of("0", MAX, "1", MAX / 2));

		assertThat(result.status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(result.score()).isCloseTo(2.0 / 3.0, within(1e-15));

		Judgment reversed = new WeightedAverageStrategy().aggregate(List.of(booleanPass("a"), booleanFail("b")),
				Map.of("0", MAX / 2, "1", MAX));
		assertThat(reversed.status()).isEqualTo(JudgmentStatus.FAIL);
		assertThat(reversed.score()).isCloseTo(1.0 / 3.0, within(1e-15));
	}

	@Test
	void manyLargeWeightsWithATinyOneStillAverageCorrectly() {
		// Three weights of MAX/2 overflow; the tiny weight is negligible beside them.
		Judgment result = new WeightedAverageStrategy().aggregate(
				List.of(passJudgment(1.0), passJudgment(0.5), failJudgment(0.0), passJudgment(1.0)),
				Map.of("0", MAX / 2, "1", MAX / 2, "2", MAX / 2, "3", Double.MIN_VALUE));

		assertThat(result.score()).isCloseTo(0.5, within(1e-15));
		assertThat(evidence(result)).containsEntry(AggregationEvidence.INPUT_WEIGHT, MAX)
			.containsEntry(AggregationEvidence.ELIGIBLE_WEIGHT, MAX);
	}

	@Test
	void anOverflowingInputTotalWithAFiniteEligibleTotalReportsTheEligibleTotalExactly() {
		// The abstaining judge's weight pushes the input total past MAX; the eligible total is
		// MAX/2 and is reported as computed.
		Judgment result = new WeightedAverageStrategy().aggregate(
				List.of(Judgment.abstain("not applicable"), passJudgment(0.8), failJudgment(0.2)),
				Map.of("0", MAX, "1", MAX / 4, "2", MAX / 4));

		assertThat(result.status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(result.score()).isEqualTo(0.5);
		assertThat(evidence(result)).containsEntry(AggregationEvidence.INPUT_WEIGHT, MAX)
			.containsEntry(AggregationEvidence.ELIGIBLE_WEIGHT, MAX / 2);
	}

	@Test
	void anOverflowingInputTotalWithNothingEligibleAbstainsWithFiniteEvidence() {
		Judgment result = new WeightedAverageStrategy().aggregate(
				List.of(Judgment.abstain("not applicable"), Judgment.abstain("not applicable")),
				Map.of("0", MAX, "1", MAX));

		assertThat(result.status()).isEqualTo(JudgmentStatus.ABSTAIN);
		assertThat(result.score()).isNull();
		assertThat(evidence(result)).containsEntry(AggregationEvidence.INPUT_WEIGHT, MAX)
			.containsEntry(AggregationEvidence.ELIGIBLE_WEIGHT, 0.0);
	}

	@Test
	void anErrorTreatedAsFailParticipatesWithItsOverflowingWeight() {
		Judgment result = new WeightedAverageStrategy(ErrorPolicy.TREAT_AS_FAIL)
			.aggregate(List.of(Judgment.error("judge threw"), booleanPass("ok")), Map.of("0", MAX, "1", MAX));

		assertThat(result.status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(result.score()).isCloseTo(0.5, within(1e-15));
		assertThat(evidence(result)).containsEntry(AggregationEvidence.ERRORS_TREATED_AS_FAIL_COUNT, 1)
			.containsEntry(AggregationEvidence.INPUT_WEIGHT, MAX)
			.containsEntry(AggregationEvidence.ELIGIBLE_WEIGHT, MAX);
	}

	@Test
	void aSimpleJuryWithMaxValueWeightsVotesNormally() {
		SimpleJury jury = SimpleJury.builder()
			.judge(context -> passJudgment(0.9), MAX)
			.judge(context -> failJudgment(0.3), MAX)
			.votingStrategy(new WeightedAverageStrategy())
			.parallel(false)
			.build();

		Verdict verdict = jury.vote(simpleContext("overflowing weights"));

		assertThat(verdict.aggregated().status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(verdict.aggregated().score()).isCloseTo(0.6, within(1e-15));
		assertThat(verdict.individual()).hasSize(2);
		assertThat(evidence(verdict.aggregated())).containsEntry(AggregationEvidence.INPUT_WEIGHT, MAX)
			.containsEntry(AggregationEvidence.ELIGIBLE_WEIGHT, MAX);
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> evidence(Judgment judgment) {
		return (Map<String, Object>) judgment.metadata().get(Judgment.AGGREGATION_KEY);
	}

}
