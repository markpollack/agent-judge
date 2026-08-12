/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import io.github.markpollack.judge.result.Judgment;
import io.github.markpollack.judge.result.JudgmentStatus;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

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
	 * M5: applicable judges that split between PASS and FAIL reached no collective
	 * finding, so consensus reports ABSTAIN. Rejecting or escalating that disagreement is
	 * a downstream gate decision, not an aggregation conclusion.
	 */
	@Test
	void mixedApplicableVotesAbstain() {
		ConsensusStrategy strategy = new ConsensusStrategy();

		Judgment result = strategy.aggregate(List.of(booleanPass("Judge 1"), booleanFail("Judge 2")), Map.of());

		assertThat(result.status()).isEqualTo(JudgmentStatus.ABSTAIN);
		assertThat(result.score()).isNull();
		assertThat(result.reasoning()).contains("No consensus").contains("1 passed, 1 failed");
	}

	/**
	 * Disagreement and unanimous failure are different facts. They must differ in status,
	 * reasoning, and vote evidence rather than only in prose.
	 */
	@Test
	void disagreementIsDistinguishedFromUnanimousFailure() {
		ConsensusStrategy strategy = new ConsensusStrategy();

		Judgment disagreed = strategy
			.aggregate(List.of(booleanPass("Judge 1"), booleanPass("Judge 2"), booleanFail("Judge 3")), Map.of());
		Judgment unanimouslyFailed = strategy
			.aggregate(List.of(booleanFail("Judge 1"), booleanFail("Judge 2")), Map.of());

		assertThat(disagreed.status()).isEqualTo(JudgmentStatus.ABSTAIN);
		assertThat(disagreed.reasoning()).contains("No consensus").contains("2 passed, 1 failed");
		assertThat(evidence(disagreed)).containsEntry(AggregationEvidence.PASS_COUNT, 2)
			.containsEntry(AggregationEvidence.FAIL_COUNT, 1)
			.containsEntry(AggregationEvidence.ELIGIBLE_COUNT, 3);

		assertThat(unanimouslyFailed.status()).isEqualTo(JudgmentStatus.FAIL);
		assertThat(unanimouslyFailed.reasoning()).contains("Unanimous consensus");
		assertThat(evidence(unanimouslyFailed)).containsEntry(AggregationEvidence.PASS_COUNT, 0)
			.containsEntry(AggregationEvidence.FAIL_COUNT, 2);
	}

	/**
	 * A disagreement ABSTAIN and a no-applicable-judge ABSTAIN share a status, so the
	 * evidence has to keep them apart: disagreement has an eligible population and both
	 * vote counts, while a no-result abstention has neither.
	 */
	@Test
	void disagreementIsDistinguishedFromNoApplicableJudges() {
		ConsensusStrategy strategy = new ConsensusStrategy();

		Judgment disagreed = strategy.aggregate(List.of(booleanPass("Judge 1"), booleanFail("Judge 2")), Map.of());
		Judgment noneApplicable = strategy
			.aggregate(List.of(Judgment.abstain("n/a"), Judgment.abstain("n/a")), Map.of());

		assertThat(disagreed.status()).isEqualTo(JudgmentStatus.ABSTAIN);
		assertThat(noneApplicable.status()).isEqualTo(JudgmentStatus.ABSTAIN);

		assertThat(disagreed.reasoning()).contains("No consensus");
		assertThat(noneApplicable.reasoning()).contains("All 2 judge(s) abstained");

		assertThat(evidence(disagreed)).containsEntry(AggregationEvidence.ELIGIBLE_COUNT, 2)
			.containsEntry(AggregationEvidence.EXPLICIT_ABSTAIN_COUNT, 0)
			.containsEntry(AggregationEvidence.PASS_COUNT, 1)
			.containsEntry(AggregationEvidence.FAIL_COUNT, 1);
		assertThat(evidence(noneApplicable)).containsEntry(AggregationEvidence.ELIGIBLE_COUNT, 0)
			.containsEntry(AggregationEvidence.EXPLICIT_ABSTAIN_COUNT, 2)
			.doesNotContainKeys(AggregationEvidence.PASS_COUNT, AggregationEvidence.FAIL_COUNT);
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
	void disagreementAcrossScoredAndStatusOnlyJudgmentsAbstains() {
		ConsensusStrategy strategy = new ConsensusStrategy();

		List<Judgment> judgments = List.of(booleanPass("Judge 1"), failJudgment(0.3), booleanPass("Judge 3"));

		Judgment result = strategy.aggregate(judgments, Map.of());

		assertThat(result.status()).isEqualTo(JudgmentStatus.ABSTAIN);
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

		assertThat(result.status()).isEqualTo(JudgmentStatus.ABSTAIN);
	}

	@Test
	void oneMajorityFailIsNotConsensus() {
		ConsensusStrategy strategy = new ConsensusStrategy();

		// 2 fail, 1 pass → no consensus
		List<Judgment> judgments = List.of(booleanFail("Judge 1"), booleanFail("Judge 2"), booleanPass("Judge 3"));

		Judgment result = strategy.aggregate(judgments, Map.of());

		// A fail-leaning majority is still disagreement, not a collective failure.
		assertThat(result.status()).isEqualTo(JudgmentStatus.ABSTAIN);
		assertThat(result.reasoning()).contains("No consensus");
	}

	// ==================== M5 Truth Table ====================

	/**
	 * The complete consensus table for populations without errors. The error policy is
	 * varied deliberately: no row here may depend on it, and the M5 repair must not have
	 * made disagreement policy-sensitive.
	 */
	@ParameterizedTest(name = "{0} under {1}")
	@MethodSource("errorFreeTruthTable")
	@DisplayName("the consensus truth table holds under every error policy")
	void consensusTruthTableHoldsUnderEveryErrorPolicy(String row, ErrorPolicy policy, List<Judgment> judgments,
			JudgmentStatus expected) {
		Judgment result = new ConsensusStrategy(policy).aggregate(judgments, Map.of());

		assertThat(result.status()).as("%s under %s", row, policy).isEqualTo(expected);
	}

	private static Stream<Arguments> errorFreeTruthTable() {
		return Stream.of(ErrorPolicy.values())
			.flatMap(policy -> Stream.of(
					Arguments.of("all PASS", policy, List.of(booleanPass("Judge 1"), booleanPass("Judge 2")),
							JudgmentStatus.PASS),
					Arguments.of("all FAIL", policy, List.of(booleanFail("Judge 1"), booleanFail("Judge 2")),
							JudgmentStatus.FAIL),
					Arguments.of("PASS + ABSTAIN", policy, List.of(booleanPass("Judge 1"), Judgment.abstain("n/a")),
							JudgmentStatus.PASS),
					Arguments.of("FAIL + ABSTAIN", policy, List.of(booleanFail("Judge 1"), Judgment.abstain("n/a")),
							JudgmentStatus.FAIL),
					Arguments.of("PASS + FAIL", policy, List.of(booleanPass("Judge 1"), booleanFail("Judge 2")),
							JudgmentStatus.ABSTAIN),
					Arguments.of("all ABSTAIN", policy, List.of(Judgment.abstain("a"), Judgment.abstain("b")),
							JudgmentStatus.ABSTAIN)));
	}

	/**
	 * The ERROR row is the one the policy owns. TREAT_AS_FAIL is the interesting case: it
	 * puts a FAIL vote next to a PASS vote and so produces disagreement, which must reach
	 * ABSTAIN by the same rule as any other split panel.
	 */
	@ParameterizedTest
	@EnumSource(ErrorPolicy.class)
	@DisplayName("an errored judge follows the error policy, then the same consensus rule")
	void errorRowFollowsTheErrorPolicy(ErrorPolicy policy) {
		ConsensusStrategy strategy = new ConsensusStrategy(policy);

		Judgment passPlusError = strategy.aggregate(List.of(booleanPass("Judge 1"), Judgment.error("boom")), Map.of());
		Judgment failPlusError = strategy.aggregate(List.of(booleanFail("Judge 1"), Judgment.error("boom")), Map.of());
		Judgment allError = strategy.aggregate(List.of(Judgment.error("one"), Judgment.error("two")), Map.of());

		JudgmentStatus expectedPassPlusError = switch (policy) {
			case PROPAGATE -> JudgmentStatus.ERROR;
			// The converted FAIL disagrees with the PASS.
			case TREAT_AS_FAIL -> JudgmentStatus.ABSTAIN;
			case TREAT_AS_ABSTAIN, IGNORE -> JudgmentStatus.PASS;
		};
		JudgmentStatus expectedFailPlusError = switch (policy) {
			case PROPAGATE -> JudgmentStatus.ERROR;
			case TREAT_AS_FAIL, TREAT_AS_ABSTAIN, IGNORE -> JudgmentStatus.FAIL;
		};
		JudgmentStatus expectedAllError = switch (policy) {
			case PROPAGATE -> JudgmentStatus.ERROR;
			case TREAT_AS_FAIL -> JudgmentStatus.FAIL;
			case TREAT_AS_ABSTAIN, IGNORE -> JudgmentStatus.ABSTAIN;
		};

		assertThat(passPlusError.status()).as("PASS + ERROR under %s", policy).isEqualTo(expectedPassPlusError);
		assertThat(failPlusError.status()).as("FAIL + ERROR under %s", policy).isEqualTo(expectedFailPlusError);
		assertThat(allError.status()).as("all ERROR under %s", policy).isEqualTo(expectedAllError);
	}

	/**
	 * An errored judge converted to a FAIL vote produces disagreement, not a collective
	 * failure, and the evidence still says the vote came from an error.
	 */
	@Test
	void errorTreatedAsFailCanProduceDisagreement() {
		Judgment result = new ConsensusStrategy(ErrorPolicy.TREAT_AS_FAIL)
			.aggregate(List.of(booleanPass("Judge 1"), Judgment.error("boom")), Map.of());

		assertThat(result.status()).isEqualTo(JudgmentStatus.ABSTAIN);
		assertThat(result.reasoning()).contains("No consensus").contains("1 passed, 1 failed");
		assertThat(evidence(result)).containsEntry(AggregationEvidence.ERRORS_TREATED_AS_FAIL_COUNT, 1)
			.containsEntry(AggregationEvidence.ELIGIBLE_COUNT, 2);
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
