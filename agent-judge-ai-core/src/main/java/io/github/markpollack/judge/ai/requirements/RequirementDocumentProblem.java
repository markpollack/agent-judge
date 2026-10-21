/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.ai.requirements;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * One reason a requirement document did not load cleanly.
 *
 * <p>
 * The permissive readers log these and return what they could build; the strict readers
 * refuse the document and hand every problem back in a
 * {@link RequirementDocumentException}. Either way a malformed entry is named rather than
 * silently left out of the roster.
 *
 * @param source the document's file name, as the readers log it
 * @param id the requirement identifier the problem belongs to, or null when the heading
 * itself offered none
 * @param line the 1-based line in the document where the problem was found
 * @param reason what is wrong, in one sentence
 * @author Mark Pollack
 * @since 0.19.0
 */
public record RequirementDocumentProblem(String source, @Nullable String id, int line, String reason) {

	/**
	 * Validate the location and reason.
	 * @throws IllegalArgumentException if the line is not positive or the reason is blank
	 */
	public RequirementDocumentProblem {
		Objects.requireNonNull(source, "source");
		Objects.requireNonNull(reason, "reason");
		if (line < 1) {
			throw new IllegalArgumentException("line numbers start at 1");
		}
		if (reason.isBlank()) {
			throw new IllegalArgumentException("a problem needs a reason");
		}
	}

	/**
	 * The problem on one line: {@code source:line [id]: reason}.
	 * @return a description a person can act on
	 */
	public String describe() {
		return source + ":" + line + (id == null ? "" : " " + id) + ": " + reason;
	}
}
