/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.assertj;

import io.github.markpollack.judge.acceptance.Policies;

import static io.github.markpollack.judge.assertj.Assertions.assertThat;
import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.JudgeMetadata;
import io.github.markpollack.judge.JudgeType;
import io.github.markpollack.judge.Judges;
import io.github.markpollack.judge.NamedJudge;
import io.github.markpollack.judge.PolicyJudges;
import io.github.markpollack.judge.assertions.AssertionResult;
import io.github.markpollack.judge.assertions.RequirementAssertionError;
import io.github.markpollack.judge.assertions.RequirementAssertions;
import io.github.markpollack.judge.jury.AllMustPassStrategy;
import io.github.markpollack.judge.jury.CascadedJury;
import io.github.markpollack.judge.jury.ConsensusStrategy;
import io.github.markpollack.judge.jury.Jury;
import io.github.markpollack.judge.jury.Seat;
import io.github.markpollack.judge.jury.SimpleJury;
import io.github.markpollack.judge.jury.TierPolicy;
import io.github.markpollack.judge.jury.Verdict;
import io.github.markpollack.judge.jury.interpretation.RequirementOutcome;
import io.github.markpollack.judge.requirement.Requirement;
import io.github.markpollack.judge.requirement.RequirementEvidence;
import io.github.markpollack.judge.judgment.Judgment;
import io.github.markpollack.judge.judgment.JudgmentStatus;
import io.github.markpollack.judge.acceptance.AcceptanceAction;
import io.github.markpollack.judge.acceptance.AcceptanceDecision;
import io.github.markpollack.judge.acceptance.AcceptancePolicy;
import io.github.markpollack.judge.acceptance.AppliedPolicy;
import io.github.markpollack.judge.acceptance.Policies;
import io.github.markpollack.judge.acceptance.PolicyFailure;
import io.github.markpollack.judge.provenance.PolicyRef;

class RequirementAssertionTest {

	static final Requirement<String> REQUIREMENT = Requirement.text("ready", "1", "The answer is READY");
	static final String EVIDENCE = "READY";
	static final AcceptancePolicy USE = policy("use", AcceptanceAction.RELY);
	static final AcceptancePolicy WITHHOLD = policy("withhold", AcceptanceAction.ABSTAIN);
	static final AcceptancePolicy ESCALATE = policy("escalate", AcceptanceAction.ESCALATE);
	static final Judge<RequirementEvidence<String, String>> PASS = input -> Judgment.pass("ready");

	static AcceptancePolicy policy(String name, AcceptanceAction action) {
		return Policies.recorded(new PolicyRef(name, "1", "a".repeat(64)), j -> new AcceptanceDecision(action, name));
	}

	@Test
	void arbitraryEvidenceAndTypedJuryNeedNoRequirement() {
		Judge<String> length = text -> text.length() == 5 ? Judgment.pass("length") : Judgment.fail("length");
		Jury<String> jury = SimpleJury.<String>builder()
			.judge(length)
			.votingStrategy(new AllMustPassStrategy())
			.parallel(false)
			.build();
		assertThat(jury.vote("READY").judgment().pass()).isTrue();
	}

	@Test
	void terminalAloneInvokesAndDeliversOriginalObjects() {
		var calls = new AtomicInteger();
		var received = new AtomicReference<RequirementEvidence<String, String>>();
		Judge<RequirementEvidence<String, String>> judge = input -> {
			calls.incrementAndGet();
			received.set(input);
			return Judgment.pass("ready");
		};
		var terminal = assertThat(REQUIREMENT).judgedByRequirement(judge).withEvidence(EVIDENCE);
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
		assertThat(REQUIREMENT).judgedByRequirement(PASS).withEvidence(EVIDENCE).isSatisfied();
	}

