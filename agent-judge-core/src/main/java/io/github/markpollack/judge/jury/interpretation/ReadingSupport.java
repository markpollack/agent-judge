/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury.interpretation;

/**
 * Whether the recorded facts support the reading.
 *
 * <p>The reading is what the record says; this is whether the record can back it. A stored
 * status is checked against the recorded aggregation evidence under the recorded strategy and
 * policies where that rule is closed-form (consensus, majority, all-must-pass, the threshold
 * strategies, the propagate and refuse policy exits); a recorded decision is checked against the
 * chain it names; a {@code tier_outcome} root is checked against its used stage's aggregate.
 *
 * <p>What follows from each value — whether an undetermined reading is counted, whether a
 * contradicted one is reported as unattestable — is the consumer's policy.
 *
 * @author Mark Pollack
 * @since 0.17.0
 */
public enum ReadingSupport {

	/** The facts needed to check are present and agree with the reading. */
	SUPPORTED,

	/**
	 * A recorded fact contradicts the reading. Every contradiction is one
	 * {@link DefectKind#INCONSISTENT} defect.
	 */
	CONTRADICTED,

	/**
	 * The facts needed to check are absent, or the strategy's rule is not closed-form. Every
	 * absent fact the check needed is one {@link DefectKind#ABSENT} defect.
	 */
	UNDETERMINED

}
