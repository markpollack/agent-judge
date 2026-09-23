/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.result;

/** Application-owned decision over raw producer facts, independent of earlier policy. */
@FunctionalInterface
public interface AcceptancePolicy {

	/**
	 * Decide how to use the producer assessment without invoking its judge.
	 * @param judgment raw producer view with no policy application
	 * @return the acceptance decision; an invalid or null result becomes policy failure
	 */
	Acceptance evaluate(Judgment judgment);

}
