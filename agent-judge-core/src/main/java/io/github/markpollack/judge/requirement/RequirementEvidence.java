/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.requirement;

import java.util.Objects;

/**
 * The exact requirement and evidence supplied for one evaluation. Pairing is not
 * authentication.
 *
 * @param <S> native requirement specification type
 * @param <E> evidence type
 * @param requirement original requirement
 * @param evidence original evidence snapshot
 */
public record RequirementEvidence<S, E>(Requirement<S> requirement, E evidence) {
	/** Refuse absent inputs; neither value is substituted or copied. */
	public RequirementEvidence {
		Objects.requireNonNull(requirement, "requirement");
		Objects.requireNonNull(evidence, "evidence");
	}
}
