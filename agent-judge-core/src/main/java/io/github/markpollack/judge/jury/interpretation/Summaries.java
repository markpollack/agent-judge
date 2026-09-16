/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury.interpretation;

/**
 * The deterministic prose summary of an {@link Interpretation}.
 *
 * @author Mark Pollack
 * @since 0.17.0
 */
public final class Summaries {

	private Summaries() {
	}

	/**
	 * Summarise an interpretation from its fields alone.
	 * @param interpretation the interpretation
	 * @return the summary
	 */
	public static String of(Interpretation interpretation) {
		throw new UnsupportedOperationException("Summaries.of is not implemented yet");
	}

}