	@Test
	void explicitPolicyOverridesDefaultWithoutChangingRequirement() {
		var fixture = new RequirementAssertions(WITHHOLD);
		var defaultResult = fixture.evaluateRequirement(REQUIREMENT, PASS, EVIDENCE, null);
		var explicitResult = fixture.evaluateRequirement(REQUIREMENT, PASS, EVIDENCE, ESCALATE);
		assertThat(defaultResult.policySource()).isEqualTo(AssertionResult.PolicySource.DEFAULT);
		assertThat(explicitResult.policySource()).isEqualTo(AssertionResult.PolicySource.EXPLICIT);
		assertThat((Object) explicitResult.requirement()).isSameAs(REQUIREMENT);
		assertThat(explicitResult.policy()).isEqualTo(Policies.referenceOf(ESCALATE));
		assertThat(explicitResult.verdict().judgment().status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(explicitResult.interpretation().outcome()).isEqualTo(RequirementOutcome.SATISFIED);
		assertThat(((AppliedPolicy) explicitResult.acceptanceExecution().application()).action())
			.isEqualTo(AcceptanceAction.ESCALATE);
	}

	@Test
	void genuinelyMissingPolicyFailsBeforeJudgeInvocation() {
		var calls = new AtomicInteger();
		Judge<RequirementEvidence<String, String>> judge = input -> {
			calls.incrementAndGet();
			return Judgment.pass("ready");
		};
		var entry = Assertions.using(new RequirementAssertions(null));
		assertThatThrownBy(
				() -> entry.assertThat(REQUIREMENT).judgedByRequirement(judge).withEvidence(EVIDENCE).isSatisfied())
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("before evaluation");
		assertThat(calls.get()).isZero();
		entry.assertThat(REQUIREMENT)
			.judgedByRequirement(judge)
			.withEvidence(EVIDENCE)
			.withAcceptancePolicy(USE)
			.isSatisfied();
		assertThat(calls.get()).isEqualTo(1);
	}

	@Test
	void assertJDescriptionAndCompleteSemanticFailureRemainAvailable() {
		AssertionError failure = catchThrowableOfType(() -> assertThat(REQUIREMENT).as("critical lock order")
			.judgedByRequirement(PASS)
			.withEvidence(EVIDENCE)
			.withAcceptancePolicy(ESCALATE)
			.isSatisfied(), AssertionError.class);
		assertThat(failure).hasMessageContaining("[critical lock order]").hasMessageContaining("resolved EXPLICIT");
		assertThat(failure.getCause()).isInstanceOf(RequirementAssertionError.Inconclusive.class);
		var semantic = (RequirementAssertionError) failure.getCause();
		assertThat((Object) semantic.result().requirement()).isSameAs(REQUIREMENT);
		assertThat(semantic.result().verdict().individual()).hasSize(1);
	}

	@Test
	void acceptedNegativeInsufficiencyErrorAndDeclaredNaRemainDistinct() {
		var fixture = new RequirementAssertions(USE);
		Map<Judgment, Class<? extends RequirementAssertionError>> cases = Map.of(Judgment.fail("violated"),
				RequirementAssertionError.Rejected.class, Judgment.abstain("evidence missing"),
				RequirementAssertionError.Inconclusive.class, Judgment.error("producer failed"),
				RequirementAssertionError.InstrumentFailure.class, Judgment.notApplicable("no persistence"),
				RequirementAssertionError.NotApplicable.class);
		cases.forEach((judgment, errorType) -> {
			var judge = new NamedJudge<RequirementEvidence<String, String>>(input -> judgment, new JudgeMetadata(
					"conditional", "native applicability", JudgeType.DETERMINISTIC, "subject has persistence"));
			var result = fixture.evaluateRequirement(REQUIREMENT, judge, EVIDENCE, null);
			assertThatThrownBy(() -> RequirementAssertions.requireSatisfied(result)).isInstanceOf(errorType);
			assertThat(result.verdict().judgment().producerStatus()).isEqualTo(judgment.producerStatus());
		});
	}

	@Test
	void policyFailureRetainsProducerFactsSeparatelyFromProducerError() {
		var failing = Policies.recorded(Policies.referenceOf(USE), j -> {
			throw new IllegalStateException("policy failed");
		});
		var result = new RequirementAssertions(failing).evaluateRequirement(REQUIREMENT, PASS, EVIDENCE, null);
		assertThat(result.verdict().judgment().producerStatus()).isEqualTo(JudgmentStatus.PASS);
		assertThat(result.acceptanceExecution().application()).isInstanceOf(PolicyFailure.class);
		assertThat(result.verdict().judgment().policyApplication()).isNull();
		assertThatThrownBy(() -> RequirementAssertions.requireSatisfied(result))
			.isInstanceOf(RequirementAssertionError.InstrumentFailure.class);
	}

	@Test
	void juryPathRetainsDisagreementNamesOrderAndAggregate() {
		var jury = SimpleJury.<RequirementEvidence<String, String>>builder()
			.judge(Judges.named(PASS, "one"))
			.judge(Judges.named(input -> Judgment.fail("violation"), "two"))
			.votingStrategy(new ConsensusStrategy())
			.parallel(false)
			.build();
		var result = new RequirementAssertions(USE).evaluateRequirement(REQUIREMENT, jury, EVIDENCE, null);
		assertThat(result.verdict().individual()).extracting(Judgment::producerStatus)
			.containsExactly(JudgmentStatus.PASS, JudgmentStatus.FAIL);
		assertThat(result.verdict().seats()).extracting(Seat::verdictKey).containsExactly("one", "two");
		assertThat(result.verdict().individualByName()).containsOnlyKeys("one", "two");
		assertThat(result.verdict().judgment().status()).isEqualTo(JudgmentStatus.ABSTAIN);
		assertThat(result.interpretation().outcome()).isEqualTo(RequirementOutcome.UNRESOLVED);
		assertThatThrownBy(() -> assertThat(REQUIREMENT).judgedByRequirement(jury).withEvidence(EVIDENCE).isSatisfied())
			.hasCauseInstanceOf(RequirementAssertionError.Inconclusive.class);
	}

	@Test
	void consensusDoesNotClaimTwoIndependentConfirmations() {
		var jury = SimpleJury.<RequirementEvidence<String, String>>builder()
			.judge(PASS)
			.judge(input -> Judgment.abstain("unknown"))
			.votingStrategy(new ConsensusStrategy())
			.parallel(false)
			.build();
		var result = new RequirementAssertions(USE).evaluateRequirement(REQUIREMENT, jury, EVIDENCE, null);
		assertThat(result.interpretation().outcome()).isEqualTo(RequirementOutcome.SATISFIED);
		assertThat(result.verdict().individual()).extracting(Judgment::status)
			.containsExactly(JudgmentStatus.PASS, JudgmentStatus.ABSTAIN);
	}

	@Test
	void cascadeRetainsAttemptsAndSkipsFallbackOnDecisiveFirstTier() {
		var laterCalls = new AtomicInteger();
		var first = SimpleJury.<RequirementEvidence<String, String>>builder()
			.judge(PolicyJudges.apply(PASS, Policies.referenceOf(USE), USE))
			.votingStrategy(new AllMustPassStrategy())
			.parallel(false)
			.build();
		var later = SimpleJury.<RequirementEvidence<String, String>>builder().judge(input -> {
			laterCalls.incrementAndGet();
			return Judgment.pass("fallback");
		}).votingStrategy(new AllMustPassStrategy()).parallel(false).build();
		var cascade = CascadedJury.<RequirementEvidence<String, String>>builder()
			.tier("deterministic", first, TierPolicy.STOP_ON_RELIED_JUDGMENT)
			.tier("fallback", later, TierPolicy.FINAL_TIER)
			.build();
		var result = new RequirementAssertions(USE).evaluateRequirement(REQUIREMENT, cascade, EVIDENCE, null);
		RequirementAssertions.requireSatisfied(result);
		assertThat(laterCalls.get()).isZero();
		assertThat(result.verdict().compositeAttempts()).hasSize(1);
		assertThat(result.verdict().provenance().tier()).isEqualTo("deterministic");
		var escalated = new RequirementAssertions(ESCALATE).evaluateRequirement(REQUIREMENT, cascade, EVIDENCE, null);
		assertThat(laterCalls.get()).isZero();
		assertThat(escalated.verdict().compositeAttempts()).hasSize(1);
		assertThat(escalated.interpretation().outcome()).isEqualTo(RequirementOutcome.SATISFIED);
		assertThatThrownBy(() -> RequirementAssertions.requireSatisfied(escalated))
			.isInstanceOf(RequirementAssertionError.Inconclusive.class);
	}

	@Test
	void retainedV3ResultMakesNoJudgeOrPolicyCalls() throws Exception {
		var calls = new AtomicInteger();
		var policies = new AtomicInteger();
		Judge<RequirementEvidence<String, String>> judge = input -> {
			calls.incrementAndGet();
			return Judgment.pass("ready");
		};
		var binding = Policies.recorded(Policies.referenceOf(USE), j -> {
			policies.incrementAndGet();
			return USE.decide(j);
		});
		var result = new RequirementAssertions(binding).evaluateRequirement(REQUIREMENT, judge, EVIDENCE, null);
		var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
		var restored = mapper.readValue(mapper.writeValueAsBytes(result.verdict()), Verdict.class);
		var reopened = new AssertionResult(REQUIREMENT, result.acceptanceExecution(), restored);
		RequirementAssertions.requireSatisfied(reopened);
		RequirementAssertions.requireSatisfied(reopened);
		assertThat(calls.get()).isEqualTo(1);
		assertThat(policies.get()).isEqualTo(1);
		assertThat(reopened.verdict()).isEqualTo(result.verdict());
	}

}
