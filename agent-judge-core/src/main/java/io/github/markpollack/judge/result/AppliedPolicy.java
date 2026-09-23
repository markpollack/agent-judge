/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.result;

import java.util.Objects;

/**
 * Successful execution of policy, which may withhold the producer assessment.
 *
 * @param policy policy identity
 * @param action operational decision
 * @param reason explanation of that decision
 */
public record AppliedPolicy(PolicyRef policy, AcceptanceAction action, String reason) implements PolicyApplication {
	/** Validate and freeze this value. */
	public AppliedPolicy {
		Objects.requireNonNull(policy, "policy");
		Objects.requireNonNull(action, "action");
		ValueRequirements.text(reason, "reason");
	}
}
