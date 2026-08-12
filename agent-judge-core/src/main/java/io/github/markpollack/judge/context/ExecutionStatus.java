/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.context;

/**
 * Status of agent execution.
 *
 * @author Mark Pollack
 * @since 0.1.0
 */
public enum ExecutionStatus {

	/**
	 * Agent completed successfully.
	 */
	SUCCESS,

	/**
	 * Agent failed with an error.
	 */
	FAILED,

	/**
	 * Agent execution timed out.
	 */
	TIMEOUT,

	/**
	 * Agent execution was cancelled.
	 */
	CANCELLED,

	/**
	 * Agent ran but the model refused to produce output (e.g., content filter).
	 */
	REFUSED,

	/**
	 * Agent execution status is unknown.
	 */
	UNKNOWN

}
