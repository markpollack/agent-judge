/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.judgment;

import io.github.markpollack.judge.requirement.Requirement;
import java.util.Objects;

/**
 * Complete returned result and the expectation that refused it.
 *
 * @param original unchanged native domain return, including its own Checks and
 * invocations
 * @param expected actual configured Requirement
 * @param reason explicit boundary refusal
 */
public record RefusedReturn(Judgment original, Requirement<?> expected, RefusalReason reason) {
	/** Validate the exact refusal while preserving the original. */
	public RefusedReturn {
		Objects.requireNonNull(original);
		Requirement.validate(expected);
		Objects.requireNonNull(reason);
		if (original.requirement() == null || Requirement.equivalent(original.requirement(), expected))
			throw new IllegalArgumentException("Requirement mismatch needs a distinct actual Requirement");
		JudgmentBounds.validate(original, 1, original);
	}
}
