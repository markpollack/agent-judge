/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.construction;

import io.github.markpollack.judge.Judge;

/** Non-executing construction of a ready Judge. */
@FunctionalInterface
public interface ReadyJudge {

	/**
	 * Builds the configured evaluator.
	 * @return ready evaluator
	 */
	Judge build();

}
