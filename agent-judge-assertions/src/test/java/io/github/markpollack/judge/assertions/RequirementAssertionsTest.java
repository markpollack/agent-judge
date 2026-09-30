/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.assertions;

import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.NamedJudge;
import io.github.markpollack.judge.JudgeMetadata;
import io.github.markpollack.judge.JudgeType;
import io.github.markpollack.judge.acceptance.AcceptanceAction;
import io.github.markpollack.judge.acceptance.AcceptanceDecision;
import io.github.markpollack.judge.acceptance.AcceptancePolicy;
import io.github.markpollack.judge.acceptance.AppliedPolicy;
import io.github.markpollack.judge.acceptance.Policies;
import io.github.markpollack.judge.judgment.Judgment;
import io.github.markpollack.judge.judgment.JudgmentStatus;
import io.github.markpollack.judge.jury.interpretation.RequirementOutcome;
import io.github.markpollack.judge.requirement.Requirement;
import io.github.markpollack.judge.requirement.RequirementEvidence;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.assertj.core.api.Assertions.*;

class RequirementAssertionsTest {

	static final Requirement<String> READY_REQUIREMENT = Requirement.text("ready", "1", "The response is READY");
	static final Judge<String> READY_RESPONSE_JUDGE = response -> "READY".equals(response) ? Judgment.pass("ready")
			: Judgment.fail("different response");

	static AcceptancePolicy policy(AcceptanceAction action) {
		return judgment -> new AcceptanceDecision(action, "application reliance rule");
	}

	static AssertionResult evaluate(Judge<String> judge, AcceptancePolicy policy) {
		return new RequirementAssertions(policy).evaluate(READY_REQUIREMENT, judge, "READY", null);
	}

	@Test
	void requirementContainsOnlyTheSpecificationAndItsSource() {
		assertThat(
				Arrays.stream(Requirement.class.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName))
			.containsExactly("id", "revision", "text", "specification", "source");
		assertThat(Arrays.stream(Requirement.class.getMethods()).map(java.lang.reflect.Method::getName))
			.doesNotContain("under", "acceptancePolicy");
	}

	@Test
	void ordinaryLambdaAndRetainedTerminalRequireNoIdentityAndExecuteOnce() throws Exception {
		var judges = new AtomicInteger();
		var policies = new AtomicInteger();
		Judge<String> judge = evidence -> {
			judges.incrementAndGet();
			return READY_RESPONSE_JUDGE.judge(evidence);
		};
		AcceptancePolicy policy = judgment -> {
			policies.incrementAndGet();
			return new AcceptanceDecision(AcceptanceAction.RELY, "rely");
		};
		var result = evaluate(judge, policy);
		assertThat(result.policy()).isNull();
		assertThat(result.verdict().judgment().finding()).isNull();
		assertThat(result.verdict().judgment()).isSameAs(result.verdict().individual().getFirst());
		var json = new com.fasterxml.jackson.databind.ObjectMapper();
		var execution = json.readValue(json.writeValueAsBytes(result.acceptanceExecution()), AcceptanceExecution.class);
		var verdict = json.readValue(json.writeValueAsBytes(result.verdict()),
				io.github.markpollack.judge.jury.Verdict.class);
		var reopened = new AssertionResult(READY_REQUIREMENT, execution, verdict);
		RequirementAssertions.requireSatisfied(reopened);
		RequirementAssertions.requireSatisfied(reopened);
		assertThat(judges).hasValue(1);
		assertThat(policies).hasValue(1);
	}

	@Test
	void relyOnANegativeJudgmentEstablishesViolation() {
		var result = evaluate(evidence -> Judgment.fail("violated"), policy(AcceptanceAction.RELY));
		assertThat(result.interpretation().outcome()).isEqualTo(RequirementOutcome.VIOLATED);
		assertThat(result.verdict().judgment().finding()).isNull();
		var failure = catchThrowableOfType(() -> RequirementAssertions.requireSatisfied(result),
				RequirementAssertionError.Rejected.class);
		assertThat(failure.result()).isSameAs(result);
		assertThat(failure).isInstanceOf(org.opentest4j.AssertionFailedError.class);
	}

	@ParameterizedTest
	@EnumSource(value = AcceptanceAction.class, names = { "ABSTAIN", "ESCALATE" })
	void withheldNegativeRetainsViolationButCannotBeReliedOn(AcceptanceAction action) {
		var result = evaluate(evidence -> Judgment.fail("violated"), policy(action));
		assertThat(result.interpretation().outcome()).isEqualTo(RequirementOutcome.VIOLATED);
		assertThat(result.verdict().judgment().status()).isEqualTo(JudgmentStatus.FAIL);
		assertThatThrownBy(() -> RequirementAssertions.requireSatisfied(result))
			.isInstanceOf(RequirementAssertionError.Inconclusive.class);
	}

