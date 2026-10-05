/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.serialization;

import io.github.markpollack.judge.requirement.*;

/**
 * Pure reconstruction of a registered immutable Requirement implementation. Factories
 * must perform no acquisition, producer or policy execution.
 *
 * @param <S> native specification type
 */
@FunctionalInterface
public interface RequirementFactory<S> {

	/**
	 * Reconstructs the exact registered implementation.
	 * @param id stable identity
	 * @param revision semantic revision
	 * @param text display description
	 * @param specification decoded native specification
	 * @param source exact source snapshot
	 * @return pure immutable requirement
	 */
	Requirement<S> create(String id, String revision, String text, S specification, RequirementSource source);

}
