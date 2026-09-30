/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.github.markpollack.judge.serialization.StrictIntegerDeserializer;

import io.github.markpollack.judge.description.KeySource;

/**
 * One judgment's place in a verdict: where it sat, what key it is stored under, and where
 * that key came from.
 *
 * <p>
 * A verdict has always carried {@link Verdict#individual()} in order and
 * {@link Verdict#individualByName()} by key, and joining them was left to the reader.
 * That worked until the join stopped being obvious — a duplicate declared name collapses
 * two judgments into one map entry, a meta-jury omits members that failed, and a key like
 * {@code "Judge#2"} identifies a position rather than a judge. A seat records the join
 * explicitly, so a stored result can be attributed to the judge that produced it rather
 * than to whichever key happened to survive.
 * </p>
 *
 * <p>
 * {@link #keySource()} is the part worth reading. Only {@link KeySource#DECLARED} is an
 * identity: a positional key means something different the moment a judge is inserted
 * above it, and a deduplicated key depends on the order the judges were supplied in. A
 * reader attributing results across runs should treat anything but {@code DECLARED} as
 * unattributable rather than as a name.
 * </p>
 *
 * @param position the seat's zero-based configured position, which indexes
 * {@link Verdict#individual()} and keys {@link Verdict#weights()}
 * @param verdictKey the key this judgment is stored under in
 * {@link Verdict#individualByName()}
 * @param execution recorded invocation outcome
 * @param participation parent treatment, separate from producer outcome
 * @param cause original invocation exception, memory only; excluded from portable
 * equality
 * @param keySource where the verdict key came from
 * @author Mark Pollack
 * @since 0.17.0
 */
@JsonPropertyOrder({ "position", "verdictKey", "keySource", "execution", "participation" })
public record Seat(
		@JsonProperty(required = true) @JsonDeserialize(using = StrictIntegerDeserializer.class) int position,
		String verdictKey, KeySource keySource, SeatExecution execution, Participation participation,
		@com.fasterxml.jackson.annotation.JsonIgnore @org.jspecify.annotations.Nullable Throwable cause) {

	/**
	 * Construct a seat explicitly asserting a valid returned judgment.
	 * @param position configured position
	 * @param verdictKey result key
	 * @param keySource source of the key
	 */
	public Seat(int position, String verdictKey, KeySource keySource) {
		this(position, verdictKey, keySource, SeatExecution.RETURNED);
	}

	/**
	 * Record a seat without claiming a reduction treatment.
	 * @param position configured position
	 * @param verdictKey result key
	 * @param keySource origin of the key
	 * @param execution invocation outcome
	 */
	public Seat(int position, String verdictKey, KeySource keySource, SeatExecution execution) {
		this(position, verdictKey, keySource, execution, Participation.NOT_RECORDED, null);
	}

	Seat treated(Participation treatment) {
		return new Seat(position, verdictKey, keySource, execution, treatment, cause);
	}

	/** Equality concerns portable seat facts; a live Throwable is diagnostic context. */
	@Override
	public boolean equals(Object other) {
		return other instanceof Seat seat && position == seat.position && verdictKey.equals(seat.verdictKey)
				&& keySource == seat.keySource && execution == seat.execution && participation == seat.participation;
	}

	@Override
	public int hashCode() {
		return Objects.hash(position, verdictKey, keySource, execution, participation);
	}

	/**
	 * Validate the seat.
	 * @throws IllegalArgumentException if the position is negative or the verdict key is
	 * blank
	 */
	public Seat {
		Objects.requireNonNull(execution, "execution must be explicit");
		Objects.requireNonNull(participation, "participation must be explicit");
		if (position < 0) {
			throw new IllegalArgumentException("position must not be negative, but was " + position);
		}
		Objects.requireNonNull(verdictKey, "verdictKey must not be null");
		if (verdictKey.isBlank()) {
			throw new IllegalArgumentException("verdictKey must be non-blank");
		}
		Objects.requireNonNull(keySource, "keySource must not be null");
	}

}
