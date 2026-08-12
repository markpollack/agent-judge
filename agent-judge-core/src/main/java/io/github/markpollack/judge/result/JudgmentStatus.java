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
 * The central distinction this enum draws:
 * </p>
 * Executable examples are maintained in the Agent Judge Tutorial: https://github.com/markpollack/agent-judge-tutorial.
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
	 * Judge cannot or chooses not to evaluate (insufficient information, not applicable).
	 */
	ABSTAIN("abstain"),

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
