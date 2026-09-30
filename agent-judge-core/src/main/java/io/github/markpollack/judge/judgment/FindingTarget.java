/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.judgment;

/** The finding component to which a support signal belongs. */
public enum FindingTarget {

	/** The booleanFinding component. */
	BOOLEAN,

	/** The numeric component. */
	NUMERIC,

	/** The category component. */
	CATEGORY

}
