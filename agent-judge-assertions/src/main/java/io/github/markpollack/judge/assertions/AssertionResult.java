/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.assertions;

import io.github.markpollack.judge.requirement.Requirement;
import io.github.markpollack.judge.result.PolicyBinding;

import java.util.Objects;
import io.github.markpollack.judge.result.PolicyRef;
import io.github.markpollack.judge.jury.Verdict;
import io.github.markpollack.judge.jury.interpretation.Interpretation;
import io.github.markpollack.judge.jury.interpretation.Verdicts;

/**
 * An evaluation ready for inspection or assertion without invoking its Judge again. It
 * keeps the requirement and resolved policy context beside the normal Verdict and its
 * authoritative Interpretation. Pass it to
 * {@link SemanticAssertions#requireSatisfied(AssertionResult)} to assert that reading.
 *
 * <p>
 * The constructor checks policy-source consistency and, when present, the root's retained
 * policy identity. It does not require a root policy application: a composed Jury may
 * apply different policies to its contributing judgments and derive an aggregate without
 * one. A declared policy reference alone is not proof that a policy ran. Producer ERROR
 * and NOT_APPLICABLE bypass policy; contained failures and unsupported readings remain
 * representable for diagnostic assertion failures.
 *
 * <p>
 * The caller is responsible for pairing the correct requirement and evidence with a
 * retained Verdict. This record cannot authenticate that pairing. It is not a new wire
 * contract: persist requirement identity/text, policy reference/source, and the existing
 * portable Verdict/Interpretation separately, never the policy function.
 *
 * @param requirement resolved requirement
 * @param policy resolved policy identity
 * @param policySource where the policy came from
 * @param verdict complete normal jury result
 * @param interpretation authoritative reading, checked against the verdict
 */
public record AssertionResult(Requirement<?> requirement, PolicyRef policy, PolicySource policySource, Verdict verdict,
		Interpretation interpretation) {

	/**
	 * Validate the supplied context against the facts this result can establish.
	 * @throws IllegalArgumentException if the policy source/override or retained root
	 * policy contradicts the resolved policy, or the Interpretation differs from the
	 * authoritative reading of the Verdict
	 * @throws NullPointerException if a required component is null
	 */
	public AssertionResult {
		Objects.requireNonNull(requirement, "requirement");
		Objects.requireNonNull(policy, "policy");
		Objects.requireNonNull(policySource, "policySource");
		Objects.requireNonNull(verdict, "verdict");
		Objects.requireNonNull(interpretation, "interpretation");
		PolicyBinding override = requirement.acceptancePolicy();
		if (policySource == PolicySource.ASSOCIATED && (override == null || !policy.equals(override.reference()))) {
			throw new IllegalArgumentException("ASSOCIATED requires an override matching the resolved policy");
		}
		if (policySource == PolicySource.DEFAULT && override != null) {
			throw new IllegalArgumentException("DEFAULT cannot accompany a requirement policy override");
		}
		var application = verdict.aggregated().policyApplication();
		if (application != null && !policy.equals(application.policy())) {
			throw new IllegalArgumentException("Retained root policy must match the resolved policy");
		}
		if (!Verdicts.interpret(verdict).equals(interpretation))
			throw new IllegalArgumentException("Reading must be authoritative for this verdict");
	}
	/** Policy resolution source. */
	public enum PolicySource {

		/** Explicit Requirement.under binding. */
		ASSOCIATED,
		/** Explicit application override at the assertion. */
		EXPLICIT,
		/** Explicit configured facade default. */
		DEFAULT

	}

	/**
	 * Retain the authoritative reading of a normal jury result.
	 * @param requirement resolved requirement
	 * @param policy resolved policy identity
	 * @param policySource resolution source
	 * @param verdict normal jury result
	 * @throws IllegalArgumentException if policy/source facts contradict one another
	 * @throws NullPointerException if a required component is null
	 */
	public AssertionResult(Requirement<?> requirement, PolicyRef policy, PolicySource policySource, Verdict verdict) {
		this(requirement, policy, policySource, verdict, Verdicts.interpret(verdict));
	}
}
