/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.result;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Pure policy application and replacement over immutable producer facts. */
public final class Policies {

	private Policies() {
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
	public static Judgment apply(Judgment judgment, PolicyRef reference, AcceptancePolicy policy) {
		Objects.requireNonNull(judgment, "judgment");
		Objects.requireNonNull(reference, "reference");
		Objects.requireNonNull(policy, "policy");
		if (judgment.producerStatus() == JudgmentStatus.ERROR
				|| judgment.producerStatus() == JudgmentStatus.NOT_APPLICABLE) {
			return judgment;
		}
		Judgment raw = withApplication(judgment, null);
		PolicyApplication application;
		try {
			Acceptance acceptance = Objects.requireNonNull(policy.evaluate(raw), "policy returned no acceptance");
			application = new AppliedPolicy(reference, acceptance.action(), acceptance.reason());
		}
		catch (Exception failure) {
			application = new PolicyFailure(reference, JudgmentReasonCode.POLICY_FAILED,
					"Policy '" + reference.id() + "' failed: " + failure.getClass().getName()
							+ (failure.getMessage() == null ? "" : ": " + failure.getMessage()));
		}
		return withApplication(raw, application);
	}

	private static Judgment withApplication(Judgment raw, @Nullable PolicyApplication application) {
		return new Judgment(raw.producerStatus(), raw.assessment(), raw.certainty(), raw.distribution(),
				raw.reasonCode(), raw.reasoning(), raw.checks(), raw.provenance(), application, raw.metadata());
	}

}
