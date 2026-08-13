/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Code-only portable evidence that a composite stage produced no verdict.
 * @param code stable Agent Judge-owned failure code
 * @since 0.14.0
 */
@JsonPropertyOrder("code")
public record CompositeFailure(CompositeFailureCode code) {

	/** Validate the failure code. */
	public CompositeFailure {
		Objects.requireNonNull(code, "code must not be null");
	}

}
