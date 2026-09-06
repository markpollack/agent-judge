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

import static io.github.markpollack.judge.JudgeTestFixtures.failJudgment;
import static io.github.markpollack.judge.JudgeTestFixtures.passJudgment;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link ConjunctiveStrategy}.
 *
 * <p>
 * The first test is the reason this class exists: the same seven scores that a mean passes,
 * the conjunction rejects. Everything else guards the edges around that.
 * </p>
 *
 * @author Mark Pollack
 * @since 0.16.0
 */
class ConjunctiveStrategyTest {

	/** {3,3,3,3,3,3,0} on a 0-3 rubric, normalized. One criterion missed entirely. */
	private static final List<Judgment> SIX_STRONG_ONE_MISSED = List.of(passJudgment(1.0), passJudgment(1.0),
			passJudgment(1.0), passJudgment(1.0), passJudgment(1.0), passJudgment(1.0), failJudgment(0.0));

	@Test
	void meanPassesTheRubricHoleAndTheConjunctionRejectsIt() {
		// The mean: 6 of 7 criteria perfect, one missed entirely -> 0.857, a comfortable pass.
		Judgment byMean = new AverageVotingStrategy().aggregate(SIX_STRONG_ONE_MISSED, Map.of());
		assertThat(byMean.status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(byMean.score()).isCloseTo(0.857, org.assertj.core.data.Offset.offset(0.001));

		// The conjunction, on identical input, against a bar of "every criterion >= 2/3".
		Judgment byConjunction = new ConjunctiveStrategy(2.0 / 3.0).aggregate(SIX_STRONG_ONE_MISSED, Map.of());
		assertThat(byConjunction.status()).isEqualTo(JudgmentStatus.FAIL);
		assertThat(byConjunction.score()).isEqualTo(0.0);
	}

	@Test
	void reportsTheBindingJudgmentByItsSubmittedIndex() {
		Judgment result = new ConjunctiveStrategy(0.5).aggregate(SIX_STRONG_ONE_MISSED, Map.of());

		assertThat(result.reasoning()).contains("binding judgment at index 6");
		assertThat(aggregation(result)).containsEntry(AggregationEvidence.BINDING_ELIGIBLE_INDEX, 6);
	}

	@Test
	void bindingIndexCountsSubmittedPositionNotSurvivingPosition() {
		// Two abstentions ahead of the low score: the survivor list shifts, the index must not.
		List<Judgment> judgments = List.of(Judgment.abstain("n/a"), Judgment.abstain("n/a"), passJudgment(0.9),
				failJudgment(0.1));

		Judgment result = new ConjunctiveStrategy(0.5).aggregate(judgments, Map.of());

		assertThat(result.status()).isEqualTo(JudgmentStatus.FAIL);
		assertThat(aggregation(result)).containsEntry(AggregationEvidence.BINDING_ELIGIBLE_INDEX, 3);
	}

	@Test
	void passesOnlyWhenEveryApplicableJudgmentClearsTheBar() {
		ConjunctiveStrategy strategy = new ConjunctiveStrategy(0.7);

		assertThat(strategy.aggregate(List.of(passJudgment(0.7), passJudgment(0.9)), Map.of()).status())
			.isEqualTo(JudgmentStatus.PASS);
		assertThat(strategy.aggregate(List.of(passJudgment(0.69), passJudgment(0.9)), Map.of()).status())
			.isEqualTo(JudgmentStatus.FAIL);
	}

	@Test
	void scoreIsTheMinimumNotTheMean() {
		Judgment result = new ConjunctiveStrategy(0.1).aggregate(List.of(passJudgment(0.9), passJudgment(0.3)),
				Map.of());

		assertThat(result.status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(result.score()).isEqualTo(0.3);
	}

	@Test
	void abstentionsLeaveThePopulationRatherThanScoringZero() {
		Judgment result = new ConjunctiveStrategy(0.5)
			.aggregate(List.of(passJudgment(0.8), Judgment.abstain("not applicable")), Map.of());

		assertThat(result.status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(result.score()).isEqualTo(0.8);
		assertThat(aggregation(result)).containsEntry(AggregationEvidence.ELIGIBLE_COUNT, 1)
			.containsEntry(AggregationEvidence.EXPLICIT_ABSTAIN_COUNT, 1);
	}

	@Test
	void anEmptyConjunctionAbstainsRatherThanPassingVacuously() {
		// The whole point of the strategy: "every one of nothing cleared the bar" is not a pass.
		Judgment result = new ConjunctiveStrategy(0.9)
			.aggregate(List.of(Judgment.abstain("n/a"), Judgment.abstain("n/a")), Map.of());

		assertThat(result.status()).isEqualTo(JudgmentStatus.ABSTAIN);
		assertThat(result.score()).isNull();
		assertThat(result.reasoning()).contains("All 2 judge(s) abstained");
	}

	@Test
	void errorsAreGovernedByTheErrorPolicy() {
		List<Judgment> withError = List.of(passJudgment(0.9), Judgment.error("model unavailable"));

		assertThat(new ConjunctiveStrategy(0.5).aggregate(withError, Map.of()).status())
			.as("PROPAGATE is the default and must not silently drop the errored judge")
			.isEqualTo(JudgmentStatus.ERROR);
		assertThat(new ConjunctiveStrategy(0.5, ErrorPolicy.TREAT_AS_FAIL).aggregate(withError, Map.of()).status())
			.isEqualTo(JudgmentStatus.FAIL);
		assertThat(new ConjunctiveStrategy(0.5, ErrorPolicy.IGNORE).aggregate(withError, Map.of()).status())
			.isEqualTo(JudgmentStatus.PASS);
	}

	@Test
	void recordsTheBarItAppliedSoAStoredVerdictCanBeAudited() {
		Judgment result = new ConjunctiveStrategy(0.75).aggregate(List.of(passJudgment(0.8)), Map.of());

		assertThat(aggregation(result)).containsEntry(AggregationEvidence.STRATEGY, "conjunctive")
			.containsEntry(AggregationEvidence.THRESHOLD, 0.75);
	}

	@Test
	void thresholdIsRequiredAndValidated() {
		assertThatThrownBy(() -> new ConjunctiveStrategy(Double.NaN)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("finite");
		assertThatThrownBy(() -> new ConjunctiveStrategy(1.5)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("between 0.0 and 1.0");
		assertThatThrownBy(() -> new ConjunctiveStrategy(-0.1)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("between 0.0 and 1.0");
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> aggregation(Judgment judgment) {
		return (Map<String, Object>) judgment.metadata().get(Judgment.AGGREGATION_KEY);
	}

}
