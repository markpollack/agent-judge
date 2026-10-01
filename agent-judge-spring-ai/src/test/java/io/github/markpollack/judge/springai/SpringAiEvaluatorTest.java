/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.springai;

import java.util.List;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.completion.CompletionEvidence;
import io.github.markpollack.judge.jury.MajorityVotingStrategy;
import io.github.markpollack.judge.jury.SimpleJury;
import io.github.markpollack.judge.jury.Verdict;
import io.github.markpollack.judge.judgment.Judgment;
import io.github.markpollack.judge.judgment.JudgmentStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpringAiEvaluatorTest {

	private ChatResponse mockResponse() {
		AssistantMessage message = new AssistantMessage("Here is a concise summary.");
		ChatGenerationMetadata genMeta = ChatGenerationMetadata.builder().finishReason("stop").build();
		Generation generation = new Generation(message, genMeta);
		return new ChatResponse(List.of(generation));
	}

	@Test
	void shouldEvaluateWithSingleJudge() {
		java.util.function.Function<CompletionEvidence, Judge> judge = (
				CompletionEvidence ctx) -> () -> Judgment.pass("Output is correct");

		Judgment result = SpringAiEvaluator.evaluate("Summarize", this::mockResponse, judge);

		assertThat(result.status()).isEqualTo(JudgmentStatus.PASS);
	}

	@Test
	void shouldEvaluateWithJury() {
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

		Verdict verdict = SpringAiEvaluator.evaluateJury("Summarize", this::mockResponse, jury);

		assertThat(verdict.judgment().status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(verdict.individual()).hasSize(3);
	}

}
