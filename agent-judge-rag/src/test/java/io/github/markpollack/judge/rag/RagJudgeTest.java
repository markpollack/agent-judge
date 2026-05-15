package io.github.markpollack.judge.rag;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import io.github.markpollack.judge.JudgeMetadata;
import io.github.markpollack.judge.JudgeType;
import io.github.markpollack.judge.context.ExecutionStatus;
import io.github.markpollack.judge.context.JudgmentContext;
import io.github.markpollack.judge.result.Judgment;
import io.github.markpollack.judge.result.JudgmentStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for RAG judges — prompt construction and response parsing.
 * Uses null ChatClient (no real LLM calls).
 */
class RagJudgeTest {

	private JudgmentContext ragContext(String question, String context, String answer) {
		return JudgmentContext.builder()
			.goal(question)
			.workspace(Path.of("/tmp/test"))
			.status(ExecutionStatus.SUCCESS)
			.startedAt(Instant.now())
			.executionTime(Duration.ofSeconds(1))
			.agentOutput(answer)
			.metadata(Map.of(RagContext.QUESTION_KEY, question, RagContext.CONTEXT_KEY, context, RagContext.ANSWER_KEY,
					answer))
			.build();
	}

	// --- FaithfulnessJudge ---

	@Test
	void faithfulnessJudgeShouldHaveCorrectMetadata() {
		FaithfulnessJudge judge = new FaithfulnessJudge(null);
		JudgeMetadata metadata = judge.metadata();
		assertThat(metadata.name()).isEqualTo("Faithfulness");
		assertThat(metadata.type()).isEqualTo(JudgeType.LLM_POWERED);
	}

	@Test
	void faithfulnessJudgeShouldBuildPromptWithRagTriple() {
		FaithfulnessJudge judge = new FaithfulnessJudge(null);
		JudgmentContext ctx = ragContext("What is Java?", "Java is a programming language.", "Java is a language.");

		String prompt = judge.buildPrompt(ctx);

		assertThat(prompt).contains("What is Java?");
		assertThat(prompt).contains("Java is a programming language.");
		assertThat(prompt).contains("Java is a language.");
		assertThat(prompt).contains("faithful");
	}

	@Test
	void faithfulnessJudgeShouldParseYesResponse() {
		FaithfulnessJudge judge = new FaithfulnessJudge(null);
		JudgmentContext ctx = ragContext("q", "c", "a");

		Judgment result = judge.parseResponse("Answer: YES\nReasoning: All claims are supported by context.", ctx);

		assertThat(result.status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(result.reasoning()).contains("All claims are supported");
	}

	@Test
	void faithfulnessJudgeShouldParseNoResponse() {
		FaithfulnessJudge judge = new FaithfulnessJudge(null);
		JudgmentContext ctx = ragContext("q", "c", "a");

		Judgment result = judge
			.parseResponse("Answer: NO\nReasoning: The answer claims Java was created in 1990, but context says 1995.",
					ctx);

		assertThat(result.status()).isEqualTo(JudgmentStatus.FAIL);
		assertThat(result.reasoning()).contains("1990");
	}

	// --- ContextualRelevanceJudge ---

	@Test
	void contextualRelevanceJudgeShouldHaveCorrectMetadata() {
		ContextualRelevanceJudge judge = new ContextualRelevanceJudge(null);
		JudgeMetadata metadata = judge.metadata();
		assertThat(metadata.name()).isEqualTo("ContextualRelevance");
		assertThat(metadata.type()).isEqualTo(JudgeType.LLM_POWERED);
	}

	@Test
	void contextualRelevanceJudgeShouldBuildPromptWithQuestionAndContext() {
		ContextualRelevanceJudge judge = new ContextualRelevanceJudge(null);
		JudgmentContext ctx = ragContext("What is Spring Boot?", "Spring Boot simplifies Spring applications.",
				"irrelevant");

		String prompt = judge.buildPrompt(ctx);

		assertThat(prompt).contains("What is Spring Boot?");
		assertThat(prompt).contains("Spring Boot simplifies Spring applications.");
		assertThat(prompt).contains("relevant");
	}

	@Test
	void contextualRelevanceJudgeShouldParseYesResponse() {
		ContextualRelevanceJudge judge = new ContextualRelevanceJudge(null);
		JudgmentContext ctx = ragContext("q", "c", "a");

		Judgment result = judge.parseResponse("Answer: YES\nReasoning: The context directly addresses the question.",
				ctx);

		assertThat(result.status()).isEqualTo(JudgmentStatus.PASS);
	}

	// --- HallucinationJudge ---

	@Test
	void hallucinationJudgeShouldHaveCorrectMetadata() {
		HallucinationJudge judge = new HallucinationJudge(null);
		JudgeMetadata metadata = judge.metadata();
		assertThat(metadata.name()).isEqualTo("Hallucination");
		assertThat(metadata.type()).isEqualTo(JudgeType.LLM_POWERED);
	}

	@Test
	void hallucinationJudgeShouldBuildPromptWithFullTriple() {
		HallucinationJudge judge = new HallucinationJudge(null);
		JudgmentContext ctx = ragContext("What is Maven?", "Maven is a build tool for Java.", "Maven is a build tool.");

		String prompt = judge.buildPrompt(ctx);

		assertThat(prompt).contains("What is Maven?");
		assertThat(prompt).contains("Maven is a build tool for Java.");
		assertThat(prompt).contains("Maven is a build tool.");
		assertThat(prompt).contains("hallucin");
	}

	@Test
	void hallucinationJudgeShouldParseYesAsPass() {
		HallucinationJudge judge = new HallucinationJudge(null);
		JudgmentContext ctx = ragContext("q", "c", "a");

		Judgment result = judge.parseResponse("Answer: YES\nReasoning: No hallucinations detected.", ctx);

		assertThat(result.status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(result.reasoning()).contains("No hallucinations");
	}

	@Test
	void hallucinationJudgeShouldParseNoAsFail() {
		HallucinationJudge judge = new HallucinationJudge(null);
		JudgmentContext ctx = ragContext("q", "c", "a");

		Judgment result = judge
			.parseResponse("Answer: NO\nReasoning: The answer claims Maven supports Python, which is not in context.",
					ctx);

		assertThat(result.status()).isEqualTo(JudgmentStatus.FAIL);
	}

	// --- RagContext helper ---

	@Test
	void ragContextShouldFallBackToGoalAndAgentOutput() {
		JudgmentContext ctx = JudgmentContext.builder()
			.goal("What is Java?")
			.workspace(Path.of("/tmp"))
			.status(ExecutionStatus.SUCCESS)
			.startedAt(Instant.now())
			.executionTime(Duration.ofSeconds(1))
			.agentOutput("Java is a language")
			.build();

		assertThat(RagContext.question(ctx)).isEqualTo("What is Java?");
		assertThat(RagContext.answer(ctx)).isEqualTo("Java is a language");
		assertThat(RagContext.context(ctx)).isEmpty();
	}

}
