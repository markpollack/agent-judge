/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.llm;

import io.github.markpollack.judge.completion.CompletionEvidence;
import io.github.markpollack.judge.judgment.Judgment;
import org.springframework.ai.chat.client.ChatClient;

/**
 * LLM-powered judge that evaluates if the agent accomplished its goal.
 *
 * <p>
 * CorrectnessJudge uses an LLM to determine if the agent successfully completed the task
 * specified in the goal. It provides a simple YES/NO judgment with reasoning, making it
 * Missing goal/output or a malformed or ambiguous answer yields ABSTAIN without inventing
 * a negative assessment. The answer must be the leading exact YES/NO field (or legacy
 * bare YES/NO, optionally followed by a dash and explanation). It is ideal for cases
 * where semantic understanding is needed but deterministic rules are insufficient.
 * </p>
 *
 * <p>
 * <strong>When to Use:</strong>
 * </p>
 * <ul>
 * <li>Goal requires semantic interpretation (e.g., "write helpful documentation")</li>
 * <li>Success criteria are subjective or nuanced</li>
 * <li>Deterministic judges (file checks, command output) don't capture full success</li>
 * <li>Need reasoning explanation for why task succeeded/failed</li>
 * </ul>
 *
 * <p>
 * <strong>Trade-offs:</strong>
 * </p>
 * <ul>
 * <li>✅ Handles subjective/semantic criteria</li>
 * <li>✅ Provides natural language reasoning</li>
 * <li>❌ Slower than deterministic judges (LLM call latency)</li>
 * <li>❌ More expensive (API costs)</li>
 * <li>❌ Non-deterministic (same input may yield different outputs)</li>
 * </ul>
 *
 * <p>
 * <strong>Best Practice:</strong> Combine with deterministic judges in a Jury for robust
 * evaluation. Use deterministic judges for objective criteria (file exists, build
 * succeeds) and CorrectnessJudge for subjective finding (quality, helpfulness).
 * </p>
 *
 * <p>
 * Example usage:
 * </p>
 * Executable examples are maintained in the Agent Judge Tutorial:
 * https://github.com/markpollack/agent-judge-tutorial.
 *
 * @author Mark Pollack
 * @since 0.1.0
 */
public class CorrectnessJudge extends LLMJudge<CompletionEvidence> {

	/**
	 * Create a correctness judge with the given chat client builder.
	 * @param chatClientBuilder the chat client builder for LLM calls
	 * @param evidence fresh evidence acquisition provider, invoked once per direct
	 * execution
	 */
	public CorrectnessJudge(java.util.function.Supplier<? extends CompletionEvidence> evidence,
			ChatClient.Builder chatClientBuilder) {
		super(evidence, "Correctness", "Evaluates if agent accomplished the goal", chatClientBuilder);
	}

	@Override
	protected String buildPrompt(CompletionEvidence context) {
		String goal = context.request();
		String output = java.util.Optional.ofNullable(context.response()).orElse("No output provided");

		return String.format("""
				Goal: %s
				Agent Output: %s

				Did the agent accomplish the goal? Answer YES or NO, followed by your reasoning.

				Format your response as:
				Answer: [YES or NO]
				Reasoning: [Your explanation]
				""", goal, output);
	}

	@Override
	protected Judgment evaluate(CompletionEvidence context) {
		if (context.request().isBlank())
			return Judgment.abstain("No goal provided — cannot evaluate correctness");
		if (context.response() == null || context.response().isBlank())
			return Judgment.abstain("No output provided — cannot evaluate correctness");
		return super.evaluate(context);
	}

	@Override
	protected Judgment parseResponse(String response, CompletionEvidence context) {
		return parseYesNoAnswer(response, true);
	}

	/**
	 * Configures native judging without acquiring evidence.
	 * @param client Spring AI client configuration
	 * @return typed evidence stage
	 */
	public static io.github.markpollack.judge.construction.EvidenceStep<CompletionEvidence> builder(
			ChatClient.Builder client) {
		return io.github.markpollack.judge.construction.EvidenceSteps
			.of(evidence -> new CorrectnessJudge(evidence, client));
	}

}
