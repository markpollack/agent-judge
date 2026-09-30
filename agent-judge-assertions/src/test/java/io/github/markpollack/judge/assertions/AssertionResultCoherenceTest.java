/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.assertions;

import io.github.markpollack.judge.acceptance.Policies;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.markpollack.judge.PolicyJudges;
import io.github.markpollack.judge.assertions.AcceptanceExecution.Bypass;
import io.github.markpollack.judge.assertions.AssertionResult.PolicySource;
import io.github.markpollack.judge.completion.CompletionEvidence;
import io.github.markpollack.judge.jury.AllMustPassStrategy;
import io.github.markpollack.judge.jury.SimpleJury;
import io.github.markpollack.judge.jury.Verdict;
import io.github.markpollack.judge.jury.interpretation.ReadingSupport;
import io.github.markpollack.judge.jury.interpretation.Verdicts;
import io.github.markpollack.judge.requirement.Requirement;
import io.github.markpollack.judge.judgment.Judgment;
import io.github.markpollack.judge.judgment.JudgmentReasonCode;
import io.github.markpollack.judge.acceptance.AcceptanceAction;
import io.github.markpollack.judge.acceptance.AcceptanceDecision;
import io.github.markpollack.judge.acceptance.AcceptancePolicy;
import io.github.markpollack.judge.acceptance.AppliedPolicy;
import io.github.markpollack.judge.acceptance.Policies;
import io.github.markpollack.judge.acceptance.PolicyFailure;
import io.github.markpollack.judge.provenance.PolicyRef;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AssertionResultCoherenceTest {

	private static final Requirement<?> REQUIREMENT = Requirement.text("example", "1", "exact requirement");

	private static final PolicyRef A = new PolicyRef("policy", "1", "a".repeat(64));

	private static final PolicyRef B = new PolicyRef("policy", "1", "b".repeat(64));

	private static final AcceptancePolicy BINDING = binding(A);

	static AcceptancePolicy binding(PolicyRef ref) {
		return Policies.recorded(ref, j -> new AcceptanceDecision(AcceptanceAction.RELY, "use retained evaluation"));
	}

	static AcceptanceExecution applied(PolicyRef ref, PolicySource source) {
		return new AcceptanceExecution(ref, source, new AppliedPolicy(ref, AcceptanceAction.RELY, "use"), null);
	}

	static Verdict accepted() {
		return Verdict.single("candidate", Policies.apply(Judgment.pass("satisfied"), A, BINDING));
	}

	static AssertionResult result(Requirement<?> requirement, PolicyRef ref, PolicySource source, Verdict verdict) {
		return AssertionResult.applyPolicy(requirement, binding(ref), source, verdict);
	}

	@Test
	void ordinaryPoliciesNeedNoRecordedIdentity() throws Exception {
		AcceptancePolicy policy = judgment -> new AcceptanceDecision(AcceptanceAction.RELY, "rely");
		var original = AssertionResult.applyPolicy(REQUIREMENT, policy, PolicySource.EXPLICIT, accepted());
		assertThat(original.policy()).isNull();
		assertThat(original.acceptanceExecution().application().policy()).isNull();
		var json = new ObjectMapper();
		var retained = json.readValue(json.writeValueAsBytes(original.acceptanceExecution()),
				AcceptanceExecution.class);
		RequirementAssertions.requireSatisfied(new AssertionResult(REQUIREMENT, retained, original.verdict()));
	}

	@Test
	void finalDecisionRequiresMatchingIdentityIncludingFailureAndDigest() {
		assertThatThrownBy(() -> new AcceptanceExecution(B, PolicySource.DEFAULT,
				new AppliedPolicy(A, AcceptanceAction.RELY, "use"), null))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new AcceptanceExecution(B, PolicySource.DEFAULT,
				new PolicyFailure(A, JudgmentReasonCode.POLICY_FAILED, "failed"), null))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new AcceptanceExecution(A, PolicySource.DEFAULT, null, null))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new AcceptanceExecution(A, PolicySource.DEFAULT,
				new AppliedPolicy(A, AcceptanceAction.RELY, "use"), Bypass.NOT_ASSESSED))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void finalPolicyMayDifferFromInternalRootPolicyWithoutReplacingIt() {
		var verdict = accepted();
		var result = result(REQUIREMENT, B, PolicySource.DEFAULT, verdict);
		assertThat(result.verdict()).isSameAs(verdict);
		assertThat(result.verdict().judgment().policyApplication().policy()).isEqualTo(A);
		assertThat(result.acceptanceExecution().application().policy()).isEqualTo(B);
		RequirementAssertions.requireSatisfied(result);
	}

	@Test
	void internalPolicyFailureCannotBeRepairedByFinalUse() {
		var failed = Policies.apply(Judgment.pass("producer succeeded"), A, j -> {
			throw new IllegalStateException("internal policy");
		});
		var calls = new AtomicInteger();
		var verdict = Verdict.single("candidate", failed);
		var result = AssertionResult.applyPolicy(REQUIREMENT, Policies.recorded(B, j -> {
			calls.incrementAndGet();
			return new AcceptanceDecision(AcceptanceAction.RELY, "use");
		}), PolicySource.DEFAULT, verdict);
		assertThat(result.acceptanceExecution().bypass()).isEqualTo(Bypass.NOT_ASSESSED);
		assertThat(result.verdict().judgment().policyApplication()).isInstanceOf(PolicyFailure.class);
		assertThat(calls).hasValue(0);
		assertThatThrownBy(() -> RequirementAssertions.requireSatisfied(result))
			.isInstanceOf(RequirementAssertionError.InstrumentFailure.class);
	}

	@Test
	void composedVerdictKeepsDifferentSeatPoliciesAndNoRootApplication() {
		var jury = SimpleJury.<CompletionEvidence>builder()
			.judge(PolicyJudges.apply(c -> Judgment.pass("first"), A, BINDING))
			.judge(PolicyJudges.apply(c -> Judgment.pass("second"), B, BINDING))
			.votingStrategy(new AllMustPassStrategy())
			.parallel(false)
			.build();
		var verdict = jury.vote(CompletionEvidence.builder().request(REQUIREMENT.text()).build());
		var result = result(REQUIREMENT, B, PolicySource.DEFAULT, verdict);
		assertThat(result.verdict()).isSameAs(verdict);
		assertThat(verdict.judgment().policyApplication()).isNull();
		assertThat(verdict.individual()).extracting(j -> j.policyApplication().policy()).containsExactly(A, B);
		assertThat(result.interpretation().readingSupport()).isEqualTo(ReadingSupport.SUPPORTED);
		RequirementAssertions.requireSatisfied(result);
	}

	@Test
	void aRetainedApplicationCannotFabricateOrOmitRequiredBypass() {
		var error = Verdict.single("candidate", Judgment.error("failed"));
		var na = Verdict.single("candidate", Judgment.notApplicable("outside domain"));
		for (var verdict : List.of(error, na)) {
			assertThatThrownBy(() -> new AssertionResult(REQUIREMENT, applied(A, PolicySource.DEFAULT), verdict))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("bypass");
		}
		var valid = result(REQUIREMENT, A, PolicySource.DEFAULT, error);
		assertThatThrownBy(() -> new AssertionResult(REQUIREMENT, valid.acceptanceExecution(), accepted()))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("bypass");
		assertThatThrownBy(() -> new AssertionResult(REQUIREMENT,
				new AcceptanceExecution(A, PolicySource.DEFAULT, null, Bypass.NOT_APPLICABLE), error))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("bypass");
	}

	@Test
	void unsupportedReadingsBypassAndCannotUseAForgedAcceptedInterpretation() {
		var accepted = accepted();
		var invalid = new Verdict(3, accepted.judgment(), accepted.individual(), accepted.individualByName(),
				accepted.weights(), accepted.seats(), accepted.provenance(), accepted.compositeAttempts(), 0);
		var result = AssertionResult.applyPolicy(REQUIREMENT, Policies.recorded(A, j -> {
			throw new AssertionError("must bypass");
		}), PolicySource.DEFAULT, invalid);
		assertThat(result.acceptanceExecution().bypass()).isEqualTo(Bypass.UNSUPPORTED_READING);
		assertThatThrownBy(() -> RequirementAssertions.requireSatisfied(result))
			.isInstanceOf(RequirementAssertionError.UnsupportedReading.class);
		assertThatThrownBy(() -> new AssertionResult(REQUIREMENT, applied(A, PolicySource.DEFAULT), invalid,
				Verdicts.interpret(accepted)))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("authoritative");
	}

	@Test
	void reopeningTypedValuesMakesNoPolicyCallsAndPreservesFinalWithholding() throws Exception {
		var calls = new AtomicInteger();
		var original = AssertionResult.applyPolicy(REQUIREMENT, Policies.recorded(B, j -> {
			calls.incrementAndGet();
			assertThat(j.policyApplication()).isNull();
			return new AcceptanceDecision(AcceptanceAction.ESCALATE, "application follow-up");
		}), PolicySource.DEFAULT, accepted());
		var json = new ObjectMapper();
		var provenance = json.readValue(json.writeValueAsBytes(original.acceptanceExecution()),
				AcceptanceExecution.class);
		var verdict = json.readValue(json.writeValueAsBytes(original.verdict()), Verdict.class);
		var reopened = new AssertionResult(REQUIREMENT, provenance, verdict);
		for (int i = 0; i < 2; i++)
			assertThatThrownBy(() -> RequirementAssertions.requireSatisfied(reopened))
				.isInstanceOf(RequirementAssertionError.Inconclusive.class);
		assertThat(reopened).isEqualTo(original);
		assertThat(calls).hasValue(1);
	}

}
