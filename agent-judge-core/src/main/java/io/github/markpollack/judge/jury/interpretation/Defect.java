/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury.interpretation;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * One thing the record is missing, cannot say, or contradicts itself on.
 *
 * <p>Every defect names the path of the structure it is about — {@code verdict},
 * {@code verdict.compositeAttempts[1].verdict.aggregated} — and the field within it. A reader
 * that wants to know what is wrong reads the list; a reader that wants to know what follows from
 * it applies its own policy.
 *
 * @param path the path of the structure the defect is about, rooted at {@code verdict}
 * @param field the field within that structure
 * @param kind what is wrong
 * @param note why it matters, in prose
 * @author Mark Pollack
 * @since 0.17.0
 */
@JsonPropertyOrder({ "path", "field", "kind", "note" })
public record Defect(String path, String field, DefectKind kind, String note) {

	/** Validate that every component is present. */
	public Defect {
		Objects.requireNonNull(path, "path must not be null");
		Objects.requireNonNull(field, "field must not be null");
		Objects.requireNonNull(kind, "kind must not be null");
		Objects.requireNonNull(note, "note must not be null");
	}

}
