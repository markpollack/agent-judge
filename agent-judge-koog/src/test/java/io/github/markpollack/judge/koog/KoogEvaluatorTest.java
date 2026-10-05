/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.koog;

import ai.koog.agents.core.agent.AIAgent;
import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.completion.CompletionEvidence;
import io.github.markpollack.judge.voting.MajorityVotingStrategy;
import io.github.markpollack.judge.jury.SimpleJury;
import io.github.markpollack.judge.verdict.Verdict;
import io.github.markpollack.judge.judgment.Judgment;
import io.github.markpollack.judge.judgment.JudgmentStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link KoogEvaluator} one-liner convenience methods.
 */
class KoogEvaluatorTest {

	@SuppressWarnings("unchecked")
	private AIAgent<String, String> mockAgent() {
		AIAgent<String, String> agent = mock(AIAgent.class);
		when(agent.run("Build a REST API")).thenReturn("Created RestController.java");
		when(agent.getId()).thenReturn("test-agent");
		return agent;
	}

	@Test
	void shouldEvaluateWithSingleJudge() {
		AIAgent<String, String> agent = mockAgent();
		java.util.function.Function<CompletionEvidence, Judge> judge = (
				CompletionEvidence ctx) -> () -> Judgment.pass("Output looks good");

		Judgment result = KoogEvaluator.evaluate(agent, "Build a REST API", judge);

		assertThat(result.status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(result.reasoning()).isEqualTo("Output looks good");
	}

	@Test
	void shouldEvaluateWithJury() {
		AIAgent<String, String> agent = mockAgent();
		java.util.function.Function<CompletionEvidence, Judge> passJudge = (
				CompletionEvidence ctx) -> () -> Judgment.pass("Looks good");
		java.util.function.Function<CompletionEvidence, Judge> failJudge = (
				CompletionEvidence ctx) -> () -> Judgment.fail("Missing tests");
		java.util.function.Function<CompletionEvidence, Judge> passJudge2 = (
				CompletionEvidence ctx) -> () -> Judgment.pass("Compiles fine");

		java.util.function.Function<CompletionEvidence, io.github.markpollack.judge.jury.Jury> jury = ctx -> SimpleJury
			.builder()
			.judge(passJudge.apply(ctx))
			.judge(failJudge.apply(ctx))
			.judge(passJudge2.apply(ctx))
			.votingStrategy(new MajorityVotingStrategy())
			.build();

		Verdict verdict = KoogEvaluator.evaluateJury(agent, "Build a REST API", jury);

		assertThat(verdict.judgment().status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(verdict.individual()).hasSize(3);
	}

}
