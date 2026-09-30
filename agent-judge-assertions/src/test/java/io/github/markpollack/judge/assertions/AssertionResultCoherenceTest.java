/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.assertions;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.markpollack.judge.PolicyJudges;
import io.github.markpollack.judge.assertions.ApplicationDecision.Bypass;
import io.github.markpollack.judge.assertions.AssertionResult.PolicySource;
import io.github.markpollack.judge.context.JudgmentContext;
import io.github.markpollack.judge.jury.AllMustPassStrategy;
import io.github.markpollack.judge.jury.SimpleJury;
import io.github.markpollack.judge.jury.Verdict;
import io.github.markpollack.judge.jury.interpretation.ReadingSupport;
import io.github.markpollack.judge.jury.interpretation.Verdicts;
import io.github.markpollack.judge.requirement.Requirement;
import io.github.markpollack.judge.result.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AssertionResultCoherenceTest {
    private static final Requirement<?> REQUIREMENT = Requirement.text("example", "1", "exact requirement");
    private static final PolicyRef A = new PolicyRef("policy", "1", "a".repeat(64));
    private static final PolicyRef B = new PolicyRef("policy", "1", "b".repeat(64));
    private static final PolicyBinding BINDING = binding(A);

    static PolicyBinding binding(PolicyRef ref) {
        return new PolicyBinding(ref, j -> new Acceptance(AcceptanceAction.USE_ASSESSMENT, "use retained evaluation"));
    }
    static ApplicationDecision applied(PolicyRef ref, PolicySource source) {
        return new ApplicationDecision(ref, source, new AppliedPolicy(ref, AcceptanceAction.USE_ASSESSMENT, "use"), null);
    }
    static Verdict accepted() {
        return Verdict.single("candidate", Policies.apply(Judgment.pass("satisfied"), A, BINDING.policy()));
    }
    static AssertionResult result(Requirement<?> requirement, PolicyRef ref, PolicySource source, Verdict verdict) {
        return AssertionResult.applyPolicy(requirement, binding(ref), source, verdict);
    }

    @Test
    void associatedSourceNeedsTheExactResolvedIdentity() {
        assertThatThrownBy(() -> result(REQUIREMENT, A, PolicySource.ASSOCIATED, accepted()))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("override");
        assertThatThrownBy(() -> result(REQUIREMENT.under(BINDING), B, PolicySource.ASSOCIATED, accepted()))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("override");
        assertThatCode(() -> result(REQUIREMENT.under(BINDING), A, PolicySource.ASSOCIATED, accepted()))
            .doesNotThrowAnyException();
        assertThatThrownBy(() -> new AssertionResult(REQUIREMENT, applied(A, PolicySource.ASSOCIATED), accepted()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void defaultCannotHideAssociationButExplicitCanOverrideIt() {
        assertThatThrownBy(() -> result(REQUIREMENT.under(BINDING), A, PolicySource.DEFAULT, accepted()))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("DEFAULT");
        assertThat(result(REQUIREMENT.under(BINDING), B, PolicySource.EXPLICIT, accepted()).policy()).isEqualTo(B);
    }

    @Test
    void resolutionIsValidatedBeforeFinalPolicyExecution() {
        var calls = new AtomicInteger();
        var policy = new PolicyBinding(B, j -> { calls.incrementAndGet(); return new Acceptance(AcceptanceAction.USE_ASSESSMENT, "use"); });
        assertThatThrownBy(() -> AssertionResult.applyPolicy(REQUIREMENT.under(BINDING), policy, PolicySource.ASSOCIATED, accepted()))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(calls).hasValue(0);
    }

    @Test
    void finalDecisionRequiresMatchingIdentityIncludingFailureAndDigest() {
        assertThatThrownBy(() -> new ApplicationDecision(B, PolicySource.DEFAULT,
            new AppliedPolicy(A, AcceptanceAction.USE_ASSESSMENT, "use"), null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ApplicationDecision(B, PolicySource.DEFAULT,
            new PolicyFailure(A, JudgmentReasonCode.POLICY_FAILED, "failed"), null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ApplicationDecision(A, PolicySource.DEFAULT, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ApplicationDecision(A, PolicySource.DEFAULT,
            new AppliedPolicy(A, AcceptanceAction.USE_ASSESSMENT, "use"), Bypass.NOT_ASSESSED)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void finalPolicyMayDifferFromInternalRootPolicyWithoutReplacingIt() {
        var verdict = accepted();
        var result = result(REQUIREMENT, B, PolicySource.DEFAULT, verdict);
        assertThat(result.verdict()).isSameAs(verdict);
        assertThat(result.verdict().aggregated().policyApplication().policy()).isEqualTo(A);
        assertThat(result.applicationDecision().application().policy()).isEqualTo(B);
        SemanticAssertions.requireSatisfied(result);
    }

    @Test
    void internalPolicyFailureCannotBeRepairedByFinalUse() {
        var failed = Policies.apply(Judgment.pass("producer succeeded"), A, j -> { throw new IllegalStateException("internal policy"); });
        var calls = new AtomicInteger();
        var verdict = Verdict.single("candidate", failed);
        var result = AssertionResult.applyPolicy(REQUIREMENT, new PolicyBinding(B, j -> {
            calls.incrementAndGet(); return new Acceptance(AcceptanceAction.USE_ASSESSMENT, "use");
        }), PolicySource.DEFAULT, verdict);
        assertThat(result.applicationDecision().bypass()).isEqualTo(Bypass.NOT_ASSESSED);
        assertThat(result.verdict().aggregated().policyApplication()).isInstanceOf(PolicyFailure.class);
        assertThat(calls).hasValue(0);
        assertThatThrownBy(() -> SemanticAssertions.requireSatisfied(result)).isInstanceOf(SemanticAssertionError.InstrumentFailure.class);
    }

    @Test
    void composedVerdictKeepsDifferentSeatPoliciesAndNoRootApplication() {
        var jury = SimpleJury.<JudgmentContext>builder()
            .judge(PolicyJudges.apply(c -> Judgment.pass("first"), A, BINDING.policy()))
            .judge(PolicyJudges.apply(c -> Judgment.pass("second"), B, BINDING.policy()))
            .votingStrategy(new AllMustPassStrategy()).parallel(false).build();
        var verdict = jury.vote(JudgmentContext.builder().goal(REQUIREMENT.text()).build());
        var result = result(REQUIREMENT, B, PolicySource.DEFAULT, verdict);
        assertThat(result.verdict()).isSameAs(verdict);
        assertThat(verdict.aggregated().policyApplication()).isNull();
        assertThat(verdict.individual()).extracting(j -> j.policyApplication().policy()).containsExactly(A, B);
        assertThat(result.interpretation().readingSupport()).isEqualTo(ReadingSupport.SUPPORTED);
        SemanticAssertions.requireSatisfied(result);
    }

    @Test
    void aRetainedApplicationCannotFabricateOrOmitRequiredBypass() {
        var error = Verdict.single("candidate", Judgment.error("failed"));
        var na = Verdict.single("candidate", Judgment.notApplicable("outside domain"));
        for (var verdict : List.of(error, na)) {
            assertThatThrownBy(() -> new AssertionResult(REQUIREMENT, applied(A, PolicySource.DEFAULT), verdict))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("bypass");
        }
        var valid = result(REQUIREMENT, A, PolicySource.DEFAULT, error);
        assertThatThrownBy(() -> new AssertionResult(REQUIREMENT, valid.applicationDecision(), accepted()))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("bypass");
        assertThatThrownBy(() -> new AssertionResult(REQUIREMENT,
            new ApplicationDecision(A, PolicySource.DEFAULT, null, Bypass.NOT_APPLICABLE), error))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("bypass");
    }

    @Test
    void unsupportedReadingsBypassAndCannotUseAForgedAcceptedInterpretation() {
        var accepted = accepted();
        var invalid = new Verdict(2, accepted.aggregated(), accepted.individual(), accepted.individualByName(),
            accepted.weights(), accepted.seats(), accepted.decision(), accepted.compositeAttempts(), 0);
        var result = AssertionResult.applyPolicy(REQUIREMENT, new PolicyBinding(A, j -> { throw new AssertionError("must bypass"); }),
            PolicySource.DEFAULT, invalid);
        assertThat(result.applicationDecision().bypass()).isEqualTo(Bypass.UNSUPPORTED_READING);
        assertThatThrownBy(() -> SemanticAssertions.requireSatisfied(result)).isInstanceOf(SemanticAssertionError.UnsupportedReading.class);
        assertThatThrownBy(() -> new AssertionResult(REQUIREMENT, applied(A, PolicySource.DEFAULT), invalid, Verdicts.interpret(accepted)))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("authoritative");
    }

    @Test
    void reopeningTypedValuesMakesNoPolicyCallsAndPreservesFinalWithholding() throws Exception {
        var calls = new AtomicInteger();
        var original = AssertionResult.applyPolicy(REQUIREMENT, new PolicyBinding(B, j -> {
            calls.incrementAndGet();
            assertThat(j.policyApplication()).isNull();
            return new Acceptance(AcceptanceAction.ESCALATE, "application follow-up");
        }), PolicySource.DEFAULT, accepted());
        var json = new ObjectMapper();
        var decision = json.readValue(json.writeValueAsBytes(original.applicationDecision()), ApplicationDecision.class);
        var verdict = json.readValue(json.writeValueAsBytes(original.verdict()), Verdict.class);
        var reopened = new AssertionResult(REQUIREMENT, decision, verdict);
        for (int i = 0; i < 2; i++) assertThatThrownBy(() -> SemanticAssertions.requireSatisfied(reopened))
            .isInstanceOf(SemanticAssertionError.Inconclusive.class);
        assertThat(reopened).isEqualTo(original);
        assertThat(calls).hasValue(1);
    }
}
