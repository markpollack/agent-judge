package io.github.markpollack.judge.langchain4j;

import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.service.Result;
import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.context.JudgmentContext;
import io.github.markpollack.judge.jury.MajorityVotingStrategy;
import io.github.markpollack.judge.jury.SimpleJury;
import io.github.markpollack.judge.jury.Verdict;
import io.github.markpollack.judge.result.Judgment;
import io.github.markpollack.judge.result.JudgmentStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link LangChain4jSupport} one-liner convenience methods.
 */
class LangChain4jSupportTest {

	@Test
	void shouldEvaluateServiceCallWithJudge() {
		Judge judge = (JudgmentContext ctx) -> Judgment.pass("Output is correct");

		Judgment result = LangChain4jSupport.evaluate("Summarize", goal -> Result.<String>builder()
			.content("A concise summary")
			.finishReason(FinishReason.STOP)
			.build(), judge);

		assertThat(result.status()).isEqualTo(JudgmentStatus.PASS);
	}

	@Test
	void shouldEvaluateServiceCallWithJury() {
		Judge passJudge = (JudgmentContext ctx) -> Judgment.pass("Good");
		Judge failJudge = (JudgmentContext ctx) -> Judgment.fail("Bad");
		Judge passJudge2 = (JudgmentContext ctx) -> Judgment.pass("Fine");

		SimpleJury jury = SimpleJury.builder()
			.judge(passJudge)
			.judge(failJudge)
			.judge(passJudge2)
			.votingStrategy(new MajorityVotingStrategy())
			.build();

		Verdict verdict = LangChain4jSupport.evaluate("Summarize", goal -> Result.<String>builder()
			.content("A summary")
			.finishReason(FinishReason.STOP)
			.build(), jury);

		assertThat(verdict.aggregated().status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(verdict.individual()).hasSize(3);
	}

}
