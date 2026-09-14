/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.result;

import java.util.Arrays;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import org.jspecify.annotations.Nullable;

/**
 * The countable cause of a judgment, alongside its mandatory free text.
 *
 * <p>
 * One code plus {@link Judgment#reasoning()}. The code is what a reader counts; the reasoning
 * is what a human reads. Neither replaces the other, and a category is never guessed: every
 * constant below corresponds to an observed failure shape, a source, a policy or decision
 * outcome, or a library mechanism. New constants are added only when observed reason clusters
 * earn them.
 * </p>
 *
 * <h2>Two families</h2>
 * <table border="1">
 * <caption>Which status each family may appear on</caption>
 * <tr><th>Family</th><th>Status</th><th>Meaning</th></tr>
 * <tr><td>{@link Family#INSTRUMENT}</td><td>{@code ERROR} only, and required</td>
 * <td>the instrument failed, so no finding was reached</td></tr>
 * <tr><td>{@link Family#SUBJECT}</td><td>{@code FAIL} only, and optional</td>
 * <td>a coded subject failure</td></tr>
 * </table>
 * <p>
 * A {@code FAIL} with no code is an <em>uncoded rejection</em>, by deliberate choice: most
 * rejections are explained in prose and inventing a category for them would manufacture a
 * taxonomy nobody asked for.
 * </p>
 *
 * <h2>Three origin families</h2>
 * <p>
 * {@link #originFamily()} says who failed, which decides whether an
 * {@link io.github.markpollack.judge.jury.ErrorPolicy} may convert the error into a failing
 * contribution:
 * </p>
 * <ul>
 * <li>{@link OriginFamily#JUDGE} — a configured judge failed. The error policy governs it.</li>
 * <li>{@link OriginFamily#MACHINERY} — the library's own composition or reduction failed. It is
 * never scored, under any error policy: machinery failure never supplies rejection
 * evidence.</li>
 * <li>{@link OriginFamily#WRAPPER} — {@link #ERRORS_PROPAGATED}, which carries the origins it
 * propagated rather than being an origin itself. Its own aggregation evidence must name them,
 * and a wrapper whose flattened origin includes a machinery code is machinery-origin.</li>
 * </ul>
 *
 * <h2>Wire representation</h2>
 * <p>
 * Java keeps its own conventions ({@code JudgmentReasonCode.JUDGE_FAILED}); JSON gets portable
 * ones ({@code "judge_failed"}). The mapping is an explicit stable field rather than a
 * derivation from {@link #name()}, so renaming a Java constant cannot silently alter the
 * published contract. Parsing is exact and case-sensitive.
 * </p>
 *
 * @author Mark Pollack
 * @since 0.17.0
 * @see Judgment#reasonCode()
 */
public enum JudgmentReasonCode {

	/**
	 * A configured judge threw, or returned no judgment at all.
	 * <p>
	 * An observed failure shape, recorded at judge level by
	 * {@code SimpleJury}.
	 * </p>
	 */
	JUDGE_FAILED("judge_failed", Family.INSTRUMENT, OriginFamily.JUDGE),

	/**
	 * A judge's {@code metadata()} threw or returned null, so the jury could not tell what the
	 * judge is called and did not run it.
	 * <p>
	 * An observed failure shape, recorded at judge level by {@code SimpleJury}'s seat key.
	 * </p>
	 */
	JUDGE_METADATA_UNREADABLE("judge_metadata_unreadable", Family.INSTRUMENT, OriginFamily.JUDGE),

	/**
	 * The judge itself reported that it could not evaluate.
	 * <p>
	 * A source, not a shape: the default for {@link Judgment#error(String)} and for the
	 * requirement judges' protocol and roster errors. It says the error came from inside a
	 * judge, and the reasoning says what happened.
	 * </p>
	 */
	JUDGE_REPORTED("judge_reported", Family.INSTRUMENT, OriginFamily.JUDGE),

	/**
	 * A seat returned {@link JudgmentStatus#NOT_APPLICABLE} without declaring that it can.
	 * <p>
	 * A library mechanism: the seat guard converts an undeclared exclusion into an error so the
	 * error policy governs it, rather than letting a judge dodge a criterion by declaring it
	 * inapplicable after the fact.
	 * </p>
	 */
	UNDECLARED_NOT_APPLICABLE("undeclared_not_applicable", Family.INSTRUMENT, OriginFamily.JUDGE),

	/**
	 * The aggregate is an error because errored inputs were propagated.
	 * <p>
	 * A policy outcome, recorded at aggregate level. It is a wrapper: its aggregation evidence
	 * must carry a structurally valid, non-empty {@code errorCodeCounts} naming the terminal
	 * codes it propagated, and {@link Judgment#propagatedError} is the only way to build one.
	 * </p>
	 */
	ERRORS_PROPAGATED("errors_propagated", Family.INSTRUMENT, OriginFamily.WRAPPER),

