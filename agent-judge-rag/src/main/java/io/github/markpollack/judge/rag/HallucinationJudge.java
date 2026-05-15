package io.github.markpollack.judge.rag;

import io.github.markpollack.judge.context.JudgmentContext;
import io.github.markpollack.judge.llm.LLMJudge;
import io.github.markpollack.judge.result.Judgment;
import io.github.markpollack.judge.result.JudgmentStatus;
import io.github.markpollack.judge.score.BooleanScore;
import org.springframework.ai.chat.client.ChatClient;

/**
 * LLM-powered judge that detects hallucinated claims in an answer.
 * <p>
 * Hallucination detection identifies specific claims in the answer that are not
 * supported by the provided context. Unlike {@link FaithfulnessJudge} which asks
 * "is the answer grounded?", this judge asks "what specifically was made up?"
 * and provides per-claim analysis.
 * <p>
 * This is typically the most expensive RAG judge (requires careful claim-by-claim
 * analysis), making it a natural final-tier judge in a CascadedJury.
 * <p>
 * Works against any (question, context, answer) triple regardless of retrieval source.
 *
 * @author Mark Pollack
 * @since 0.10.0
 */
public class HallucinationJudge extends LLMJudge {

	public HallucinationJudge(ChatClient.Builder chatClientBuilder) {
		super("Hallucination", "Detects claims in the answer not supported by the context", chatClientBuilder);
	}

	@Override
	protected String buildPrompt(JudgmentContext context) {
		String question = RagContext.question(context);
		String retrievedContext = RagContext.context(context);
		String answer = RagContext.answer(context);

		return String.format("""
				You are a hallucination detector. Your job is to identify claims in the \
				answer that are NOT supported by the provided context.

				Question: %s

				Context:
				%s

				Answer: %s

				Analyze each factual claim in the answer. Does any claim go beyond what \
				the context supports? Answer YES if the answer is free of hallucinations \
				(all claims are supported), NO if any claims are hallucinated.

				Format your response as:
				Answer: [YES or NO]
				Reasoning: [List any hallucinated claims, or confirm all claims are supported]
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
