/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury.interpretation;

import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.jspecify.annotations.Nullable;

/**
 * One judge's seat in a stage, with everything its judgment recorded.
 *
 * <p>A reader never opens {@code individualByName} to learn why a judge said what it said: the
 * reasoning, checks, score and reason code are here. Only a {@code DECLARED} key source is an
 * identity; a record written before seats were recorded reports {@code null}, because whether
 * each name was declared is unrecorded.
 *
 * @param position the seat's configured position; for a record without seats, the judge's
 * position in the keyed map
 * @param name the key the judgment is stored under
 * @param keySource where the key came from — {@code DECLARED}, {@code DEDUPLICATED} or
 * {@code POSITIONAL} — or null when the record carries no seats
 * @param status the judgment's status, as the wire token 0.17 writes; an upper-case 0.13 token
 * is read as its 0.17 equivalent; an unrecognised token is carried as recorded; null only when
 * the record carries no readable status, which its defects say
 * @param reasonCode the judgment's reason code, or null when none was recorded
 * @param score the judgment's score normalised to {@code [0, 1]}, or null when none was
 * recorded or the recorded one could not be read
 * @param scoreScale the scale the score was recorded on, present exactly when the stored score
 * carried its own bounds; omitted from the serialised form when null
 * @param reasoning the judgment's reasoning, as recorded; an empty string when none was recorded
 * @param checks the checks the judgment recorded, in order
 * @author Mark Pollack
 * @since 0.17.0
 */
@JsonPropertyOrder({ "position", "name", "keySource", "status", "reasonCode", "score", "scoreScale", "reasoning",
		"checks" })
public record JudgeSeat(int position, String name, @Nullable String keySource, @Nullable String status,
		@Nullable String reasonCode, @Nullable Double score,
		@JsonInclude(JsonInclude.Include.NON_NULL) @Nullable ScoreScale scoreScale, String reasoning,
		List<Check> checks) {

	/** Validate and copy. */
	public JudgeSeat {
		Objects.requireNonNull(name, "name must not be null");
		Objects.requireNonNull(reasoning, "reasoning must not be null");
		Objects.requireNonNull(checks, "checks must not be null");
		checks = List.copyOf(checks);
	}

}
