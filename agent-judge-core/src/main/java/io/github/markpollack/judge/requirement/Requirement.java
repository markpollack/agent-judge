/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.requirement;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import io.github.markpollack.judge.provenance.ArtifactRef;

/**
 * A pure versioned requirement with a stable native specification. Implementations must
 * supply immutable snapshots; construction and result boundaries validate all
 * implementations. Value equivalence uses all five components.
 *
 * @param <S> native specification type
 */
@com.fasterxml.jackson.databind.annotation.JsonSerialize(
		using = io.github.markpollack.judge.serialization.ResultJson.RequirementWriter.class)
@com.fasterxml.jackson.databind.annotation.JsonDeserialize(
		using = io.github.markpollack.judge.serialization.ResultJson.RequirementReader.class)
public interface Requirement<S> {

	/**
	 * Returns the stable identity.
	 * @return nonblank identity
	 */
	String id();

	/**
	 * Returns the semantic revision.
	 * @return nonblank revision
	 */
	String revision();

	/**
	 * Returns the display description.
	 * @return nonblank description
	 */
	String text();

	/**
	 * Returns the native specification.
	 * @return immutable specification snapshot
	 */
	S specification();

	/**
	 * Returns the exact source identity.
	 * @return source reference
	 */
	RequirementSource source();

	/**
	 * Creates a content-addressed text requirement.
	 * @param id stable identity
	 * @param revision semantic revision
	 * @param text exact specification
	 * @return immutable text requirement
	 */
	static Requirement<String> text(String id, String revision, String text) {
		requireText(text);
		return new GeneralRequirement<>(id, revision, text, text, new RequirementSource(
				ArtifactRef.ofBytes("requirement", text.getBytes(StandardCharsets.UTF_8), null), null));
	}

	/**
	 * Validates the common values of any implementation.
	 * @param requirement requirement to validate
	 */
	static void validate(Requirement<?> requirement) {
		Objects.requireNonNull(requirement, "requirement");
		requireText(requirement.id());
		requireText(requirement.revision());
		requireText(requirement.text());
		Objects.requireNonNull(requirement.specification(), "specification");
		Objects.requireNonNull(requirement.source(), "source");
	}

	/**
	 * Compares complete requirement values across implementations.
	 * @param first first requirement
	 * @param second second requirement
	 * @return true when all common components are equal
	 */
	static boolean equivalent(Requirement<?> first, Requirement<?> second) {
		validate(first);
		validate(second);
		return first.id().equals(second.id()) && first.revision().equals(second.revision())
				&& first.text().equals(second.text()) && first.specification().equals(second.specification())
				&& first.source().equals(second.source());
	}

	/**
	 * Requires nonblank, well-formed Unicode text.
	 * @param text required value
	 * @throws IllegalArgumentException if blank or containing an unpaired surrogate
	 */
	public static void requireText(String text) {
		if (Objects.requireNonNull(text).isBlank())
			throw new IllegalArgumentException("Nonblank requirement value required");
		for (int i = 0; i < text.length(); i++) {
			char value = text.charAt(i);
			if (Character.isHighSurrogate(value)) {
				if (++i == text.length() || !Character.isLowSurrogate(text.charAt(i)))
					throw new IllegalArgumentException("Unpaired surrogate in requirement");
			}
			else if (Character.isLowSurrogate(value))
				throw new IllegalArgumentException("Unpaired surrogate in requirement");
		}
	}

}
