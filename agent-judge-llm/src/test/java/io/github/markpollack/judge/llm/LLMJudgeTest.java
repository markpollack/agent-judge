/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.llm;

import org.junit.jupiter.api.Test;
import io.github.markpollack.judge.JudgeMetadata;
import io.github.markpollack.judge.JudgeType;
import io.github.markpollack.judge.completion.CompletionStatus;
import io.github.markpollack.judge.completion.CompletionEvidence;
import io.github.markpollack.judge.judgment.Judgment;
import io.github.markpollack.judge.judgment.JudgmentStatus;
import org.springframework.ai.chat.client.ChatClient;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link LLMJudge}.
 *
 * @author Mark Pollack
 * @since 0.1.0
 */
class LLMJudgeTest {

	@Test
	void shouldCreateJudgeWithMetadata() {
		TestLLMJudge judge = new TestLLMJudge("TestJudge", "Test Description", null);

		JudgeMetadata metadata = judge.metadata();

		assertThat(metadata.name()).isEqualTo("TestJudge");
		assertThat(metadata.description()).isEqualTo("Test Description");
		assertThat(metadata.type()).isEqualTo(JudgeType.LLM_POWERED);
	}

	@Test
	void shouldBuildPromptFromContext() {
		TestLLMJudge judge = new TestLLMJudge("TestJudge", "Test", null);

		CompletionEvidence context = createTestContext();

		String prompt = judge.buildPrompt(context);

		assertThat(prompt).contains("Test goal");
		assertThat(prompt).contains("Test output");
	}

	@Test
	void shouldParseResponseIntoJudgment() {
		TestLLMJudge judge = new TestLLMJudge("TestJudge", "Test", null);

		CompletionEvidence context = createTestContext();
		String response = "Test LLM response";

		Judgment judgment = judge.parseResponse(response, context);

		assertThat(judgment).isNotNull();
		assertThat(judgment.status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(judgment.reasoning()).contains("Parsed from LLM");
	}

	@Test
	void shouldAllowNullChatClientForTesting() {
		TestLLMJudge judge = new TestLLMJudge("TestJudge", "Test", null);

		assertThat(judge.metadata().name()).isEqualTo("TestJudge");
	}

	// ==================== Helper Methods ====================

	private CompletionEvidence createTestContext() {
		return CompletionEvidence.builder()
			.request("Test goal")
			.response("Test output")
			.elapsedTime(Duration.ofSeconds(1))
			.startedAt(Instant.now())
			.status(CompletionStatus.SUCCESS)
			.build();
	}

	/**
	 * Test implementation of LLMJudge for testing the abstract base class.
	 */
	static class TestLLMJudge extends LLMJudge<CompletionEvidence> {

		TestLLMJudge(String name, String description, ChatClient.Builder chatClientBuilder) {
			super(() -> CompletionEvidence.builder().request("parsing fixture").build(), name, description,
					chatClientBuilder);
		}

		@Override
		protected String buildPrompt(CompletionEvidence context) {
			return String.format("Evaluate: Goal=%s, Output=%s", context.request(),
					java.util.Optional.ofNullable(context.response()).orElse("None"));
		}

		@Override
		protected Judgment parseResponse(String response, CompletionEvidence context) {
			return Judgment.builder().pass().reasoning("Parsed from LLM: " + response).build();
		}

	}

}
