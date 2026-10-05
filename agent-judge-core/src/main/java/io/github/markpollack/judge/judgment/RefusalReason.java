/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.judgment;

/** Exact boundary reason, distinct from the producer's original disposition. */
public enum RefusalReason {

	/** Returned actual Requirement differs from the configured expected Requirement. */
	REQUIREMENT_MISMATCH

}
