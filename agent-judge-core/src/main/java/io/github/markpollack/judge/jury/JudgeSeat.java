/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury;

import io.github.markpollack.judge.Judge;
import org.jspecify.annotations.Nullable;

/**
 * Immutable composition seat; the same Judge can occupy independently declared seats.
 *
 * @param name composition name
 * @param judge configured evaluator
 * @param notApplicableWhen local exclusion permission, absent by default
 */
public record JudgeSeat(String name, Judge judge, @Nullable String notApplicableWhen) {
	/** Validates the seat without execution. */
	public JudgeSeat {
		NamedJury.requireValidName(name);
		java.util.Objects.requireNonNull(judge);
		if (notApplicableWhen != null)
			io.github.markpollack.judge.requirement.Requirement.requireText(notApplicableWhen);
	}

	/**
	 * Creates a named seat without exclusion permission.
	 * @param name seat name
	 * @param judge ready evaluator
	 * @return seat
	 */
	public static JudgeSeat named(String name, Judge judge) {
		return new JudgeSeat(name, judge, null);
	}

	/**
	 * Declares the seat's exclusion condition.
	 * @param condition verifiable condition
	 * @return immutable declared seat
	 */
	public JudgeSeat notApplicableWhen(String condition) {
		return new JudgeSeat(name, judge, condition);
	}
}
