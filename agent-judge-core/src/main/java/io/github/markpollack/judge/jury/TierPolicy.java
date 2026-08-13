/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Determines how a tier's verdict maps to cascade control flow.
 *
 * @author Mark Pollack
 * @since 0.9.0
 */
public enum TierPolicy {

	/**
	 * If ANY judge in the tier fails, stop the cascade and REJECT. If all pass, escalate
	 * to the next tier for further evaluation. Use for deterministic fail-fast gates
	 * (Tier 1).
	 */
	REJECT_ON_ANY_FAIL("REJECT_ON_ANY_FAIL"),

	/**
	 * If ALL judges in the tier pass, stop the cascade and ACCEPT. If any judge fails or
	 * is uncertain (low confidence), escalate. Use for structural analysis tiers (Tier
	 * 2).
	 */
	ACCEPT_ON_ALL_PASS("ACCEPT_ON_ALL_PASS"),

	/**
	 * Always produce a verdict — no escalation possible. Must be the last tier in the
	 * cascade. Use for LLM semantic evaluation (Tier 3).
	 */
	FINAL_TIER("FINAL_TIER");

	private final String wireName;

	TierPolicy(String wireName) {
		this.wireName = wireName;
	}

	/**
	 * Return the stable wire token.
	 * @return upper-case wire token
	 */
	@JsonValue
	public String wireName() {
		return wireName;
	}

	/**
	 * Parse an exact, case-sensitive wire token.
	 * @param value wire token
	 * @return matching policy
	 * @throws IllegalArgumentException when the token is unknown
	 */
	@JsonCreator
	public static TierPolicy fromWire(String value) {
		for (TierPolicy policy : values()) {
			if (policy.wireName.equals(value)) {
				return policy;
			}
		}
		throw new IllegalArgumentException("Unknown tier policy: " + value);
	}

}
