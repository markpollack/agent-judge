/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.ai.requirements;

import io.github.markpollack.judge.requirement.Requirement;

/**
 * Immutable native specification; identity, revision and source belong to the
 * Requirement.
 *
 * @param title native criterion title
 * @param requirement actual configured requirement, absent for rule-only producers
 * @param applicability declared exclusion condition, or null
 */
public record EarsSpecification(String title, String requirement,
		@org.jspecify.annotations.Nullable String applicability) {
	/** Validates the native fields. */
	public EarsSpecification {
		Requirement.requireText(title);
		Requirement.requireText(requirement);
		if (applicability != null)
			Requirement.requireText(applicability);
	}

	/**
	 * Renders native specification content.
	 * @return faithful native text
	 */
	public String asPrompt() {
		return "Title: " + title + "\n" + requirement
				+ (applicability == null ? "" : " (Applies when: " + applicability + ")");
	}
}
