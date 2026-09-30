/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.assertj;

import static io.github.markpollack.judge.assertj.Assertions.assertThat;
import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import io.github.markpollack.judge.*;
import io.github.markpollack.judge.assertions.*;
import io.github.markpollack.judge.jury.*;
import io.github.markpollack.judge.jury.interpretation.VerdictReading;
import io.github.markpollack.judge.requirement.*;
import io.github.markpollack.judge.result.*;

class RequirementAssertionTest {
    static final Requirement<String> REQUIREMENT = Requirement.text("ready", "1", "The answer is READY");
    static final String EVIDENCE = "READY";
    static final PolicyBinding USE = policy("use", AcceptanceAction.USE_ASSESSMENT);
    static final PolicyBinding WITHHOLD = policy("withhold", AcceptanceAction.ABSTAIN);
    static final PolicyBinding ESCALATE = policy("escalate", AcceptanceAction.ESCALATE);
    static final Judge<RequirementEvidence<Requirement<String>, String>> PASS = input -> Judgment.pass("ready");

    static PolicyBinding policy(String name, AcceptanceAction action) {
        return new PolicyBinding(new PolicyRef(name, "1", "a".repeat(64)), j -> new Acceptance(action, name));
    }

    @Test
    void arbitraryEvidenceAndTypedJuryNeedNoRequirement() {
        Judge<String> length = text -> text.length() == 5 ? Judgment.pass("length") : Judgment.fail("length");
        Jury<String> jury = SimpleJury.<String>builder().judge(length).votingStrategy(new AllMustPassStrategy()).parallel(false).build();
        assertThat(jury.vote("READY").aggregated().pass()).isTrue();
    }

