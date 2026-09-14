/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.jspecify.annotations.Nullable;

/**
 * One named composite stage that was entered during jury execution.
 *
 * <p>
 * Every attempt says whether its parent could use what the stage produced. That marker is
 * always written, because the alternative is that a composition failure becomes invisible the
 * moment a later stage succeeds: the item passes, the evidence of the failure is a verdict
 * buried three levels down that looks exactly like a verdict nobody minded, and nobody ever
 * counts it. A {@link AttemptDisposition#STAGE_FAILED} attempt keeps the child's actual verdict
 * — the claim it made is not rewritten — and names the reason in a fixed vocabulary a reader
 * can count.
 * </p>
 *
 * @param name stable configured sibling identity
 * @param relation relationship to the parent verdict
 * @param policy cascade policy, required only for {@link CompositeRelation#CASCADE_TIER}
 * @param disposition whether the parent could use this stage; always present
 * @param dispositionReason why not, required if and only if the disposition is
 * {@link AttemptDisposition#STAGE_FAILED}
 * @param verdict complete returned verdict, mutually exclusive with {@code failure}
 * @param failure portable failure evidence, mutually exclusive with {@code verdict}
 * @since 0.14.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "name", "relation", "policy", "disposition", "dispositionReason", "verdict", "failure" })
public record CompositeAttempt(String name, CompositeRelation relation, @Nullable TierPolicy policy,
		AttemptDisposition disposition, @Nullable DispositionReason dispositionReason, @Nullable Verdict verdict,
		@Nullable CompositeFailure failure) {

	/** Validate identity, relation/policy legality, the exactly-one outcome rule, and the disposition. */
	public CompositeAttempt {
		name = NamedJury.requireValidName(name);
		Objects.requireNonNull(relation, "relation must not be null");
		Objects.requireNonNull(disposition, "disposition must not be null");
		if ((verdict == null) == (failure == null)) {
			throw new IllegalArgumentException("exactly one of verdict and failure must be present");
		}
		if (relation == CompositeRelation.CASCADE_TIER && policy == null) {
			throw new IllegalArgumentException("CASCADE_TIER requires a policy");
		}
		if (relation == CompositeRelation.META_MEMBER && policy != null) {
			throw new IllegalArgumentException("META_MEMBER forbids a policy");
		}
		if ((disposition == AttemptDisposition.STAGE_FAILED) != (dispositionReason != null)) {
			throw new IllegalArgumentException(
					"dispositionReason is required exactly when the disposition is STAGE_FAILED, but was "
							+ disposition + " with " + (dispositionReason == null ? "no reason" : dispositionReason));
		}
		// The reason must agree with what the attempt actually holds, or the marker describes a
		// stage other than the one recorded.
		if (disposition == AttemptDisposition.USED && verdict == null) {
			throw new IllegalArgumentException("a USED attempt consumed a verdict, so it must carry one");
		}
		if (dispositionReason == DispositionReason.EXECUTION_FAILED && failure == null) {
			throw new IllegalArgumentException("EXECUTION_FAILED means the stage threw, so it carries a failure code "
					+ "rather than a verdict");
		}
		if ((dispositionReason == DispositionReason.CHILD_UNDECIDED
				|| dispositionReason == DispositionReason.UNDECLARED_NOT_APPLICABLE) && verdict == null) {
			throw new IllegalArgumentException(dispositionReason
					+ " describes a verdict the stage returned, so the attempt must keep it");
		}
	}

	/**
	 * The parent consumed this stage's verdict normally.
	 * @param name the stage's configured name
	 * @param relation how the stage relates to its parent
	 * @param policy the cascade policy, or null for a meta-jury member
	 * @param verdict the returned verdict
	 * @return a used attempt
	 * @since 0.17.0
	 */
	public static CompositeAttempt used(String name, CompositeRelation relation, @Nullable TierPolicy policy,
			Verdict verdict) {
		return new CompositeAttempt(name, relation, policy, AttemptDisposition.USED, null, verdict, null);
	}

	/**
	 * The stage returned a verdict the parent could not use.
	 * @param name the stage's configured name
	 * @param relation how the stage relates to its parent
	 * @param policy the cascade policy, or null for a meta-jury member
	 * @param reason why the verdict could not be used
	 * @param verdict the verdict the stage actually returned, kept unchanged
	 * @return a stage-failed attempt
	 * @since 0.17.0
	 */
	public static CompositeAttempt stageFailed(String name, CompositeRelation relation, @Nullable TierPolicy policy,
			DispositionReason reason, Verdict verdict) {
		return new CompositeAttempt(name, relation, policy, AttemptDisposition.STAGE_FAILED, reason, verdict, null);
	}

	/**
	 * The stage threw, so it produced no verdict at all.
	 * @param name the stage's configured name
	 * @param relation how the stage relates to its parent
	 * @param policy the cascade policy, or null for a meta-jury member
	 * @param failure the portable failure evidence
	 * @return a stage-failed attempt carrying a failure code
	 * @since 0.17.0
	 */
	public static CompositeAttempt executionFailed(String name, CompositeRelation relation, @Nullable TierPolicy policy,
			CompositeFailure failure) {
		return new CompositeAttempt(name, relation, policy, AttemptDisposition.STAGE_FAILED,
				DispositionReason.EXECUTION_FAILED, null, failure);
	}

}
