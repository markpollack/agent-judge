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
 * <pre>{@code
 * FAIL    = the judge completed and rejected the subject
 * ERROR   = the judge could not complete the evaluation
 * ABSTAIN = the judge does not apply to this subject, so it casts no vote
 * }</pre>
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
