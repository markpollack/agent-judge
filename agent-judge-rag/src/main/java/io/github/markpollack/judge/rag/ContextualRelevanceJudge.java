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
 * LLM-powered judge that evaluates whether the retrieved context is relevant to the
 * question.
 * <p>
 * Contextual relevance measures whether the retrieval step returned useful information.
 * If the context is irrelevant, any answer derived from it cannot be meaningfully
 * evaluated for faithfulness or hallucination — making this a natural first-tier judge in
 * a CascadedJury.
 * <p>
 * Returns {@link JudgmentStatus#ABSTAIN} when question or context is empty or when the
 * LLM response cannot be parsed. Only a leading exact Answer: YES or Answer: NO is
 * admitted; repeated fields and labels appearing solely in reasoning abstain.
 *
 * @author Mark Pollack
 * @since 0.10.0
 */
public class ContextualRelevanceJudge extends LLMJudge<RagEvidence> {

	/**
	 * Create a contextual-relevance judge.
	 * @param chatClientBuilder Spring AI client used for judging
	 * @param evidence fresh evidence acquisition provider, invoked once per direct
	 * execution
	 */
	public ContextualRelevanceJudge(java.util.function.Supplier<? extends RagEvidence> evidence,
			ChatClient.Builder chatClientBuilder) {
		super(evidence, "ContextualRelevance", "Evaluates whether retrieved context is relevant to the question",
				chatClientBuilder);
	}

 /** Configure a portable generated runtime.
  * @param runtime configured runtime
  * @param evidence fresh evidence provider */
 public ContextualRelevanceJudge(io.github.markpollack.judge.ai.model.EvalModel runtime,
   java.util.function.Supplier<? extends RagEvidence> evidence) {
  super(runtime,evidence,"ContextualRelevance","Evaluates whether retrieved context is relevant to the question");
 }
 /** Runtime-first construction without acquiring evidence.
  * @return runtime stage */
 public static RuntimeStep builder() { return runtime -> io.github.markpollack.judge.construction.EvidenceSteps.of(evidence -> new ContextualRelevanceJudge(runtime,evidence)); }
 /** Select the generated runtime before evidence. */
 public interface RuntimeStep {
  /** Select the configured runtime without execution.
  * @param runtime configured generated runtime
  * @return typed evidence stage */
  io.github.markpollack.judge.construction.EvidenceStep<RagEvidence> runtime(io.github.markpollack.judge.ai.model.EvalModel runtime);
 }

	@Override
	protected Judgment evaluate(RagEvidence context) {
		if (context.question().isBlank())
			return Judgment.abstain("No question provided — cannot evaluate relevance");
		Optional<String> ctx = java.util.Optional.of(context.retrievedContext()).filter(value -> !value.isBlank());
		if (ctx.isEmpty()) {
			return Judgment.abstain("No context provided — cannot evaluate relevance");
		}
		return super.evaluate(context);
	}

	@Override
	protected String buildPrompt(RagEvidence context) {
		String question = context.question();
		String retrievedContext = java.util.Optional.of(context.retrievedContext())
			.filter(value -> !value.isBlank())
			.orElse("");

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
		return builder().runtime(new io.github.markpollack.judge.llm.SpringAiEvalModel(client));
	}

}
