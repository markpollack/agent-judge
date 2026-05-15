package io.github.markpollack.judge.koog;

import ai.koog.agents.core.agent.AIAgent;
import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.context.JudgmentContext;
import io.github.markpollack.judge.result.Judgment;
import io.github.markpollack.judge.result.JudgmentStatus;
import io.github.markpollack.judge.score.BooleanScore;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Demo test showing the end-to-end Koog evaluation flow.
 * This is the CI-runnable version of the samples/koog-evaluation/ README.
 */
class KoogEvaluationDemoTest {

	@SuppressWarnings("unchecked")
	@Test
	void koogAgentEvaluatedWithSingleJudge() {
		// Simulate a Koog agent that answers a question
		AIAgent<String, String> agent = mock(AIAgent.class);
		when(agent.run("Explain dependency injection"))
			.thenReturn("Dependency injection is a design pattern where objects receive their "
					+ "dependencies from an external source rather than creating them internally.");
		when(agent.getId()).thenReturn("docs-assistant");

		// A simple judge that checks whether the output mentions the key concept
		Judge containsKeyConceptJudge = (JudgmentContext ctx) -> {
			String output = ctx.agentOutput().orElse("");
			boolean mentionsDI = output.toLowerCase().contains("dependencies")
					&& output.toLowerCase().contains("external");
			return Judgment.builder()
				.score(new BooleanScore(mentionsDI))
				.status(mentionsDI ? JudgmentStatus.PASS : JudgmentStatus.FAIL)
				.reasoning(mentionsDI ? "Answer correctly describes DI" : "Answer missing key DI concepts")
				.build();
		};

		// One-liner evaluation
		Judgment judgment = KoogEvaluator.evaluate(agent, "Explain dependency injection", containsKeyConceptJudge);

		assertThat(judgment.status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(judgment.reasoning()).isEqualTo("Answer correctly describes DI");
	}

}
