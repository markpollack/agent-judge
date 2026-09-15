/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import io.github.markpollack.judge.result.JudgmentStatus;

/**
 * The boundary check a composite parent applies to what a child jury returned.
 *
 * <p>
 * Two things can come back from a child that a parent must not simply use. A verdict whose
 * decision is {@link DecisionKind#UNDECIDED} determined nothing, so feeding its aggregate into a
 * reduction would treat an instrument failure as a finding. And a
 * {@link JudgmentStatus#NOT_APPLICABLE} aggregate from a child that never declared it may
 * exclude is a denominator shrinking without anyone having authorized it — a jury does not
 * acquire that right by being nested inside something.
 * </p>
 *
 * <p>
 * The built-in meta-jury and cascade apply this at every child boundary. It is public so that a
 * custom composite parent applies the same rule rather than inventing a second one: an opaque
 * jury makes no pre-spend guarantee about its own aggregate, so wherever its output is consumed
 * it has to be checked at runtime.
 * </p>
 *
 * <p>
 * The check never rewrites the child's verdict. A parent that finds a stage failure records the
 * disposition and reason on the attempt, keeps the child's actual verdict there, and builds its
 * own aggregate; the claim the child made stays visible and countable even when a later stage
 * succeeds.
 * </p>
 *
 * @author Mark Pollack
 * @since 0.17.0
 */
public final class NotApplicableGuard {

	private NotApplicableGuard() {
		// Utility class - no instantiation
	}

	/**
	 * Whether a parent may use what this child returned, and if not, why.
	 * @param child the child jury, whose declared capability decides whether an exclusion is
	 * honoured
	 * @param verdict the verdict the child returned
	 * @return the reason the verdict cannot be used, or null when the parent may use it
	 */
	public static @Nullable DispositionReason stageFailure(Jury child, Verdict verdict) {
		Objects.requireNonNull(child, "child must not be null");
		Objects.requireNonNull(verdict, "verdict must not be null");
		if (verdict.decision().kind() == DecisionKind.UNDECIDED) {
			return DispositionReason.CHILD_UNDECIDED;
		}
		if (verdict.aggregated().status() == JudgmentStatus.NOT_APPLICABLE && !child.aggregateMayBeNotApplicable()) {
			return DispositionReason.UNDECLARED_NOT_APPLICABLE;
		}
		return null;
	}

	/**
	 * Say, in the parent's own words, which stages tried to exclude the subject and were refused.
	 * <p>
	 * The disposition enum makes the refusal countable; this makes it legible. A root that only
	 * says a stage "did not produce a determination" describes a stage that threw, a stage whose
	 * reduction broke, and a stage that tried to exclude the subject in exactly the same words —
	 * and the child's own verdict is no help, because it asserts that the criterion did not
	 * apply, which is precisely the claim the parent rejected.
	 * </p>
	 * <p>
	 * The one case this deliberately does not cover is R-E's: a boundary rejection followed by a
	 * later selected tier, whose reasoning belongs to that tier and which a parent note would
	 * displace. That path never reaches here, because it builds no parent-authored root.
	 * </p>
	 * @param attempts the attempts recorded so far
	 * @param stage what this parent calls its children, such as "Tier" or "Member"
	 * @return a sentence naming the refused exclusions, or an empty string when there were none
	 */
	static String refusedExclusionNote(List<CompositeAttempt> attempts, String stage) {
		List<String> refused = attempts.stream()
			.filter(attempt -> attempt.dispositionReason() == DispositionReason.UNDECLARED_NOT_APPLICABLE)
			.map(CompositeAttempt::name)
			.toList();
		if (refused.isEmpty()) {
			return "";
		}
		String names = refused.stream().collect(Collectors.joining("', '", "'", "'"));
		return " " + stage + (refused.size() == 1 ? " " + names + " returned" : "s " + names + " each returned")
				+ " NOT_APPLICABLE without declaring that its aggregate may be excluded, so the exclusion was not "
				+ "honoured and the stage counts as a failure rather than as a subject the criteria did not apply to.";
	}

}
