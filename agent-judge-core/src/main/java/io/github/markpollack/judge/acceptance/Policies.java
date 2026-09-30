/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.acceptance;

import io.github.markpollack.judge.judgment.JudgmentReasonCode;
import io.github.markpollack.judge.provenance.PolicyRef;
import io.github.markpollack.judge.judgment.Judgment;
import io.github.markpollack.judge.judgment.JudgmentStatus;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Pure policy application and replacement over immutable producer facts. */
public final class Policies {

	private Policies() {
	}

	/**
	 * Apply a reliance rule without recording an identity.
	 * @param judgment completed producer result
	 * @param policy application-owned rule
	 * @return retained facts and policy execution
	 */
	public static Judgment apply(Judgment judgment, AcceptancePolicy policy) {
		return apply(judgment, referenceOf(policy), policy);
	}

	/**
	 * Attach optional recording identity to a rule without changing its behavior.
	 * Ordinary policies remain lambdas; identity is needed only by applications that
	 * record it.
	 * @param reference caller-supplied identity of the retained configuration
	 * @param policy rule to execute
	 * @return the same behavior with optional recording information
	 */
	public static AcceptancePolicy recorded(PolicyRef reference, AcceptancePolicy policy) {
		return new RecordedPolicy(Objects.requireNonNull(reference), Objects.requireNonNull(policy));
	}

	/**
	 * Read explicitly attached recording identity, without executing the rule.
	 * @param policy application rule
	 * @return identity or null when the application did not provide one
	 */
	public static @Nullable PolicyRef referenceOf(AcceptancePolicy policy) {
		Objects.requireNonNull(policy, "policy");
		return policy instanceof RecordedPolicy recorded ? recorded.reference() : null;
	}

	private record RecordedPolicy(PolicyRef reference, AcceptancePolicy delegate) implements AcceptancePolicy {
		@Override
		public AcceptanceDecision decide(Judgment judgment) {
			return delegate.decide(judgment);
		}
	}

	/**
	 * Apply a policy to the raw producer view, replacing only an earlier application.
	 * ERROR and NOT_APPLICABLE bypass evaluation. Policy exceptions and null decisions
	 * become PolicyFailure while every producer fact remains unchanged.
	 * @param judgment retained producer result
	 * @param reference explicit policy identity and configuration digest
	 * @param policy application-owned function
	 * @return the same producer facts with the new application, or bypassed result
	 * @throws NullPointerException if an argument is null
	 */
	public static Judgment apply(Judgment judgment, @Nullable PolicyRef reference, AcceptancePolicy policy) {
		Objects.requireNonNull(judgment, "judgment");
		Objects.requireNonNull(policy, "policy");
		if (judgment.producerStatus() == JudgmentStatus.ERROR
				|| judgment.producerStatus() == JudgmentStatus.NOT_APPLICABLE) {
			return judgment;
		}
		Judgment raw = withApplication(judgment, null);
		PolicyApplication application;
		try {
			AcceptanceDecision acceptance = Objects.requireNonNull(policy.decide(raw), "policy returned no acceptance");
			application = new AppliedPolicy(reference, acceptance.action(), acceptance.reason());
		}
		catch (Exception failure) {
			application = new PolicyFailure(reference, JudgmentReasonCode.POLICY_FAILED, "Acceptance policy failed: "
					+ failure.getClass().getName() + (failure.getMessage() == null ? "" : ": " + failure.getMessage()));
		}
		return withApplication(raw, application);
	}

	private static Judgment withApplication(Judgment raw, @Nullable PolicyApplication application) {
		return new Judgment(raw.producerStatus(), raw.finding(), raw.confidence(), raw.probabilityDistribution(),
				raw.reasonCode(), raw.reasoning(), raw.checks(), raw.provenance(), application, raw.metadata());
	}

}
