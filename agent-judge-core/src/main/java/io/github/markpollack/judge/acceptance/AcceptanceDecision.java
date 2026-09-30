/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.acceptance;

import java.util.Objects;

/**
 * Result of an application-owned acceptance decision.
 *
 * @param action how to use the producer finding
 * @param reason explanation
 */
public record AcceptanceDecision(AcceptanceAction action, String reason) {
	/** Validate and freeze this value. */
	public AcceptanceDecision {
		Objects.requireNonNull(action, "action");
		ValueRequirements.text(reason, "reason");
	}
}
