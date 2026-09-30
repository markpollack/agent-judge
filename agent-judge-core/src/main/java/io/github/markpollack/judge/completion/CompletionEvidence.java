/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.completion;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A request and the observed completion of that request. Shared by runtime response
 * bridges; it says nothing about a filesystem workspace or a requirement specification.
 * Native telemetry in metadata is incidental and never a substitute for required input.
 *
 * @param request original request text
 * @param response response text, absent when none was produced
 * @param status invocation outcome, distinct from any Judgment
 * @param startedAt observed start time, if known
 * @param elapsedTime observed duration, if known
 * @param error invocation failure, if any; this input is not a stored result
 * @param metadata incidental runtime telemetry
 */
public record CompletionEvidence(String request, @Nullable String response, CompletionStatus status,
		@Nullable Instant startedAt, @Nullable Duration elapsedTime, @Nullable Throwable error,
		Map<String, Object> metadata) {

	/** Freeze telemetry and validate required fields. */
	public CompletionEvidence {
		Objects.requireNonNull(request, "request");
		Objects.requireNonNull(status, "status");
		metadata = Map.copyOf(metadata);
	}

	/**
	 * Start a completion capture.
	 * @return a response capture builder
	 */
	public static Builder builder() {
		return new Builder();
	}
	/** Builds a completion snapshot with explicit unknown/absent observations. */
	public static final class Builder {

		private String request = "";

		private @Nullable String response;

		private CompletionStatus status = CompletionStatus.UNKNOWN;

		private @Nullable Instant startedAt;

		private @Nullable Duration elapsedTime;

		private @Nullable Throwable error;

		private final Map<String, Object> metadata = new HashMap<>();

		/** Create an empty capture. */
		public Builder() {
		}

		/**
		 * Set the captured request.
		 * @param request request text
		 * @return this builder
		 */
		public Builder request(String request) {
			this.request = request;
			return this;
		}

		/**
		 * Set the captured response.
		 * @param response observed text
		 * @return this builder
		 */
		public Builder response(@Nullable String response) {
			this.response = response;
			return this;
		}

		/**
		 * Set the captured status.
		 * @param status invocation outcome
		 * @return this builder
		 */
		public Builder status(CompletionStatus status) {
			this.status = status;
			return this;
		}

		/**
		 * Set the captured startedAt.
		 * @param startedAt observed start
		 * @return this builder
		 */
		public Builder startedAt(@Nullable Instant startedAt) {
			this.startedAt = startedAt;
			return this;
		}

		/**
		 * Set the captured elapsedTime.
		 * @param elapsedTime observed duration
		 * @return this builder
		 */
		public Builder elapsedTime(@Nullable Duration elapsedTime) {
			this.elapsedTime = elapsedTime;
			return this;
		}

		/**
		 * Set the captured error.
		 * @param error invocation failure
		 * @return this builder
		 */
		public Builder error(@Nullable Throwable error) {
			this.error = error;
			return this;
		}

		/**
		 * Set the captured values.
		 * @param values incidental telemetry
		 * @return this builder
		 */
		public Builder metadata(Map<String, Object> values) {
			metadata.clear();
			metadata.putAll(values);
			return this;
		}

		/**
		 * Set the captured key.
		 * @param key telemetry name
		 * @param value telemetry value
		 * @return this builder
		 */
		public Builder metadata(String key, Object value) {
			metadata.put(key, value);
			return this;
		}

		/**
		 * Freeze the captured observations.
		 * @return immutable response capture
		 */
		public CompletionEvidence build() {
			return new CompletionEvidence(request, response, status, startedAt, elapsedTime, error, metadata);
		}

	}
}
