package io.github.markpollack.judge.koog;

import java.time.Duration;

import ai.koog.agents.core.agent.AIAgent;
import ai.koog.agents.core.agent.config.AIAgentConfig;
import ai.koog.prompt.llm.LLModel;
import ai.koog.prompt.llm.LLMProvider;
import io.github.markpollack.judge.completion.CompletionStatus;
import io.github.markpollack.judge.completion.CompletionEvidence;
import io.github.markpollack.judge.conformance.CompletionEvidenceConformance;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link KoogCompletionEvidenceBuilder}.
 */
class KoogCompletionEvidenceBuilderTest {

	@SuppressWarnings("unchecked")
	private AIAgent<String, String> mockAgent() {
		return mock(AIAgent.class);
	}

	@Test
	void shouldCaptureSuccessfulExecution() {
		AIAgent<String, String> agent = mockAgent();
		when(agent.run("Write a hello world program")).thenReturn("Created HelloWorld.java");
		when(agent.getId()).thenReturn("test-agent-1");

		CompletionEvidence context = KoogCompletionEvidenceBuilder.from(agent, "Write a hello world program");

		assertThat(context.request()).isEqualTo("Write a hello world program");
		assertThat(context.status()).isEqualTo(CompletionStatus.SUCCESS);
		assertThat(context.response()).isEqualTo("Created HelloWorld.java");
		assertThat(context.startedAt()).isNotNull();
		assertThat(context.elapsedTime()).isGreaterThanOrEqualTo(Duration.ZERO);
		assertThat(context.metadata()).containsEntry("koog.agentId", "test-agent-1");
		assertThat(context.error()).isNull();
		CompletionEvidenceConformance.assertSuccessful(context, "Write a hello world program",
				"Created HelloWorld.java");
	}

	@Test
	void shouldExposeConfiguredProviderAndModelIdentity() {
		AIAgent<String, String> agent = mockAgent();
		AIAgentConfig config = mock(AIAgentConfig.class);
		when(agent.run("Identify model")).thenReturn("done");
		when(agent.getId()).thenReturn("model-agent");
		when(agent.getAgentConfig()).thenReturn(config);
		when(config.getModel()).thenReturn(new LLModel(LLMProvider.OpenAI, "gpt-5"));

		CompletionEvidence context = KoogCompletionEvidenceBuilder.from(agent, "Identify model");

		assertThat(context.metadata()).containsEntry("koog.provider", "openai").containsEntry("koog.model", "gpt-5");
	}

	@Test
	void shouldCaptureFailedExecution() {
		AIAgent<String, String> agent = mockAgent();
		RuntimeException failure = new RuntimeException("Agent timed out");
		when(agent.run("Impossible task")).thenThrow(failure);
		when(agent.getId()).thenReturn("test-agent-2");

		CompletionEvidence context = KoogCompletionEvidenceBuilder.from(agent, "Impossible task");

		assertThat(context.request()).isEqualTo("Impossible task");
		assertThat(context.status()).isEqualTo(CompletionStatus.FAILED);
		assertThat(context.response()).isNull();
		assertThat(context.error()).isNotNull().satisfies(e -> assertThat(e.getMessage()).isEqualTo("Agent timed out"));
		assertThat(context.metadata()).containsEntry("koog.agentId", "test-agent-2");
		CompletionEvidenceConformance.assertFailed(context, "Impossible task", failure);
	}

}
