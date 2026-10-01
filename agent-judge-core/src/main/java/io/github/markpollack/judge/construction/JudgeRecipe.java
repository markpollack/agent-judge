/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.construction;

import io.github.markpollack.judge.requirement.Requirement;

/**
 * Typed construction from the actual requirement, without executing it.
 *
 * @param <S> specification type
 * @param <E> prepared evidence type
 */
@FunctionalInterface
public interface JudgeRecipe<S, E> {

	/**
	 * Configures the actual requirement once.
	 * @param requirement immutable requirement
	 * @return stage accepting prepared evidence
	 */
	EvidenceStep<E> requirement(Requirement<S> requirement);

}
