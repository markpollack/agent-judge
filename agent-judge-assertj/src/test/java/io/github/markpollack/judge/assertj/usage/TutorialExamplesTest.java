/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.assertj.usage;

import io.github.markpollack.judge.acceptance.Policies;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.PolicyJudges;
import io.github.markpollack.judge.assertj.Assertions;
import io.github.markpollack.judge.assertions.AssertionResult;
import io.github.markpollack.judge.assertions.RequirementAssertions;
import io.github.markpollack.judge.assertions.RequirementAssertionError;
import io.github.markpollack.judge.jury.AllMustPassStrategy;
import io.github.markpollack.judge.jury.CascadedJury;
import io.github.markpollack.judge.jury.SimpleJury;
import io.github.markpollack.judge.jury.TierPolicy;
import io.github.markpollack.judge.jury.interpretation.RequirementOutcome;
import io.github.markpollack.judge.requirement.Requirement;
import io.github.markpollack.judge.requirement.RequirementEvidence;
import io.github.markpollack.judge.acceptance.AcceptanceDecision;
import io.github.markpollack.judge.acceptance.AcceptanceAction;
import io.github.markpollack.judge.acceptance.AcceptancePolicy;
import io.github.markpollack.judge.acceptance.AppliedPolicy;
import io.github.markpollack.judge.provenance.ArtifactRef;
import io.github.markpollack.judge.judgment.Judgment;
import io.github.markpollack.judge.provenance.PolicyRef;
import org.junit.jupiter.api.Test;

import static io.github.markpollack.judge.assertj.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Executable counterparts of the module README's local, credential-free examples. */
class TutorialExamplesTest {

	private static final Requirement<String> READY_REQUIREMENT = Requirement.text("response-ready", "1",
			"The response is exactly READY");

	private static final Judge<RequirementEvidence<String, String>> READY_RESPONSE_JUDGE = input -> "READY"
		.equals(input.evidence()) ? Judgment.pass("Response is exactly READY")
				: Judgment.fail("Response differs from READY");

	@Test
	void helloWorldNeedsNoPolicyBoilerplate() {
		assertThat(READY_REQUIREMENT).judgedByRequirement(READY_RESPONSE_JUDGE).withEvidence("READY").isSatisfied();
	}

	@Test
	void explicitApplicationEscalationRetainsTheAcceptedDetermination() {
		AcceptancePolicy criticalPolicy = policy("critical-response",
				"always ESCALATE; require independent confirmation",
				raw -> new AcceptanceDecision(AcceptanceAction.ESCALATE,
						"Independent confirmation is required before acting"));

		AssertionError error = assertThrows(AssertionError.class,
				() -> assertThat(READY_REQUIREMENT).judgedByRequirement(READY_RESPONSE_JUDGE)
					.withEvidence("READY")
					.withAcceptancePolicy(criticalPolicy)
					.isSatisfied());

		var inconclusive = assertInstanceOf(RequirementAssertionError.Inconclusive.class, error.getCause());
		assertEquals(RequirementOutcome.SATISFIED, inconclusive.result().interpretation().outcome());
		assertEquals(AssertionResult.PolicySource.EXPLICIT, inconclusive.result().policySource());
		var finalApplication = assertInstanceOf(AppliedPolicy.class,
				inconclusive.result().acceptanceExecution().application());
		assertEquals(AcceptanceAction.ESCALATE, finalApplication.action());
	}

	@Test
	void applicationConfigurationProvidesTheDefault() {
		AcceptancePolicy applicationDefault = policy("local-response", "always RELY; exact response",
				raw -> new AcceptanceDecision(AcceptanceAction.RELY, "Use the deterministic response finding"));
		var applicationAssertions = Assertions.using(new RequirementAssertions(applicationDefault));

		applicationAssertions.assertThat(READY_REQUIREMENT)
			.judgedByRequirement(READY_RESPONSE_JUDGE)
			.withEvidence("READY")
			.isSatisfied();
	}

