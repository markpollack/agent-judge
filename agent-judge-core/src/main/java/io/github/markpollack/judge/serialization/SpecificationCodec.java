/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.serialization;

import java.util.Objects;
import io.github.markpollack.judge.requirement.*;

/**
 * Explicit native specification type and pure Requirement reconstruction. The stable wire
 * name is supplied separately; no stored Java class name is loaded.
 *
 * @param <S> native specification type
 * @param specificationType trusted concrete Jackson type
 * @param factory exact pure Requirement reconstruction
 */
public record SpecificationCodec<S>(Class<S> specificationType, RequirementFactory<S> factory) {
	/** Requires explicit trusted collaborators. */
	public SpecificationCodec {
		Objects.requireNonNull(specificationType);
		Objects.requireNonNull(factory);
	}

	/**
	 * Registers a general Requirement containing a concrete native specification.
	 * @param <S> specification type
	 * @param type trusted concrete Jackson type
	 * @return general reconstruction
	 */
	public static <S> SpecificationCodec<S> general(Class<S> type) {
		return new SpecificationCodec<>(type, GeneralRequirement::new);
	}

	Requirement<?> reconstruct(String id, String revision, String text, Object specification,
			RequirementSource source) {
		Requirement<S> value = Objects.requireNonNull(
				factory.create(id, revision, text, specificationType.cast(specification), source),
				"reconstructed requirement");
		Requirement.validate(value);
		return value;
	}
}
