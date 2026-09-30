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
 * Authoritative interpretation of live and stored results.
 *
 * <p>
 * Both entry points read the same portable shape. Explicit version-2 Judgment and Verdict
 * roots retain product finding, native support, checks, provenance, policy, raw reasons
 * and derived operational values. Nested results must use the same version. Unknown or
 * malformed modern records have no usable subject reading and report UNDETERMINED support
 * with defects.
 *
 * <p>
 * Unversioned 0.13–0.17 maps use their own tolerant historical rules, retaining missing
 * facts and legacy boolean checks without inventing modern outcomes. Direct
 * deserialization into live result types is not a historical migration reader. The public
 * portable-results-v2 contract specifies version selection, identity, execution evidence
 * and reduction/routing validation.
 */
public final class Verdicts {

	private static final ObjectMapper PORTABLE = new ObjectMapper();

	private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
	};

	private Verdicts() {
	}

	/**
	 * Interpret a live version-2 verdict through its ordinary portable JSON projection. A
	 * valid one-seat {@link Verdict#single} has supported whole-value identity without
	 * new aggregation evidence. Actual reductions and composite decisions require
	 * coherent retained inputs and execution facts. Malformed custom results are
	 * unsupported.
	 * @param verdict the verdict
	 * @return its interpretation
	 */
	public static Interpretation interpret(Verdict verdict) {
		Objects.requireNonNull(verdict, "verdict must not be null");
		return Interpreter.interpret(PORTABLE.convertValue(verdict, MAP));
	}

	/**
	 * Interpret a stored verdict of any age.
	 * @param stored the stored verdict, as parsed JSON: string keys, with objects as
	 * maps, arrays as lists, and numbers, strings, booleans and nulls as themselves
	 * @return its interpretation, never null and never an exception on the map's content
	 */
	public static Interpretation interpret(Map<String, Object> stored) {
		Objects.requireNonNull(stored, "stored must not be null");
		return Interpreter.interpret(stored);
	}

}
