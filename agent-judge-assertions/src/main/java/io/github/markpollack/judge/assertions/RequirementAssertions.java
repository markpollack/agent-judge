/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.assertions;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.PolicyJudges;
import io.github.markpollack.judge.requirement.Requirement;
import io.github.markpollack.judge.requirement.RequirementEvidence;
import io.github.markpollack.judge.result.*;
import io.github.markpollack.judge.jury.*;

/**
 * Immutable application configuration for requirement-oriented evaluation. No evaluator
 * owns this default. An explicit override wins over a requirement's application association,
 * which wins over this fixture's default. A missing policy fails before any judge call.
 */
public final class RequirementAssertions {
    private final @Nullable PolicyBinding defaultPolicy;

    /**
     * Configure application consequence once for a fixture.
     * @param defaultPolicy default, or null to require an association or explicit override
     */
    public RequirementAssertions(@Nullable PolicyBinding defaultPolicy) {
        this.defaultPolicy = defaultPolicy;
    }

    /**
     * The transparent assertion default: use the producer assessment without a confidence
     * threshold. Selecting this fixture is an application choice, not a calibration claim.
     * @return immutable configuration with a versioned, content-addressed default
     */
    public static RequirementAssertions usingAssessment() {
        String configuration = "agent-eval.assertion-default:1:USE_ASSESSMENT:no-confidence-threshold";
        String digest = ArtifactRef.ofBytes("policy", configuration.getBytes(StandardCharsets.UTF_8), null).sha256();
        return new RequirementAssertions(new PolicyBinding(new PolicyRef("agent-eval.assertion-default", "1", digest),
            judgment -> new Acceptance(AcceptanceAction.USE_ASSESSMENT, "Use the producer assessment")));
    }

    /**
     * Evaluate once through the normal one-seat engine without asserting success.
     * @param <S> native specification
     * @param <E> evidence type
     * @param requirement exact requirement
     * @param judge requirement-aware evaluator
     * @param evidence exact evidence
     * @param override explicit application override, or null
     * @return complete authoritative result
     */
    public <S, E> AssertionResult evaluate(Requirement<S> requirement,
            Judge<? super RequirementEvidence<Requirement<S>, E>> judge, E evidence,
            @Nullable PolicyBinding override) {
        var resolution = resolve(requirement, override);
        var pair = new RequirementEvidence<>(requirement, evidence);
        var jury = SimpleJury.<RequirementEvidence<Requirement<S>, E>>builder()
            .judge(PolicyJudges.apply(Objects.requireNonNull(judge), resolution.binding().reference(), resolution.binding().policy()))
            .votingStrategy(new AllMustPassStrategy(ErrorPolicy.PROPAGATE, NotApplicablePolicy.EXCLUDE))
            .parallel(false).build();
        return new AssertionResult(requirement, resolution.binding().reference(), resolution.source(), jury.vote(pair));
    }

    /**
     * Evaluate a built-in Jury once, applying application policy at its leaf boundaries.
     * Retains all individual judgments, identities, attempts, decisions and the full Verdict.
     * @param <S> native specification
     * @param <E> evidence type
     * @param requirement exact requirement
     * @param jury complete built-in jury
     * @param evidence exact evidence
     * @param override explicit application override, or null
     * @return complete authoritative result
     */
    public <S, E> AssertionResult evaluate(Requirement<S> requirement,
            Jury<RequirementEvidence<Requirement<S>, E>> jury, E evidence, @Nullable PolicyBinding override) {
        var resolution = resolve(requirement, override);
        var configured = Juries.withAcceptancePolicy(jury, resolution.binding());
        return new AssertionResult(requirement, resolution.binding().reference(), resolution.source(),
            configured.vote(new RequirementEvidence<>(requirement, evidence)));
    }

    private Resolution resolve(Requirement<?> requirement, @Nullable PolicyBinding override) {
        Objects.requireNonNull(requirement, "requirement");
        if (override != null) return new Resolution(override, AssertionResult.PolicySource.EXPLICIT);
        var associated = requirement.acceptancePolicy();
        if (associated != null) return new Resolution(associated, AssertionResult.PolicySource.ASSOCIATED);
        if (defaultPolicy != null) return new Resolution(defaultPolicy, AssertionResult.PolicySource.DEFAULT);
        throw new IllegalStateException("Acceptance policy must resolve before evaluation");
    }

    private record Resolution(PolicyBinding binding, AssertionResult.PolicySource source) {}
}
