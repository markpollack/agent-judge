/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.assertions;

import io.github.markpollack.judge.requirement.Requirement;
import io.github.markpollack.judge.result.PolicyBinding;

import io.github.markpollack.judge.PolicyJudges;
import io.github.markpollack.judge.context.JudgmentContext;
import io.github.markpollack.judge.jury.AllMustPassStrategy;
import io.github.markpollack.judge.jury.SimpleJury;
import io.github.markpollack.judge.jury.Verdict;
import io.github.markpollack.judge.jury.interpretation.ReadingSupport;
import io.github.markpollack.judge.result.Acceptance;
import io.github.markpollack.judge.result.AcceptanceAction;
import io.github.markpollack.judge.result.Judgment;
import io.github.markpollack.judge.result.JudgmentReasonCode;
import io.github.markpollack.judge.result.Policies;
import io.github.markpollack.judge.result.PolicyRef;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssertionResultCoherenceTest {

	private static final Requirement<?> REQUIREMENT = Requirement.text("example", "1", "exact requirement");

	private static final PolicyRef A = new PolicyRef("policy", "1", "a".repeat(64));

	private static final PolicyRef B = new PolicyRef("policy", "1", "b".repeat(64));

	private static final PolicyBinding BINDING = new PolicyBinding(A,
			j -> new Acceptance(AcceptanceAction.USE_ASSESSMENT, "use retained assessment"));

	@Test
	void requirementSourceNeedsAnOverrideWithTheExactResolvedIdentity() {
		Verdict verdict = accepted();
		assertThatThrownBy(() -> result(REQUIREMENT, A, AssertionResult.PolicySource.ASSOCIATED, verdict))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("override");
		assertThatThrownBy(
				() -> result(REQUIREMENT.under(BINDING), B, AssertionResult.PolicySource.ASSOCIATED, verdict))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("override");
		assertThatCode(() -> result(REQUIREMENT.under(BINDING), A, AssertionResult.PolicySource.ASSOCIATED, verdict))
			.doesNotThrowAnyException();
	}

	@Test
	void defaultSourceCannotHideARequirementOverride() {
		assertThatThrownBy(
				() -> result(REQUIREMENT.under(BINDING), A, AssertionResult.PolicySource.DEFAULT, accepted()))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("DEFAULT");
	}

	@Test
	void rootApplicationMustMatchIncludingConfigurationDigest() {
		assertThatThrownBy(() -> result(REQUIREMENT, B, AssertionResult.PolicySource.DEFAULT, accepted()))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("root policy");
	}

	@Test
	void rootPolicyFailureMustAlsoMatch() {
		Judgment failed = Policies.apply(Judgment.pass("producer succeeded"), A, j -> {
			throw new IllegalStateException("policy unavailable");
		});
		Verdict verdict = Verdict.single("candidate", failed);
		assertThatThrownBy(() -> result(REQUIREMENT, B, AssertionResult.PolicySource.DEFAULT, verdict))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("root policy");
		AssertionResult valid = result(REQUIREMENT, A, AssertionResult.PolicySource.DEFAULT, verdict);
		assertThatThrownBy(() -> SemanticAssertions.requireSatisfied(valid))
			.isInstanceOf(SemanticAssertionError.InstrumentFailure.class);
	}

	@Test
	void composedVerdictMayHaveDifferentSeatPoliciesAndNoRootApplication() {
		var jury = SimpleJury.<JudgmentContext>builder()
			.judge(PolicyJudges.apply(c -> Judgment.pass("first"), A, BINDING.policy()))
			.judge(PolicyJudges.apply(c -> Judgment.pass("second"), B, BINDING.policy()))
			.votingStrategy(new AllMustPassStrategy())
			.parallel(false)
			.build();
		var verdict = jury.vote(JudgmentContext.builder().goal(REQUIREMENT.text()).build());
		assertThat(verdict.aggregated().policyApplication()).isNull();
		assertThat(verdict.individual()).allSatisfy(j -> assertThat(j.policyApplication()).isNotNull());
		AssertionResult result = result(REQUIREMENT, A, AssertionResult.PolicySource.DEFAULT, verdict);
		assertThat(result.interpretation().readingSupport()).isEqualTo(ReadingSupport.SUPPORTED);
		assertThatCode(() -> SemanticAssertions.requireSatisfied(result)).doesNotThrowAnyException();
	}

	@Test
	void rawErrorAndNonApplicabilityKeepTheirLegitimatePolicyBypass() {
		for (Judgment raw : new Judgment[] { Judgment.error(JudgmentReasonCode.JUDGE_REPORTED, "invalid protocol"),
				Judgment.notApplicable("outside domain") }) {
			var verdict = Verdict.single("candidate", raw);
			var result = result(REQUIREMENT.under(BINDING), A, AssertionResult.PolicySource.ASSOCIATED, verdict);
			assertThat(result.verdict().aggregated()).isSameAs(raw);
			assertThat(raw.policyApplication()).isNull();
		}
	}

	private static Verdict accepted() {
		return Verdict.single("candidate", Policies.apply(Judgment.pass("satisfied"), A, BINDING.policy()));
	}

	private static AssertionResult result(Requirement<?> requirement, PolicyRef policy,
			AssertionResult.PolicySource source, Verdict verdict) {
		return new AssertionResult(requirement, policy, source, verdict);
	}

}
