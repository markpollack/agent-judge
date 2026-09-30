/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.policy;

import io.github.markpollack.judge.jury.Verdict;

/**
 * Application-owned decision about reliance on a complete Verdict. No execution or
 * routing is implied.
 */
@FunctionalInterface
public interface Policy {

	/**
	 * Decide what to do with a complete usable verdict, for any conclusion.
	 * @param verdict original complete verdict
	 * @return decision; null is a policy contract violation
	 */
	PolicyDecision decide(Verdict verdict);

}
