/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury.interpretation;

import java.util.Map;

import io.github.markpollack.judge.jury.Verdict;

/**
 * The two entry points that turn a verdict into its {@link Interpretation}.
 *
 * @author Mark Pollack
 * @since 0.17.0
 */
public final class Verdicts {

	private Verdicts() {
	}

	/**
	 * Interpret a live verdict.
	 * @param verdict the verdict
	 * @return its interpretation
	 */
	public static Interpretation interpret(Verdict verdict) {
		throw new UnsupportedOperationException("interpret(Verdict) is not implemented yet");
	}

	/**
	 * Interpret a stored verdict of any age.
	 * @param stored the stored verdict, as parsed JSON
	 * @return its interpretation
	 */
	public static Interpretation interpret(Map<String, Object> stored) {
		throw new UnsupportedOperationException("interpret(Map) is not implemented yet");
	}

}
