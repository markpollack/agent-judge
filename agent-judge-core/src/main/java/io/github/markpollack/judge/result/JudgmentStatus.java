/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.result;

import java.util.Arrays;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Status of a judgment evaluation.
 *
 * <p>
 * Represents the outcome of a judge's evaluation. This enum replaces boolean pass/fail to
 * support richer evaluation states including abstention and errors.
 * </p>
 *
 * <p>
 * The central distinction this enum draws is between three ways of not passing:
 * </p>
 * <table border="1">
 * <caption>Not passing, told apart</caption>
 * <tr><th>Status</th><th>The question</th><th>What a denominator does with it</th></tr>
 * <tr><td>{@link #FAIL}</td><td>asked, and answered no</td><td>counted, against the subject</td></tr>
 * <tr><td>{@link #ABSTAIN}</td><td>asked, and undecided</td><td>counted; no vote cast</td></tr>
 * <tr><td>{@link #NOT_APPLICABLE}</td><td>should not have been asked</td><td>excluded, and counted separately</td></tr>
 * <tr><td>{@link #ERROR}</td><td>never reached</td><td>excluded from the subject denominator</td></tr>
 * </table>
 * <p>
 * Executable examples are maintained in the Agent Judge Tutorial: https://github.com/markpollack/agent-judge-tutorial.
 * </p>
 *
 * <h2>Wire representation</h2>
 * <p>
 * Java keeps its own conventions ({@code JudgmentStatus.ERROR}); JSON gets portable ones
 * ({@code "error"}). The mapping is an explicit stable field rather than a mechanical
 * derivation from {@link #name()}, so that renaming a Java constant cannot silently alter
 * the published contract.
 * </p>
 * <p>
 * Parsing is exact and case-sensitive: {@code "ERROR"} is refused, not silently accepted.
 * Accepting both spellings would make the contract ambiguous from the outset.
 * </p>
 *
 * @author Mark Pollack
 * @since 0.1.0
 */
public enum JudgmentStatus {

	/**
	 * Judge determined the evaluation passed.
	 */
	PASS("pass"),

	/**
	 * Judge determined the evaluation failed.
	 */
	FAIL("fail"),

	/**
	 * The criterion applies and this is the right instrument, but the judge could not decide.
	 * <p>
	 * Missing evidence, an ambiguous artifact, a model that would not commit: the question was
	 * the right one to ask and it has no answer yet. A judge that reached no decision casts no
	 * vote, so an abstention leaves the population a strategy reduces over.
	 * </p>
	 * <p>
	 * This is <em>not</em> "does not apply"; that is {@link #NOT_APPLICABLE}, which is excluded
	 * from a denominator rather than merely undecided within it.
	 * </p>
	 */
	ABSTAIN("abstain"),

	/**
	 * The criterion does not apply to this subject, so the question should not have been asked.
	 * <p>
	 * Reserved for a subject that by definition lacks what the criterion is about — a Java
	 * style rule against a repository with no Java in it. Missing evidence is
	 * {@link #ABSTAIN}, not this.
	 * </p>
	 * <p>
	 * It is excluded from the denominator and counted, so a rubric can report honestly how much
	 * of it applied. Because that exclusion is also the easiest way for an instrument to dodge a
	 * criterion, a judge or jury may only return it where it declared in advance that it can:
	 * an undeclared exclusion is contained as an error rather than honoured.
	 * </p>
	 *
	 * @since 0.17.0
	 */
	NOT_APPLICABLE("not_applicable"),

	/**
	 * Judge encountered an error during evaluation.
	 */
	ERROR("error");

	private final String wireName;

	JudgmentStatus(String wireName) {
		this.wireName = wireName;
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
	 * Resolve a status from its wire name.
	 * <p>
	 * Matching is exact and case-sensitive.
	 * </p>
	 * @param value the wire name
	 * @return the matching status
	 * @throws IllegalArgumentException if no status has that exact wire name
	 */
	@JsonCreator
	public static JudgmentStatus fromWire(String value) {
		return Arrays.stream(values())
			.filter(status -> status.wireName.equals(value))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("Unknown judgment status: " + value));
	}

}
