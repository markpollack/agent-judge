/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Whether a composite parent could use what a stage returned.
 *
 * <p>
 * Always written, on every attempt. A stage that ran and a stage the parent had to set aside
 * both leave an attempt behind, and without this they look alike — which is how a composition
 * failure becomes invisible once a later stage succeeds.
 * </p>
 *
 * @author Mark Pollack
 * @since 0.17.0
 * @see DispositionReason
 */
public enum AttemptDisposition {

	/** The parent consumed the stage's verdict normally. */
	USED("used"),

	/**
	 * The parent could not use what the stage produced.
	 * <p>
	 * A structural marker, not a {@link io.github.markpollack.judge.result.JudgmentReasonCode}:
	 * it describes the parent's relationship to the stage, not a judgment's cause. The stage's
	 * own verdict, where it returned one, is kept unchanged on the attempt.
	 * </p>
	 */
	STAGE_FAILED("stage_failed");

	private final String wireName;

	AttemptDisposition(String wireName) {
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
	 * @return matching disposition
	 * @throws IllegalArgumentException when the token is unknown
	 */
	@JsonCreator
	public static AttemptDisposition fromWire(String value) {
		for (AttemptDisposition disposition : values()) {
			if (disposition.wireName.equals(value)) {
				return disposition;
			}
		}
		throw new IllegalArgumentException("Unknown attempt disposition: " + value);
	}

}
