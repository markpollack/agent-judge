/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.ai.model;

import java.util.Map;

/**
 * Response from a judge model backend.
 *
 * @param text the response text from the model
 * @param model the model that generated the response (nullable)
 * @param usage token usage statistics (nullable)
 * @param completed whether the backend completed the judging invocation
 * @param metadata additional response metadata (e.g., finish reason, response ID)
 * @param artifacts captured protected references
 * @param failure original native/capture exception, memory only
 * @author Mark Pollack
 * @since 0.10.0
 */
public record JudgeModelResponse(String text, String model, Usage usage, Map<String, Object> metadata,
		boolean completed, java.util.List<io.github.markpollack.judge.provenance.ArtifactRef> artifacts,
		@com.fasterxml.jackson.annotation.JsonIgnore Throwable failure) {
	/**
	 * Native answer without a thrown failure.
	 * @param text original answer
	 * @param model reported model
	 * @param usage native usage
	 * @param metadata portable observations
	 * @param completed native completion
	 * @param artifacts protected references
	 */
	public JudgeModelResponse(String text, String model, Usage usage, Map<String, Object> metadata, boolean completed,
			java.util.List<io.github.markpollack.judge.provenance.ArtifactRef> artifacts) {
		this(text, model, usage, metadata, completed, artifacts, null);
	}

	/**
	 * Response with no separately captured artifacts.
	 * @param text original answer
	 * @param model reported model
	 * @param usage reported quantities
	 * @param metadata portable native facts
	 * @param completed native completion
	 */
	public JudgeModelResponse(String text, String model, Usage usage, Map<String, Object> metadata, boolean completed) {
		this(text, model, usage, metadata, completed, java.util.List.of());
	}

	/**
	 * A completed model response.
	 * @param text generated text
	 * @param model reported model, if known
	 * @param usage reported usage, if known
	 * @param metadata incidental response telemetry
	 */
	public JudgeModelResponse(String text, String model, Usage usage, Map<String, Object> metadata) {
		this(text, model, usage, metadata, true);
	}

	/** Normalize absent metadata to an empty immutable map. */
	public JudgeModelResponse {
		metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
		artifacts = java.util.List.copyOf(artifacts);
		java.util.Objects.requireNonNull(text, "text");
	}

}
