package io.github.markpollack.judge.ai.model;

import java.util.Map;

/**
 * Response from a judge model backend.
 *
 * @param text the response text from the model
 * @param model the model that generated the response (nullable)
 * @param usage token usage statistics (nullable)
 * @param metadata additional response metadata (e.g., finish reason, response ID)
 * @author Mark Pollack
 * @since 0.10.0
 */
public record JudgeModelResponse(String text, String model, Usage usage, Map<String, Object> metadata) {

	public JudgeModelResponse {
		metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
	}

}
