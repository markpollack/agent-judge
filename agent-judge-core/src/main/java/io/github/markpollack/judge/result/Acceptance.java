/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.result;

import java.util.Objects;

/**
 * Result of an application-owned acceptance decision.
 *
 * @param action how to use the producer assessment
 * @param reason explanation
 */
public record Acceptance(AcceptanceAction action, String reason) {
	/** Validate and freeze this value. */
	public Acceptance {
		Objects.requireNonNull(action, "action");
		ValueRequirements.text(reason, "reason");
	}
}
