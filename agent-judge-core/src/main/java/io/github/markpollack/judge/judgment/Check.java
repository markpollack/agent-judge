/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.judgment;

import java.util.Objects;

/**
 * A named child judgment, limited to one level of checks.
 *
 * @param id unique ID within its parent roster
 * @param judgment complete child result, whose checks must be empty
 */
public record Check(String id, Judgment judgment) {
	/** Validate and freeze this value. */
	public Check {
		ValueRequirements.text(id, "id");
		Objects.requireNonNull(judgment, "judgment");
		if (!judgment.checks().isEmpty()) {
			throw new IllegalArgumentException("nested checks are forbidden");
		}
	}

	/**
	 * Create a passing check without an additional message.
	 * @param name check name
	 * @return a passing check
	 */
	public static Check pass(String name) {
		return new Check(name, Judgment.pass(""));
	}

	/**
	 * Create a passing check.
	 * @param name check name
	 * @param message supporting detail
	 * @return a passing check
	 */
	public static Check pass(String name, String message) {
		return new Check(name, Judgment.pass(message));
	}

	/**
	 * Create a failing check.
	 * @param name check name
	 * @param message failure detail
	 * @return a failing check
	 */
	public static Check fail(String name, String message) {
		return new Check(name, Judgment.fail(message));
	}

}
