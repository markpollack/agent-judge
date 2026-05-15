package io.github.markpollack.judge.rag;

import java.util.Optional;

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
 * Returns {@link JudgmentStatus#ABSTAIN} when context is empty or when the LLM response
 * cannot be parsed.
 *
 * @author Mark Pollack
 * @since 0.10.0
 */
public class ContextualRelevanceJudge extends LLMJudge {

	private static final java.util.regex.Pattern ANSWER_PATTERN = java.util.regex.Pattern
		.compile("(?mi)^\\s*Answer:\\s*(YES|NO)");

	public ContextualRelevanceJudge(ChatClient.Builder chatClientBuilder) {
		super("ContextualRelevance", "Evaluates whether retrieved context is relevant to the question",
				chatClientBuilder);
	}

	@Override
	public Judgment judge(JudgmentContext context) {
		Optional<String> ctx = RagContext.context(context);
		if (ctx.isEmpty()) {
			return Judgment.abstain("No context provided — cannot evaluate relevance");
		}
		return super.judge(context);
	}

	@Override
	protected String buildPrompt(JudgmentContext context) {
		String question = RagContext.question(context);
		String retrievedContext = RagContext.context(context).orElse("");

		return String.format("""
				Begin your response with the line "Answer: YES" or "Answer: NO".

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
		var matcher = ANSWER_PATTERN.matcher(response);
		if (!matcher.find()) {
			return Judgment.abstain("Could not parse LLM response: " + response);
		}

		boolean pass = "YES".equalsIgnoreCase(matcher.group(1));
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
