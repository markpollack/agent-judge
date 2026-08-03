/*
 * Copyright 2024 Spring AI Community
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
 * @author Mark Pollack
 * @since 0.1.0
 * @see AggregationEvidence
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
