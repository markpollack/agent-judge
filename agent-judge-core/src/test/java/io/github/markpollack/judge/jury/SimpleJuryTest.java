/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury;

import org.junit.jupiter.api.Test;
import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.context.JudgmentContext;
import io.github.markpollack.judge.result.Judgment;
import io.github.markpollack.judge.result.JudgmentStatus;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static io.github.markpollack.judge.JudgeTestFixtures.*;

/**
 * Tests for {@link SimpleJury}.
 *
 * @author Mark Pollack
 * @since 0.1.0
 */
class SimpleJuryTest {

	@Test
	void shouldExecuteJudgesInParallel() {
		SimpleJury jury = SimpleJury.builder()
			.judge(alwaysPass("Judge1"))
			.judge(alwaysPass("Judge2"))
			.judge(alwaysPass("Judge3"))
			.votingStrategy(new MajorityVotingStrategy())
			.parallel(true)
			.build();

		JudgmentContext context = simpleContext("Test goal");
		Verdict verdict = jury.vote(context);

		assertThat(verdict.aggregated().status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(verdict.individual()).hasSize(3);
		assertThat(verdict.compositeAttempts()).isEmpty();
		assertThat(verdict.compositeAttempts()).isUnmodifiable();
	}

	@Test
	void shouldExecuteJudgesSequentially() {
		SimpleJury jury = SimpleJury.builder()
			.judge(alwaysPass("Judge1"))
			.judge(alwaysFail("Judge2"))
			.judge(alwaysPass("Judge3"))
			.votingStrategy(new MajorityVotingStrategy())
			.parallel(false)
			.build();

		JudgmentContext context = simpleContext("Test goal");
		Verdict verdict = jury.vote(context);

		assertThat(verdict.aggregated().status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(verdict.individual()).hasSize(3);
	}

	@Test
	void shouldPreserveJudgeIdentityByName() {
		SimpleJury jury = SimpleJury.builder()
			.judge(alwaysPass("FileExists"))
			.judge(alwaysFail("Correctness"))
			.judge(alwaysPass("BuildSuccess"))
			.votingStrategy(new MajorityVotingStrategy())
			.build();

		JudgmentContext context = simpleContext("Test goal");
		Verdict verdict = jury.vote(context);

		assertThat(verdict.individualByName()).containsKeys("FileExists", "Correctness", "BuildSuccess");
		assertThat(verdict.individualByName().get("FileExists").status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(verdict.individualByName().get("Correctness").status()).isEqualTo(JudgmentStatus.FAIL);
	}

	@Test
	void shouldGenerateDefaultNamesForUnnamedJudges() {
		Judge unnamedJudge1 = ctx -> Judgment.pass("Pass 1");
		Judge unnamedJudge2 = ctx -> Judgment.fail("Fail 2");

		SimpleJury jury = SimpleJury.builder()
			.judge(unnamedJudge1)
			.judge(unnamedJudge2)
			.votingStrategy(new MajorityVotingStrategy())
			.build();

		JudgmentContext context = simpleContext("Test goal");
		Verdict verdict = jury.vote(context);

		assertThat(verdict.individualByName()).containsKeys("Judge#1", "Judge#2");
	}

	@Test
	void shouldUseWeightedVoting() {
		SimpleJury jury = SimpleJury.builder()
			.judge(alwaysPass("Judge1"), 0.3)
			.judge(alwaysFail("Judge2"), 0.7)
			.votingStrategy(new WeightedAverageStrategy())
			.build();

		JudgmentContext context = simpleContext("Test goal");
		Verdict verdict = jury.vote(context);

		// Weighted: (1.0 * 0.3 + 0.0 * 0.7) / 1.0 = 0.3 < 0.5 → FAIL
		assertThat(verdict.aggregated().status()).isEqualTo(JudgmentStatus.FAIL);
		assertThat(verdict.weights()).containsEntry("0", 0.3).containsEntry("1", 0.7);
	}

	@Test
	void shouldUseCustomExecutor() {
		var executor = Executors.newFixedThreadPool(2);

		SimpleJury jury = SimpleJury.builder()
			.judge(alwaysPass("Judge1"))
			.judge(alwaysPass("Judge2"))
			.votingStrategy(new MajorityVotingStrategy())
			.parallel(true)
			.executor(executor)
			.build();

		JudgmentContext context = simpleContext("Test goal");
		Verdict verdict = jury.vote(context);

		assertThat(verdict.aggregated().status()).isEqualTo(JudgmentStatus.PASS);

		executor.shutdown();
	}

	@Test
	void shouldReturnJudgesList() {
		Judge judge1 = alwaysPass("Judge1");
		Judge judge2 = alwaysFail("Judge2");

		SimpleJury jury = SimpleJury.builder()
			.judge(judge1)
			.judge(judge2)
			.votingStrategy(new MajorityVotingStrategy())
			.build();

		assertThat(jury.getJudges()).containsExactly(judge1, judge2);
	}

	@Test
	void shouldReturnVotingStrategy() {
		VotingStrategy strategy = new MajorityVotingStrategy();

		SimpleJury jury = SimpleJury.builder().judge(alwaysPass("Judge1")).votingStrategy(strategy).build();

		assertThat(jury.getVotingStrategy()).isEqualTo(strategy);
	}

	@Test
	void shouldPreserveOrderInIndividualByName() {
		SimpleJury jury = SimpleJury.builder()
			.judge(alwaysPass("First"))
			.judge(alwaysFail("Second"))
			.judge(alwaysPass("Third"))
			.votingStrategy(new MajorityVotingStrategy())
			.build();

		JudgmentContext context = simpleContext("Test goal");
		Verdict verdict = jury.vote(context);

		// LinkedHashMap preserves insertion order
		assertThat(verdict.individualByName()).containsKeys("First", "Second", "Third");
		assertThat(verdict.individualByName()).hasSize(3);
	}

	// ==================== Builder Tests ====================

	@Test
	void builderShouldRequireVotingStrategy() {
		assertThatThrownBy(() -> SimpleJury.builder().judge(alwaysPass("Judge1")).build())
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Voting strategy is required");
	}

	@Test
	void builderShouldRequireAtLeastOneJudge() {
		assertThatThrownBy(() -> SimpleJury.builder().votingStrategy(new MajorityVotingStrategy()).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("at least one judge");
	}

	@Test
	void builderShouldRejectNullJudge() {
		assertThatThrownBy(() -> SimpleJury.builder().judge(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Judge cannot be null");
	}

	@Test
	void builderShouldRejectNegativeWeight() {
		assertThatThrownBy(() -> SimpleJury.builder().judge(alwaysPass("Judge1"), -1.0))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("non-negative");
	}

	@Test
	void builderShouldAcceptZeroWeight() {
		// Zero weight is valid - judge participates but with no influence
		SimpleJury jury = SimpleJury.builder()
			.judge(alwaysPass("Judge1"), 0.0)
			.judge(alwaysFail("Judge2"), 1.0)
			.votingStrategy(new WeightedAverageStrategy())
			.build();

		JudgmentContext context = simpleContext("Test goal");
		Verdict verdict = jury.vote(context);

		// Only Judge2 has weight → result should be FAIL
		assertThat(verdict.aggregated().status()).isEqualTo(JudgmentStatus.FAIL);
	}

	@Test
	void builderShouldDefaultToParallelTrue() {
		SimpleJury jury = SimpleJury.builder()
			.judge(alwaysPass("Judge1"))
			.votingStrategy(new MajorityVotingStrategy())
			.build();

		// No direct way to test parallel flag, but verify it works
		JudgmentContext context = simpleContext("Test goal");
		Verdict verdict = jury.vote(context);

		assertThat(verdict.aggregated().status()).isEqualTo(JudgmentStatus.PASS);
	}

	@Test
	void builderShouldSupportJudgeWithoutWeight() {
		SimpleJury jury = SimpleJury.builder()
			.judge(alwaysPass("Judge1")) // defaults to weight 1.0
			.judge(alwaysFail("Judge2")) // defaults to weight 1.0
			.votingStrategy(new WeightedAverageStrategy())
			.build();

		JudgmentContext context = simpleContext("Test goal");
		Verdict verdict = jury.vote(context);

		// Equal weights: (1.0 * 1.0 + 0.0 * 1.0) / 2.0 = 0.5 → PASS
		assertThat(verdict.aggregated().status()).isEqualTo(JudgmentStatus.PASS);
	}

	// ==================== Edge Cases ====================

	@Test
	void shouldHandleRecordingJudges() {
		var recording = recording("RecordingJudge", booleanPass("Recorded"));

		SimpleJury jury = SimpleJury.builder()
			.judge(recording)
			.judge(alwaysPass("Judge2"))
			.votingStrategy(new MajorityVotingStrategy())
			.build();

		JudgmentContext context = simpleContext("Test goal");
		jury.vote(context);

		assertThat(recording.getInvocationCount()).isEqualTo(1);
		assertThat(recording.getLastInvocation()).isEqualTo(context);
	}

	@Test
	void shouldHandleSlowJudgesWithTimeout() {
		SimpleJury jury = SimpleJury.builder()
			.judge(slow("SlowJudge", 100, booleanPass("Slow pass")))
			.judge(alwaysPass("FastJudge"))
			.votingStrategy(new MajorityVotingStrategy())
			.parallel(true)
			.build();

		JudgmentContext context = simpleContext("Test goal");
		Verdict verdict = jury.vote(context);

		// Both should complete
		assertThat(verdict.individual()).hasSize(2);
		assertThat(verdict.aggregated().status()).isEqualTo(JudgmentStatus.PASS);
	}

	// ==================== A failing judge still votes ====================
	//
	// Regression guards for the defect where a throwing judge escaped SimpleJury
	// entirely, bypassing ErrorPolicy and discarding every other judge's result in the
	// same jury. Before the fix each of these tests failed by propagating the judge's
	// exception out of vote().

	@Test
	void throwingJudgeBecomesAnErrorJudgmentTheErrorPolicyResolves() {
		SimpleJury jury = SimpleJury.builder()
			.judge(withScore("Scorer", 0.9))
			.judge(alwaysThrows("Exploder", new IllegalStateException("model timed out")))
			.judge(alwaysPass("Checker"))
			.votingStrategy(new MajorityVotingStrategy(TiePolicy.FAIL, ErrorPolicy.TREAT_AS_ABSTAIN))
			.parallel(true)
			.build();

		Verdict verdict = jury.vote(simpleContext("Test goal"));

		// TREAT_AS_ABSTAIN makes the failed judge a non-vote; the two that worked decide.
		assertThat(verdict.aggregated().status()).isEqualTo(JudgmentStatus.PASS);

		// Every configured judge is still represented, and the working judges keep their
		// scores rather than being discarded along with the failure.
		assertThat(verdict.individual()).hasSize(3);
		assertThat(verdict.individual().get(0).status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(verdict.individual().get(0).score()).isEqualTo(0.9);
		assertThat(verdict.individual().get(2).status()).isEqualTo(JudgmentStatus.PASS);

		Judgment failed = verdict.individual().get(1);
		assertThat(failed.status()).isEqualTo(JudgmentStatus.ERROR);
		assertThat(failed.reasoning()).contains("Exploder")
			.contains(IllegalStateException.class.getName())
			.contains("model timed out");
		assertThat(verdict.individualByName()).containsKeys("Scorer", "Exploder", "Checker");
	}

	@Test
	void throwingJudgeIsContainedInSequentialModeToo() {
		SimpleJury jury = SimpleJury.builder()
			.judge(alwaysThrows("Exploder", new IllegalStateException("model timed out")))
			.judge(alwaysPass("Checker"))
			.votingStrategy(new MajorityVotingStrategy(TiePolicy.FAIL, ErrorPolicy.TREAT_AS_ABSTAIN))
			.parallel(false)
			.build();

		Verdict verdict = jury.vote(simpleContext("Test goal"));

		assertThat(verdict.aggregated().status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(verdict.individual()).hasSize(2);
		assertThat(verdict.individual().get(0).status()).isEqualTo(JudgmentStatus.ERROR);
	}

	@Test
	void throwingJudgeUnderPropagateErrorsRatherThanEscaping() {
		SimpleJury jury = SimpleJury.builder()
			.judge(alwaysPass("Checker"))
			.judge(alwaysThrows("Exploder", new IllegalStateException("model timed out")))
			.votingStrategy(new MajorityVotingStrategy())
			.build();

		Verdict verdict = jury.vote(simpleContext("Test goal"));

		// PROPAGATE is the default, so the aggregate is an ERROR — but it is a verdict
		// the caller can read, with the working judge's result still attached, not an
		// exception that destroys the whole jury's output.
		assertThat(verdict.aggregated().status()).isEqualTo(JudgmentStatus.ERROR);
		assertThat(verdict.individual()).hasSize(2);
		assertThat(verdict.individual().get(0).status()).isEqualTo(JudgmentStatus.PASS);
	}

	@Test
	void judgeReturningNoJudgmentBecomesAnErrorJudgment() {
		SimpleJury jury = SimpleJury.builder()
			.judge(returnsNothing("Silent"))
			.judge(alwaysPass("Checker"))
			.votingStrategy(new MajorityVotingStrategy(TiePolicy.FAIL, ErrorPolicy.TREAT_AS_ABSTAIN))
			.build();

		Verdict verdict = jury.vote(simpleContext("Test goal"));

		assertThat(verdict.aggregated().status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(verdict.individual().get(0).status()).isEqualTo(JudgmentStatus.ERROR);
		assertThat(verdict.individual().get(0).reasoning()).contains("Silent").contains("returned no judgment");
	}

	@Test
	@SuppressWarnings("unchecked")
	void aggregationEvidenceReportsTheCountThatActuallyVoted() {
		SimpleJury jury = SimpleJury.builder()
			.judge(alwaysPass("Checker"))
			.judge(alwaysThrows("Exploder", new IllegalStateException("model timed out")))
			.judge(alwaysPass("Reviewer"))
			.votingStrategy(new MajorityVotingStrategy(TiePolicy.FAIL, ErrorPolicy.TREAT_AS_ABSTAIN))
			.build();

		Verdict verdict = jury.vote(simpleContext("Test goal"));

		// This is what makes silent under-counting impossible to miss: the aggregate
		// states that three judges were submitted and only two reduced.
		Object block = verdict.aggregated().metadata().get(Judgment.AGGREGATION_KEY);
		assertThat(block).isInstanceOf(Map.class);
		assertThat((Map<String, Object>) block).containsEntry(AggregationEvidence.INPUT_COUNT, 3)
			.containsEntry(AggregationEvidence.ELIGIBLE_COUNT, 2)
			.containsEntry(AggregationEvidence.ERROR_COUNT, 1)
			.containsEntry(AggregationEvidence.ERRORS_TREATED_AS_ABSTAIN_COUNT, 1);
	}

}
