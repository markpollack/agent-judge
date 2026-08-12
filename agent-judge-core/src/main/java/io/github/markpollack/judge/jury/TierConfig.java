/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury;

import java.util.Objects;

/**
 * Configuration for a single tier within a {@link CascadedJury}.
 *
 * @param name human-readable tier name for diagnostics (e.g., "deterministic")
 * @param jury the jury implementation for this tier
 * @param policy cascade control flow policy
 * @author Mark Pollack
 * @since 0.9.0
 */
public record TierConfig(String name, Jury jury, TierPolicy policy) {

	/** Validate all tier components. */
	public TierConfig {
		Objects.requireNonNull(name, "name must not be null");
		Objects.requireNonNull(jury, "jury must not be null");
		Objects.requireNonNull(policy, "policy must not be null");
	}

}
