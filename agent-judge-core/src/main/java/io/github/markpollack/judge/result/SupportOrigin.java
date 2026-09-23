/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.result;

/** Whether scalar support was reported or explicitly derived. */
public enum SupportOrigin {

	/** Returned directly by the instrument. */
	REPORTED,

	/** Computed by a named versioned derivation. */
	DERIVED

}
