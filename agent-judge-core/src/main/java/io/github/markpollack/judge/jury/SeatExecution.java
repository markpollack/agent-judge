/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.jury;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Whether a seat returned a valid value or orchestration contained an invocation failure.
 * This is execution evidence, never inferred from a judgment reason code.
 */
public enum SeatExecution {

	/** A valid value was returned, including a returned ERROR. */
	RETURNED,
	/**
	 * Orchestration replaced a failed/null invocation, unreadable metadata or undeclared
	 * exclusion.
	 */
	CONTAINED_FAILURE;

	/**
	 * Parse an exact execution token without numeric enum coercion.
	 * @param value recorded token
	 * @return execution outcome
	 */
	@JsonCreator
	public static SeatExecution fromWire(String value) {
		return valueOf(value);
	}

}