	@Test
	void finalEscalationDoesNotEnterAnotherJuryTier() {
		AcceptancePolicy internalUse = policy("response-tier", "always RELY; first tier",
				raw -> new AcceptanceDecision(AcceptanceAction.RELY, "Use this tier's finding"));
		AcceptancePolicy fallbackWithhold = policy("fallback-tier", "always ABSTAIN; require manual confirmation",
				raw -> new AcceptanceDecision(AcceptanceAction.ABSTAIN, "Fallback requires manual confirmation"));
		AcceptancePolicy finalEscalation = policy("application-follow-up", "always ESCALATE; application follow-up",
				raw -> new AcceptanceDecision(AcceptanceAction.ESCALATE, "Application follow-up is required"));
		AtomicInteger fallbackCalls = new AtomicInteger();
		Judge<RequirementEvidence<String, String>> fallback = input -> {
			fallbackCalls.incrementAndGet();
			return Judgment.pass("Fallback finding");
		};
		var firstTier = SimpleJury.<RequirementEvidence<String, String>>builder()
			.judge(PolicyJudges.apply(READY_RESPONSE_JUDGE, Policies.referenceOf(internalUse), internalUse))
			.votingStrategy(new AllMustPassStrategy())
			.parallel(false)
			.build();
		var fallbackTier = SimpleJury.<RequirementEvidence<String, String>>builder()
			.judge(PolicyJudges.apply(fallback, Policies.referenceOf(fallbackWithhold), fallbackWithhold))
			.votingStrategy(new AllMustPassStrategy())
			.parallel(false)
			.build();
		var jury = CascadedJury.<RequirementEvidence<String, String>>builder()
			.tier("response", firstTier, TierPolicy.STOP_ON_RELIED_JUDGMENT)
			.tier("fallback", fallbackTier, TierPolicy.FINAL_TIER)
			.build();

		AssertionResult result = RequirementAssertions.relyingOnJudgment()
			.evaluateRequirement(READY_REQUIREMENT, jury, "READY", finalEscalation);

		assertEquals(RequirementOutcome.SATISFIED, result.interpretation().outcome());
		assertEquals(1, result.verdict().compositeAttempts().size());
		assertEquals(0, fallbackCalls.get());
		var internalApplication = assertInstanceOf(AppliedPolicy.class,
				result.verdict().individual().getFirst().policyApplication());
		assertEquals(Policies.referenceOf(internalUse), internalApplication.policy());
		assertThrows(RequirementAssertionError.Inconclusive.class,
				() -> RequirementAssertions.requireSatisfied(result));
		assertEquals(0, fallbackCalls.get());
	}

	@Test
	void retainedResultsCanBeAssertedRepeatedlyWithoutEvaluationOrPolicyCalls() {
		AtomicInteger judgeCalls = new AtomicInteger();
		AtomicInteger policyCalls = new AtomicInteger();
		Judge<RequirementEvidence<String, String>> judge = input -> {
			assertSame(READY_REQUIREMENT, input.requirement());
			judgeCalls.incrementAndGet();
			return READY_RESPONSE_JUDGE.judge(input);
		};
		AcceptancePolicy applicationDefault = policy("retained-response", "always RELY; retained finding", raw -> {
			policyCalls.incrementAndGet();
			return new AcceptanceDecision(AcceptanceAction.RELY, "Use the retained finding");
		});
		AssertionResult result = new RequirementAssertions(applicationDefault).evaluateRequirement(READY_REQUIREMENT,
				judge, "READY", null);

		RequirementAssertions.requireSatisfied(result);
		RequirementAssertions.requireSatisfied(result);
		assertEquals(1, judgeCalls.get());
		assertEquals(1, policyCalls.get());
	}

	private static AcceptancePolicy policy(String id, String configuration, AcceptancePolicy policy) {
		String digest = ArtifactRef.ofBytes("policy", configuration.getBytes(StandardCharsets.UTF_8), null).sha256();
		return Policies.recorded(new PolicyRef(id, "1", digest), policy);
	}

}
