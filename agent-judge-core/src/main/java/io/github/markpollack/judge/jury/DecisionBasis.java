/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * How a cascade's outcome came from the tier it names.
 *
 * <p>
 * The distinction is the whole reason a stored cascade result can be read correctly on its own.
 * One of these is a tier that finished and was believed; the other is a tier that did not
 * finish, but had already established a real rejection on the way.
 * </p>
 *
 * @author Mark Pollack
 * @since 0.17.0
 * @see Decision
 */
public enum DecisionBasis {

	/**
	 * The cascade adopted the tier's returned verdict as that tier's own determination.
	 * <p>
	 * The tier completed a reduction and the cascade stopped on it. Every copied component —
	 * aggregate, individuals, map, weights, seats — equals the tier's.
	 * </p>
	 */
	TIER_OUTCOME("tier_outcome"),

	/**
	 * The tier did not produce a usable determination, but a genuine individual FAIL inside it
	 * established a rejection, and the cascade stopped on that.
	 * <p>
	 * One established violation justifies rejecting; a broken reduction cannot justify
	 * accepting. So the asymmetry is deliberate: this basis exists only for rejection, and the
	 * root aggregate stays a machinery error rather than a manufactured FAIL. The rejection is
	 * carried by the <em>decision</em>, not by reading an error as a failure, and a reader
	 * classifies the item as non-pass on that basis while counting one machinery failure.
	 * </p>
	 */
	INDIVIDUAL_REJECTION("individual_rejection");

	private final String wireName;

	DecisionBasis(String wireName) {
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
	 * @return matching basis
	 * @throws IllegalArgumentException when the token is unknown
	 */
	@JsonCreator
	public static DecisionBasis fromWire(String value) {
		for (DecisionBasis basis : values()) {
			if (basis.wireName.equals(value)) {
				return basis;
			}
		}
		throw new IllegalArgumentException("Unknown decision basis: " + value);
	}

}
