/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.acceptance;

import io.github.markpollack.judge.judgment.Judgment;

/**
 * Application-owned reliance rule over raw producer facts, independent of earlier policy.
 * Callers may configure this abstraction internally on a Jury seat, where its operational
 * result participates in reduction/routing, or as final assertion policy after
 * evaluation. Those scopes are distinct: final application does not replace a Jury's
 * internal policy or rewrite its retained Verdict and Interpretation.
 */
@FunctionalInterface
public interface AcceptancePolicy {

	/**
	 * Decide how to use the Judgment without invoking its judge.
	 * @param judgment completed Judgment; policy execution uses its raw producer view
	 * @return the acceptance decision; an invalid or null result becomes policy failure
	 */
	AcceptanceDecision decide(Judgment judgment);

}
