/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury;

/**
 * A read-only routing decision derived from the retained accepted input and rule.
 *
 * @param stops whether the cascade terminates
 * @param reason observable reason, never a reliance authorization
 */
public record RoutingDecision(boolean stops, Reason reason) {
	/** Reasons derived without executing a producer or policy. */
	public enum Reason {

		/** Terminal declared last tier, including a refused or failed attempt. */
		FINAL_TIER,
		/** Whole tier was refused or failed; it supplies no routing input. */
		REFUSED_TIER,
		/** Returned facts fail structural or semantic validation. */
		INVALID_TIER,
		/** Opinion rule had no actual root opinions. */
		NO_ROOT_OPINIONS,
		/** A genuine FAIL opinion establishes the stopping condition. */
		OPINION_FAIL,
		/** All actual opinions pass and a root determination was adopted. */
		ALL_OPINIONS_PASS,
		/** The complete conclusion satisfies a conclusion rule. */
		CONCLUSION_STOP,
		/** Passing opinions cannot establish acceptance after a failed reduction. */
		ROOT_UNDECIDED,
		/** The accepted tier does not satisfy the declared stopping condition. */
		CONTINUE

	}

	/** Validate the reason. */
	public RoutingDecision {
		java.util.Objects.requireNonNull(reason);
	}
}
