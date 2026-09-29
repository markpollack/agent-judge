/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.assertions.usage;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.assertions.AssertionResult;
import io.github.markpollack.judge.assertions.PolicyBinding;
import io.github.markpollack.judge.assertions.Requirement;
import io.github.markpollack.judge.assertions.SemanticAssertionError;
import io.github.markpollack.judge.assertions.SemanticAssertions;
import io.github.markpollack.judge.context.JudgmentContext;
import io.github.markpollack.judge.jury.Verdict;
import io.github.markpollack.judge.jury.interpretation.ReadingSupport;
import io.github.markpollack.judge.jury.interpretation.VerdictReading;
import io.github.markpollack.judge.result.Acceptance;
import io.github.markpollack.judge.result.AcceptanceAction;
import io.github.markpollack.judge.result.AcceptancePolicy;
import io.github.markpollack.judge.result.AppliedPolicy;
import io.github.markpollack.judge.result.ArtifactRef;
import io.github.markpollack.judge.result.Judgment;
import io.github.markpollack.judge.result.JudgmentStatus;
import io.github.markpollack.judge.result.Policies;
import io.github.markpollack.judge.result.PolicyRef;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Executable caller examples: this package has no access to package-private helpers. */
class SemanticAssertionUsageTest {

	private static final Requirement READY = new Requirement("response-ready", "1", "The response is exactly READY");

	private final AtomicInteger routeCalls = new AtomicInteger();

	private final AtomicInteger judgeCalls = new AtomicInteger();

	private final AtomicInteger defaultPolicyCalls = new AtomicInteger();

	private final AtomicInteger criticalPolicyCalls = new AtomicInteger();

	private final PolicyBinding defaultPolicy = policy("local-response", "use exact-response finding", raw -> {
		defaultPolicyCalls.incrementAndGet();
		return new Acceptance(AcceptanceAction.USE_ASSESSMENT, "Use the deterministic exact-response finding");
	});

	private final PolicyBinding criticalPolicy = policy("critical-response", "require independent confirmation",
			raw -> {
				criticalPolicyCalls.incrementAndGet();
				return new Acceptance(AcceptanceAction.ESCALATE,
						"Independent confirmation is required before acting; none is retained");
			});

	private final Judge judge = context -> {
		judgeCalls.incrementAndGet();
		return "READY".equals(context.agentOutput().orElse("")) ? Judgment.pass("Response is exactly READY")
				: Judgment.fail("Response differs from READY");
	};

	private final SemanticAssertions assertions = new SemanticAssertions(requirement -> {
		routeCalls.incrementAndGet();
		if (!READY.text().equals(requirement.text()))
			throw new IllegalArgumentException("No judge configured for this requirement");
		return judge;
	}, defaultPolicy);

	@Test
	void defaultPolicySatisfiesNamedRequirementEagerly() {
		assertions.assertThat(evidence("READY")).satisfies(READY);
		assertCalls(1, 1, 1, 0);
	}

	@Test
	void stringRequirementUsesTheConfiguredDefaultPolicy() {
		assertions.assertThat(evidence("READY")).satisfies("The response is exactly READY");
		assertCalls(1, 1, 1, 0);
	}

	@Test
	void evaluateThenAssertRetainsOneEvaluation() {
		AssertionResult result = assertions.evaluate(evidence("READY"), READY);
		Verdict retainedVerdict = result.verdict();
		var retainedReading = result.interpretation();
		assertCalls(1, 1, 1, 0);

		SemanticAssertions.requireSatisfied(result);
		SemanticAssertions.requireSatisfied(result);

		assertCalls(1, 1, 1, 0);
		assertSame(retainedVerdict, result.verdict());
		assertSame(retainedReading, result.interpretation());
		assertEquals(ReadingSupport.SUPPORTED, result.interpretation().readingSupport());
		assertEquals(VerdictReading.ACCEPTED, result.interpretation().reading());
	}

