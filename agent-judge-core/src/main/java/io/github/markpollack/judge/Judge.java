/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge;

import io.github.markpollack.judge.judgment.Judgment;

/**
 * A configured evaluator. Each call performs a fresh evaluation. Configuration owns
 * requirements, evidence and execution; a rule-only lambda needs none of these objects.
 * Implementations document their collaborator's thread safety.
 */
@FunctionalInterface
public interface Judge {

	/**
	 * Evaluates the configured subject.
	 * @return original producer judgment
	 */
	Judgment judge();

}
