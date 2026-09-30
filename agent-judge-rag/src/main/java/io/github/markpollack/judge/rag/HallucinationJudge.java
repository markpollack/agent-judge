package io.github.markpollack.judge.rag;

import java.util.Optional;

import io.github.markpollack.judge.llm.LLMJudge;
import io.github.markpollack.judge.judgment.Judgment;
import io.github.markpollack.judge.judgment.JudgmentStatus;
import org.springframework.ai.chat.client.ChatClient;

/**
 * LLM-powered judge that detects hallucinated claims in an answer.
 * <p>
 * Hallucination detection identifies specific claims in the answer that are not supported
 * by the provided context. Unlike {@link FaithfulnessJudge} which asks "is the answer
 * grounded?", this judge asks "what specifically was made up?" and provides per-claim
 * analysis.
 * <p>
 * This is typically the most expensive RAG judge (requires careful claim-by-claim
 * analysis), making it a natural final-tier judge in a CascadedJury.
 * <p>
 * Returns {@link JudgmentStatus#ABSTAIN} when context or answer is empty, or when the LLM
 * response cannot be parsed.
 *
 * @author Mark Pollack
 * @since 0.10.0
 */
public class HallucinationJudge extends LLMJudge<RagEvidence> {

	private static final java.util.regex.Pattern ANSWER_PATTERN = java.util.regex.Pattern
		.compile("(?mi)^\\s*Answer:\\s*(YES|NO)");

	/**
	 * Create a hallucination judge.
	 * @param chatClientBuilder Spring AI client used for judging
	 */
	public HallucinationJudge(ChatClient.Builder chatClientBuilder) {
		super("Hallucination", "Detects claims in the answer not supported by the context", chatClientBuilder);
	}

	@Override
	public Judgment judge(RagEvidence context) {
		Optional<String> ctx = java.util.Optional.of(context.retrievedContext()).filter(value -> !value.isBlank());
		Optional<String> ans = java.util.Optional.of(context.answer()).filter(value -> !value.isBlank());
		if (ctx.isEmpty()) {
			return Judgment.abstain("No context provided — cannot detect hallucinations");
		}
		if (ans.isEmpty()) {
			return Judgment.abstain("No answer provided — cannot detect hallucinations");
		}
		return super.judge(context);
	}

	@Override
	protected String buildPrompt(RagEvidence context) {
		String question = context.question();
		String retrievedContext = java.util.Optional.of(context.retrievedContext())
			.filter(value -> !value.isBlank())
			.orElse("");
		String answer = java.util.Optional.of(context.answer()).filter(value -> !value.isBlank()).orElse("");

		return String.format("""
				Begin your response with the line "Answer: YES" or "Answer: NO".

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
	protected Judgment parseResponse(String response, RagEvidence context) {
		var matcher = ANSWER_PATTERN.matcher(response);
		if (!matcher.find()) {
			return Judgment.abstain("Could not parse LLM response: " + response);
		}

		boolean pass = "YES".equalsIgnoreCase(matcher.group(1));
		String reasoning = extractAfter(response, "Reasoning:");
		if (reasoning.isEmpty()) {
			reasoning = response;
		}

		return (pass ? Judgment.builder().pass() : Judgment.builder().fail()).reasoning(reasoning).build();
	}

	private static String extractAfter(String text, String marker) {
		int idx = text.indexOf(marker);
		if (idx >= 0) {
			return text.substring(idx + marker.length()).trim();
		}
		return "";
	}

}
