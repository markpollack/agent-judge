/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury;
import io.github.markpollack.judge.verdict.RoutingRule;

import java.util.Objects;

/**
 * Configuration for a single tier within a {@link CascadedJury}.
 *
 * @param name human-readable tier name for diagnostics (e.g., "deterministic")
 * @param jury the jury implementation for this tier
 * @param routingRule cascade control flow routingRule
 * @author Mark Pollack
 * @since 0.9.0
 */
public record TierConfig(String name, Jury jury, RoutingRule routingRule) {

	/** Validate all tier components. */
	public TierConfig {
		name = NamedJury.requireValidName(name);
		Objects.requireNonNull(jury, "jury must not be null");
		Objects.requireNonNull(routingRule, "routingRule must not be null");
	}

}
