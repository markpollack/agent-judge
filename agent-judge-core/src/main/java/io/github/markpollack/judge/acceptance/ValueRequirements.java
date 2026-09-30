/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.acceptance;

import java.util.Objects;

/** Shared construction requirements for portable result values. */
final class ValueRequirements {

	private ValueRequirements() {
	}

	static String text(String value, String name) {
		Objects.requireNonNull(value, name + " must not be null");
		if (value.isBlank()) {
			throw new IllegalArgumentException(name + " must be non-blank");
		}
		return value;
	}

}
