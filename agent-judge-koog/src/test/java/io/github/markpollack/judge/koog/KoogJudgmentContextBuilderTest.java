package io.github.markpollack.judge.koog;

import java.time.Duration;

import ai.koog.agents.core.agent.AIAgent;
import io.github.markpollack.judge.context.ExecutionStatus;
import io.github.markpollack.judge.context.JudgmentContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link KoogJudgmentContextBuilder}.
 */
class KoogJudgmentContextBuilderTest {

	@SuppressWarnings("unchecked")
	private AIAgent<String, String> mockAgent() {
		return mock(AIAgent.class);
	}

	@Test
	void shouldCaptureSuccessfulExecution() {
		AIAgent<String, String> agent = mockAgent();
		when(agent.run("Write a hello world program")).thenReturn("Created HelloWorld.java");
		when(agent.getId()).thenReturn("test-agent-1");

		JudgmentContext context = KoogJudgmentContextBuilder.from(agent, "Write a hello world program");

		assertThat(context.goal()).isEqualTo("Write a hello world program");
		assertThat(context.status()).isEqualTo(ExecutionStatus.SUCCESS);
		assertThat(context.agentOutput()).isPresent().hasValue("Created HelloWorld.java");
		assertThat(context.startedAt()).isNotNull();
		assertThat(context.executionTime()).isGreaterThanOrEqualTo(Duration.ZERO);
		assertThat(context.metadata()).containsEntry("koog.agentId", "test-agent-1");
		assertThat(context.error()).isEmpty();
	}

	@Test
	void shouldCaptureFailedExecution() {
		AIAgent<String, String> agent = mockAgent();
		RuntimeException failure = new RuntimeException("Agent timed out");
		when(agent.run("Impossible task")).thenThrow(failure);
		when(agent.getId()).thenReturn("test-agent-2");

		JudgmentContext context = KoogJudgmentContextBuilder.from(agent, "Impossible task");

		assertThat(context.goal()).isEqualTo("Impossible task");
		assertThat(context.status()).isEqualTo(ExecutionStatus.FAILED);
		assertThat(context.agentOutput()).isEmpty();
		assertThat(context.error()).isPresent().hasValueSatisfying(e -> assertThat(e.getMessage()).isEqualTo("Agent timed out"));
		assertThat(context.metadata()).containsEntry("koog.agentId", "test-agent-2");
	}

}
