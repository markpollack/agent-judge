/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.llm;

import io.github.markpollack.judge.JudgeMetadata;
import io.github.markpollack.judge.JudgeType;
import io.github.markpollack.judge.JudgeWithMetadata;
import io.github.markpollack.judge.judgment.Judgment;
import org.springframework.ai.chat.client.ChatClient;

/**
 * Base class for LLM-powered judges.
 *
 * <p>
 * LLM judges use language models to evaluate agent execution results. This abstract class
 * provides a template method pattern where subclasses customize prompt construction and
 * response parsing.
 * </p>
 *
 * <p>
 * <strong>Template Method Pattern:</strong> The {@link #judge()} method orchestrates the
 * evaluation flow: build prompt → call LLM → parse response. Subclasses implement
 * {@link #buildPrompt(Object)} and {@link #parseResponse(String, Object)} to customize
 * behavior.
 * </p>
 *
 * <p>
 * <strong>Design Rationale:</strong> LLM judges complement deterministic judges by
 * providing nuanced evaluation that's difficult to express in rules. Examples: code
 * quality finding, semantic correctness, creativity evaluation. While slower and more
 * expensive than deterministic judges, they excel at subjective or complex criteria.
 * </p>
 *
 * <p>
 * Example usage:
 * </p>
 * Executable examples are maintained in the Agent Judge Tutorial:
 * https://github.com/markpollack/agent-judge-tutorial.
 *
 * @param <E> evidence type
 * @author Mark Pollack
 * @since 0.1.0
 */
public abstract class LLMJudge<E> implements JudgeWithMetadata {

	private final JudgeMetadata metadata;

	private final java.util.function.Supplier<? extends E> evidence;

	/** Chat client used by subclasses to evaluate prompts. */
	protected final ChatClient chatClient;

	/**
	 * Create an LLM judge with metadata and chat client.
	 * @param name the judge name
	 * @param description the judge description
	 * @param chatClientBuilder the chat client builder for LLM calls (null allowed for
	 * testing)
	 * @param evidence fresh evidence acquisition provider, invoked once per direct
	 * execution
	 */
	protected LLMJudge(java.util.function.Supplier<? extends E> evidence, String name, String description,
			ChatClient.Builder chatClientBuilder) {
		this.evidence = java.util.Objects.requireNonNull(evidence);
		// No exclusion capability: this judge always answers its question, or errors
		// trying.
		this.metadata = new JudgeMetadata(name, description, JudgeType.LLM_POWERED);
		this.chatClient = chatClientBuilder != null ? chatClientBuilder.build() : null;
	}

	/**
	 * Build the prompt to send to the LLM.
	 * <p>
	 * Subclasses implement this to construct prompts from judgment context. Include goal,
	 * workspace, agent output, and any other relevant context. Use clear instructions for
	 * the LLM to follow.
	 * </p>
	 * @param context the judgment context
	 * @return the prompt string
	 */
	protected abstract String buildPrompt(E context);

	/**
	 * Parse the LLM response into a judgment.
	 * <p>
	 * Subclasses implement this to extract a required outcome, any independently measured
	 * normalized score or label, and reasoning from the LLM's text response. Handle edge
	 * cases like unclear responses, missing data, or unexpected formats.
	 * </p>
	 * @param response the LLM response text
	 * @param context the original judgment context
	 * @return the parsed judgment
	 */
	protected abstract Judgment parseResponse(String response, E context);

	/**
	 * Evaluate the agent execution using the LLM.
	 * <p>
	 * Template method that orchestrates: build prompt → call LLM → parse response.
	 * Subclasses customize via {@link #buildPrompt} and {@link #parseResponse}.
	 * </p>
	 * @return the judgment from the LLM
	 */
	@Override
	public final Judgment judge() {
		return evaluate(java.util.Objects.requireNonNull(evidence.get(), "acquired evidence"));
	}

	/**
	 * Evaluates one acquired snapshot.
	 * @param context actual evidence
	 * @return native judgment
	 */
	protected Judgment evaluate(E context) {
		String prompt = buildPrompt(context);
		var result = new SpringAiJudgeModel(this.chatClient)
			.execute(io.github.markpollack.judge.ai.model.JudgeModelRequest.user(prompt));
		Judgment judgment;
		try {
			judgment = result.answer().completed() ? parseResponse(result.answer().text(), context)
					: Judgment.error(result.answer().text());
		}
		catch (java.util.concurrent.CancellationException cancelled) {
			throw cancelled;
		}
		catch (RuntimeException failure) {
			if (Thread.currentThread().isInterrupted())
				throw new java.util.concurrent.CancellationException("Native decoding interrupted");
			judgment = Judgment.error("Native decoding failed: " + failure.getClass().getName() + ": "
					+ java.util.Objects.toString(failure.getMessage(), ""));
		}
		return judgment.withInvocation(result.invocation());
	}

	@Override
	public JudgeMetadata metadata() {
		return this.metadata;
	}

}
