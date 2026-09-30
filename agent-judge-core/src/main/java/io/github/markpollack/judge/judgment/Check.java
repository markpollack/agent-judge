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
		ValueRequirements.text(id, "name/id");
		Objects.requireNonNull(judgment, "judgment");
		if (!judgment.checks().isEmpty()) {
			throw new IllegalArgumentException("nested checks are forbidden");
		}
	}

	/**
	 * Compatibility constructor for declared Boolean checks.
	 * @param name check ID
	 * @param passed declared Boolean result
	 * @param message explanation
	 */
	public Check(String name, boolean passed, String message) {
		this(name, Judgment.verdict(passed).reasoning(Objects.requireNonNull(message, "message")).build());
	}

	/**
	 * Returns check ID (compatibility view).
	 * @return check ID (compatibility view)
	 */
	public String name() {
		return id;
	}

	/**
	 * Returns whether the child operationally passed; false includes inconclusive
	 * outcomes.
	 * @return whether the child operationally passed; false includes inconclusive
	 * outcomes
	 */
	public boolean passed() {
		return judgment.pass();
	}

	/**
	 * Returns child operational explanation (compatibility view).
	 * @return child operational explanation (compatibility view)
	 */
	public String message() {
		return judgment.operationalReasoning();
	}

	/**
	 * Create a passing check without an additional message.
	 * @param name check name
	 * @return a passing check
	 */
	public static Check pass(String name) {
		return new Check(name, true, "");
	}

	/**
	 * Create a passing check.
	 * @param name check name
	 * @param message supporting detail
	 * @return a passing check
	 */
	public static Check pass(String name, String message) {
		return new Check(name, true, message);
	}

	/**
	 * Create a failing check.
	 * @param name check name
	 * @param message failure detail
	 * @return a failing check
	 */
	public static Check fail(String name, String message) {
		return new Check(name, false, message);
	}

}
