/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury.interpretation;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * The scale a stored score was recorded on, when the record stated one.
 *
 * <p>Judgments written before 0.14 carried a score object with its own bounds,
 * {@code {"value": 7.5, "min": 0.0, "max": 10.0}}. {@link JudgeSeat#score()} reports such a
 * score normalised to {@code [0, 1]} — the record states its own scale, and dividing by it is
 * reading, not inferring — and this record says what the scale was, so a rescaled score is never
 * indistinguishable from one recorded in {@code [0, 1]}. It is present exactly when the stored
 * score carried recorded bounds, and null for a bare number or an absent score.
 *
 * @param min the recorded lower bound
 * @param max the recorded upper bound, greater than {@code min}
 * @author Mark Pollack
 * @since 0.17.0
 */
@JsonPropertyOrder({ "min", "max" })
public record ScoreScale(double min, double max) {

	/** Validate that the bounds are finite and ordered. */
	public ScoreScale {
		if (!Double.isFinite(min) || !Double.isFinite(max) || max <= min) {
			throw new IllegalArgumentException("a score scale needs finite bounds with max > min, but was [" + min
					+ ", " + max + "]");
		}
	}

}
