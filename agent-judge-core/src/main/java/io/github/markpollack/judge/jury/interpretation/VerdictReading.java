/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury.interpretation;

/**
 * What a verdict says about the subject.
 *
 * <p>Read from the recorded decision and the recorded aggregate, in this precedence: a decision
 * that stopped on an individual rejection reads {@link #REJECTED} whatever the aggregate says;
 * then an {@code error} aggregate reads {@link #NOT_ASSESSED} <em>before</em> an {@code abstain}
 * reads {@link #UNDECIDED}, because a machinery failure and an abstention are opposite facts;
 * then {@code not_applicable}, {@code pass} and {@code fail} read as themselves.
 *
 * <p>None of these values says whether the subject counts as a pass, enters a denominator, or
 * affects a rate. That is the consumer's policy.
 *
 * @author Mark Pollack
 * @since 0.17.0
 */
public enum VerdictReading {

	/** The subject was judged and accepted: the aggregate is {@code pass}. */
	ACCEPTED,

	/**
	 * The subject was judged and rejected: the aggregate is {@code fail}, or the recorded decision
	 * stopped on an individual rejection a stage had established.
	 */
	REJECTED,

	/** The subject was judged and the jury could not decide: the aggregate is {@code abstain}. */
	UNDECIDED,

	/** The criteria did not apply to the subject: the aggregate is {@code not_applicable}. */
	NOT_APPLICABLE,

	/**
	 * The instrument failed before assessing the subject: the aggregate is {@code error}. Distinct
	 * from an item that carries no verdict at all, which is never interpreted.
	 */
	NOT_ASSESSED

}