	@Test
	void acceptingANegativeFindingRejectsTheSubject() {
		var error = assertThrows(SemanticAssertionError.Rejected.class,
				() -> assertions.assertThat(evidence("NOT READY")).satisfies(READY));
		AssertionResult result = error.result();
		assertEquals(JudgmentStatus.FAIL, result.verdict().aggregated().producerStatus());
		assertEquals(JudgmentStatus.FAIL, result.verdict().aggregated().status());
		assertEquals(AcceptanceAction.USE_ASSESSMENT, applied(result).action());
		assertEquals(VerdictReading.REJECTED, result.interpretation().reading());

		var repeated = assertThrows(SemanticAssertionError.Rejected.class,
				() -> SemanticAssertions.requireSatisfied(result));
		assertSame(result, repeated.result());
		assertCalls(1, 1, 1, 0);
	}

	@Test
	void namedCriticalPolicyRequestsEscalationWithoutChangingTheFinding() {
		Requirement criticalRequirement = READY.under(criticalPolicy);
		var error = assertThrows(SemanticAssertionError.Inconclusive.class,
				() -> assertions.assertThat(evidence("READY")).satisfies(criticalRequirement));
		AssertionResult result = error.result();

		assertNull(READY.acceptancePolicy());
		assertEquals(AssertionResult.PolicySource.REQUIREMENT, result.policySource());
		assertEquals(criticalPolicy.reference(), result.policy());
		assertEquals(JudgmentStatus.PASS, result.verdict().aggregated().producerStatus());
		assertEquals(JudgmentStatus.ABSTAIN, result.verdict().aggregated().status());
		assertEquals(AcceptanceAction.ESCALATE, applied(result).action());
		assertEquals(VerdictReading.UNDECIDED, result.interpretation().reading());
		assertEquals(0, result.verdict().compositeAttempts().size());

		var repeated = assertThrows(SemanticAssertionError.Inconclusive.class,
				() -> SemanticAssertions.requireSatisfied(result));
		assertSame(result, repeated.result());
		assertCalls(1, 1, 0, 1);
	}

	@Test
	void aNewPolicyOverTheRetainedFindingLeavesTheOriginalResultUntouched() {
		AssertionResult original = assertions.evaluate(evidence("READY"), READY);
		Verdict originalVerdict = original.verdict();
		var originalReading = original.interpretation();
		Judgment originalJudgment = originalVerdict.aggregated();

		Judgment withheld = Policies.apply(originalJudgment, criticalPolicy.reference(), criticalPolicy.policy());
		AssertionResult reconsidered = new AssertionResult(READY.under(criticalPolicy), criticalPolicy.reference(),
				AssertionResult.PolicySource.REQUIREMENT, Verdict.single("retained-response", withheld));
		assertCalls(1, 1, 1, 1);

		SemanticAssertions.requireSatisfied(original);
		var error = assertThrows(SemanticAssertionError.Inconclusive.class,
				() -> SemanticAssertions.requireSatisfied(reconsidered));

		assertCalls(1, 1, 1, 1);
		assertSame(originalVerdict, original.verdict());
		assertSame(originalReading, original.interpretation());
		assertSame(reconsidered, error.result());
		assertEquals(AcceptanceAction.USE_ASSESSMENT, applied(original).action());
		assertEquals(AcceptanceAction.ESCALATE, applied(reconsidered).action());
		assertEquals(JudgmentStatus.PASS, originalJudgment.status());
		assertEquals(JudgmentStatus.PASS, withheld.producerStatus());
		assertEquals(JudgmentStatus.ABSTAIN, withheld.status());
		assertEquals(originalJudgment.reasoning(), withheld.reasoning());
	}

	private static JudgmentContext evidence(String response) {
		return JudgmentContext.builder().goal(READY.text()).agentOutput(response).build();
	}

	private static PolicyBinding policy(String id, String configuration, AcceptancePolicy decision) {
		String digest = ArtifactRef.ofBytes(id, configuration.getBytes(StandardCharsets.UTF_8), null).sha256();
		return new PolicyBinding(new PolicyRef(id, "1", digest), decision);
	}

	private static AppliedPolicy applied(AssertionResult result) {
		return assertInstanceOf(AppliedPolicy.class, result.verdict().aggregated().policyApplication());
	}

	private void assertCalls(int routes, int judges, int defaults, int critical) {
		assertEquals(routes, routeCalls.get(), "route calls");
		assertEquals(judges, judgeCalls.get(), "judge calls");
		assertEquals(defaults, defaultPolicyCalls.get(), "default policy calls");
		assertEquals(critical, criticalPolicyCalls.get(), "critical policy calls");
	}

}