	@Test
	void errorAbstentionAndApplicabilityStayDistinct() {
		assertThatThrownBy(() -> RequirementAssertions
			.requireSatisfied(evaluate(e -> Judgment.abstain("unknown"), policy(AcceptanceAction.RELY))))
			.isInstanceOf(RequirementAssertionError.Inconclusive.class);
		for (Judge<String> judge : List.<Judge<String>>of(e -> Judgment.error("broken"), e -> {
			throw new IllegalStateException("transport");
		}, e -> null)) {
			assertThatThrownBy(
					() -> RequirementAssertions.requireSatisfied(evaluate(judge, policy(AcceptanceAction.RELY))))
				.isInstanceOf(RequirementAssertionError.InstrumentFailure.class);
		}
		Judge<String> outside = e -> Judgment.notApplicable("outside domain");
		assertThatThrownBy(
				() -> RequirementAssertions.requireSatisfied(evaluate(outside, policy(AcceptanceAction.RELY))))
			.isInstanceOf(RequirementAssertionError.InstrumentFailure.class);
		var declared = new NamedJudge<>(outside,
				new JudgeMetadata("conditional", "domain", JudgeType.DETERMINISTIC, "outside domain"));
		assertThatThrownBy(
				() -> RequirementAssertions.requireSatisfied(evaluate(declared, policy(AcceptanceAction.RELY))))
			.isInstanceOf(RequirementAssertionError.NotApplicable.class);
	}

	@Test
	void missingPolicyAndInvalidSetupFailBeforeEvaluation() {
		var calls = new AtomicInteger();
		Judge<String> judge = e -> {
			calls.incrementAndGet();
			return Judgment.pass("ready");
		};
		assertThatThrownBy(() -> new RequirementAssertions(null).evaluate(READY_REQUIREMENT, judge, "READY", null))
			.isInstanceOf(IllegalStateException.class);
		assertThat(calls).hasValue(0);
		assertThatThrownBy(() -> RequirementAssertions.relyingOnJudgment().evaluate(null, judge, "READY", null))
			.isInstanceOf(NullPointerException.class);
		var jury = io.github.markpollack.judge.jury.SimpleJury.<String>builder()
			.judge(judge)
			.votingStrategy(new io.github.markpollack.judge.jury.AllMustPassStrategy())
			.build();
		assertThatThrownBy(() -> RequirementAssertions.relyingOnJudgment().evaluate(null, jury, "READY", null))
			.isInstanceOf(NullPointerException.class);
		assertThat(calls).hasValue(0);
		assertThatThrownBy(() -> Requirement.text(" ", "1", "x")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Policies.recorded(null, j -> null)).isInstanceOf(NullPointerException.class);
	}

	@Test
	void concurrentRequirementAwareCallsRetainTheirOwnRequirementEvidenceAndPolicy() throws Exception {
		var assertions = RequirementAssertions.relyingOnJudgment();
		try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
			List<Future<AssertionResult>> futures = new ArrayList<>();
			for (int n = 0; n < 24; n++) {
				int index = n;
				futures.add(executor.submit(() -> {
					String id = "requirement-" + index;
					var requirement = Requirement.text(id, "1", id);
					Judge<RequirementEvidence<String, String>> judge = pair -> {
						assertThat(pair.requirement()).isSameAs(requirement);
						assertThat(pair.evidence()).isEqualTo(id);
						return Judgment.pass(pair.requirement().id());
					};
					return assertions.evaluateRequirement(requirement, judge, id,
							policy(index % 2 == 0 ? AcceptanceAction.RELY : AcceptanceAction.ESCALATE));
				}));
			}
			for (int n = 0; n < futures.size(); n++) {
				var result = futures.get(n).get();
				assertThat(result.verdict().judgment().reasoning()).isEqualTo("requirement-" + n);
				assertThat(((AppliedPolicy) result.acceptanceExecution().application()).action())
					.isEqualTo(n % 2 == 0 ? AcceptanceAction.RELY : AcceptanceAction.ESCALATE);
			}
		}
	}

	@Test
	void preInterruptedCallerKeepsCancellation() {
		try {
			Thread.currentThread().interrupt();
			var result = evaluate(e -> Thread.currentThread().isInterrupted() ? Judgment.error("pre-interrupted")
					: Judgment.pass("lost cancellation"), policy(AcceptanceAction.RELY));
			assertThat(result.verdict().judgment().status()).isEqualTo(JudgmentStatus.ERROR);
			assertThat(Thread.currentThread().isInterrupted()).isTrue();
		}
		finally {
			Thread.interrupted();
		}
	}

}
