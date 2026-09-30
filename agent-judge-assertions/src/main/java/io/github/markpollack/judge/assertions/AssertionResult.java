/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.assertions;

import java.util.Objects;
import io.github.markpollack.judge.jury.Verdict;
import io.github.markpollack.judge.jury.interpretation.Interpretation;
import io.github.markpollack.judge.jury.interpretation.Verdicts;
import io.github.markpollack.judge.requirement.Requirement;
import io.github.markpollack.judge.acceptance.AcceptancePolicy;
import io.github.markpollack.judge.provenance.PolicyRef;
import org.jspecify.annotations.Nullable;

/**
 * Complete original evaluation and the separate final acceptance execution.
 * Constructors and {@link RequirementAssertions#requireSatisfied(AssertionResult)} invoke
 * no Judge or policy. Reconstructing a result requires the recorded final execution; a
 * policy reference or an internal seat policy is not evidence of final application.
 *
 * <p>
 * The caller owns the correct Requirement/evidence pairing; these coherence checks do not
 * authenticate it. Persist requirement identity, final application and the existing
 * portable Verdict/Interpretation separately, never the executable policy function. The
 * embedded Judgment/Verdict values use schema 3.
 *
 * @param requirement exact evaluated requirement
 * @param acceptanceExecution resolved final application, separate from internal policies
 * @param verdict complete unchanged Jury result
 * @param interpretation authoritative reading of that original result
 */
public record AssertionResult(Requirement<?> requirement, AcceptanceExecution acceptanceExecution, Verdict verdict,
		Interpretation interpretation) {

	/**
	 * Validate resolution, final application/bypass, and authoritative reading coherence.
	 * @throws IllegalArgumentException if supplied facts contradict each other
	 * @throws NullPointerException if any component is null
	 */
	public AssertionResult {
		Objects.requireNonNull(requirement, "requirement");
		Objects.requireNonNull(acceptanceExecution, "acceptanceExecution");
		Objects.requireNonNull(verdict, "verdict");
		Objects.requireNonNull(interpretation, "interpretation");
		if (!Verdicts.interpret(verdict).equals(interpretation)) {
			throw new IllegalArgumentException("Reading must be authoritative for this verdict");
		}
		if (acceptanceExecution.bypass() != AcceptanceExecution.requiredBypass(verdict, interpretation)) {
			throw new IllegalArgumentException("Final policy application/bypass must match the retained evaluation");
		}
	}

	/** Final application policy resolution source. */
	public enum PolicySource {

		/** Explicit application override at the assertion. */
		EXPLICIT,
		/** Configured assertion/application default. */
		DEFAULT

	}

	/**
	 * Reopen a complete result without any policy or Judge execution.
	 * @param requirement exact evaluated requirement
	 * @param acceptanceExecution recorded final application or bypass
	 * @param verdict unchanged complete evaluation
	 */
	public AssertionResult(Requirement<?> requirement, AcceptanceExecution acceptanceExecution, Verdict verdict) {
		this(requirement, acceptanceExecution, verdict, Verdicts.interpret(verdict));
	}

	/**
	 * Apply final application policy to a completed evaluation, without invoking its
	 * Jury. The policy receives the raw aggregate view under its existing contract. Only
	 * its application is retained separately; it cannot change the original Verdict or
	 * promote its Interpretation. Unsupported, failed and N/A evaluations bypass
	 * execution. Policy exceptions/null decisions become a retained policy failure.
	 * @param requirement exact evaluated requirement
	 * @param binding resolved application policy
	 * @param source resolution source
	 * @param verdict complete original evaluation
	 * @return original evaluation plus its separate final policy provenance
	 * @throws NullPointerException if a required argument is null
	 */
	public static AssertionResult applyPolicy(Requirement<?> requirement, AcceptancePolicy binding, PolicySource source,
			Verdict verdict) {
		Objects.requireNonNull(requirement, "requirement");
		Objects.requireNonNull(binding, "binding");
		Objects.requireNonNull(source, "source");
		Objects.requireNonNull(verdict, "verdict");
		var interpretation = Verdicts.interpret(verdict);
		return new AssertionResult(requirement, AcceptanceExecution.evaluate(verdict, interpretation, binding, source),
				verdict, interpretation);
	}

	/**
	 * Return the resolved final application policy identity.
	 * @return final application policy identity, not a seat's internal policy
	 */
	public @Nullable PolicyRef policy() {
		return acceptanceExecution.policy();
	}

	/**
	 * Return how the final application policy was resolved.
	 * @return final application resolution source
	 */
	public PolicySource policySource() {
		return acceptanceExecution.source();
	}

}
