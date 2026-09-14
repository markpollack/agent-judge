/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge;

import org.jspecify.annotations.Nullable;

/**
 * Metadata about a judge including its name, description, type, and whether it may exclude a
 * subject as out of scope.
 *
 * <h2>Declaring an exclusion capability</h2>
 * <p>
 * {@code notApplicableWhen} is how a judge says, <em>before it runs</em>, that it may return
 * {@link io.github.markpollack.judge.result.JudgmentStatus#NOT_APPLICABLE} and under what
 * condition. Absence is the default and means the judge never excludes anything; a jury
 * contains an exclusion from an undeclared seat as an error rather than honouring it.
 * </p>
 * <p>
 * The declaration exists because exclusion is the one outcome a judge can use to remove itself
 * from its own denominator. Requiring it in advance means the condition is written down while
 * the jury is being assembled — where a reviewer can disagree with it — rather than asserted
 * after the subject has been seen.
 * </p>
 * <p>
 * It is a <em>declaration, not proof</em>. Nothing checks that the judge only excludes when
 * the stated condition holds; the sentence is an author's assertion, and it is worth reading
 * as one. Write the condition a reader could check against the subject — "the repository
 * contains no Java sources", "criteria UC3-AC7 and UC3-AC9 are conditional" — not a restatement
 * of the fact that the judge sometimes excludes.
 * </p>
 *
 * @param name the judge name (e.g., "FileExistsJudge", "CorrectnessJudge")
 * @param description human-readable description of what this judge evaluates
 * @param type the judge type (deterministic, LLM-powered, hybrid, or agent)
 * @param notApplicableWhen the condition under which this judge may return
 * {@code NOT_APPLICABLE}, or null when it never does; must be non-blank when present
 * @author Mark Pollack
 * @since 0.1.0
 */
public record JudgeMetadata(String name, String description, JudgeType type, @Nullable String notApplicableWhen) {

	/**
	 * Validate the exclusion declaration.
	 * <p>
	 * A blank declaration is refused rather than read as absence: it would claim a capability
	 * while saying nothing about when it applies, which is the one thing the declaration is
	 * for. A judge that never excludes passes {@code null}.
	 * </p>
	 * @throws IllegalArgumentException if {@code notApplicableWhen} is present and blank
	 */
	public JudgeMetadata {
		if (notApplicableWhen != null && notApplicableWhen.isBlank()) {
			throw new IllegalArgumentException(
					"notApplicableWhen must be non-blank when present; use null when the judge never excludes a subject");
		}
	}

}
