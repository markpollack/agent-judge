package io.github.markpollack.judge.rag;

import io.github.markpollack.judge.context.JudgmentContext;
import io.github.markpollack.judge.llm.LLMJudge;
import io.github.markpollack.judge.result.Judgment;
import io.github.markpollack.judge.result.JudgmentStatus;
import io.github.markpollack.judge.score.BooleanScore;
import org.springframework.ai.chat.client.ChatClient;

/**
 * LLM-powered judge that evaluates whether the retrieved context is relevant to the
 * question.
 * <p>
 * Contextual relevance measures whether the retrieval step returned useful information.
 * If the context is irrelevant, any answer derived from it cannot be meaningfully
 * evaluated for faithfulness or hallucination — making this a natural first-tier judge
 * in a CascadedJury.
 * <p>
 * Works against any (question, context, answer) triple regardless of retrieval source.
 *
 * @author Mark Pollack
 * @since 0.10.0
 */
public class ContextualRelevanceJudge extends LLMJudge {

	public ContextualRelevanceJudge(ChatClient.Builder chatClientBuilder) {
		super("ContextualRelevance", "Evaluates whether retrieved context is relevant to the question",
				chatClientBuilder);
	}

	@Override
	protected String buildPrompt(JudgmentContext context) {
		String question = RagContext.question(context);
		String retrievedContext = RagContext.context(context);

		return String.format("""
				You are evaluating whether retrieved context is relevant to a question.

				Question: %s

				Retrieved Context:
				%s

				Does the retrieved context contain information that is relevant and useful \
				for answering the question? Answer YES if the context is relevant, NO if \
				the context is off-topic or unhelpful.

				Format your response as:
				Answer: [YES or NO]
				Reasoning: [Your explanation of why the context is or is not relevant]
				""", question, retrievedContext);
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
