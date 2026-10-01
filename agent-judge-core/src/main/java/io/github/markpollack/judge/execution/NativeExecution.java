/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.execution;

import java.util.Objects;
import io.github.markpollack.judge.provenance.Invocation;

/**
 * A native answer and immutable execution observations.
 *
 * @param <A> answer type
 * @param answer original typed answer
 * @param invocation observed facts
 */
public record NativeExecution<A>(A answer, Invocation invocation) {
	/** Requires the answer and observations. */
	public NativeExecution {
		Objects.requireNonNull(answer, "answer");
		Objects.requireNonNull(invocation, "invocation");
	}
}
