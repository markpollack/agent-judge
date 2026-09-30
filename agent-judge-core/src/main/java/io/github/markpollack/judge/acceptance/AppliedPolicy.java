/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.acceptance;

import org.jspecify.annotations.Nullable;

import io.github.markpollack.judge.provenance.PolicyRef;

import java.util.Objects;

/**
 * Successful execution of policy, which may withhold the producer finding.
 *
 * @param policy policy identity
 * @param action reliance action
 * @param reason explanation of that decision
 */
public record AppliedPolicy(@Nullable PolicyRef policy, AcceptanceAction action,
		String reason) implements PolicyApplication {
	/** Validate and freeze this value. */
	public AppliedPolicy {
		Objects.requireNonNull(action, "action");
		ValueRequirements.text(reason, "reason");
	}
}
