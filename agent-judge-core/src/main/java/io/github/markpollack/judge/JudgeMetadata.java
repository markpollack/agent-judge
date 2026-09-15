/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge;

import java.util.Objects;

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
 * <h2>The name identifies a seat</h2>
 * <p>
 * A jury stores each judgment under the name its judge declared, so the name is an identity
 * rather than a label, and a blank one is refused here — where the metadata is made, before any
 * jury is assembled and long before any judge runs. Refusing it later, where a seat is built,
 * would put the rejection outside the jury's containment: one blank-named seat would discard
 * every other judge's result and collapse the enclosing cascade tier, which is a jury voting
 * with fewer judges than it lists.
 * </p>
 *
 * @param name the judge name (e.g., "FileExistsJudge", "CorrectnessJudge"); must be non-blank
 * @param description human-readable description of what this judge evaluates
 * @param type the judge type (deterministic, LLM-powered, hybrid, or agent)
 * @param notApplicableWhen the condition under which this judge may return
 * {@code NOT_APPLICABLE}, or null when it never does; must be non-blank when present
 * @author Mark Pollack
 * @since 0.1.0
 */
public record JudgeMetadata(String name, String description, JudgeType type, @Nullable String notApplicableWhen) {

	/**
	 * Validate the name and the exclusion declaration.
	 * <p>
	 * A blank name is refused because a jury keys judgments by it: a seat it cannot be built
	 * from must never be reached with judges' work already spent behind it.
	 * </p>
	 * <p>
	 * A blank declaration is refused rather than read as absence: it would claim a capability
	 * while saying nothing about when it applies, which is the one thing the declaration is
	 * for. A judge that never excludes passes {@code null}.
	 * </p>
	 * @throws IllegalArgumentException if {@code name} is blank, or if {@code notApplicableWhen}
	 * is present and blank
	 * @throws NullPointerException if {@code name} is null
	 */
	public JudgeMetadata {
		if (Objects.requireNonNull(name, "name must not be null").isBlank()) {
			throw new IllegalArgumentException("name must be non-blank: a jury stores each judgment under the name its "
					+ "judge declared, and a blank name identifies no seat");
		}
		if (notApplicableWhen != null && notApplicableWhen.isBlank()) {
			throw new IllegalArgumentException(
					"notApplicableWhen must be non-blank when present; use null when the judge never excludes a subject");
		}
	}

}
