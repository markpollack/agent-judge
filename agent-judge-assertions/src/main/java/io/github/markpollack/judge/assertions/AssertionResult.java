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
import io.github.markpollack.judge.result.PolicyBinding;
import io.github.markpollack.judge.result.PolicyRef;

/**
 * Complete original evaluation and the separate final application decision. Constructors
 * and {@link SemanticAssertions#requireSatisfied(AssertionResult)} invoke no Judge or
 * policy. Reconstructing a result requires the recorded final decision; a policy reference
 * or an internal seat policy is not evidence of final application.
 *
 * <p>The caller owns the correct Requirement/evidence pairing; these coherence checks do
 * not authenticate it. Persist requirement identity, final application and the existing
 * portable Verdict/Interpretation separately, never the executable policy function. The
 * original Judgment/Verdict wire contract is unchanged.
 * @param requirement exact evaluated requirement
 * @param applicationDecision resolved final application, separate from internal policies
 * @param verdict complete unchanged Jury result
 * @param interpretation authoritative reading of that original result
 */
public record AssertionResult(Requirement<?> requirement, ApplicationDecision applicationDecision, Verdict verdict,
        Interpretation interpretation) {

    /**
     * Validate resolution, final application/bypass, and authoritative reading coherence.
     * @throws IllegalArgumentException if supplied facts contradict each other
     * @throws NullPointerException if any component is null
     */
    public AssertionResult {
        Objects.requireNonNull(requirement, "requirement");
        Objects.requireNonNull(applicationDecision, "applicationDecision");
        Objects.requireNonNull(verdict, "verdict");
        Objects.requireNonNull(interpretation, "interpretation");
        validateResolution(requirement, applicationDecision.policy(), applicationDecision.source());
        if (!Verdicts.interpret(verdict).equals(interpretation)) {
            throw new IllegalArgumentException("Reading must be authoritative for this verdict");
        }
        if (applicationDecision.bypass() != ApplicationDecision.requiredBypass(verdict, interpretation)) {
            throw new IllegalArgumentException("Final policy application/bypass must match the retained evaluation");
        }
    }

    /** Final application policy resolution source. */
    public enum PolicySource {
        /** Explicit application association using Requirement.under. */
        ASSOCIATED,
        /** Explicit application override at the assertion. */
        EXPLICIT,
        /** Configured assertion/application default. */
        DEFAULT
    }

    /**
     * Reopen a complete result without any policy or Judge execution.
     * @param requirement exact evaluated requirement
     * @param applicationDecision recorded final application or bypass
     * @param verdict unchanged complete evaluation
     */
    public AssertionResult(Requirement<?> requirement, ApplicationDecision applicationDecision, Verdict verdict) {
        this(requirement, applicationDecision, verdict, Verdicts.interpret(verdict));
    }

    /**
     * Apply final application policy to a completed evaluation, without invoking its Jury.
     * The policy receives the raw aggregate view under its existing contract. Only its
     * application is retained separately; it cannot change the original Verdict or promote
     * its Interpretation. Unsupported, failed and N/A evaluations bypass execution.
     * Policy exceptions/null decisions become a retained policy failure.
     * @param requirement exact evaluated requirement
     * @param binding resolved application policy
     * @param source resolution source
     * @param verdict complete original evaluation
     * @return original evaluation plus its separate final policy decision
     * @throws IllegalArgumentException if resolution contradicts the requirement association
     * @throws NullPointerException if a required argument is null
     */
    public static AssertionResult applyPolicy(Requirement<?> requirement, PolicyBinding binding, PolicySource source,
            Verdict verdict) {
        Objects.requireNonNull(requirement, "requirement");
        Objects.requireNonNull(binding, "binding");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(verdict, "verdict");
        validateResolution(requirement, binding.reference(), source);
        var interpretation = Verdicts.interpret(verdict);
        return new AssertionResult(requirement, ApplicationDecision.evaluate(verdict, interpretation, binding, source),
            verdict, interpretation);
    }

    /**
     * Return the resolved final application policy identity.
     * @return final application policy identity, not a seat's internal policy
     */
    public PolicyRef policy() { return applicationDecision.policy(); }

    /**
     * Return how the final application policy was resolved.
     * @return final application resolution source
     */
    public PolicySource policySource() { return applicationDecision.source(); }

    private static void validateResolution(Requirement<?> requirement, PolicyRef policy, PolicySource source) {
        var associated = requirement.acceptancePolicy();
        if (source == PolicySource.ASSOCIATED && (associated == null || !policy.equals(associated.reference()))) {
            throw new IllegalArgumentException("ASSOCIATED requires an override matching the resolved policy");
        }
        if (source == PolicySource.DEFAULT && associated != null) {
            throw new IllegalArgumentException("DEFAULT cannot accompany a requirement policy override");
        }
    }
}
