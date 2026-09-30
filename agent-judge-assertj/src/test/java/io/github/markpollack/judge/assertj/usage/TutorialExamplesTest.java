/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.assertj.usage;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.PolicyJudges;
import io.github.markpollack.judge.assertj.Assertions;
import io.github.markpollack.judge.assertions.AssertionResult;
import io.github.markpollack.judge.assertions.RequirementAssertions;
import io.github.markpollack.judge.assertions.SemanticAssertionError;
import io.github.markpollack.judge.assertions.SemanticAssertions;
import io.github.markpollack.judge.jury.AllMustPassStrategy;
import io.github.markpollack.judge.jury.CascadedJury;
import io.github.markpollack.judge.jury.SimpleJury;
import io.github.markpollack.judge.jury.TierPolicy;
import io.github.markpollack.judge.jury.interpretation.VerdictReading;
import io.github.markpollack.judge.requirement.Requirement;
import io.github.markpollack.judge.requirement.RequirementEvidence;
import io.github.markpollack.judge.result.Acceptance;
import io.github.markpollack.judge.result.AcceptanceAction;
import io.github.markpollack.judge.result.AcceptancePolicy;
import io.github.markpollack.judge.result.AppliedPolicy;
import io.github.markpollack.judge.result.ArtifactRef;
import io.github.markpollack.judge.result.Judgment;
import io.github.markpollack.judge.result.PolicyBinding;
import io.github.markpollack.judge.result.PolicyRef;
import org.junit.jupiter.api.Test;

import static io.github.markpollack.judge.assertj.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Executable counterparts of the module README's local, credential-free examples. */
class TutorialExamplesTest {

	private static final Requirement<String> READY =
			Requirement.text("response-ready", "1", "The response is exactly READY");

	private static final Judge<RequirementEvidence<Requirement<String>, String>> EXACT_RESPONSE =
			input -> "READY".equals(input.evidence())
					? Judgment.pass("Response is exactly READY") : Judgment.fail("Response differs from READY");

	@Test
	void helloWorldNeedsNoPolicyBoilerplate() {
		assertThat(READY).judgedBy(EXACT_RESPONSE).withEvidence("READY").isSatisfied();
	}

	@Test
	void explicitApplicationEscalationRetainsTheAcceptedDetermination() {
		PolicyBinding criticalPolicy = policy("critical-response", "always ESCALATE; require independent confirmation", raw ->
				new Acceptance(AcceptanceAction.ESCALATE, "Independent confirmation is required before acting"));

		AssertionError error = assertThrows(AssertionError.class, () ->
				assertThat(READY).judgedBy(EXACT_RESPONSE).withEvidence("READY")
						.withAcceptancePolicy(criticalPolicy).isSatisfied());

		var inconclusive = assertInstanceOf(SemanticAssertionError.Inconclusive.class, error.getCause());
		assertEquals(VerdictReading.ACCEPTED, inconclusive.result().interpretation().reading());
		assertEquals(AssertionResult.PolicySource.EXPLICIT, inconclusive.result().policySource());
		var finalApplication = assertInstanceOf(AppliedPolicy.class,
				inconclusive.result().applicationDecision().application());
		assertEquals(AcceptanceAction.ESCALATE, finalApplication.action());
	}

	@Test
	void applicationConfigurationProvidesTheDefault() {
		PolicyBinding applicationDefault = policy("local-response", "always USE_ASSESSMENT; exact response", raw ->
				new Acceptance(AcceptanceAction.USE_ASSESSMENT, "Use the deterministic response finding"));
		var applicationAssertions = Assertions.using(new RequirementAssertions(applicationDefault));

		applicationAssertions.assertThat(READY).judgedBy(EXACT_RESPONSE).withEvidence("READY").isSatisfied();
	}

	@Test
	void finalEscalationDoesNotEnterAnotherJuryTier() {
		PolicyBinding internalUse = policy("response-tier", "always USE_ASSESSMENT; first tier", raw ->
				new Acceptance(AcceptanceAction.USE_ASSESSMENT, "Use this tier's finding"));
		PolicyBinding fallbackWithhold = policy("fallback-tier", "always ABSTAIN; require manual confirmation", raw ->
				new Acceptance(AcceptanceAction.ABSTAIN, "Fallback requires manual confirmation"));
		PolicyBinding finalEscalation = policy("application-follow-up", "always ESCALATE; application follow-up", raw ->
				new Acceptance(AcceptanceAction.ESCALATE, "Application follow-up is required"));
		AtomicInteger fallbackCalls = new AtomicInteger();
		Judge<RequirementEvidence<Requirement<String>, String>> fallback = input -> {
			fallbackCalls.incrementAndGet();
			return Judgment.pass("Fallback finding");
		};
		var firstTier = SimpleJury.<RequirementEvidence<Requirement<String>, String>>builder()
				.judge(PolicyJudges.apply(EXACT_RESPONSE, internalUse.reference(), internalUse.policy()))
				.votingStrategy(new AllMustPassStrategy()).parallel(false).build();
		var fallbackTier = SimpleJury.<RequirementEvidence<Requirement<String>, String>>builder()
				.judge(PolicyJudges.apply(fallback, fallbackWithhold.reference(), fallbackWithhold.policy()))
				.votingStrategy(new AllMustPassStrategy()).parallel(false).build();
		var jury = CascadedJury.<RequirementEvidence<Requirement<String>, String>>builder()
				.tier("response", firstTier, TierPolicy.STOP_ON_USABLE_ASSESSMENT)
				.tier("fallback", fallbackTier, TierPolicy.FINAL_TIER).build();

		AssertionResult result = RequirementAssertions.usingAssessment()
				.evaluate(READY, jury, "READY", finalEscalation);

		assertEquals(VerdictReading.ACCEPTED, result.interpretation().reading());
		assertEquals(1, result.verdict().compositeAttempts().size());
		assertEquals(0, fallbackCalls.get());
		var internalApplication = assertInstanceOf(AppliedPolicy.class,
				result.verdict().individual().getFirst().policyApplication());
		assertEquals(internalUse.reference(), internalApplication.policy());
		assertThrows(SemanticAssertionError.Inconclusive.class, () -> SemanticAssertions.requireSatisfied(result));
		assertEquals(0, fallbackCalls.get());
	}

	@Test
	void retainedResultsCanBeAssertedRepeatedlyWithoutEvaluationOrPolicyCalls() {
		AtomicInteger judgeCalls = new AtomicInteger();
		AtomicInteger policyCalls = new AtomicInteger();
		Judge<RequirementEvidence<Requirement<String>, String>> judge = input -> {
			assertSame(READY, input.requirement());
			judgeCalls.incrementAndGet();
			return EXACT_RESPONSE.judge(input);
		};
		PolicyBinding applicationDefault = policy("retained-response", "always USE_ASSESSMENT; retained finding", raw -> {
			policyCalls.incrementAndGet();
			return new Acceptance(AcceptanceAction.USE_ASSESSMENT, "Use the retained finding");
		});
		AssertionResult result = new RequirementAssertions(applicationDefault)
				.evaluate(READY, judge, "READY", null);

		SemanticAssertions.requireSatisfied(result);
		SemanticAssertions.requireSatisfied(result);
		assertEquals(1, judgeCalls.get());
		assertEquals(1, policyCalls.get());
	}

	private static PolicyBinding policy(String id, String configuration, AcceptancePolicy policy) {
		String digest = ArtifactRef.ofBytes("policy", configuration.getBytes(StandardCharsets.UTF_8), null).sha256();
		return new PolicyBinding(new PolicyRef(id, "1", digest), policy);
	}
}
