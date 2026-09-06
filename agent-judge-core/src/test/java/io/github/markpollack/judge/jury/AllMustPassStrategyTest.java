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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link AllMustPassStrategy}.
 *
 * @author Mark Pollack
 * @since 0.16.0
 */
class AllMustPassStrategyTest {

	private final AllMustPassStrategy strategy = new AllMustPassStrategy();

	@Test
	void mixedPassAndFailIsARejection_whereConsensusAbstains() {
		List<Judgment> mixed = List.of(Judgment.pass("build succeeded"), Judgment.fail("coverage dropped"));

		// The distinction that justifies this class existing.
		assertThat(new ConsensusStrategy().aggregate(mixed, Map.of()).status())
			.as("consensus reports disagreement and leaves rejection to a gate")
			.isEqualTo(JudgmentStatus.ABSTAIN);
		assertThat(strategy.aggregate(mixed, Map.of()).status())
			.as("a definition of done has no notion of its requirements disagreeing")
			.isEqualTo(JudgmentStatus.FAIL);
	}

	@Test
	void everyApplicableRequirementMustPass() {
		assertThat(strategy.aggregate(List.of(Judgment.pass("a"), Judgment.pass("b")), Map.of()).status())
			.isEqualTo(JudgmentStatus.PASS);
		assertThat(strategy.aggregate(List.of(Judgment.fail("a"), Judgment.fail("b")), Map.of()).status())
			.isEqualTo(JudgmentStatus.FAIL);
	}

	@Test
	void anEmptyGateAbstainsRatherThanPassingVacuously() {
		// allMatch over an empty stream is true; a gate that lost its requirements must not
		// report that everything is done.
		Judgment result = strategy.aggregate(List.of(Judgment.abstain("not applicable"),
				Judgment.abstain("not applicable")), Map.of());

		assertThat(result.status()).isEqualTo(JudgmentStatus.ABSTAIN);
		assertThat(result.status()).isNotEqualTo(JudgmentStatus.PASS);
		assertThat(result.reasoning()).contains("All 2 judge(s) abstained");
	}

	@Test
	void carriesNoScore_becauseTheJudgesCarriedNone() {
		// The whole point: a Boolean gate must not be aggregated through effectiveScore().
		Judgment result = strategy.aggregate(List.of(Judgment.pass("a"), Judgment.pass("b")), Map.of());

		assertThat(result.score()).isNull();
		assertThat(result.status()).isEqualTo(JudgmentStatus.PASS);
	}

	@Test
	void abstainingJudgesLeaveThePopulation() {
		Judgment result = strategy
			.aggregate(List.of(Judgment.pass("applies"), Judgment.abstain("no security files changed")), Map.of());

		assertThat(result.status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(aggregation(result)).containsEntry(AggregationEvidence.ELIGIBLE_COUNT, 1)
			.containsEntry(AggregationEvidence.EXPLICIT_ABSTAIN_COUNT, 1)
			.containsEntry(AggregationEvidence.PASS_COUNT, 1)
			.containsEntry(AggregationEvidence.FAIL_COUNT, 0);
	}

	@Test
	void aRequirementThatCouldNotBeEvaluatedDoesNotQuietlyPass() {
		List<Judgment> withError = List.of(Judgment.pass("build succeeded"), Judgment.error("judge model unavailable"));

		assertThat(strategy.aggregate(withError, Map.of()).status()).isEqualTo(JudgmentStatus.ERROR);
		assertThat(new AllMustPassStrategy(ErrorPolicy.TREAT_AS_FAIL).aggregate(withError, Map.of()).status())
			.isEqualTo(JudgmentStatus.FAIL);
		assertThat(new AllMustPassStrategy(ErrorPolicy.IGNORE).aggregate(withError, Map.of()).status())
			.isEqualTo(JudgmentStatus.PASS);
	}

	@Test
	void reasoningCountsTheFailuresAgainstTheApplicableTotal() {
		Judgment result = strategy.aggregate(
				List.of(Judgment.pass("a"), Judgment.fail("b"), Judgment.fail("c"), Judgment.abstain("n/a")),
				Map.of());

		assertThat(result.reasoning()).isEqualTo("2 of 3 applicable requirement(s) failed");
		assertThat(aggregation(result)).containsEntry(AggregationEvidence.STRATEGY, "allMustPass");
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> aggregation(Judgment judgment) {
		return (Map<String, Object>) judgment.metadata().get(Judgment.AGGREGATION_KEY);
	}

}
