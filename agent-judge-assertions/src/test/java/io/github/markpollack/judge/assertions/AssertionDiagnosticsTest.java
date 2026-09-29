/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.assertions;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.markpollack.judge.context.JudgmentContext;
import io.github.markpollack.judge.jury.Verdict;
import io.github.markpollack.judge.jury.interpretation.ReadingSupport;
import io.github.markpollack.judge.result.Acceptance;
import io.github.markpollack.judge.result.AcceptanceAction;
import io.github.markpollack.judge.result.AppliedPolicy;
import io.github.markpollack.judge.result.ArtifactRef;
import io.github.markpollack.judge.result.Assessment;
import io.github.markpollack.judge.result.AssessmentTarget;
import io.github.markpollack.judge.result.Category;
import io.github.markpollack.judge.result.Certainty;
import io.github.markpollack.judge.result.Distribution;
import io.github.markpollack.judge.result.EvaluationProvenance;
import io.github.markpollack.judge.result.Judgment;
import io.github.markpollack.judge.result.JudgmentReasonCode;
import io.github.markpollack.judge.result.JudgmentStatus;
import io.github.markpollack.judge.result.Policies;
import io.github.markpollack.judge.result.PolicyRef;
import io.github.markpollack.judge.result.ProbabilityMass;
import io.github.markpollack.judge.result.Proposition;
import io.github.markpollack.judge.result.SupportOrigin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class AssertionDiagnosticsTest {

	private static final Requirement LOCKS = new Requirement("lock-order", "1", "Locks follow the required order");

	private static final PolicyRef POLICY = new PolicyRef("independent-confirmation", "1", "a".repeat(64));

	private static final ArtifactRef RESPONSE = ArtifactRef.ofBytes("retained-response",
			"fixture, not a provider response".getBytes(StandardCharsets.UTF_8), null);

	private static final EvaluationProvenance PROVENANCE = new EvaluationProvenance("local-fixture", "1",
			"b".repeat(64), List.of(RESPONSE), RESPONSE, List.of());

	@ParameterizedTest
	@EnumSource(value = AcceptanceAction.class, names = { "ABSTAIN", "ESCALATE" })
	void withheldPositivePreservesEveryProducerFieldAndExplainsTheDecision(AcceptanceAction action) throws Exception {
		Judgment raw = choice(JudgmentStatus.PASS, "satisfied");
		ObjectMapper json = new ObjectMapper();
		byte[] before = json.writeValueAsBytes(raw);
		var binding = new PolicyBinding(POLICY,
				j -> new Acceptance(action, "Independent confirmation required for this high-consequence property"));
		AssertionResult result = new SemanticAssertions(r -> c -> raw, binding)
			.evaluate(JudgmentContext.builder().goal(LOCKS.text()).build(), LOCKS);
		var error = catchThrowableOfType(() -> SemanticAssertions.requireSatisfied(result),
				SemanticAssertionError.Inconclusive.class);
		Judgment withheld = result.verdict().aggregated();
		assertThat(withheld).usingRecursiveComparison().ignoringFields("policyApplication").isEqualTo(raw);
		assertThat(withheld.status()).isEqualTo(JudgmentStatus.ABSTAIN);
		assertThat(((AppliedPolicy) withheld.policyApplication()).action()).isEqualTo(action);
		assertThat(json.writeValueAsBytes(raw)).isEqualTo(before);
		assertThat(error.result()).isSameAs(result);
		assertThat(error.getMessage()).contains("INCONCLUSIVE", LOCKS.text(), "producer=PASS", "category=satisfied",
				"jev.choice.confidence:v1=0.57", "jev.choice.distribution:v1", "p(satisfied)=0.71",
				"independent-confirmation@1", action.name(), "original assessment unchanged",
				"Operational result: ABSTAIN", "Interpretation: UNDECIDED", "reading support=SUPPORTED",
				"structural, not model confidence", "Response: retained-response");
		assertThat(error.getMessage().length()).isLessThan(1600);
		if (action == AcceptanceAction.ESCALATE) {
			assertThat(error.getMessage()).contains("escalation requested", "caller must act");
		}
	}

	@Test
	void nativeInsufficiencyRemainsAnAssessmentAndAnInconclusiveFailure() {
		var result = result(choice(JudgmentStatus.ABSTAIN, "insufficient_evidence"), AcceptanceAction.USE_ASSESSMENT);
		var error = catchThrowableOfType(() -> SemanticAssertions.requireSatisfied(result),
				SemanticAssertionError.Inconclusive.class);
		assertThat(error.getMessage()).contains("producer=ABSTAIN", "category=insufficient_evidence", "USE_ASSESSMENT",
				"Interpretation: UNDECIDED", "Declared projection has no supported determination");
		assertThat(error.getMessage()).doesNotContain("Policy: FAILED", "producer=FAIL");
	}

	@Test
	void protocolErrorHasNoSuccessfulNativeSupportAndRetainsInvalidResponseReference() {
		Judgment raw = new Judgment(JudgmentStatus.ERROR, null, null, null, JudgmentReasonCode.JUDGE_REPORTED,
				"Jev request failed or returned invalid protocol", List.of(), PROVENANCE, null,
				Map.of("invalidNativeConfidence", 0.25));
		var result = result(raw, AcceptanceAction.USE_ASSESSMENT);
		var error = catchThrowableOfType(() -> SemanticAssertions.requireSatisfied(result),
				SemanticAssertionError.InstrumentFailure.class);
		assertThat(error.getMessage()).contains("producer=ERROR; no assessment", "Support: none (producer ERROR)",
				"no application (producer ERROR bypasses policy)", "Operational result: ERROR",
				"Interpretation: NOT_ASSESSED", "invalid protocol", "Response: retained-response");
		assertThat(error.getMessage()).doesNotContain("0.25", "jev.choice.confidence", "USE_ASSESSMENT");
		assertThat(error.result().verdict().aggregated().metadata()).containsEntry("invalidNativeConfidence", 0.25);
	}

	@Test
	void policyFailureKeepsTheValidProducerAssessmentAndSupportVisible() {
		Judgment raw = choice(JudgmentStatus.PASS, "satisfied");
		Judgment failed = Policies.apply(raw, POLICY, j -> {
			throw new IllegalStateException("policy unavailable");
		});
		var result = new AssertionResult(LOCKS, POLICY, AssertionResult.PolicySource.DEFAULT,
				Verdict.single("retained", failed));
		var error = catchThrowableOfType(() -> SemanticAssertions.requireSatisfied(result),
				SemanticAssertionError.InstrumentFailure.class);
		assertThat(error.getMessage()).contains("producer=PASS", "category=satisfied", "jev.choice.confidence:v1=0.57",
				"FAILED; producer assessment retained", "policy unavailable", "Operational result: ERROR");
		assertThat(error.getMessage()).doesNotContain("producer ERROR bypasses", "Support: none");
	}

	@Test
	void noulProbabilityDoesNotAcquireChoiceConfidence() {
		Judgment raw = new Judgment(JudgmentStatus.PASS, new Assessment(new Proposition(true), null, null), null,
				new Distribution(AssessmentTarget.PROPOSITION, "jev.noul.probability-of-true:v1",
						List.of(new ProbabilityMass("false", 0.36), new ProbabilityMass("true", 0.64))),
				null, "", List.of(), PROVENANCE, null, Map.of());
		var error = catchThrowableOfType(
				() -> SemanticAssertions.requireSatisfied(result(raw, AcceptanceAction.ABSTAIN)),
				SemanticAssertionError.Inconclusive.class);
		assertThat(error.getMessage()).contains("proposition=true", "jev.noul.probability-of-true:v1", "p(true)=0.64");
		assertThat(error.getMessage()).doesNotContain("jev.choice", "confidence=", "certainty=");
	}

	@Test
	void hugeFieldsAndEvidenceDoNotDominateThePrimaryDiagnostic() {
		String huge = "long-value\n".repeat(2000);
		var requirement = new Requirement(huge, huge, huge);
		var provenance = new EvaluationProvenance(huge, huge, "b".repeat(64), List.of(RESPONSE), RESPONSE, List.of());
		Judgment raw = new Judgment(JudgmentStatus.ERROR, null, null, null, JudgmentReasonCode.JUDGE_REPORTED, huge,
				List.of(), provenance, null, Map.of("payload", "EVIDENCE_BODY_MUST_NOT_BE_DUMPED"));
		var result = new AssertionResult(requirement, POLICY, AssertionResult.PolicySource.DEFAULT,
				Verdict.single("one", raw));
		var error = catchThrowableOfType(() -> SemanticAssertions.requireSatisfied(result),
				SemanticAssertionError.InstrumentFailure.class);
		assertThat(error.getMessage().length()).isLessThan(1800);
		assertThat(error.getMessage().lines().count()).isLessThanOrEqualTo(10);
		assertThat(error.getMessage()).contains("…", "Interpretation: NOT_ASSESSED");
		assertThat(error.getMessage()).doesNotContain("EVIDENCE_BODY_MUST_NOT_BE_DUMPED");
		assertThat(error.result().requirement().text()).isEqualTo(huge);
		assertThat(error.interpretation().summary().length()).isGreaterThan(10000);
	}

	@Test
	void reopenedUnsupportedVerdictReachesUnsupportedDiagnostic() throws Exception {
		Verdict incomplete = Verdict.of(Judgment.pass("unjustified"), Map.of("one", Judgment.fail("violated")));
		var json = new ObjectMapper();
		Verdict reopened = json.readValue(json.writeValueAsBytes(incomplete), Verdict.class);
		var result = new AssertionResult(LOCKS, POLICY, AssertionResult.PolicySource.DEFAULT, reopened);
		assertThat(result.interpretation().readingSupport()).isNotEqualTo(ReadingSupport.SUPPORTED);
		assertThatThrownBy(() -> SemanticAssertions.requireSatisfied(result))
			.isInstanceOf(SemanticAssertionError.UnsupportedReading.class)
			.hasMessageContaining("UNSUPPORTED_READING")
			.hasMessageContaining("Reading defect:");
	}

	private static AssertionResult result(Judgment raw, AcceptanceAction action) {
		Judgment applied = Policies.apply(raw, POLICY, j -> new Acceptance(action, "Explicit fixture policy"));
		return new AssertionResult(LOCKS, POLICY, AssertionResult.PolicySource.DEFAULT,
				Verdict.single("retained", applied));
	}

	private static Judgment choice(JudgmentStatus status, String selected) {
		List<String> alternatives = List.of("satisfied", "violated", "insufficient_evidence");
		boolean insufficient = status == JudgmentStatus.ABSTAIN;
		return new Judgment(status, new Assessment(null, null, new Category(selected, alternatives)),
				new Certainty(insufficient ? 0.61 : 0.57, "jev.choice.confidence:v1", SupportOrigin.REPORTED,
						AssessmentTarget.CATEGORY, null),
				new Distribution(AssessmentTarget.CATEGORY, "jev.choice.distribution:v1",
						List.of(new ProbabilityMass("satisfied", insufficient ? 0.02 : 0.71),
								new ProbabilityMass("violated", insufficient ? 0.23 : 0.27),
								new ProbabilityMass("insufficient_evidence", insufficient ? 0.75 : 0.02))),
				null, status == JudgmentStatus.ABSTAIN ? "Declared projection has no supported determination" : "",
				List.of(), PROVENANCE, null, Map.of("retained", List.of("original")));
	}

}
