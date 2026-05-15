package io.github.markpollack.judge.rag;

import java.util.Optional;

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
 * <p>
 * Returns {@link JudgmentStatus#ABSTAIN} when context or answer is empty, or when
 * the LLM response cannot be parsed.
 *
 * @author Mark Pollack
 * @since 0.10.0
 */
public class FaithfulnessJudge extends LLMJudge {

	private static final java.util.regex.Pattern ANSWER_PATTERN = java.util.regex.Pattern
		.compile("(?mi)^\\s*Answer:\\s*(YES|NO)");

	public FaithfulnessJudge(ChatClient.Builder chatClientBuilder) {
		super("Faithfulness", "Evaluates whether the answer is grounded in the provided context",
				chatClientBuilder);
	}

	@Override
	public Judgment judge(JudgmentContext context) {
		Optional<String> ctx = RagContext.context(context);
		Optional<String> ans = RagContext.answer(context);
		if (ctx.isEmpty()) {
			return Judgment.abstain("No context provided — cannot evaluate faithfulness");
		}
		if (ans.isEmpty()) {
			return Judgment.abstain("No answer provided — cannot evaluate faithfulness");
		}
		return super.judge(context);
	}

	@Override
	protected String buildPrompt(JudgmentContext context) {
		String question = RagContext.question(context);
		String retrievedContext = RagContext.context(context).orElse("");
		String answer = RagContext.answer(context).orElse("");

		return String.format("""
				Begin your response with the line "Answer: YES" or "Answer: NO".

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
