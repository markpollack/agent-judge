package io.github.markpollack.judge.rag;

import io.github.markpollack.judge.context.JudgmentContext;
import io.github.markpollack.judge.llm.LLMJudge;
import io.github.markpollack.judge.result.Judgment;
import io.github.markpollack.judge.result.JudgmentStatus;
import io.github.markpollack.judge.score.BooleanScore;
import org.springframework.ai.chat.client.ChatClient;

/**
 * LLM-powered judge that evaluates whether an answer is faithful to the provided context.
 * <p>
 * Faithfulness measures whether every claim in the answer can be traced back to the
 * retrieved context. An answer that is factually correct but not supported by the given
 * context is considered unfaithful.
 * <p>
 * Works against any (question, context, answer) triple regardless of how the context
 * was retrieved — vector store, tool call, agentic CLI browsing, or manual curation.
 *
 * @author Mark Pollack
 * @since 0.10.0
 */
public class FaithfulnessJudge extends LLMJudge {

	public FaithfulnessJudge(ChatClient.Builder chatClientBuilder) {
		super("Faithfulness", "Evaluates whether the answer is grounded in the provided context",
				chatClientBuilder);
	}

	@Override
	protected String buildPrompt(JudgmentContext context) {
		String question = RagContext.question(context);
		String retrievedContext = RagContext.context(context);
		String answer = RagContext.answer(context);

		return String.format("""
				You are evaluating whether an answer is faithful to the provided context.

				Question: %s

				Context:
				%s

				Answer: %s

				Is every claim in the answer supported by the context? Answer YES if the answer \
				is entirely grounded in the context, NO if it contains claims not supported by \
				the context.

				Format your response as:
				Answer: [YES or NO]
				Reasoning: [Your explanation of which claims are or are not supported]
				""", question, retrievedContext, answer);
	}

	@Override
	protected Judgment parseResponse(String response, JudgmentContext context) {
		boolean pass = response.toUpperCase().contains("ANSWER: YES")
				|| (response.toUpperCase().contains("YES") && !response.toUpperCase().contains("ANSWER: NO"));

		String reasoning = extractAfter(response, "Reasoning:");
		if (reasoning.isEmpty()) {
			reasoning = response;
		}

		return Judgment.builder()
			.score(new BooleanScore(pass))
			.status(pass ? JudgmentStatus.PASS : JudgmentStatus.FAIL)
			.reasoning(reasoning)
			.build();
	}

	private static String extractAfter(String text, String marker) {
		int idx = text.indexOf(marker);
		if (idx >= 0) {
			return text.substring(idx + marker.length()).trim();
		}
		return "";
	}

}
