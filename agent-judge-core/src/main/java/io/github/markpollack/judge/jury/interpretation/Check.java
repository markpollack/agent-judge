/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury.interpretation;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * One check a judge recorded, as recorded.
 *
 * <p>The same fact as {@link io.github.markpollack.judge.result.Check}, carried on the
 * {@link JudgeSeat} so a reader never opens the stored verdict to learn what a judge checked.
 * {@code detail} is the check's recorded message.
 *
 * @param name the check's name
 * @param passed whether the check passed
 * @param detail the recorded message, or an empty string when none was recorded
 * @author Mark Pollack
 * @since 0.17.0
 */
@JsonPropertyOrder({ "name", "passed", "detail" })
public record Check(String name, boolean passed, String detail) {

	/** Validate that the name and detail are present. */
	public Check {
		Objects.requireNonNull(name, "name must not be null");
		Objects.requireNonNull(detail, "detail must not be null");
	}

}
