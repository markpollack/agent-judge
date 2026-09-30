/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.policy;

import java.util.Objects;

/**
 * A successful policy decision, independent of the verdict's conclusion.
 *
 * @param action requested caller action
 * @param reason explanation
 */
public record PolicyDecision(PolicyAction action, String reason) {
	/** Require an action and an explanation. */
	public PolicyDecision {
		Objects.requireNonNull(action, "action");
		if (Objects.requireNonNull(reason, "reason").isBlank())
			throw new IllegalArgumentException("reason must not be blank");
	}
}
