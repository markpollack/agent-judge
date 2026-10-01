/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.rag;

import io.github.markpollack.judge.JudgeMetadata;
import io.github.markpollack.judge.JudgeType;
import io.github.markpollack.judge.judgment.Judgment;
import io.github.markpollack.judge.judgment.JudgmentStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for RAG judges — prompt construction, response parsing, and ABSTAIN behavior.
 * Uses null ChatClient (no real LLM calls).
 */
class RagJudgeTest {

	private RagEvidence ragContext(String question, String context, String answer) {
		return new RagEvidence(question, context, answer);
	}

	// --- FaithfulnessJudge ---

	@Test
	void faithfulnessJudgeShouldHaveCorrectMetadata() {
		FaithfulnessJudge judge = new FaithfulnessJudge(() -> new RagEvidence("parsing fixture", "context", "answer"),
				null);
		JudgeMetadata metadata = judge.metadata();
		assertThat(metadata.name()).isEqualTo("Faithfulness");
		assertThat(metadata.type()).isEqualTo(JudgeType.LLM_POWERED);
	}

	@Test
	void faithfulnessJudgeShouldBuildPromptWithRagTriple() {
		FaithfulnessJudge judge = new FaithfulnessJudge(() -> new RagEvidence("parsing fixture", "context", "answer"),
				null);
		RagEvidence ctx = ragContext("What is Java?", "Java is a programming language.", "Java is a language.");

		String prompt = judge.buildPrompt(ctx);

		assertThat(prompt).contains("What is Java?");
		assertThat(prompt).contains("Java is a programming language.");
		assertThat(prompt).contains("Java is a language.");
		assertThat(prompt).contains("faithful");
		assertThat(prompt).startsWith("Begin your response");
	}

