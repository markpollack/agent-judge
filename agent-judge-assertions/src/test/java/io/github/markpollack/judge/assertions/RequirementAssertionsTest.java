/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.assertions;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import io.github.markpollack.judge.*;
import io.github.markpollack.judge.judgment.*;
import io.github.markpollack.judge.jury.*;
import io.github.markpollack.judge.requirement.*;
import io.github.markpollack.judge.policy.*;
import io.github.markpollack.judge.evaluation.*;
import io.github.markpollack.judge.reporting.VerdictReport;
import static org.assertj.core.api.Assertions.*;

class RequirementAssertionsTest {

	static final Requirement<String> READY = Requirement.text("ready", "1", "READY");
	static final io.github.markpollack.judge.construction.JudgeRecipe<String, String> CHECK = io.github.markpollack.judge.assertions.ConfiguredRules
		.rule((r, e) -> r.specification().equals(e) ? Judgment.pass("matches") : Judgment.fail("differs"));

	@Test
	void pureRequirementContainsNoExecutionConfiguration() {
		assertThat(Arrays.stream(io.github.markpollack.judge.requirement.GeneralRequirement.class.getRecordComponents())
			.map(java.lang.reflect.RecordComponent::getName))
			.containsExactly("id", "revision", "text", "specification", "source");
	}

	@Test
	void noPolicyIsACompleteSuccessfulEvaluation() {
		var result = ConfiguredRules.evaluate(READY, CHECK, "READY");
		assertThat(result.policyResult()).isInstanceOf(PolicyResult.NotRequested.class);
		assertThatCode(() -> RequirementAssertions.requireSatisfied(result)).doesNotThrowAnyException();
		assertThat(result.verdict().requirement()).isSameAs(READY);
	}

	@ParameterizedTest
	@EnumSource(PolicyAction.class)
	void positiveSatisfactionRequiresRequestedPolicyToPermitReliance(PolicyAction action) {
		var result = ConfiguredRules.evaluate(READY, CHECK, "READY", v -> new PolicyDecision(action, "reviewed"));
		assertThat(result.verdict().conclusion()).isEqualTo(Verdict.Conclusion.PASS);
		if (action == PolicyAction.RELY)
			assertThatCode(() -> RequirementAssertions.requireSatisfied(result)).doesNotThrowAnyException();
		else
			assertThatThrownBy(() -> RequirementAssertions.requireSatisfied(result))
				.isInstanceOf(RequirementAssertionError.class)
				.hasMessageContaining(action.name());
	}

	@ParameterizedTest
	@EnumSource(PolicyAction.class)
	void negativeNeverBecomesSatisfiedIncludingRely(PolicyAction action) {
		var result = ConfiguredRules.evaluate(READY, CHECK, "NO", v -> new PolicyDecision(action, "reviewed"));
		assertThat(result.verdict().conclusion()).isEqualTo(Verdict.Conclusion.FAIL);
		assertThatThrownBy(() -> RequirementAssertions.requireSatisfied(result))
			.isInstanceOfSatisfying(RequirementAssertionError.class, error -> {
				assertThat(error.result()).isSameAs(result);
				assertThat(error.getMessage()).contains("violated");
			});
	}

	@Test
	void repeatedAssertionsAndReportsDoNotExecuteAgain() {
		var calls = new AtomicInteger();
		var policies = new AtomicInteger();
		io.github.markpollack.judge.construction.JudgeRecipe<String, String> judge = io.github.markpollack.judge.assertions.ConfiguredRules
			.rule((r, e) -> {
				calls.incrementAndGet();
				assertThat(r).isSameAs(READY);
				return CHECK.requirement(r).evidence(e).build().judge();
			});
		var originalDecision = new PolicyDecision(PolicyAction.RELY, "retain original");
		var result = ConfiguredRules.evaluate(READY, judge, "READY", v -> {
			policies.incrementAndGet();
			return originalDecision;
		});
		for (int i = 0; i < 3; i++) {
			RequirementAssertions.requireSatisfied(result);
			VerdictReport.of(result.verdict()).summary();
		}
		assertThat(((PolicyResult.Decided) result.policyResult()).decision()).isSameAs(originalDecision);
		assertThat(calls).hasValue(1);
		assertThat(policies).hasValue(1);
	}

	@Test
	void policyExceptionAndNullReturnCannotEstablishSatisfaction() {
		var original = new IllegalStateException("backend unavailable");
		for (Policy policy : List.<Policy>of(v -> {
			throw original;
		}, v -> null)) {
			var result = ConfiguredRules.evaluate(READY, CHECK, "READY", policy);
			assertThat(result.verdict().conclusion()).isEqualTo(Verdict.Conclusion.PASS);
			assertThatThrownBy(() -> RequirementAssertions.requireSatisfied(result))
				.isInstanceOfSatisfying(RequirementAssertionError.class, error -> assertThat(error.getCause())
					.isSameAs(((PolicyResult.Failed) result.policyResult()).cause()));
		}
	}

	@Test
	void ordinaryEvidenceEvaluationHasNoFabricatedRequirement() {
		var result = Evaluations.evaluate((Judge) () -> Judgment.pass("checked"));
		assertThat(result.verdict().requirement()).isNull();
		assertThatThrownBy(() -> RequirementAssertions.requireSatisfied(result))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void missingConfigurationMakesZeroCalls() {
		var calls = new AtomicInteger();
		io.github.markpollack.judge.construction.JudgeRecipe<String, String> judge = io.github.markpollack.judge.assertions.ConfiguredRules
			.rule((r, e) -> {
				calls.incrementAndGet();
				return Judgment.pass("yes");
			});
		assertThatNullPointerException().isThrownBy(() -> ConfiguredRules.evaluate(null, judge, "x"));
		assertThatNullPointerException().isThrownBy(() -> ConfiguredRules.evaluate(READY, judge, "x", null));
		assertThat(calls).hasValue(0);
	}

	@Test
	void concurrentInvocationsRetainTheirOwnActualInput() throws Exception {
		try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
			var results = new ArrayList<java.util.concurrent.Future<EvaluationResult>>();
			for (int i = 0; i < 30; i++) {
				String value = "value-" + i;
				results.add(executor
					.submit(() -> ConfiguredRules.evaluate(Requirement.text(value, "1", value), CHECK, value)));
			}
			for (var future : results)
				RequirementAssertions.requireSatisfied(future.get());
		}
	}

	@Test
	void cancellationAndFatalErrorsEscapeWithoutCompletedResult() {
		var calls = new AtomicInteger();
		try {
			Thread.currentThread().interrupt();
			assertThatThrownBy(() -> ConfiguredRules.evaluate(READY, CHECK, "READY", v -> {
				calls.incrementAndGet();
				return new PolicyDecision(PolicyAction.RELY, "yes");
			})).isInstanceOf(java.util.concurrent.CancellationException.class);
		}
		finally {
			Thread.interrupted();
		}
		assertThat(calls).hasValue(0);
		assertThatThrownBy(() -> ConfiguredRules.evaluate(READY, ConfiguredRules.<String, String>rule((r, e) -> {
			throw new AssertionError("fatal");
		}), "x")).isInstanceOf(AssertionError.class).hasMessage("fatal");
	}

}
