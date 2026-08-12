/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury;

/**
 * Policy for handling tie scenarios in voting strategies.
 *
 * <p>
 * Defines how voting strategies should behave when there is no clear majority or
 * consensus among judges.
 * </p>
 *
 * @author Mark Pollack
 * @since 0.1.0
 */
public enum TiePolicy {

	/**
	 * Treat ties as passing judgments (optimistic).
	 */
	PASS,

	/**
	 * Treat ties as failing judgments (pessimistic, safest default).
	 */
	FAIL,

	/**
	 * Treat ties as abstentions (neutral).
	 */
	ABSTAIN

}
