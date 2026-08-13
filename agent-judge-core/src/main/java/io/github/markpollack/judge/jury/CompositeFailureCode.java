/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Stable portable code describing why a composite stage produced no verdict.
 * @since 0.14.0
 */
public enum CompositeFailureCode {

	/** The configured jury threw an {@link Exception} while executing. */
	JURY_EXECUTION_FAILED("jury_execution_failed");

	private final String wireName;

	CompositeFailureCode(String wireName) {
		this.wireName = wireName;
	}

	/**
	 * Return the stable wire token.
	 * @return lower-case wire token
	 */
	@JsonValue
	public String wireName() {
		return wireName;
	}

	/**
	 * Parse an exact, case-sensitive wire token.
	 * @param value wire token
	 * @return matching failure code
	 * @throws IllegalArgumentException when the token is unknown
	 */
	@JsonCreator
	public static CompositeFailureCode fromWire(String value) {
		for (CompositeFailureCode code : values()) {
			if (code.wireName.equals(value)) {
				return code;
			}
		}
		throw new IllegalArgumentException("Unknown composite failure code: " + value);
	}

}