	/**
	 * The strategy's not-applicable policy is {@code REFUSE} and an input was
	 * {@link JudgmentStatus#NOT_APPLICABLE}.
	 * <p>
	 * A policy outcome, recorded at aggregate level. Machinery: the jury was configured not to
	 * accept exclusions, so nothing about the subject was established.
	 * </p>
	 */
	NOT_APPLICABLE_REFUSED("not_applicable_refused", Family.INSTRUMENT, OriginFamily.MACHINERY),

	/**
	 * The reduction itself failed: the strategy threw, returned null, returned an aggregate the
	 * jury does not allow, or excluded the subject without being able to.
	 * <p>
	 * An observed failure shape, recorded at aggregate level by containment.
	 * </p>
	 */
	AGGREGATION_FAILED("aggregation_failed", Family.INSTRUMENT, OriginFamily.MACHINERY),

	/**
	 * A composite stage did not produce a usable determination.
	 * <p>
	 * An observed failure shape and a mechanism, recorded at aggregate level: a meta-jury member
	 * threw, returned an undecided verdict, or returned an undeclared
	 * {@link JudgmentStatus#NOT_APPLICABLE} aggregate; or a cascade built this root when it
	 * stopped on a genuine individual rejection in a boundary-rejected tier.
	 * </p>
	 */
	STAGE_FAILED("stage_failed", Family.INSTRUMENT, OriginFamily.MACHINERY),

	/**
	 * No cascade tier produced a determination.
	 * <p>
	 * A decision outcome, recorded at aggregate level by {@code CascadedJury}.
	 * </p>
	 */
	NO_TIER_DECIDED("no_tier_decided", Family.INSTRUMENT, OriginFamily.MACHINERY),

	/**
	 * The judge inspected the artifact and found nothing to assess.
	 * <p>
	 * The one subject code. It belongs on a {@code FAIL} produced by a judge that looked and
	 * found the subject empty, with reasoning stating its bounded definition of empty. Absence
	 * arranged by the harness, and empty grader output, are not this: the first is a run-level
	 * record the harness owns, and the second is an instrument failure.
	 * </p>
	 */
	SUBJECT_EMPTY("subject_empty", Family.SUBJECT, null);

	/** Whether a code describes the instrument or the subject. */
	public enum Family {

		/** The instrument failed. Legal only on {@link JudgmentStatus#ERROR}, where it is required. */
		INSTRUMENT,

		/** The subject failed for a named cause. Legal only on {@link JudgmentStatus#FAIL}, where it is optional. */
		SUBJECT

	}

	/** Who failed, for an instrument code. */
	public enum OriginFamily {

		/** A configured judge. The error policy governs it. */
		JUDGE,

		/** The library's own composition or reduction. Never scored, under any error policy. */
		MACHINERY,

		/** A propagating aggregate, whose real origins are in its {@code errorCodeCounts}. */
		WRAPPER

	}

	private final String wireName;

	private final Family family;

	private final @Nullable OriginFamily originFamily;

	JudgmentReasonCode(String wireName, Family family, @Nullable OriginFamily originFamily) {
		this.wireName = wireName;
		this.family = family;
		this.originFamily = originFamily;
	}

	/**
	 * Return the stable lower-case identifier used in JSON and other wire formats.
	 * @return the wire name
	 */
	@JsonValue
	public String wireName() {
		return wireName;
	}

	/**
	 * Which status this code may appear on.
	 * @return the family
	 */
	public Family family() {
		return family;
	}

	/**
	 * Who failed, for an instrument code.
	 * @return the origin family, or null for a subject code, which describes the subject rather
	 * than an instrument
	 */
	public @Nullable OriginFamily originFamily() {
		return originFamily;
	}

	/**
	 * Whether this code names an origin rather than propagating other origins.
	 * <p>
	 * Every instrument code except {@link #ERRORS_PROPAGATED} is terminal, and only terminal
	 * codes may appear as keys of an {@code errorCodeCounts} block.
	 * </p>
	 * @return true when the code is terminal
	 */
	public boolean terminal() {
		return this.originFamily != OriginFamily.WRAPPER;
	}

	/**
	 * Resolve a reason code from its wire name.
	 * <p>
	 * Matching is exact and case-sensitive.
	 * </p>
	 * @param value the wire name
	 * @return the matching reason code
	 * @throws IllegalArgumentException if no code has that exact wire name
	 */
	@JsonCreator
	public static JudgmentReasonCode fromWire(String value) {
		return Arrays.stream(values())
			.filter(code -> code.wireName.equals(value))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("Unknown judgment reason code: " + value));
	}

}
