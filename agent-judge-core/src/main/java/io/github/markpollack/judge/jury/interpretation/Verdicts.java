/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury.interpretation;

import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.markpollack.judge.jury.Verdict;

/**
 * The two entry points that turn a verdict into its {@link Interpretation}.
 *
 * <p>Both read the same rules over the same shape. {@link #interpret(Verdict)} reads a live
 * verdict through its portable projection — the same map a reader parses from the wire — so the
 * two paths agree by construction, and a stored 0.17 verdict interprets exactly as the live one
 * it was written from.
 *
 * <h2>What a stored map must look like</h2>
 *
 * <p>Keys are the names of the live record's components, at every level: for a verdict,
 * {@code aggregated}, {@code individual}, {@code individualByName}, {@code weights},
 * {@code seats}, {@code decision}, {@code compositeAttempts}; for a judgment, {@code status},
 * {@code score}, {@code label}, {@code reasonCode}, {@code reasoning}, {@code checks},
 * {@code metadata}. Values are the ordinary JSON forms of those components, with vocabulary
 * members carrying their wire names. Records written before 0.17 are read under the same keys:
 * the 0.13 {@code subVerdicts} container, upper-case status tokens and bounded score objects,
 * and the 0.14–0.16 attempts without dispositions, each yield an interpretation whose
 * {@link Interpretation#defects() defects} say what the record lacks. A map that does not match
 * degrades into a list of missing facts; it never throws on its content.
 *
 * @author Mark Pollack
 * @since 0.17.0
 */
public final class Verdicts {

	private static final ObjectMapper PORTABLE = new ObjectMapper();

	private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
	};

	private Verdicts() {
	}

	/**
	 * Interpret a live verdict.
	 * <p>
	 * For every verdict a built-in jury produces the defect list is empty. A hand-built verdict
	 * whose aggregate carries no aggregation evidence — {@link Verdict#single} and
	 * {@link Verdict#of}, which reduce nothing — reports {@link ReadingSupport#UNDETERMINED} with
	 * the {@link DefectKind#ABSENT} defect that names the missing block, because nothing recorded
	 * how its aggregate was produced.
	 * </p>
	 * @param verdict the verdict
	 * @return its interpretation
	 */
	public static Interpretation interpret(Verdict verdict) {
		Objects.requireNonNull(verdict, "verdict must not be null");
		return Interpreter.interpret(PORTABLE.convertValue(verdict, MAP));
	}

	/**
	 * Interpret a stored verdict of any age.
	 * @param stored the stored verdict, as parsed JSON: string keys, with objects as maps,
	 * arrays as lists, and numbers, strings, booleans and nulls as themselves
	 * @return its interpretation, never null and never an exception on the map's content
	 */
	public static Interpretation interpret(Map<String, Object> stored) {
		Objects.requireNonNull(stored, "stored must not be null");
		return Interpreter.interpret(stored);
	}

}
