package io.github.markpollack.judge.springai;

import java.util.List;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.context.JudgmentContext;
import io.github.markpollack.judge.jury.MajorityVotingStrategy;
import io.github.markpollack.judge.jury.SimpleJury;
import io.github.markpollack.judge.jury.Verdict;
import io.github.markpollack.judge.result.Judgment;
import io.github.markpollack.judge.result.JudgmentStatus;
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
		Judge judge = (JudgmentContext ctx) -> Judgment.pass("Output is correct");

		Judgment result = SpringAiEvaluator.evaluate("Summarize", this::mockResponse, judge);

		assertThat(result.status()).isEqualTo(JudgmentStatus.PASS);
	}

	@Test
	void shouldEvaluateWithJury() {
		Judge passJudge = (JudgmentContext ctx) -> Judgment.pass("Good");
		Judge failJudge = (JudgmentContext ctx) -> Judgment.fail("Bad");
		Judge passJudge2 = (JudgmentContext ctx) -> Judgment.pass("Fine");

		SimpleJury jury = SimpleJury.builder()
			.judge(passJudge)
			.judge(failJudge)
			.judge(passJudge2)
			.votingStrategy(new MajorityVotingStrategy())
			.build();

		Verdict verdict = SpringAiEvaluator.evaluate("Summarize", this::mockResponse, jury);

		assertThat(verdict.aggregated().status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(verdict.individual()).hasSize(3);
	}

}
