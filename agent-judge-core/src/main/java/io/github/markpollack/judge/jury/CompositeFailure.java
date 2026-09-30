/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Code-only portable evidence that a composite stage produced no verdict.
 *
 * @param cause original runtime exception, absent in stored code-only historical records
 * @param code stable Agent Judge-owned failure code
 * @since 0.14.0
 */
@JsonPropertyOrder("code")
public record CompositeFailure(CompositeFailureCode code,
		@com.fasterxml.jackson.annotation.JsonIgnore @org.jspecify.annotations.Nullable Throwable cause) {

	/**
	 * Create code-only evidence when no exception is available.
	 * @param code stable failure code
	 */
	public CompositeFailure(CompositeFailureCode code) {
		this(code, null);
	}

	/** Portable equality excludes the original in-memory exception. */
	@Override
	public boolean equals(Object other) {
		return other instanceof CompositeFailure failure && code == failure.code;
	}

	@Override
	public int hashCode() {
		return code.hashCode();
	}

	/** Portable display never expands the original exception. */
	@Override
	public String toString() {
		return "CompositeFailure[code=" + code + "]";
	}

	/** Validate the failure code. */
	public CompositeFailure {
		Objects.requireNonNull(code, "code must not be null");
	}

}
