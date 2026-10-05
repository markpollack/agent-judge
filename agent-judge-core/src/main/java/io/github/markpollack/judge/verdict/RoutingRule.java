/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.verdict;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Determines how a tier's verdict maps to cascade control flow.
 *
 * @author Mark Pollack
 * @since 0.9.0
 */
public enum RoutingRule {

	/**
	 * If ANY judge in the tier fails, stop the cascade and REJECT. If all pass, escalate
	 * to the next tier for further evaluation. Use for deterministic fail-fast gates
	 * (Tier 1).
	 */
	STOP_ON_ANY_OPINION_FAIL("STOP_ON_ANY_OPINION_FAIL"),

	/**
	 * If ALL judges in the tier pass, stop the cascade and ACCEPT. If any judge fails or
	 * is uncertain (low confidence), escalate. Use for structural analysis tiers (Tier
	 * 2).
	 */
	STOP_ON_ALL_OPINIONS_PASS("STOP_ON_ALL_OPINIONS_PASS"),

	/**
	 * Continue while the verdict is inconclusive or not applicable; stop on PASS or FAIL.
	 */
	STOP_ON_CONCLUSIVE("STOP_ON_CONCLUSIVE"),
	/** Last tier: retain its result or failed attempt without resuming composition. */
	FINAL_TIER("FINAL_TIER"),

	/** stop only on a passing complete conclusion. */
	STOP_ON_CONCLUSION_PASS("STOP_ON_CONCLUSION_PASS"),

	/** stop only on a failing complete conclusion. */
	STOP_ON_CONCLUSION_FAIL("STOP_ON_CONCLUSION_FAIL");

	private final String wireName;

	RoutingRule(String wireName) {
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
	public static RoutingRule fromWire(String value) {
		for (RoutingRule policy : values()) {
			if (policy.wireName.equals(value)) {
				return policy;
			}
		}
		throw new IllegalArgumentException("Unknown tier policy: " + value);
	}

}
