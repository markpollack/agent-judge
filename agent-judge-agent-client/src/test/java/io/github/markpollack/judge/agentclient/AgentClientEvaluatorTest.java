/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

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
import io.github.markpollack.judge.jury.MajorityVotingStrategy;
import io.github.markpollack.judge.jury.SimpleJury;
import io.github.markpollack.judge.jury.Verdict;
import io.github.markpollack.judge.judgment.Judgment;
import io.github.markpollack.judge.judgment.JudgmentStatus;
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
		java.util.function.Function<AgentExecutionEvidence, Judge> judge = (
				AgentExecutionEvidence ctx) -> () -> Judgment.pass("Output looks good");

		Judgment result = AgentClientEvaluator.evaluate("Build a REST API", Path.of("/tmp/project"), this::mockResponse,
				judge);

		assertThat(result.status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(result.reasoning()).isEqualTo("Output looks good");
	}

	@Test
	void shouldEvaluateWithJury() {
		java.util.function.Function<AgentExecutionEvidence, Judge> passJudge = (
				AgentExecutionEvidence ctx) -> () -> Judgment.pass("Looks good");
		java.util.function.Function<AgentExecutionEvidence, Judge> failJudge = (
				AgentExecutionEvidence ctx) -> () -> Judgment.fail("Missing tests");
		java.util.function.Function<AgentExecutionEvidence, Judge> passJudge2 = (
				AgentExecutionEvidence ctx) -> () -> Judgment.pass("Compiles fine");

		java.util.function.Function<AgentExecutionEvidence, io.github.markpollack.judge.jury.Jury> jury = ctx -> SimpleJury
			.builder()
			.judge(passJudge.apply(ctx))
			.judge(failJudge.apply(ctx))
			.judge(passJudge2.apply(ctx))
			.votingStrategy(new MajorityVotingStrategy())
			.build();

		Verdict verdict = AgentClientEvaluator.evaluateJury("Build a REST API", Path.of("/tmp/project"),
				this::mockResponse, jury);

		assertThat(verdict.judgment().status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(verdict.individual()).hasSize(3);
	}

}
