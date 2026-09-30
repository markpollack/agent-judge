package io.github.markpollack.judge.koog;

import ai.koog.agents.core.agent.AIAgent;
import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.completion.CompletionEvidence;
import io.github.markpollack.judge.jury.MajorityVotingStrategy;
import io.github.markpollack.judge.jury.SimpleJury;
import io.github.markpollack.judge.jury.Verdict;
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
		Judge<CompletionEvidence> judge = (CompletionEvidence ctx) -> Judgment.pass("Output looks good");

		Judgment result = KoogEvaluator.evaluate(agent, "Build a REST API", judge);

		assertThat(result.status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(result.reasoning()).isEqualTo("Output looks good");
	}

	@Test
	void shouldEvaluateWithJury() {
		AIAgent<String, String> agent = mockAgent();
		Judge<CompletionEvidence> passJudge = (CompletionEvidence ctx) -> Judgment.pass("Looks good");
		Judge<CompletionEvidence> failJudge = (CompletionEvidence ctx) -> Judgment.fail("Missing tests");
		Judge<CompletionEvidence> passJudge2 = (CompletionEvidence ctx) -> Judgment.pass("Compiles fine");

		SimpleJury<CompletionEvidence> jury = SimpleJury.<CompletionEvidence>builder()
			.judge(passJudge)
			.judge(failJudge)
			.judge(passJudge2)
			.votingStrategy(new MajorityVotingStrategy())
			.build();

		Verdict verdict = KoogEvaluator.evaluate(agent, "Build a REST API", jury);

		assertThat(verdict.judgment().status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(verdict.individual()).hasSize(3);
	}

}
