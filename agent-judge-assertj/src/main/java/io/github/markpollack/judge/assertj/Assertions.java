/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.assertj;

import java.util.Objects;
import java.util.function.Function;
import org.assertj.core.api.AbstractAssert;
import org.jspecify.annotations.Nullable;
import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.assertions.AssertionResult;
import io.github.markpollack.judge.assertions.RequirementAssertions;
import io.github.markpollack.judge.assertions.SemanticAssertionError;
import io.github.markpollack.judge.assertions.SemanticAssertions;
import io.github.markpollack.judge.jury.Jury;
import io.github.markpollack.judge.requirement.Requirement;
import io.github.markpollack.judge.requirement.RequirementEvidence;
import io.github.markpollack.judge.result.PolicyBinding;

/**
 * Optional AssertJ entry for the requirement, judge, evidence, application-policy,
 * satisfaction progression. Static use selects the immutable USE_ASSESSMENT assertion
 * default; {@link #using(RequirementAssertions)} selects an application's fixture instead.
 * The final application policy acts on the completed evaluation, preserving configured
 * Judge/Jury internal policies and the complete Verdict and authoritative Interpretation.
 * No evaluation occurs until {@link SatisfactionStage#isSatisfied()}. Repeating that
 * terminal evaluates again; forgetting it performs no assertion. Retained results use
 * {@link SemanticAssertions#requireSatisfied(AssertionResult)} directly.
 */
public final class Assertions {
    private Assertions() {}

    /**
     * Start with the original Requirement as the AssertJ actual. The immutable static
     * default is versioned, content-addressed USE_ASSESSMENT with no confidence threshold.
     * An associated requirement policy or explicit terminal-stage override takes priority.
     * @param <S> native specification
     * @param requirement original requirement
     * @return judge-selection stage
     */
    public static <S> RequirementStage<S> assertThat(Requirement<S> requirement) {
        return using(RequirementAssertions.usingAssessment()).assertThat(requirement);
    }

    /**
     * Use immutable application assertion configuration.
     * @param configuration fixture default and resolution rules
     * @return reusable configured entry
     */
    public static ConfiguredAssertions using(RequirementAssertions configuration) {
        return new ConfiguredAssertions(Objects.requireNonNull(configuration));
    }

    /** A reusable application-owned entry; no mutable global configuration. */
    public static final class ConfiguredAssertions {
        private final RequirementAssertions configuration;
        private ConfiguredAssertions(RequirementAssertions configuration) { this.configuration = configuration; }
        /**
         * Start a requirement assertion in this fixture.
         * @param <S> native specification
         * @param requirement original requirement
         * @return judge-selection stage
         */
        public <S> RequirementStage<S> assertThat(Requirement<S> requirement) {
            return new RequirementAssert<>(Objects.requireNonNull(requirement), configuration);
        }
    }

    /**
     * Requirement stage; evidence type is selected by the evaluator.
     * @param <S> native specification
     */
    public interface RequirementStage<S> {
        /**
         * Select a Judge without invoking it. Evaluation preserves its configured internal
         * policy through a normal one-seat Jury; final application policy runs separately
         * after that evaluation.
         * @param <E> evidence type
         * @param judge evaluator over the exact Requirement and Evidence pair
         * @return evidence stage
         */
        <E> EvidenceStage<E> judgedBy(Judge<RequirementEvidence<Requirement<S>, E>> judge);

        /**
         * Select a Jury without invoking or reconfiguring it. Evaluation retains the
         * original complete Verdict, individual Judgments and internal policy applications,
         * seats, attempts and authoritative Interpretation. Final application policy never
         * replaces configured voting, reduction or cascade routing.
         * @param <E> evidence type
         * @param jury configured Jury over the exact Requirement and Evidence pair
         * @return evidence stage
         */
        <E> EvidenceStage<E> judgedBy(Jury<RequirementEvidence<Requirement<S>, E>> jury);

        /**
         * Set the ordinary AssertJ description for a later assertion failure.
         * @param description assertion description
         * @param arguments format values
         * @return this stage
         */
        RequirementStage<S> as(String description, Object... arguments);
    }

    /**
     * Evidence stage with no premature terminal.
     * @param <E> evidence type
     */
    public interface EvidenceStage<E> {
        /**
         * Supply the original evidence without invoking the evaluator.
         * @param evidence exact stable snapshot
         * @return satisfaction stage
         */
        SatisfactionStage withEvidence(E evidence);
    }

    /** Ready for an optional application override and the eager assertion. */
    public interface SatisfactionStage {
        /**
         * Select an explicit final application policy, overriding the requirement association
         * and configured default. It runs after the unchanged Judge/Jury evaluation and its
         * result is recorded separately with EXPLICIT provenance. It never replaces internal
         * seat or tier policies. Final ABSTAIN or ESCALATE withholds satisfaction without
         * changing the Verdict or Interpretation; ESCALATE invokes no additional tier.
         * @param policy explicit final application override
         * @return new terminal stage
         */
        SatisfactionStage withAcceptancePolicy(PolicyBinding policy);

        /**
         * Evaluate once and require a SUPPORTED, ACCEPTED retained Interpretation plus a
         * successful final USE_ASSESSMENT application. Final withholding is inconclusive;
         * final policy failure is an application instrument failure. Evaluation failure,
         * non-applicability and unsupported readings cannot be repaired by final policy.
         * Each invocation evaluates again. Semantic failure is retained as the AssertJ
         * error's cause, including the complete evaluation and final application decision.
         * @throws AssertionError if satisfaction is not established
         */
        void isSatisfied();
    }

    private static final class RequirementAssert<S> extends AbstractAssert<RequirementAssert<S>, Requirement<S>>
            implements RequirementStage<S> {
        private final RequirementAssertions configuration;
        RequirementAssert(Requirement<S> actual, RequirementAssertions configuration) {
            super(actual, RequirementAssert.class);
            this.configuration = configuration;
        }
        @Override
        public RequirementAssert<S> as(String description, Object... arguments) {
            super.as(description, arguments);
            return this;
        }
        @Override
        public <E> EvidenceStage<E> judgedBy(Judge<RequirementEvidence<Requirement<S>, E>> judge) {
            Objects.requireNonNull(judge, "judge");
            return evidence -> terminal(policy -> configuration.evaluate(actual, judge, evidence, policy));
        }
        @Override
        public <E> EvidenceStage<E> judgedBy(Jury<RequirementEvidence<Requirement<S>, E>> jury) {
            Objects.requireNonNull(jury, "jury");
            return evidence -> terminal(policy -> configuration.evaluate(actual, jury, evidence, policy));
        }
        private SatisfactionStage terminal(Function<@Nullable PolicyBinding, AssertionResult> evaluation) {
            return new Terminal(evaluation, null);
        }
        private final class Terminal implements SatisfactionStage {
            private final Function<@Nullable PolicyBinding, AssertionResult> evaluation;
            private final @Nullable PolicyBinding override;
            Terminal(Function<@Nullable PolicyBinding, AssertionResult> evaluation, @Nullable PolicyBinding override) {
                this.evaluation = evaluation;
                this.override = override;
            }
            @Override
            public SatisfactionStage withAcceptancePolicy(PolicyBinding policy) {
                return new Terminal(evaluation, Objects.requireNonNull(policy));
            }
            @Override
            public void isSatisfied() {
                try {
                    SemanticAssertions.requireSatisfied(evaluation.apply(override));
                } catch (SemanticAssertionError semantic) {
                    AssertionError described = failure("%s", semantic.getMessage());
                    described.initCause(semantic);
                    throw described;
                }
            }
        }
    }
}
