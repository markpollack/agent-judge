/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * What produced a verdict's aggregate.
 *
 * <p>
 * A composite verdict copies its aggregate from somewhere, and until now a reader could not
 * tell where from. That matters because the three cases mean different things to a rate: an
 * outcome this jury computed, an outcome it adopted from a named stage, and no outcome at all.
 * </p>
 *
 * @author Mark Pollack
 * @since 0.17.0
 * @see Decision
 */
public enum DecisionKind {

	/**
	 * This jury's own reduction, or a policy outcome of it.
	 * <p>
	 * The aggregate is whatever the strategy produced, or the error a policy short-circuited
	 * to. Either way it is this jury's finding.
	 * </p>
	 */
	OWN("own"),

	/**
	 * The outcome was determined by the direct tier the decision names.
	 * <p>
	 * {@link DecisionBasis} says how: the cascade either adopted the tier's own determination,
	 * or stopped on an individual rejection the tier established without completing a
	 * reduction.
	 * </p>
	 */
	TIER("tier"),

	/**
	 * Nothing determined an outcome.
	 * <p>
	 * The aggregate is an {@code ERROR} with a machinery reason code, and it is the instrument
	 * that failed rather than the subject. A reader excludes an undecided verdict from a subject
	 * denominator and counts it as an instrument failure.
	 * </p>
	 */
	UNDECIDED("undecided");

	private final String wireName;

	DecisionKind(String wireName) {
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
	 * @return matching kind
	 * @throws IllegalArgumentException when the token is unknown
	 */
	@JsonCreator
	public static DecisionKind fromWire(String value) {
		for (DecisionKind kind : values()) {
			if (kind.wireName.equals(value)) {
				return kind;
			}
		}
		throw new IllegalArgumentException("Unknown decision kind: " + value);
	}

}
