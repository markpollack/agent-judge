/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.requirement;

/**
 * An immutable general requirement; callers supply a stable native specification.
 *
 * @param <S> specification type
 * @param id stable identity
 * @param revision semantic revision
 * @param text display text
 * @param specification immutable specification
 * @param source exact source
 */
public record GeneralRequirement<S>(String id, String revision, String text, S specification,
		RequirementSource source) implements Requirement<S> {
	/** Validates the common contract. */
	public GeneralRequirement {
		Requirement.requireText(id);
		Requirement.requireText(revision);
		Requirement.requireText(text);
		java.util.Objects.requireNonNull(specification, "specification");
		java.util.Objects.requireNonNull(source, "source");
	}
}
