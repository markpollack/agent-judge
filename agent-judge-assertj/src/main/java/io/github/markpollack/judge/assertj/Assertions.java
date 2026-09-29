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
import io.github.markpollack.judge.jury.Jury;
import io.github.markpollack.judge.requirement.Requirement;
import io.github.markpollack.judge.requirement.RequirementEvidence;
import io.github.markpollack.judge.result.PolicyBinding;
import io.github.markpollack.judge.assertions.*;

/**
 * Optional AssertJ entry for the requirement, judge, evidence, application-policy,
 * satisfaction progression. Static use selects the immutable USE_ASSESSMENT assertion
 * default; {@link #using(RequirementAssertions)} selects an application's fixture instead.
 * No evaluation occurs until isSatisfied. Repeating that terminal evaluates again;
 * forgetting it performs no assertion. Retained results use SemanticAssertions directly.
 */
public final class Assertions {
    private Assertions() {}

    /**
     * Start with the original Requirement as the AssertJ actual.
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

    /** Requirement stage; evidence type is selected by the evaluator. @param <S> native specification */
    public interface RequirementStage<S> {
        /** @param <E> evidence type @param judge evaluator @return evidence stage */
        <E> EvidenceStage<E> judgedBy(Judge<RequirementEvidence<Requirement<S>, E>> judge);
        /** @param <E> evidence type @param jury full jury @return evidence stage */
        <E> EvidenceStage<E> judgedBy(Jury<RequirementEvidence<Requirement<S>, E>> jury);
        /** @param description assertion description @param arguments format values @return this stage */
        RequirementStage<S> as(String description, Object... arguments);
    }

    /** Evidence stage with no premature terminal. @param <E> evidence type */
    public interface EvidenceStage<E> {
        /** @param evidence exact snapshot @return satisfaction stage */
        SatisfactionStage withEvidence(E evidence);
    }

    /** Ready for an optional application override and the eager assertion. */
    public interface SatisfactionStage {
        /** @param policy explicit application override @return new terminal stage */
        SatisfactionStage withAcceptancePolicy(PolicyBinding policy);
        /** Evaluate once and require supported acceptance; semantic failure is retained as the error cause. */
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
