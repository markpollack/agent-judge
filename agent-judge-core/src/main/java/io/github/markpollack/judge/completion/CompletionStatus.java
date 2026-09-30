/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.completion;

/**
 * Status of completion invocation.
 *
 * @author Mark Pollack
 * @since 0.1.0
 */
public enum CompletionStatus {

	/**
	 * Invocation completed successfully.
	 */
	SUCCESS,

	/**
	 * Invocation failed with an error.
	 */
	FAILED,

	/**
	 * Invocation execution timed out.
	 */
	TIMEOUT,

	/**
	 * Invocation execution was cancelled.
	 */
	CANCELLED,

	/**
	 * Invocation ran but the model refused to produce output (e.g., content filter).
	 */
	REFUSED,

	/**
	 * Invocation execution status is unknown.
	 */
	UNKNOWN

}