	@Test
	void faithfulnessJudgeShouldParseYesResponse() {
		FaithfulnessJudge judge = new FaithfulnessJudge(() -> new RagEvidence("parsing fixture", "context", "answer"),
				null);
		RagEvidence ctx = ragContext("q", "c", "a");

		Judgment result = judge.parseResponse("Answer: YES\nReasoning: All claims are supported by context.", ctx);

		assertThat(result.status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(result.reasoning()).contains("All claims are supported");
	}

	@Test
	void faithfulnessJudgeShouldParseNoResponse() {
		FaithfulnessJudge judge = new FaithfulnessJudge(() -> new RagEvidence("parsing fixture", "context", "answer"),
				null);
		RagEvidence ctx = ragContext("q", "c", "a");

		Judgment result = judge.parseResponse(
				"Answer: NO\nReasoning: The answer claims Java was created in 1990, but context says 1995.", ctx);

		assertThat(result.status()).isEqualTo(JudgmentStatus.FAIL);
		assertThat(result.reasoning()).contains("1990");
	}

	@Test
	void faithfulnessJudgeShouldAbstainOnUnparseableResponse() {
		FaithfulnessJudge judge = new FaithfulnessJudge(() -> new RagEvidence("parsing fixture", "context", "answer"),
				null);
		RagEvidence ctx = ragContext("q", "c", "a");

		Judgment result = judge.parseResponse("I'm not sure what to say here.", ctx);

		assertThat(result.status()).isEqualTo(JudgmentStatus.ABSTAIN);
		assertThat(result.reasoning()).contains("Could not parse");
	}

	@Test
	void faithfulnessJudgeShouldNotFalsePassOnNoWithYesInReasoning() {
		FaithfulnessJudge judge = new FaithfulnessJudge(() -> new RagEvidence("parsing fixture", "context", "answer"),
				null);
		RagEvidence ctx = ragContext("q", "c", "a");

		Judgment result = judge.parseResponse(
				"Answer: NO\nReasoning: The answer claims yes, it was created in 1990, which is not in context.", ctx);

		assertThat(result.status()).isEqualTo(JudgmentStatus.FAIL);
	}

	@Test
	void faithfulnessJudgeShouldAbstainOnEmptyContext() {
		FaithfulnessJudge judge = new FaithfulnessJudge(() -> new RagEvidence("parsing fixture", "context", "answer"),
				null);
		RagEvidence ctx = new RagEvidence("What is Java?", "", "Java is a language");

		Judgment result = judge.evaluate(ctx);

		assertThat(result.status()).isEqualTo(JudgmentStatus.ABSTAIN);
		assertThat(result.reasoning()).contains("No context");
	}

	@Test
	void faithfulnessJudgeShouldAbstainOnEmptyAnswer() {
		FaithfulnessJudge judge = new FaithfulnessJudge(() -> new RagEvidence("parsing fixture", "context", "answer"),
				null);
		RagEvidence ctx = new RagEvidence("What is Java?", "Java is a programming language", "");

		Judgment result = judge.evaluate(ctx);

		assertThat(result.status()).isEqualTo(JudgmentStatus.ABSTAIN);
		assertThat(result.reasoning()).contains("No answer");
	}

	// --- ContextualRelevanceJudge ---

	@Test
	void contextualRelevanceJudgeShouldHaveCorrectMetadata() {
		ContextualRelevanceJudge judge = new ContextualRelevanceJudge(
				() -> new RagEvidence("parsing fixture", "context", "answer"), null);
		JudgeMetadata metadata = judge.metadata();
		assertThat(metadata.name()).isEqualTo("ContextualRelevance");
		assertThat(metadata.type()).isEqualTo(JudgeType.LLM_POWERED);
	}

	@Test
	void contextualRelevanceJudgeShouldParseYesResponse() {
		ContextualRelevanceJudge judge = new ContextualRelevanceJudge(
				() -> new RagEvidence("parsing fixture", "context", "answer"), null);
		RagEvidence ctx = ragContext("q", "c", "a");

		Judgment result = judge.parseResponse("Answer: YES\nReasoning: The context directly addresses the question.",
				ctx);

		assertThat(result.status()).isEqualTo(JudgmentStatus.PASS);
	}

	@Test
	void contextualRelevanceJudgeShouldAbstainOnEmptyContext() {
		ContextualRelevanceJudge judge = new ContextualRelevanceJudge(
				() -> new RagEvidence("parsing fixture", "context", "answer"), null);
		RagEvidence ctx = new RagEvidence("What is Spring Boot?", "", "");

		Judgment result = judge.evaluate(ctx);

		assertThat(result.status()).isEqualTo(JudgmentStatus.ABSTAIN);
	}

	// --- HallucinationJudge ---

	@Test
	void hallucinationJudgeShouldHaveCorrectMetadata() {
		HallucinationJudge judge = new HallucinationJudge(() -> new RagEvidence("parsing fixture", "context", "answer"),
				null);
		JudgeMetadata metadata = judge.metadata();
		assertThat(metadata.name()).isEqualTo("Hallucination");
		assertThat(metadata.type()).isEqualTo(JudgeType.LLM_POWERED);
	}

	@Test
	void hallucinationJudgeShouldParseYesAsPass() {
		HallucinationJudge judge = new HallucinationJudge(() -> new RagEvidence("parsing fixture", "context", "answer"),
				null);
		RagEvidence ctx = ragContext("q", "c", "a");

		Judgment result = judge.parseResponse("Answer: YES\nReasoning: No hallucinations detected.", ctx);

		assertThat(result.status()).isEqualTo(JudgmentStatus.PASS);
	}

	@Test
	void hallucinationJudgeShouldParseNoAsFail() {
		HallucinationJudge judge = new HallucinationJudge(() -> new RagEvidence("parsing fixture", "context", "answer"),
				null);
		RagEvidence ctx = ragContext("q", "c", "a");

		Judgment result = judge.parseResponse(
				"Answer: NO\nReasoning: The answer claims Maven supports Python, which is not in context.", ctx);

		assertThat(result.status()).isEqualTo(JudgmentStatus.FAIL);
	}

	@Test
	void hallucinationJudgeShouldAbstainOnUnparseableResponse() {
		HallucinationJudge judge = new HallucinationJudge(() -> new RagEvidence("parsing fixture", "context", "answer"),
				null);
		RagEvidence ctx = ragContext("q", "c", "a");

		Judgment result = judge.parseResponse("This is a confusing response without a clear verdict.", ctx);

		assertThat(result.status()).isEqualTo(JudgmentStatus.ABSTAIN);
	}

}
