/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.construction;

import io.github.markpollack.judge.requirement.Requirement;

/**
 * Typed Jury construction from one real requirement.
 *
 * @param <S> specification type
 * @param <E> evidence type
 */
@FunctionalInterface
public interface JuryRecipe<S, E> {

	/**
	 * Selects the actual requirement without execution.
	 * @param requirement actual requirement
	 * @return evidence stage
	 */
	JuryEvidenceStep<E> requirement(Requirement<S> requirement);

}
