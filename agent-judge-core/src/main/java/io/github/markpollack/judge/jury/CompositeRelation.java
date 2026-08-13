/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Identifies how a composite attempt is related to its parent verdict.
 * @since 0.14.0
 */
public enum CompositeRelation {

	/** A tier entered by a {@link CascadedJury}. */
	CASCADE_TIER("cascade_tier"),

	/** A named member entered by a meta-jury. */
	META_MEMBER("meta_member");

	private final String wireName;

	CompositeRelation(String wireName) {
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
	 * @return matching relation
	 * @throws IllegalArgumentException when the token is unknown
	 */
	@JsonCreator
	public static CompositeRelation fromWire(String value) {
		for (CompositeRelation relation : values()) {
			if (relation.wireName.equals(value)) {
				return relation;
			}
		}
		throw new IllegalArgumentException("Unknown composite relation: " + value);
	}

}
