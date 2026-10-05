/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

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

	/**
	 * Create a hallucination judge.
	 * @param chatClientBuilder Spring AI client used for judging
	 * @param evidence fresh evidence acquisition provider, invoked once per direct
	 * execution
	 */
	public HallucinationJudge(java.util.function.Supplier<? extends RagEvidence> evidence,
			ChatClient.Builder chatClientBuilder) {
		super(evidence, "Hallucination", "Detects claims in the answer not supported by the context",
				chatClientBuilder);
	}

	@Override
	protected Judgment evaluate(RagEvidence context) {
		Optional<String> ctx = java.util.Optional.of(context.retrievedContext()).filter(value -> !value.isBlank());
		Optional<String> ans = java.util.Optional.of(context.answer()).filter(value -> !value.isBlank());
		if (ctx.isEmpty()) {
			return Judgment.abstain("No context provided — cannot detect hallucinations");
		}
		if (ans.isEmpty()) {
			return Judgment.abstain("No answer provided — cannot detect hallucinations");
		}
		return super.evaluate(context);
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
		return parseYesNoAnswer(response, false);
	}

	/**
	 * Configures native judging without acquiring evidence.
	 * @param client Spring AI client configuration
	 * @return typed evidence stage
	 */
	public static io.github.markpollack.judge.construction.EvidenceStep<RagEvidence> builder(
			ChatClient.Builder client) {
		return io.github.markpollack.judge.construction.EvidenceSteps
			.of(evidence -> new HallucinationJudge(evidence, client));
	}

}
