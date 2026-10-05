/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.execution;

/**
 * Typed execution port used by configured Judges, shared rosters and other compositions.
 * Each direct call is a fresh backend execution, which may involve multiple internal
 * model/tool calls. Validate unsupported requests before invoking the backend. Retain
 * available native answers/observations before decoding or normalizing common fields.
 * Unknown quantities are absent. CancellationException propagates; interruption is
 * preserved. Implementations must be safe for concurrent executions when shared among
 * parallel seats, or callers must serialize access. No global cache or ambient workspace
 * is supplied by this interface. Tools, advisors and permissions belong to the backend.
 * Deterministic Judges need no runtime.
 *
 * @param <Q> protocol request
 * @param <A> native answer
 */
@FunctionalInterface
public interface EvalRuntime<Q, A> {

	/**
	 * Executes the configured native harness once.
	 * @param request typed native request
	 * @return native answer and observed facts, retained before decoding
	 */
	NativeExecution<A> execute(Q request);

}
