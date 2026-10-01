/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.execution;

import java.util.Objects;
import io.github.markpollack.judge.requirement.Requirement;

/**
 * Typed requirement-bound native execution request.
 *
 * @param <S> specification type
 * @param <E> evidence type
 * @param requirement actual configured requirement
 * @param evidence acquired or prepared snapshot
 */
public record RequirementRequest<S, E>(Requirement<S> requirement, E evidence) {
	/** Validates arbitrary requirement implementations and evidence. */
	public RequirementRequest {
		Requirement.validate(requirement);
		Objects.requireNonNull(evidence, "evidence");
	}
}
