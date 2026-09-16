/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury.interpretation;

import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Which stage decided the verdict, as the record says.
 *
 * <p>Found by following the recorded decision chain from the root: while a decision adopts a
 * named tier's outcome ({@code tier_outcome}), move into that tier; stop at a jury's own
 * reduction, at an undecided verdict, or at a tier reached by an individual rejection. The stage
 * is then the tier the stopping decision names, or the last edge followed. A root that decided
 * itself names no stage, and a record without a decision reports {@code null} together with an
 * {@link DefectKind#ABSENT} defect — never a guess.
 *
 * @param stage the deciding stage's configured name
 * @param path the deciding stage's full path from the root, the same value as the matching
 * {@link Stage#path()}
 * @param basis how the stage decided: the wire token {@code tier_outcome} or
 * {@code individual_rejection}, or an unrecognised token carried as recorded
 * @author Mark Pollack
 * @since 0.17.0
 */
@JsonPropertyOrder({ "stage", "path", "basis" })
public record DecidedBy(String stage, List<String> path, String basis) {

	/** Validate and copy. */
	public DecidedBy {
		Objects.requireNonNull(stage, "stage must not be null");
		Objects.requireNonNull(path, "path must not be null");
		path = List.copyOf(path);
		Objects.requireNonNull(basis, "basis must not be null");
	}

}
