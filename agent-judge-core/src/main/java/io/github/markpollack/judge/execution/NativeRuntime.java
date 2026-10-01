/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.execution;

/**
 * A typed native execution boundary, separate from domain judging.
 *
 * @param <Q> protocol request
 * @param <A> native answer
 */
@FunctionalInterface
public interface NativeRuntime<Q, A> {

	/**
	 * Executes the configured native harness once.
	 * @param request typed native request
	 * @return native answer and observed facts, retained before decoding
	 */
	NativeExecution<A> execute(Q request);

}
