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
 * @param answerState whether an answer returned, independent of mapping/completion
 * @author Mark Pollack
 * @since 0.10.0
 */
public record EvalModelResponse(String text, String model, Usage usage, Map<String, Object> metadata,
		boolean completed, java.util.List<io.github.markpollack.judge.provenance.ArtifactRef> artifacts,
		@com.fasterxml.jackson.annotation.JsonIgnore Throwable failure, AnswerState answerState) {
 /** Whether a native return supplied an answer, independently of completion/mapping. */
 public enum AnswerState {
 /** A native answer returned, even if later mapping failed. */
 RETURNED,
 /** Invocation returned no answer. */
 NO_ANSWER }
 /** Retain a returned answer, including capture/mapping failures.
  * @param text original text
  * @param model reported model
  * @param usage known usage
  * @param metadata native facts
  * @param completed native completion
  * @param artifacts native references
  * @param failure observed failure */
 public EvalModelResponse(String text, String model, Usage usage, Map<String,Object> metadata,
   boolean completed, java.util.List<io.github.markpollack.judge.provenance.ArtifactRef> artifacts,
   Throwable failure) {
  this(text,model,usage,metadata,completed,artifacts,failure,AnswerState.RETURNED);
 }
 /** Native invocation failed without returning an answer; text is absent, not a generated error sentence.
  * @param failure observed invocation failure
  * @return explicit no-answer response */
 public static EvalModelResponse noAnswer(Throwable failure) {
  java.util.Objects.requireNonNull(failure);
  return new EvalModelResponse("",null,null,Map.of("failureType",failure.getClass().getName()),
    false,java.util.List.of(),failure,AnswerState.NO_ANSWER);
 }
 /** Whether an answer returned before any mapping or capture failure.
  * @return whether an original answer exists, even when mapping/capture failed */
 public boolean hasAnswer() { return answerState==AnswerState.RETURNED; }

	/**
	 * Native answer without a thrown failure.
	 * @param text original answer
	 * @param model reported model
	 * @param usage native usage
	 * @param metadata portable observations
	 * @param completed native completion
	 * @param artifacts protected references
	 */
	public EvalModelResponse(String text, String model, Usage usage, Map<String, Object> metadata, boolean completed,
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
	public EvalModelResponse(String text, String model, Usage usage, Map<String, Object> metadata, boolean completed) {
		this(text, model, usage, metadata, completed, java.util.List.of());
	}

	/**
	 * A completed model response.
	 * @param text generated text
	 * @param model reported model, if known
	 * @param usage reported usage, if known
	 * @param metadata incidental response telemetry
	 */
	public EvalModelResponse(String text, String model, Usage usage, Map<String, Object> metadata) {
		this(text, model, usage, metadata, true);
	}

	/** Normalize absent metadata to an empty immutable map. */
	public EvalModelResponse {
		metadata = metadata != null ? io.github.markpollack.judge.portable.PortableValues.copy(metadata,"response.metadata") : Map.of();
		java.util.Objects.requireNonNull(answerState);
		if(answerState==AnswerState.NO_ANSWER && (!text.isEmpty() || completed || failure==null || usage!=null)) throw new IllegalArgumentException("No-answer response cannot claim returned text, completion or usage");
		artifacts = java.util.List.copyOf(artifacts);
		java.util.Objects.requireNonNull(text, "text");
	}

}
