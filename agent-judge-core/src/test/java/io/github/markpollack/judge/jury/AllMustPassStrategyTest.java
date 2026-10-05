/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury;

import io.github.markpollack.judge.voting.AggregationEvidence;
import io.github.markpollack.judge.voting.AllEligiblePassStrategy;
import io.github.markpollack.judge.voting.ConsensusStrategy;
import io.github.markpollack.judge.voting.ErrorHandling;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.markpollack.judge.judgment.Judgment;
import io.github.markpollack.judge.judgment.JudgmentStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link AllEligiblePassStrategy}.
 *
 * @author Mark Pollack
 * @since 0.16.0
 */
class AllEligiblePassStrategyTest {

	private final AllEligiblePassStrategy strategy = new AllEligiblePassStrategy();

	@Test
	void mixedPassAndFailIsARejection_whereConsensusAbstains() {
		List<Judgment> mixed = List.of(Judgment.pass("build succeeded"), Judgment.fail("coverage dropped"));

		// The distinction that justifies this class existing.
		assertThat(new ConsensusStrategy().aggregate(io.github.markpollack.judge.voting.Ballots.of(mixed)).status())
			.as("consensus reports disagreement and leaves rejection to a gate")
			.isEqualTo(JudgmentStatus.ABSTAIN);
		assertThat(strategy.aggregate(io.github.markpollack.judge.voting.Ballots.of(mixed)).status())
			.as("a definition of done has no notion of its requirements disagreeing")
			.isEqualTo(JudgmentStatus.FAIL);
	}

	@Test
	void everyApplicableRequirementMustPass() {
		assertThat(strategy
			.aggregate(io.github.markpollack.judge.voting.Ballots.of(List.of(Judgment.pass("a"), Judgment.pass("b"))))
			.status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(strategy
			.aggregate(io.github.markpollack.judge.voting.Ballots.of(List.of(Judgment.fail("a"), Judgment.fail("b"))))
			.status()).isEqualTo(JudgmentStatus.FAIL);
	}

	@Test
	void anEmptyGateAbstainsRatherThanPassingVacuously() {
		// allMatch over an empty stream is true; a gate that lost its requirements must
		// not
		// report that everything is done.
		Judgment result = strategy.aggregate(io.github.markpollack.judge.voting.Ballots
			.of(List.of(Judgment.abstain("not applicable"), Judgment.abstain("not applicable"))));

		assertThat(result.status()).isEqualTo(JudgmentStatus.ABSTAIN);
		assertThat(result.status()).isNotEqualTo(JudgmentStatus.PASS);
		assertThat(result.reasoning()).contains("All 2 judge(s) abstained");
	}

	@Test
	void carriesNoScore_becauseTheJudgesCarriedNone() {
		// The whole point: a Boolean gate must not be judgment through effectiveScore().
		Judgment result = strategy
			.aggregate(io.github.markpollack.judge.voting.Ballots.of(List.of(Judgment.pass("a"), Judgment.pass("b"))));

		assertThat(result.score()).isNull();
		assertThat(result.status()).isEqualTo(JudgmentStatus.PASS);
	}

	@Test
	void abstainingJudgesLeaveThePopulation() {
		Judgment result = strategy.aggregate(io.github.markpollack.judge.voting.Ballots
			.of(List.of(Judgment.pass("applies"), Judgment.abstain("no security files changed"))));

		assertThat(result.status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(aggregation(result)).containsEntry(AggregationEvidence.ELIGIBLE_COUNT, 1)
			.containsEntry(AggregationEvidence.EXPLICIT_ABSTAIN_COUNT, 1)
			.containsEntry(AggregationEvidence.PASS_COUNT, 1)
			.containsEntry(AggregationEvidence.FAIL_COUNT, 0);
	}

	@Test
	void aRequirementThatCouldNotBeEvaluatedDoesNotQuietlyPass() {
		List<Judgment> withError = List.of(Judgment.pass("build succeeded"), Judgment.error("judge model unavailable"));

		assertThat(strategy.aggregate(io.github.markpollack.judge.voting.Ballots.of(withError)).status())
			.isEqualTo(JudgmentStatus.ERROR);
		assertThat(new AllEligiblePassStrategy(ErrorHandling.TREAT_AS_FAIL)
			.aggregate(io.github.markpollack.judge.voting.Ballots.of(withError))
			.status()).isEqualTo(JudgmentStatus.FAIL);
		assertThat(new AllEligiblePassStrategy(ErrorHandling.IGNORE)
			.aggregate(io.github.markpollack.judge.voting.Ballots.of(withError))
			.status()).isEqualTo(JudgmentStatus.PASS);
	}

	@Test
	void reasoningCountsTheFailuresAgainstTheApplicableTotal() {
		Judgment result = strategy.aggregate(io.github.markpollack.judge.voting.Ballots
			.of(List.of(Judgment.pass("a"), Judgment.fail("b"), Judgment.fail("c"), Judgment.abstain("n/a"))));

		assertThat(result.reasoning()).isEqualTo("2 of 3 applicable requirement(s) failed");
		assertThat(aggregation(result)).containsEntry(AggregationEvidence.STRATEGY, "allMustPass");
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> aggregation(Judgment judgment) {
		return (Map<String, Object>) judgment.metadata().get(Judgment.AGGREGATION_KEY);
	}

}
