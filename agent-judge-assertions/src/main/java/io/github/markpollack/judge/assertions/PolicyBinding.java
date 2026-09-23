/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.assertions;

import java.util.Objects;
import io.github.markpollack.judge.result.AcceptancePolicy;
import io.github.markpollack.judge.result.PolicyRef;

/**
 * Explicit application policy. The caller supplies an immutable, thread-safe function.
 * This runtime binding is not a portable wire value; retain reference and configuration.
 *
 * @param reference versioned configuration identity
 * @param policy application-owned policy
 */
public record PolicyBinding(PolicyRef reference, AcceptancePolicy policy) {
	/** Validate this binding. */
	public PolicyBinding {
		Objects.requireNonNull(reference, "reference");
		Objects.requireNonNull(policy, "policy");
	}
}
