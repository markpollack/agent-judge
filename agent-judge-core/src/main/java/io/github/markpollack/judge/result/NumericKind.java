/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.result;

/** Meaning of a numeric assessment. */
public enum NumericKind {

	/** A measurement on declared numeric bounds. */
	MEASUREMENT,

	/** An expected index in a declared ordered rubric. */
	ORDINAL_EXPECTATION

}
