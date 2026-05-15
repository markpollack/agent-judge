package io.github.markpollack.judge.agentclient;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import io.github.markpollack.agents.client.AgentClientResponse;
import io.github.markpollack.agents.model.AgentGeneration;
import io.github.markpollack.agents.model.AgentGenerationMetadata;
import io.github.markpollack.agents.model.AgentResponse;
import io.github.markpollack.agents.model.AgentResponseMetadata;
import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.context.JudgmentContext;
import io.github.markpollack.judge.jury.MajorityVotingStrategy;
import io.github.markpollack.judge.jury.SimpleJury;
import io.github.markpollack.judge.jury.Verdict;
import io.github.markpollack.judge.result.Judgment;
import io.github.markpollack.judge.result.JudgmentStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentClientEvaluatorTest {

	private AgentClientResponse mockResponse() {
		AgentGenerationMetadata genMeta = new AgentGenerationMetadata("SUCCESS", null);
		AgentGeneration gen = new AgentGeneration("Created REST endpoint", genMeta);
		AgentResponseMetadata meta = AgentResponseMetadata.builder()
			.model("claude-code")
			.duration(Duration.ofSeconds(30))
			.sessionId("sess-test")
			.build();
		AgentResponse agentResponse = new AgentResponse(List.of(gen), meta);
		return new AgentClientResponse(agentResponse);
	}

	@Test
	void shouldEvaluateWithSingleJudge() {
		Judge judge = (JudgmentContext ctx) -> Judgment.pass("Output looks good");

		Judgment result = AgentClientEvaluator.evaluate("Build a REST API", Path.of("/tmp/project"),
				this::mockResponse, judge);

		assertThat(result.status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(result.reasoning()).isEqualTo("Output looks good");
	}

	@Test
	void shouldEvaluateWithJury() {
		Judge passJudge = (JudgmentContext ctx) -> Judgment.pass("Looks good");
		Judge failJudge = (JudgmentContext ctx) -> Judgment.fail("Missing tests");
		Judge passJudge2 = (JudgmentContext ctx) -> Judgment.pass("Compiles fine");

		SimpleJury jury = SimpleJury.builder()
			.judge(passJudge)
			.judge(failJudge)
			.judge(passJudge2)
			.votingStrategy(new MajorityVotingStrategy())
			.build();

		Verdict verdict = AgentClientEvaluator.evaluate("Build a REST API", Path.of("/tmp/project"),
				this::mockResponse, jury);

		assertThat(verdict.aggregated().status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(verdict.individual()).hasSize(3);
	}

}
