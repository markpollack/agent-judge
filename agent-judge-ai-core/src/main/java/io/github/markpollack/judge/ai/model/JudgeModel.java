package io.github.markpollack.judge.ai.model;

/**
 * Framework-agnostic interface for invoking an AI model or agent backend
 * for judge evaluation.
 *
 * <p>Implementations bridge to specific AI runtimes:
 * <ul>
 *   <li>{@code SpringAiJudgeModel} — wraps Spring AI ChatClient</li>
 *   <li>{@code AgentClientJudgeModel} — wraps AgentClient for agentic judges</li>
 *   <li>Lambda — any {@code JudgeModelRequest → JudgeModelResponse} function</li>
 * </ul>
 *
 * @author Mark Pollack
 * @since 0.10.0
 */
@FunctionalInterface
public interface JudgeModel {

	/**
	 * Generate a response from the model.
	 * @param request the model request containing messages and options
	 * @return the model response
	 */
	JudgeModelResponse generate(JudgeModelRequest request);

	/**
	 * Convenience for simple single-prompt invocation.
	 * @param prompt the user prompt text
	 * @return the response text
	 */
	default String generateText(String prompt) {
		return generate(JudgeModelRequest.user(prompt)).text();
	}

}
