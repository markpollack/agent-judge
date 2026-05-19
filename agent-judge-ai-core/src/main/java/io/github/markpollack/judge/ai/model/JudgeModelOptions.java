package io.github.markpollack.judge.ai.model;

import java.time.Duration;

/**
 * Options for a judge model invocation.
 *
 * <p>All fields are nullable — {@code null} means "use the model's default".
 * Adapter implementations map non-null values to framework-specific options.
 *
 * @param model the model identifier (e.g., "gpt-4o", "claude-sonnet-4-20250514")
 * @param temperature sampling temperature
 * @param maxTokens maximum tokens in the response
 * @param timeout request timeout
 * @param responseFormat response format hint (e.g., "json")
 * @author Mark Pollack
 * @since 0.10.0
 */
public record JudgeModelOptions(String model, Double temperature, Integer maxTokens, Duration timeout,
		String responseFormat) {

	public static JudgeModelOptions defaults() {
		return new JudgeModelOptions(null, null, null, null, null);
	}

}
