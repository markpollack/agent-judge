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
 * @param keyword normative RFC2119 keyword
 * @param requirement actual configured requirement, absent for rule-only producers
 * @param reason normative rationale
 * @param applicability declared exclusion condition, or null
 */
public record Rfc2119Specification(String keyword, String requirement, String reason,
		@org.jspecify.annotations.Nullable String applicability) {
	/** Validates the native fields. */
	public Rfc2119Specification {
		Requirement.requireText(keyword);
		Requirement.requireText(requirement);
		Requirement.requireText(reason);
		if (!java.util.Set.of("MUST", "MUST NOT", "SHOULD", "SHOULD NOT", "MAY").contains(keyword))
			throw new IllegalArgumentException("Invalid RFC2119 keyword");
		if (applicability != null)
			Requirement.requireText(applicability);
	}

	/**
	 * Renders native specification content.
	 * @return faithful native text
	 */
	public String asPrompt() {
		return keyword + " " + requirement + " (Reason: " + reason + ")"
				+ (applicability == null ? "" : " (Applies when: " + applicability + ")");
	}
}
