/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury;

import io.github.markpollack.judge.result.JudgmentStatus;

/**
 * Policy for handling {@link JudgmentStatus#NOT_APPLICABLE} judgments in voting strategies.
 *
 * <p>
 * An exclusion is a claim about the <em>subject</em>: this criterion does not apply here. It
 * is the most consequential thing a judge can say, because a criterion that leaves the
 * denominator cannot fail, and a jury that accepts exclusions silently is a jury whose pass
 * rate is over a population nobody chose. So a strategy must state what it does with one
 * rather than quietly dropping it.
 * </p>
 *
 * <table border="1">
 * <caption>Effect on the aggregation population</caption>
 * <tr><th>Policy</th><th>In the population?</th></tr>
 * <tr><td>{@link #REFUSE}</td><td>aggregation short-circuits; nothing is reduced</td></tr>
 * <tr><td>{@link #EXCLUDE}</td><td>no — removed entirely, including its weight</td></tr>
 * <tr><td>{@link #TREAT_AS_FAIL}</td><td>yes, as a FAIL, keeping its weight</td></tr>
 * </table>
 *
 * <p>
 * The default is {@code REFUSE}, and deliberately so. A jury assembled without thinking about
 * exclusions has not decided that its denominator may shrink, and a strategy that honoured an
 * exclusion it was never configured for would make that decision on the author's behalf. Under
 * {@code REFUSE} the aggregate is an {@code ERROR} coded
 * {@link io.github.markpollack.judge.result.JudgmentReasonCode#NOT_APPLICABLE_REFUSED}: loud,
 * countable, and never mistaken for a finding about the subject.
 * </p>
 *
 * <p>
 * Choosing anything but {@code REFUSE} is also validated at build time. A jury whose strategy
 * refuses exclusions cannot seat a judge that declares it may exclude — the contradiction is a
 * construction error, not a surprise at vote time.
 * </p>
 *
 * @author Mark Pollack
 * @since 0.17.0
 * @see AggregationEvidence
 * @see ErrorPolicy
 */
public enum NotApplicablePolicy {

	/**
	 * An excluded judgment makes the whole aggregate an ERROR.
	 * <p>
	 * The default. A jury that was not configured to accept exclusions should not silently
	 * shrink its own denominator.
	 * </p>
	 */
	REFUSE("refuse"),

	/**
	 * The excluded judgment is removed entirely from the population — numerator, denominator,
	 * vote count, and weight.
	 * <p>
	 * The original judgment is still retained in {@link Verdict#individual()} for audit, and
	 * the count of exclusions is recorded in the aggregation evidence, so a reader can report
	 * how much of the rubric applied. Only its influence on the reduction is removed.
	 * </p>
	 */
	EXCLUDE("exclude"),

	/**
	 * The excluded judgment participates as a FAIL, keeping its configured weight.
	 * <p>
	 * For a rubric where "this does not apply" is itself a defect — a required artifact the
	 * subject was supposed to have. The original judgment never receives a score; only its
	 * contribution to the reduction is a failing one.
	 * </p>
	 */
	TREAT_AS_FAIL("treatAsFail");

	private final String token;

	NotApplicablePolicy(String token) {
		this.token = token;
	}

	/**
	 * Return the stable lower-camel-case identifier used in aggregation evidence and jury
	 * descriptions.
	 * <p>
	 * An explicit field rather than a derivation from {@link #name()}, so renaming a constant
	 * cannot silently alter the published contract.
	 * </p>
	 * @return the token
	 */
	public String token() {
		return token;
	}

}
