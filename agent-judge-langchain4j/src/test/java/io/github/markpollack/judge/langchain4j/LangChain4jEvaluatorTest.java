/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.langchain4j;

import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.service.Result;
import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.completion.CompletionEvidence;
import io.github.markpollack.judge.voting.MajorityVotingStrategy;
import io.github.markpollack.judge.jury.SimpleJury;
import io.github.markpollack.judge.verdict.Verdict;
import io.github.markpollack.judge.judgment.Judgment;
import io.github.markpollack.judge.judgment.JudgmentStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link LangChain4jEvaluator} one-liner convenience methods.
 */
class LangChain4jEvaluatorTest {

	@Test
	void shouldEvaluateServiceCallWithJudge() {
		java.util.function.Function<CompletionEvidence, Judge> judge = (
				CompletionEvidence ctx) -> () -> Judgment.pass("Output is correct");

		Judgment result = LangChain4jEvaluator.evaluate("Summarize",
				goal -> Result.<String>builder().content("A concise summary").finishReason(FinishReason.STOP).build(),
				judge);

		assertThat(result.status()).isEqualTo(JudgmentStatus.PASS);
	}

	@Test
	void shouldEvaluateServiceCallWithJury() {
		java.util.function.Function<CompletionEvidence, Judge> passJudge = (
				CompletionEvidence ctx) -> () -> Judgment.pass("Good");
		java.util.function.Function<CompletionEvidence, Judge> failJudge = (
				CompletionEvidence ctx) -> () -> Judgment.fail("Bad");
		java.util.function.Function<CompletionEvidence, Judge> passJudge2 = (
				CompletionEvidence ctx) -> () -> Judgment.pass("Fine");

		java.util.function.Function<CompletionEvidence, io.github.markpollack.judge.jury.Jury> jury = ctx -> SimpleJury
			.builder()
			.judge(passJudge.apply(ctx))
			.judge(failJudge.apply(ctx))
			.judge(passJudge2.apply(ctx))
			.votingStrategy(new MajorityVotingStrategy())
			.build();

		Verdict verdict = LangChain4jEvaluator.evaluateJury("Summarize",
				goal -> Result.<String>builder().content("A summary").finishReason(FinishReason.STOP).build(), jury);

		assertThat(verdict.judgment().status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(verdict.individual()).hasSize(3);
	}

}
