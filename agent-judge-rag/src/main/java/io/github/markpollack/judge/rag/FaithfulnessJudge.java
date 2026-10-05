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
 * LLM-powered judge that evaluates whether an answer is faithful to the provided context.
 * <p>
 * Faithfulness measures whether every claim in the answer can be traced back to the
 * retrieved context. An answer that is factually correct but not supported by the given
 * context is considered unfaithful.
 * <p>
 * Works against any (question, context, answer) triple regardless of how the context was
 * retrieved — vector store, tool call, agentic CLI browsing, or manual curation.
 * <p>
 * Returns {@link JudgmentStatus#ABSTAIN} when context or answer is empty, or when the LLM
 * response cannot be parsed.
 *
 * @author Mark Pollack
 * @since 0.10.0
 */
public class FaithfulnessJudge extends LLMJudge<RagEvidence> {

	/**
	 * Create a faithfulness judge.
	 * @param chatClientBuilder Spring AI client used for judging
	 * @param evidence fresh evidence acquisition provider, invoked once per direct
	 * execution
	 */
	public FaithfulnessJudge(java.util.function.Supplier<? extends RagEvidence> evidence,
			ChatClient.Builder chatClientBuilder) {
		super(evidence, "Faithfulness", "Evaluates whether the answer is grounded in the provided context",
				chatClientBuilder);
	}

	@Override
	protected Judgment evaluate(RagEvidence context) {
		Optional<String> ctx = java.util.Optional.of(context.retrievedContext()).filter(value -> !value.isBlank());
		Optional<String> ans = java.util.Optional.of(context.answer()).filter(value -> !value.isBlank());
		if (ctx.isEmpty()) {
			return Judgment.abstain("No context provided — cannot evaluate faithfulness");
		}
		if (ans.isEmpty()) {
			return Judgment.abstain("No answer provided — cannot evaluate faithfulness");
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
			.of(evidence -> new FaithfulnessJudge(evidence, client));
	}

}
