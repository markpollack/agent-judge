/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.voting;

import java.util.Map;

/** Explicit trusted pure reconstruction; no class loading or global discovery. */
@FunctionalInterface
public interface VotingRuleFactory {

	/**
	 * Reconstruct every setting from retained configuration, refusing unknown fields.
	 * @param configuration complete immutable portable settings
	 * @return immutable deterministic rule
	 */
	VotingStrategy reconstruct(Map<String, Object> configuration);

}