    @Test
    void terminalAloneInvokesAndDeliversOriginalObjects() {
        var calls = new AtomicInteger();
        var received = new AtomicReference<RequirementEvidence<Requirement<String>, String>>();
        Judge<RequirementEvidence<Requirement<String>, String>> judge = input -> {
            calls.incrementAndGet(); received.set(input); return Judgment.pass("ready");
        };
        var terminal = assertThat(REQUIREMENT).judgedBy(judge).withEvidence(EVIDENCE);
        assertThat(calls.get()).isZero();
        terminal.isSatisfied();
        assertThat(calls.get()).isEqualTo(1);
        assertThat((Object) received.get().requirement()).isSameAs(REQUIREMENT);
        assertThat(received.get().evidence()).isSameAs(EVIDENCE);
        terminal.isSatisfied();
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void normalStaticPathHasNoPolicyBoilerplate() {
        assertThat(REQUIREMENT).judgedBy(PASS).withEvidence(EVIDENCE).isSatisfied();
    }

    @Test
    void provenanceRecordsPriorityWithoutChangingRequirement() {
        var fixture = new RequirementAssertions(WITHHOLD);
        var associated = REQUIREMENT.under(USE);
        var defaultResult = fixture.evaluate(REQUIREMENT, PASS, EVIDENCE, null);
        var associatedResult = fixture.evaluate(associated, PASS, EVIDENCE, null);
        var explicitResult = fixture.evaluate(associated, PASS, EVIDENCE, ESCALATE);
        assertThat(defaultResult.policySource()).isEqualTo(AssertionResult.PolicySource.DEFAULT);
        assertThat(associatedResult.policySource()).isEqualTo(AssertionResult.PolicySource.ASSOCIATED);
        assertThat(explicitResult.policySource()).isEqualTo(AssertionResult.PolicySource.EXPLICIT);
        assertThat((Object) explicitResult.requirement()).isSameAs(associated);
        assertThat(explicitResult.policy()).isEqualTo(ESCALATE.reference());
        assertThat(explicitResult.verdict().aggregated().producerStatus()).isEqualTo(JudgmentStatus.PASS);
        assertThat(explicitResult.verdict().aggregated().status()).isEqualTo(JudgmentStatus.PASS);
        assertThat(explicitResult.interpretation().reading()).isEqualTo(VerdictReading.ACCEPTED);
        assertThat(((AppliedPolicy) explicitResult.applicationDecision().application()).action()).isEqualTo(AcceptanceAction.ESCALATE);
    }

    @Test
    void genuinelyMissingPolicyFailsBeforeJudgeInvocation() {
        var calls = new AtomicInteger();
        Judge<RequirementEvidence<Requirement<String>, String>> judge = input -> {
            calls.incrementAndGet(); return Judgment.pass("ready");
        };
        var entry = Assertions.using(new RequirementAssertions(null));
        assertThatThrownBy(() -> entry.assertThat(REQUIREMENT).judgedBy(judge).withEvidence(EVIDENCE).isSatisfied())
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("before evaluation");
        assertThat(calls.get()).isZero();
        entry.assertThat(REQUIREMENT).judgedBy(judge).withEvidence(EVIDENCE).withAcceptancePolicy(USE).isSatisfied();
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void assertJDescriptionAndCompleteSemanticFailureRemainAvailable() {
        AssertionError failure = catchThrowableOfType(() -> assertThat(REQUIREMENT).as("critical lock order")
            .judgedBy(PASS).withEvidence(EVIDENCE).withAcceptancePolicy(ESCALATE).isSatisfied(), AssertionError.class);
        assertThat(failure).hasMessageContaining("[critical lock order]").hasMessageContaining("resolved EXPLICIT");
        assertThat(failure.getCause()).isInstanceOf(SemanticAssertionError.Inconclusive.class);
        var semantic = (SemanticAssertionError) failure.getCause();
        assertThat((Object) semantic.result().requirement()).isSameAs(REQUIREMENT);
        assertThat(semantic.result().verdict().individual()).hasSize(1);
    }

    @Test
    void acceptedNegativeInsufficiencyErrorAndDeclaredNaRemainDistinct() {
        var fixture = new RequirementAssertions(USE);
        Map<Judgment, Class<? extends SemanticAssertionError>> cases = Map.of(
            Judgment.fail("violated"), SemanticAssertionError.Rejected.class,
            Judgment.abstain("evidence missing"), SemanticAssertionError.Inconclusive.class,
            Judgment.error("producer failed"), SemanticAssertionError.InstrumentFailure.class,
            Judgment.notApplicable("no persistence"), SemanticAssertionError.NotApplicable.class);
        cases.forEach((judgment, errorType) -> {
            var judge = new NamedJudge<RequirementEvidence<Requirement<String>, String>>(input -> judgment,
                new JudgeMetadata("conditional", "native applicability", JudgeType.DETERMINISTIC, "subject has persistence"));
            var result = fixture.evaluate(REQUIREMENT, judge, EVIDENCE, null);
            assertThatThrownBy(() -> SemanticAssertions.requireSatisfied(result)).isInstanceOf(errorType);
            assertThat(result.verdict().aggregated().producerStatus()).isEqualTo(judgment.producerStatus());
        });
    }

    @Test
    void policyFailureRetainsProducerFactsSeparatelyFromProducerError() {
        var failing = new PolicyBinding(USE.reference(), j -> { throw new IllegalStateException("policy failed"); });
        var result = new RequirementAssertions(failing).evaluate(REQUIREMENT, PASS, EVIDENCE, null);
        assertThat(result.verdict().aggregated().producerStatus()).isEqualTo(JudgmentStatus.PASS);
        assertThat(result.applicationDecision().application()).isInstanceOf(PolicyFailure.class);
        assertThat(result.verdict().aggregated().policyApplication()).isNull();
        assertThatThrownBy(() -> SemanticAssertions.requireSatisfied(result)).isInstanceOf(SemanticAssertionError.InstrumentFailure.class);
    }

    @Test
    void juryPathRetainsDisagreementNamesOrderAndAggregate() {
        var jury = SimpleJury.<RequirementEvidence<Requirement<String>, String>>builder()
            .judge(Judges.named(PASS, "one"))
            .judge(Judges.named(input -> Judgment.fail("violation"), "two"))
            .votingStrategy(new ConsensusStrategy()).parallel(false).build();
        var result = new RequirementAssertions(USE).evaluate(REQUIREMENT, jury, EVIDENCE, null);
        assertThat(result.verdict().individual()).extracting(Judgment::producerStatus)
            .containsExactly(JudgmentStatus.PASS, JudgmentStatus.FAIL);
        assertThat(result.verdict().seats()).extracting(Seat::verdictKey).containsExactly("one", "two");
        assertThat(result.verdict().individualByName()).containsOnlyKeys("one", "two");
        assertThat(result.verdict().aggregated().status()).isEqualTo(JudgmentStatus.ABSTAIN);
        assertThat(result.interpretation().reading()).isEqualTo(VerdictReading.UNDECIDED);
        assertThatThrownBy(() -> assertThat(REQUIREMENT).judgedBy(jury).withEvidence(EVIDENCE).isSatisfied())
            .hasCauseInstanceOf(SemanticAssertionError.Inconclusive.class);
    }

    @Test
    void consensusDoesNotClaimTwoIndependentConfirmations() {
        var jury = SimpleJury.<RequirementEvidence<Requirement<String>, String>>builder()
            .judge(PASS).judge(input -> Judgment.abstain("unknown"))
            .votingStrategy(new ConsensusStrategy()).parallel(false).build();
        var result = new RequirementAssertions(USE).evaluate(REQUIREMENT, jury, EVIDENCE, null);
        assertThat(result.interpretation().reading()).isEqualTo(VerdictReading.ACCEPTED);
        assertThat(result.verdict().individual()).extracting(Judgment::status)
            .containsExactly(JudgmentStatus.PASS, JudgmentStatus.ABSTAIN);
    }

    @Test
    void cascadeRetainsAttemptsAndSkipsFallbackOnDecisiveFirstTier() {
        var laterCalls = new AtomicInteger();
        var first = SimpleJury.<RequirementEvidence<Requirement<String>, String>>builder().judge(PolicyJudges.apply(PASS, USE.reference(), USE.policy())).votingStrategy(new AllMustPassStrategy()).parallel(false).build();
        var later = SimpleJury.<RequirementEvidence<Requirement<String>, String>>builder()
            .judge(input -> { laterCalls.incrementAndGet(); return Judgment.pass("fallback"); }).votingStrategy(new AllMustPassStrategy()).parallel(false).build();
        var cascade = CascadedJury.<RequirementEvidence<Requirement<String>, String>>builder()
            .tier("deterministic", first, TierPolicy.STOP_ON_USABLE_ASSESSMENT)
            .tier("fallback", later, TierPolicy.FINAL_TIER).build();
        var result = new RequirementAssertions(USE).evaluate(REQUIREMENT, cascade, EVIDENCE, null);
        SemanticAssertions.requireSatisfied(result);
        assertThat(laterCalls.get()).isZero();
        assertThat(result.verdict().compositeAttempts()).hasSize(1);
        assertThat(result.verdict().decision().tier()).isEqualTo("deterministic");
        var escalated = new RequirementAssertions(ESCALATE).evaluate(REQUIREMENT, cascade, EVIDENCE, null);
        assertThat(laterCalls.get()).isZero();
        assertThat(escalated.verdict().compositeAttempts()).hasSize(1);
        assertThat(escalated.interpretation().reading()).isEqualTo(VerdictReading.ACCEPTED);
        assertThatThrownBy(() -> SemanticAssertions.requireSatisfied(escalated)).isInstanceOf(SemanticAssertionError.Inconclusive.class);
    }

    @Test
    void retainedV2ResultMakesNoJudgeOrPolicyCalls() throws Exception {
        var calls = new AtomicInteger();
        var policies = new AtomicInteger();
        Judge<RequirementEvidence<Requirement<String>, String>> judge = input -> {
            calls.incrementAndGet(); return Judgment.pass("ready");
        };
        var binding = new PolicyBinding(USE.reference(), j -> { policies.incrementAndGet(); return USE.policy().evaluate(j); });
        var result = new RequirementAssertions(binding).evaluate(REQUIREMENT, judge, EVIDENCE, null);
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var restored = mapper.readValue(mapper.writeValueAsBytes(result.verdict()), Verdict.class);
        var reopened = new AssertionResult(REQUIREMENT, result.applicationDecision(), restored);
        SemanticAssertions.requireSatisfied(reopened);
        SemanticAssertions.requireSatisfied(reopened);
        assertThat(calls.get()).isEqualTo(1);
        assertThat(policies.get()).isEqualTo(1);
        assertThat(reopened.verdict()).isEqualTo(result.verdict());
    }
}
