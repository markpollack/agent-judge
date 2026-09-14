/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.description;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * One seat of a {@link io.github.markpollack.judge.jury.SimpleJury}: the judge at a position,
 * the key its judgment is stored under, and the weight it votes with.
 *
 * <p>
 * The position is the zero-based index used by
 * {@link io.github.markpollack.judge.jury.Verdict#individual()} and by the keys of
 * {@link io.github.markpollack.judge.jury.Verdict#weights()}. The verdict key is the key of
 * {@link io.github.markpollack.judge.jury.Verdict#individualByName()}. A seat is where the
 * two join.
 * </p>
 *
 * <h2>Portable form</h2>
 * <pre>
 * {"position": 0, "verdictKey": "Judge#1", "keySource": "POSITIONAL", "weight": 1.0, "judge": {...}}
 * </pre>
 *
 * @param position the zero-based position
 * @param verdictKey the key of this judge's judgment in a verdict
 * @param keySource where the verdict key came from; only {@link KeySource#DECLARED} is an
 * identity
 * @param weight the configured weight
 * @param judge the judge in this seat
 * @author Mark Pollack
 * @since 0.17.0
 */
public record SeatDescription(int position, String verdictKey, KeySource keySource, double weight,
		JudgeDescription judge) {

	/**
	 * Validate the seat.
	 * @throws IllegalArgumentException if the position is negative or the weight is negative
	 * or not finite
	 */
	public SeatDescription {
		if (position < 0) {
			throw new IllegalArgumentException("position must not be negative, but was " + position);
		}
		Objects.requireNonNull(verdictKey, "verdictKey must not be null");
		Objects.requireNonNull(keySource, "keySource must not be null");
		if (!Double.isFinite(weight) || weight < 0.0) {
			throw new IllegalArgumentException(
					"weight must be finite and not negative, but was " + weight + " at position " + position);
		}
		Objects.requireNonNull(judge, "judge must not be null");
	}

	Map<String, Object> portableTree() {
		Map<String, Object> tree = new LinkedHashMap<>();
		tree.put("position", position);
		tree.put("verdictKey", verdictKey);
		tree.put("keySource", keySource.wireName());
		tree.put("weight", weight);
		tree.put("judge", judge.portableTree());
		return tree;
	}

}
