/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury;

import io.github.markpollack.judge.result.JudgmentStatus;

/**
 * Policy for handling {@link JudgmentStatus#ERROR} judgments in voting strategies.
 *
 * <p>
 * An {@code ERROR} means a judge could not complete its evaluation. That is not a finding,
 * so a strategy must state what it does with one rather than silently scoring it as zero.
 * </p>
 *
 * <p>
 * The four policies differ in whether the errored judgment stays in the population being
 * reduced:
 * </p>
 * <table border="1">
 * <caption>Effect on the aggregation population</caption>
 * <tr><th>Policy</th><th>In the population?</th></tr>
 * <tr><td>{@link #PROPAGATE}</td><td>aggregation short-circuits; nothing is reduced</td></tr>
 * <tr><td>{@link #TREAT_AS_FAIL}</td><td>yes, as a FAIL</td></tr>
 * <tr><td>{@link #TREAT_AS_ABSTAIN}</td><td>no — becomes a non-vote</td></tr>
 * <tr><td>{@link #IGNORE}</td><td>no — removed entirely, including its weight</td></tr>
 * </table>
 *
 * <p>
 * {@code IGNORE} and {@code TREAT_AS_ABSTAIN} reach the same status in many cases but
 * account for it differently, and the aggregation evidence distinguishes them. Treating
 * them as interchangeable is what previously collapsed {@code IGNORE} into a duplicate.
 * </p>
 *
 * <h2>This policy governs judges, not machinery</h2>
 * <p>
 * It applies to an error a configured <em>judge</em> produced. It does not apply to an error
 * the library's own composition or reduction produced: a strategy that threw, a member stage
 * that failed, a cascade that decided nothing. Those carry a machinery
 * {@link io.github.markpollack.judge.result.JudgmentReasonCode}, and they are never converted
 * into a failing contribution — <em>not even under</em> {@link #TREAT_AS_FAIL}.
 * </p>
 * <p>
 * The reason is worth stating plainly, because the alternative looks harmless. If a broken
 * reduction could become a FAIL, the subject would be rejected for something it did not do,
 * and the resulting rejection would be indistinguishable from a real one in every stored
 * field. Machinery failure never supplies rejection evidence; a run that hits one is reported
 * as an instrument failure and excluded from the subject's denominator, so the number that
 * survives is smaller and true rather than larger and wrong. Under {@code TREAT_AS_FAIL} such
 * an input propagates instead, coded
 * {@link io.github.markpollack.judge.result.JudgmentReasonCode#ERRORS_PROPAGATED}, with the
 * configured policy still recorded as {@code treatAsFail}: the evidence says what was
 * configured, and the reasoning says why it did not apply.
 * </p>
 *
 * @author Mark Pollack
 * @since 0.1.0
 * @see AggregationEvidence
 * @see NotApplicablePolicy
 */
public enum ErrorPolicy {

	/**
	 * An errored judgment makes the whole aggregate an ERROR.
	 * <p>
	 * The default. A judge that could not evaluate should not be silently converted into a
	 * negative finding it never made.
	 * </p>
	 */
	PROPAGATE("propagate"),

	/**
	 * The errored judgment participates as a FAIL.
	 */
	TREAT_AS_FAIL("treatAsFail"),

	/**
	 * The errored judgment participates as an ABSTAIN, becoming a non-vote.
	 * <p>
	 * The conversion is recorded in the aggregation evidence, so it stays distinguishable
	 * from a judge's own abstention.
	 * </p>
	 */
	TREAT_AS_ABSTAIN("treatAsAbstain"),

	/**
	 * The errored judgment is removed entirely from the population — numerator,
	 * denominator, vote count, and weight.
	 * <p>
	 * The original judgment is still retained in {@link Verdict#individual()} for audit and
	 * diagnostics; only its influence on the reduction is removed.
	 * </p>
	 */
	IGNORE("ignore");

	private final String token;

	ErrorPolicy(String token) {
		this.token = token;
	}

	/**
	 * Return the stable lower-camel-case identifier used in aggregation evidence.
	 * <p>
	 * An explicit field rather than a derivation from {@link #name()}, so renaming a
	 * constant cannot silently alter the published contract.
	 * </p>
	 * @return the token
	 */
	public String token() {
		return token;
	}

}
