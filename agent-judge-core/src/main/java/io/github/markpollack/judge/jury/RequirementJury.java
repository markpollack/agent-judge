/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.jury;

import io.github.markpollack.judge.requirement.Requirement;

/**
 * Requirement-aware composition, distinct from a Judge and retaining the complete record.
 *
 * @param <S> native specification type
 * @param <E> evidence type
 */
@FunctionalInterface
public interface RequirementJury<S, E> {

	/**
	 * Vote on the supplied requirement.
	 * @param requirement actual requirement
	 * @param evidence typed evidence
	 * @return complete verdict, including all child records
	 */
	Verdict vote(Requirement<S> requirement, E evidence);

	/**
	 * Describe only the structure this implementation exposes, without invocation.
	 * @return honest configuration description
	 */
	default io.github.markpollack.judge.description.JuryDescription describe() {
		return new io.github.markpollack.judge.description.OpaqueJuryDescription(
				io.github.markpollack.judge.description.ImplementationIdentity.of(getClass()),
				aggregateMayBeNotApplicable(), null, java.util.List.of());
	}

	/**
	 * Whether this jury can declare the requirement inapplicable.
	 * @return false unless explicitly declared
	 */
	default boolean aggregateMayBeNotApplicable() {
		return false;
	}

}
