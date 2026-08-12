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
 * Tests for {@link MajorityVotingStrategy}.
 *
 * @author Mark Pollack
 * @since 0.1.0
 */
class MajorityVotingStrategyTest {

	@Test
	void shouldReturnPassWhenPassesOutnumberFails() {
		MajorityVotingStrategy strategy = new MajorityVotingStrategy(TiePolicy.FAIL, ErrorPolicy.TREAT_AS_FAIL);

		List<Judgment> judgments = List.of(booleanPass("Judge 1 passed"), booleanPass("Judge 2 passed"),
				booleanFail("Judge 3 failed"));

		Judgment result = strategy.aggregate(judgments, Map.of());

		assertThat(result.status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(result.reasoning()).contains("2 passed");
	}

	@Test
	void shouldReturnFailWhenFailsOutnumberPasses() {
		MajorityVotingStrategy strategy = new MajorityVotingStrategy(TiePolicy.PASS, ErrorPolicy.TREAT_AS_FAIL);

		List<Judgment> judgments = List.of(booleanPass("Judge 1 passed"), booleanFail("Judge 2 failed"),
				booleanFail("Judge 3 failed"));

		Judgment result = strategy.aggregate(judgments, Map.of());

		assertThat(result.status()).isEqualTo(JudgmentStatus.FAIL);
		assertThat(result.reasoning()).contains("2 failed");
	}

	// ==================== TiePolicy Tests ====================

	@Test
	void tieShouldUsePassPolicyWhenConfigured() {
		MajorityVotingStrategy strategy = new MajorityVotingStrategy(TiePolicy.PASS, ErrorPolicy.TREAT_AS_FAIL);

		List<Judgment> judgments = List.of(booleanPass("Judge 1"), booleanFail("Judge 2"));

		Judgment result = strategy.aggregate(judgments, Map.of());

		assertThat(result.status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(result.reasoning()).contains("tie");
	}

	@Test
	void tieShouldUseFailPolicyWhenConfigured() {
		MajorityVotingStrategy strategy = new MajorityVotingStrategy(TiePolicy.FAIL, ErrorPolicy.TREAT_AS_FAIL);

		List<Judgment> judgments = List.of(booleanPass("Judge 1"), booleanFail("Judge 2"));

		Judgment result = strategy.aggregate(judgments, Map.of());

		assertThat(result.status()).isEqualTo(JudgmentStatus.FAIL);
		assertThat(result.reasoning()).contains("tie");
	}

	@Test
	void tieShouldUseAbstainPolicyWhenConfigured() {
		MajorityVotingStrategy strategy = new MajorityVotingStrategy(TiePolicy.ABSTAIN, ErrorPolicy.TREAT_AS_FAIL);

		List<Judgment> judgments = List.of(booleanPass("Judge 1"), booleanFail("Judge 2"));

		Judgment result = strategy.aggregate(judgments, Map.of());

		assertThat(result.status()).isEqualTo(JudgmentStatus.ABSTAIN);
		assertThat(result.reasoning()).contains("tie");
	}

	// ==================== ErrorPolicy Tests ====================

	@Test
	void allErrorsShouldTreatAsFailWhenConfigured() {
		MajorityVotingStrategy strategy = new MajorityVotingStrategy(TiePolicy.FAIL, ErrorPolicy.TREAT_AS_FAIL);

		List<Judgment> judgments = List.of(Judgment.error("Error 1"),
				Judgment.error("Error 2"));

		Judgment result = strategy.aggregate(judgments, Map.of());

		assertThat(result.status()).isEqualTo(JudgmentStatus.FAIL);
		assertThat(result.reasoning()).contains("Majority vote");
		assertThat(result.reasoning()).contains("2 failed");
	}

	@Test
	void allErrorsShouldTreatAsAbstainWhenConfigured() {
		MajorityVotingStrategy strategy = new MajorityVotingStrategy(TiePolicy.FAIL, ErrorPolicy.TREAT_AS_ABSTAIN);

		List<Judgment> judgments = List.of(Judgment.error("Error 1"),
				Judgment.error("Error 2"));

		Judgment result = strategy.aggregate(judgments, Map.of());

		// DELTA-3: TREAT_AS_ABSTAIN converts the errors into non-votes, and the reasoning
		// says so — distinguishing it from IGNORE, which reaches the same ABSTAIN status.
		assertThat(result.status()).isEqualTo(JudgmentStatus.ABSTAIN);
		assertThat(result.reasoning()).contains("abstained because of evaluation errors");
		assertThat(evidence(result)).containsEntry(AggregationEvidence.ERRORS_TREATED_AS_ABSTAIN_COUNT, 2)
			.containsEntry(AggregationEvidence.IGNORED_ERROR_COUNT, 0);
	}

	@Test
	void allErrorsShouldBeIgnoredWhenConfigured() {
		MajorityVotingStrategy strategy = new MajorityVotingStrategy(TiePolicy.FAIL, ErrorPolicy.IGNORE);

		List<Judgment> judgments = List.of(Judgment.error("Error 1"),
				Judgment.error("Error 2"));

		Judgment result = strategy.aggregate(judgments, Map.of());

		// DELTA-3: IGNORE removes the errors from the population entirely. Same ABSTAIN
		// status as TREAT_AS_ABSTAIN above, but different accounting — which is the whole
		// reason both policies exist and why a status-only assertion cannot tell them apart.
		assertThat(result.status()).isEqualTo(JudgmentStatus.ABSTAIN);
		assertThat(result.reasoning()).contains("2 error(s) ignored");
		assertThat(evidence(result)).containsEntry(AggregationEvidence.IGNORED_ERROR_COUNT, 2)
			.containsEntry(AggregationEvidence.ERRORS_TREATED_AS_ABSTAIN_COUNT, 0);
	}

	@Test
	void mixedErrorsAndPassesShouldRespectErrorPolicy() {
		MajorityVotingStrategy strategy = new MajorityVotingStrategy(TiePolicy.FAIL, ErrorPolicy.TREAT_AS_FAIL);

		List<Judgment> judgments = List.of(booleanPass("Judge 1"), Judgment.error("Error"),
				booleanPass("Judge 3"));

		Judgment result = strategy.aggregate(judgments, Map.of());

		// 2 passes + 1 error-as-fail = 2 pass vs 1 fail
		assertThat(result.status()).isEqualTo(JudgmentStatus.PASS);
	}

	// ==================== Abstain Tests ====================

	@Test
	void allAbstainsShouldReturnAbstain() {
		MajorityVotingStrategy strategy = new MajorityVotingStrategy(TiePolicy.FAIL, ErrorPolicy.TREAT_AS_FAIL);

		List<Judgment> judgments = List.of(Judgment.abstain("Cannot evaluate 1"),
				Judgment.abstain("Cannot evaluate 2"));

		Judgment result = strategy.aggregate(judgments, Map.of());

		// A judge's own abstention is reported separately from an error converted into one.
		assertThat(result.status()).isEqualTo(JudgmentStatus.ABSTAIN);
		assertThat(result.reasoning()).contains("All 2 judge(s) abstained");
		assertThat(evidence(result)).containsEntry(AggregationEvidence.EXPLICIT_ABSTAIN_COUNT, 2)
			.containsEntry(AggregationEvidence.ERROR_COUNT, 0);
	}

	@Test
	void abstainsShouldNotCountInMajority() {
		MajorityVotingStrategy strategy = new MajorityVotingStrategy(TiePolicy.FAIL, ErrorPolicy.TREAT_AS_FAIL);

		List<Judgment> judgments = List.of(booleanPass("Judge 1"), Judgment.abstain("Judge 2"), booleanFail("Judge 3"));

		Judgment result = strategy.aggregate(judgments, Map.of());

		// 1 pass, 1 fail, 1 abstain → tie between pass/fail
		assertThat(result.status()).isEqualTo(JudgmentStatus.FAIL); // TiePolicy.FAIL
	}

	// ==================== Mixed Scenarios ====================

	@Test
	void mixedPassFailAbstainError() {
		MajorityVotingStrategy strategy = new MajorityVotingStrategy(TiePolicy.ABSTAIN, ErrorPolicy.IGNORE);

		List<Judgment> judgments = List.of(booleanPass("Judge 1"), booleanFail("Judge 2"), Judgment.abstain("Judge 3"),
				Judgment.error("Judge 4"));

		Judgment result = strategy.aggregate(judgments, Map.of());

		// 1 pass, 1 fail, 1 abstain (not counted), 1 error (ignored) = 1 pass vs 1
		// fail → tie
		assertThat(result.status()).isEqualTo(JudgmentStatus.ABSTAIN); // TiePolicy.ABSTAIN
	}

	// ==================== Edge Cases ====================

	@Test
	void emptyJudgmentListShouldThrowException() {
		MajorityVotingStrategy strategy = new MajorityVotingStrategy(TiePolicy.FAIL, ErrorPolicy.TREAT_AS_FAIL);

		assertThatThrownBy(() -> strategy.aggregate(List.of(), Map.of())).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("empty");
	}

	@Test
	void singleJudgmentShouldUseItsStatus() {
		MajorityVotingStrategy strategy = new MajorityVotingStrategy(TiePolicy.FAIL, ErrorPolicy.TREAT_AS_FAIL);

		Judgment result = strategy.aggregate(List.of(booleanPass("Only judge")), Map.of());

		assertThat(result.status()).isEqualTo(JudgmentStatus.PASS);
	}

	// ==================== Metadata Tests ====================

	@Test
	void shouldReturnCorrectName() {
		MajorityVotingStrategy strategy = new MajorityVotingStrategy(TiePolicy.FAIL, ErrorPolicy.TREAT_AS_FAIL);

		assertThat(strategy.getName()).isEqualTo("majority");
	}


	@SuppressWarnings("unchecked")
	private static Map<String, Object> evidence(Judgment judgment) {
		return (Map<String, Object>) judgment.metadata().get(Judgment.AGGREGATION_KEY);
	}

}
