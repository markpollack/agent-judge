/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.assertions.usage;

import io.github.markpollack.judge.acceptance.Policies;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.assertions.AssertionResult;
import io.github.markpollack.judge.acceptance.AcceptancePolicy;
import io.github.markpollack.judge.requirement.Requirement;
import io.github.markpollack.judge.assertions.RequirementAssertionError;
import io.github.markpollack.judge.assertions.RequirementAssertions;
import io.github.markpollack.judge.completion.CompletionEvidence;
import io.github.markpollack.judge.jury.Verdict;
import io.github.markpollack.judge.jury.interpretation.ReadingSupport;
import io.github.markpollack.judge.jury.interpretation.RequirementOutcome;
import io.github.markpollack.judge.acceptance.AcceptanceDecision;
import io.github.markpollack.judge.acceptance.AcceptanceAction;
import io.github.markpollack.judge.acceptance.AppliedPolicy;
import io.github.markpollack.judge.provenance.ArtifactRef;
import io.github.markpollack.judge.judgment.Judgment;
import io.github.markpollack.judge.judgment.JudgmentStatus;
import io.github.markpollack.judge.provenance.PolicyRef;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Executable caller examples: this package has no access to package-private helpers. */
class RequirementAssertionUsageTest {

	private static final Requirement<?> READY_REQUIREMENT = Requirement.text("response-ready", "1",
			"The response is exactly READY");

	private final AtomicInteger judgeCalls = new AtomicInteger();

	private final AtomicInteger defaultPolicyCalls = new AtomicInteger();

	private final AtomicInteger criticalPolicyCalls = new AtomicInteger();

	private final AcceptancePolicy defaultPolicy = policy("local-response", "use exact-response finding", raw -> {
		defaultPolicyCalls.incrementAndGet();
		return new AcceptanceDecision(AcceptanceAction.RELY, "Use the deterministic exact-response finding");
	});

	private final AcceptancePolicy criticalPolicy = policy("critical-response", "require independent confirmation",
			raw -> {
				criticalPolicyCalls.incrementAndGet();
				return new AcceptanceDecision(AcceptanceAction.ESCALATE,
						"Independent confirmation is required before acting; none is retained");
			});

	private final Judge<CompletionEvidence> judge = context -> {
		judgeCalls.incrementAndGet();
		return "READY".equals(java.util.Optional.ofNullable(context.response()).orElse(""))
				? Judgment.pass("Response is exactly READY") : Judgment.fail("Response differs from READY");
	};

	private final RequirementAssertions assertions = new RequirementAssertions(defaultPolicy);

	@Test
	void evaluateThenAssertRetainsOneEvaluation() {
		AssertionResult result = assertions.evaluate(READY_REQUIREMENT, judge, evidence("READY"), null);
		Verdict retainedVerdict = result.verdict();
		var retainedReading = result.interpretation();
		assertCalls(1, 1, 1, 0);

		RequirementAssertions.requireSatisfied(result);
		RequirementAssertions.requireSatisfied(result);

		assertCalls(1, 1, 1, 0);
		assertSame(retainedVerdict, result.verdict());
		assertSame(retainedReading, result.interpretation());
		assertEquals(ReadingSupport.SUPPORTED, result.interpretation().readingSupport());
		assertEquals(RequirementOutcome.SATISFIED, result.interpretation().outcome());
	}

	@Test
	void acceptingANegativeFindingRejectsTheSubject() {
		var error = assertThrows(RequirementAssertionError.Rejected.class, () -> RequirementAssertions
			.requireSatisfied(assertions.evaluate(READY_REQUIREMENT, judge, evidence("NOT READY"), null)));
		AssertionResult result = error.result();
		assertEquals(JudgmentStatus.FAIL, result.verdict().judgment().producerStatus());
		assertEquals(JudgmentStatus.FAIL, result.verdict().judgment().status());
		assertEquals(AcceptanceAction.RELY, applied(result).action());
		assertEquals(RequirementOutcome.VIOLATED, result.interpretation().outcome());

		var repeated = assertThrows(RequirementAssertionError.Rejected.class,
				() -> RequirementAssertions.requireSatisfied(result));
		assertSame(result, repeated.result());
		assertCalls(1, 1, 1, 0);
	}

