package io.github.markpollack.judge.langchain4j;

import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.service.Result;
import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.completion.CompletionEvidence;
import io.github.markpollack.judge.jury.MajorityVotingStrategy;
import io.github.markpollack.judge.jury.SimpleJury;
import io.github.markpollack.judge.jury.Verdict;
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
		Judge<CompletionEvidence> judge = (CompletionEvidence ctx) -> Judgment.pass("Output is correct");

		Judgment result = LangChain4jEvaluator.evaluate("Summarize",
				goal -> Result.<String>builder().content("A concise summary").finishReason(FinishReason.STOP).build(),
				judge);

		assertThat(result.status()).isEqualTo(JudgmentStatus.PASS);
	}

	@Test
	void shouldEvaluateServiceCallWithJury() {
		Judge<CompletionEvidence> passJudge = (CompletionEvidence ctx) -> Judgment.pass("Good");
		Judge<CompletionEvidence> failJudge = (CompletionEvidence ctx) -> Judgment.fail("Bad");
		Judge<CompletionEvidence> passJudge2 = (CompletionEvidence ctx) -> Judgment.pass("Fine");

		SimpleJury<CompletionEvidence> jury = SimpleJury.<CompletionEvidence>builder()
			.judge(passJudge)
			.judge(failJudge)
			.judge(passJudge2)
			.votingStrategy(new MajorityVotingStrategy())
			.build();

		Verdict verdict = LangChain4jEvaluator.evaluate("Summarize",
				goal -> Result.<String>builder().content("A summary").finishReason(FinishReason.STOP).build(), jury);

		assertThat(verdict.judgment().status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(verdict.individual()).hasSize(3);
	}

}
