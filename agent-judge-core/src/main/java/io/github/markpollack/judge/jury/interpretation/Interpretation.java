/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury.interpretation;

import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.jspecify.annotations.Nullable;

/**
 * The interpretation of one verdict.
 *
 * <p>
 * Version 3 retains complete modern semantic views and explicitly absent historical
 * facts. A consumer reads the views and reading support rather than re-deriving outcomes.
 *
 * <p>
 * {@link #root()} is the verdict's own aggregate and its judges, so a flat verdict with
 * no composite stage is fully explained. {@link #stages()} are the attempts a composite
 * jury entered, recursively, depth-first in attempt order, each with its own full path.
 *
 * @param schemaVersion the version of this shape: {@code 3}
 * @param sourceVersion what was read: {@code 0} for an unstamped verdict written before
 * 0.17, {@code 1} for the unversioned 0.17 form, {@code 3} for modern results, another
 * integer for an unknown version, or {@code -1} for a malformed/missing modern stamp
 * @param outcome what the verdict says about the subject; null when the historical root
 * has no readable status or a modern reading is unsupported
 * @param readingSupport whether the recorded facts support the reading
 * @param decidedBy which stage decided, as the record says, or null when the root decided
 * itself or the record does not say
 * @param root the verdict's own aggregate and judges
 * @param stages every stage a composite jury entered, recursively
 * @param defects what the record is missing, cannot say, or contradicts itself on; empty
 * for a complete record
 * @param summary a deterministic prose account of the fields above, produced by
 * {@link Summaries#of(Interpretation)}
 * @author Mark Pollack
 * @since 0.17.0
 */
@JsonPropertyOrder({ "schemaVersion", "sourceVersion", "outcome", "readingSupport", "decidedBy", "root", "stages",
		"defects", "summary" })
public record Interpretation(@com.fasterxml.jackson.annotation.JsonProperty(
		required = true) @com.fasterxml.jackson.databind.annotation.JsonDeserialize(
				using = io.github.markpollack.judge.serialization.StrictIntegerDeserializer.class) int schemaVersion,
		int sourceVersion, @Nullable RequirementOutcome outcome, ReadingSupport readingSupport,
		@Nullable DecidedBy decidedBy, Stage root, List<Stage> stages, List<Defect> defects, String summary) {

	/** The version of this shape. */
	public static final int SCHEMA_VERSION = 3;

	/** Validate and copy. */
	public Interpretation {
		if (schemaVersion != SCHEMA_VERSION)
			throw new IllegalArgumentException("Interpretation schemaVersion must be 3");
		Objects.requireNonNull(readingSupport, "readingSupport must not be null");
		Objects.requireNonNull(root, "root must not be null");
		Objects.requireNonNull(stages, "stages must not be null");
		stages = List.copyOf(stages);
		Objects.requireNonNull(defects, "defects must not be null");
		defects = List.copyOf(defects);
		Objects.requireNonNull(summary, "summary must not be null");
	}

}