	@Test
	void namedCriticalPolicyRequestsEscalationWithoutChangingTheFinding() {
		Requirement<?> criticalRequirement = READY_REQUIREMENT;
		var error = assertThrows(RequirementAssertionError.Inconclusive.class, () -> RequirementAssertions
			.requireSatisfied(assertions.evaluate(criticalRequirement, judge, evidence("READY"), criticalPolicy)));
		AssertionResult result = error.result();

		assertEquals(AssertionResult.PolicySource.EXPLICIT, result.policySource());
		assertEquals(Policies.referenceOf(criticalPolicy), result.policy());
		assertEquals(JudgmentStatus.PASS, result.verdict().judgment().producerStatus());
		assertEquals(JudgmentStatus.PASS, result.verdict().judgment().status());
		assertEquals(AcceptanceAction.ESCALATE, applied(result).action());
		assertEquals(RequirementOutcome.SATISFIED, result.interpretation().outcome());
		assertEquals(0, result.verdict().compositeAttempts().size());

		var repeated = assertThrows(RequirementAssertionError.Inconclusive.class,
				() -> RequirementAssertions.requireSatisfied(result));
		assertSame(result, repeated.result());
		assertCalls(1, 1, 0, 1);
	}

	@Test
	void aNewPolicyOverTheRetainedFindingLeavesTheOriginalResultUntouched() {
		AssertionResult original = assertions.evaluate(READY_REQUIREMENT, judge, evidence("READY"), null);
		Verdict originalVerdict = original.verdict();
		var originalReading = original.interpretation();
		Judgment originalJudgment = originalVerdict.judgment();

		AssertionResult reconsidered = AssertionResult.applyPolicy(READY_REQUIREMENT, criticalPolicy,
				AssertionResult.PolicySource.EXPLICIT, originalVerdict);
		assertCalls(1, 1, 1, 1);

		RequirementAssertions.requireSatisfied(original);
		var error = assertThrows(RequirementAssertionError.Inconclusive.class,
				() -> RequirementAssertions.requireSatisfied(reconsidered));

		assertCalls(1, 1, 1, 1);
		assertSame(originalVerdict, original.verdict());
		assertSame(originalReading, original.interpretation());
		assertSame(reconsidered, error.result());
		assertEquals(AcceptanceAction.RELY, applied(original).action());
		assertEquals(AcceptanceAction.ESCALATE, applied(reconsidered).action());
		assertEquals(JudgmentStatus.PASS, originalJudgment.status());
		assertSame(originalVerdict, reconsidered.verdict());
		assertEquals(originalReading, reconsidered.interpretation());
	}

	private static CompletionEvidence evidence(String response) {
		return CompletionEvidence.builder().request(READY_REQUIREMENT.text()).response(response).build();
	}

	private static AcceptancePolicy policy(String id, String configuration, AcceptancePolicy provenance) {
		String digest = ArtifactRef.ofBytes(id, configuration.getBytes(StandardCharsets.UTF_8), null).sha256();
		return Policies.recorded(new PolicyRef(id, "1", digest), provenance);
	}

	private static AppliedPolicy applied(AssertionResult result) {
		return assertInstanceOf(AppliedPolicy.class, result.acceptanceExecution().application());
	}

	private void assertCalls(int routes, int judges, int defaults, int critical) {
		assertEquals(judges, judgeCalls.get(), "judge calls");
		assertEquals(defaults, defaultPolicyCalls.get(), "default policy calls");
		assertEquals(critical, criticalPolicyCalls.get(), "critical policy calls");
	}

}
