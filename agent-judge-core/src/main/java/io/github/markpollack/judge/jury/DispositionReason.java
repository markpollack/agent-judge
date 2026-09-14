/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Why a composite parent could not use what a stage returned.
 *
 * <p>
 * Required exactly when the disposition is {@link AttemptDisposition#STAGE_FAILED}. Each
 * constant has a fixed, documented meaning, and <em>that meaning is the explanation</em>: the
 * parent adds no free text of its own, because a note restating the enum would duplicate one
 * fact in two places that could then disagree. Where a parent does author a new aggregate — a
 * cascade stopping on an individual rejection — that aggregate carries reasoning, because it is
 * a new claim rather than a restatement.
 * </p>
 *
 * @author Mark Pollack
 * @since 0.17.0
 */
public enum DispositionReason {

	/**
	 * The stage threw. There is no verdict, only a portable failure code.
	 */
	EXECUTION_FAILED("execution_failed"),

	/**
	 * The stage returned a verdict whose decision is {@link DecisionKind#UNDECIDED}: it ran, and
	 * determined nothing. The actual verdict is kept on the attempt.
	 */
	CHILD_UNDECIDED("child_undecided"),

	/**
	 * The stage returned a {@link io.github.markpollack.judge.result.JudgmentStatus#NOT_APPLICABLE}
	 * aggregate without having declared that its aggregate may be excluded.
	 * <p>
	 * A jury cannot acquire the right to shrink a denominator by being nested inside something.
	 * The child's actual verdict is kept unchanged on the attempt, so the claim it made stays
	 * visible and countable — including when a later stage succeeds and the item passes.
	 * </p>
	 */
	UNDECLARED_NOT_APPLICABLE("undeclared_not_applicable");

	private final String wireName;

	DispositionReason(String wireName) {
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
	 * @return matching reason
	 * @throws IllegalArgumentException when the token is unknown
	 */
	@JsonCreator
	public static DispositionReason fromWire(String value) {
		for (DispositionReason reason : values()) {
			if (reason.wireName.equals(value)) {
				return reason;
			}
		}
		throw new IllegalArgumentException("Unknown disposition reason: " + value);
	}

}
