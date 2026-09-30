/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.assertions;

import io.github.markpollack.judge.requirement.Requirement;
import io.github.markpollack.judge.acceptance.AcceptancePolicy;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.markpollack.judge.jury.Verdict;
import io.github.markpollack.judge.jury.interpretation.ReadingSupport;
import io.github.markpollack.judge.acceptance.AcceptanceDecision;
import io.github.markpollack.judge.acceptance.AcceptanceAction;
import io.github.markpollack.judge.acceptance.AppliedPolicy;
import io.github.markpollack.judge.provenance.ArtifactRef;
import io.github.markpollack.judge.judgment.Finding;
import io.github.markpollack.judge.judgment.FindingTarget;
import io.github.markpollack.judge.judgment.CategoryFinding;
import io.github.markpollack.judge.judgment.Confidence;
import io.github.markpollack.judge.judgment.ProbabilityDistribution;
import io.github.markpollack.judge.provenance.Provenance;
import io.github.markpollack.judge.judgment.Judgment;
import io.github.markpollack.judge.judgment.JudgmentReasonCode;
import io.github.markpollack.judge.judgment.JudgmentStatus;
import io.github.markpollack.judge.acceptance.Policies;
import io.github.markpollack.judge.provenance.PolicyRef;
import io.github.markpollack.judge.judgment.ProbabilityMass;
import io.github.markpollack.judge.judgment.BooleanFinding;
import io.github.markpollack.judge.judgment.SupportOrigin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class AssertionDiagnosticsTest {

	private static final Requirement<?> LOCKS = Requirement.text("lock-order", "1", "Locks follow the required order");

	private static final PolicyRef POLICY = new PolicyRef("independent-confirmation", "1", "a".repeat(64));

	private static final ArtifactRef RESPONSE = ArtifactRef.ofBytes("retained-response",
			"fixture, not a provider response".getBytes(StandardCharsets.UTF_8), null);

	private static final Provenance PROVENANCE = new Provenance("local-fixture", "1", "b".repeat(64), List.of(RESPONSE),
			RESPONSE, List.of());

	@ParameterizedTest
	@EnumSource(value = AcceptanceAction.class, names = { "ABSTAIN", "ESCALATE" })
	void withheldPositivePreservesEveryProducerFieldAndExplainsTheDecision(AcceptanceAction action) throws Exception {
		Judgment raw = choice(JudgmentStatus.PASS, "satisfied");
		ObjectMapper json = new ObjectMapper();
		byte[] before = json.writeValueAsBytes(raw);
		var binding = Policies.recorded(POLICY, j -> new AcceptanceDecision(action,
				"Independent confirmation required for this high-consequence property"));
		AssertionResult result = new RequirementAssertions(binding).evaluate(LOCKS,
				(io.github.markpollack.judge.Judge<String>) evidence -> raw, "captured evidence", null);
		var error = catchThrowableOfType(() -> RequirementAssertions.requireSatisfied(result),
				RequirementAssertionError.Inconclusive.class);
		Judgment withheld = result.verdict().judgment();
		assertThat(withheld).isSameAs(raw);
		assertThat(withheld.status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(((AppliedPolicy) result.acceptanceExecution().application()).action()).isEqualTo(action);
		assertThat(json.writeValueAsBytes(raw)).isEqualTo(before);
		assertThat(error.result()).isSameAs(result);
		assertThat(error.getMessage()).contains("INCONCLUSIVE", LOCKS.text(), "producer=PASS", "category=satisfied",
				"jev.choice.confidence:v1=0.57", "jev.choice.distribution:v1", "p(satisfied)=0.71",
				"independent-confirmation@1", action.name(), "original judgment unchanged", "Operational result: PASS",
				"Interpretation: SATISFIED", "reading support=SUPPORTED", "structural, not model confidence",
				"Response: retained-response");
		assertThat(error.getMessage().length()).isLessThan(1600);
		if (action == AcceptanceAction.ESCALATE) {
			assertThat(error.getMessage()).contains("escalation requested", "caller must act");
		}
	}

	@Test
	void nativeInsufficiencyRemainsAnAssessmentAndAnInconclusiveFailure() {
		var result = result(choice(JudgmentStatus.ABSTAIN, "insufficient_evidence"), AcceptanceAction.RELY);
		var error = catchThrowableOfType(() -> RequirementAssertions.requireSatisfied(result),
				RequirementAssertionError.Inconclusive.class);
		assertThat(error.getMessage()).contains("producer=ABSTAIN", "category=insufficient_evidence", "RELY",
				"Interpretation: UNRESOLVED", "Declared projection has no supported determination");
		assertThat(error.getMessage()).doesNotContain("Policy: FAILED", "producer=FAIL");
	}

	@Test
	void protocolErrorHasNoSuccessfulNativeSupportAndRetainsInvalidResponseReference() {
		Judgment raw = new Judgment(JudgmentStatus.ERROR, null, null, null, JudgmentReasonCode.JUDGE_REPORTED,
				"Jev request failed or returned invalid protocol", List.of(), PROVENANCE, null,
				Map.of("invalidNativeConfidence", 0.25));
		var result = result(raw, AcceptanceAction.RELY);
		var error = catchThrowableOfType(() -> RequirementAssertions.requireSatisfied(result),
				RequirementAssertionError.InstrumentFailure.class);
		assertThat(error.getMessage()).contains("producer=ERROR; no finding", "Support: none (producer ERROR)",
				"no application (bypassed: NOT_ASSESSED)", "Operational result: ERROR", "Interpretation: NOT_ASSESSED",
				"invalid protocol", "Response: retained-response");
		assertThat(error.getMessage()).doesNotContain("0.25", "jev.choice.confidence", "RELY");
		assertThat(error.result().verdict().judgment().metadata()).containsEntry("invalidNativeConfidence", 0.25);
	}

	@Test
	void policyFailureKeepsTheValidProducerAssessmentAndSupportVisible() {
		Judgment raw = choice(JudgmentStatus.PASS, "satisfied");
		Judgment failed = Policies.apply(raw, POLICY, j -> {
			throw new IllegalStateException("policy unavailable");
		});
		var result = AssertionResult.applyPolicy(LOCKS, binding(AcceptanceAction.RELY),
				AssertionResult.PolicySource.DEFAULT, Verdict.single("retained", failed));
		var error = catchThrowableOfType(() -> RequirementAssertions.requireSatisfied(result),
				RequirementAssertionError.InstrumentFailure.class);
		assertThat(error.getMessage()).contains("producer=PASS", "category=satisfied", "jev.choice.confidence:v1=0.57",
				"FAILED; producer judgment retained", "policy unavailable", "Operational result: ERROR");
		assertThat(error.getMessage()).doesNotContain("producer ERROR bypasses", "Support: none");
	}

	@Test
	void noulProbabilityDoesNotAcquireChoiceConfidence() {
		Judgment raw = new Judgment(JudgmentStatus.PASS, new Finding(new BooleanFinding(true), null, null), null,
				new ProbabilityDistribution(FindingTarget.BOOLEAN, "jev.noul.probability-of-true:v1",
						List.of(new ProbabilityMass("false", 0.36), new ProbabilityMass("true", 0.64))),
				null, "", List.of(), PROVENANCE, null, Map.of());
		var error = catchThrowableOfType(
				() -> RequirementAssertions.requireSatisfied(result(raw, AcceptanceAction.ABSTAIN)),
				RequirementAssertionError.Inconclusive.class);
		assertThat(error.getMessage()).contains("boolean=true", "jev.noul.probability-of-true:v1", "p(true)=0.64");
		assertThat(error.getMessage()).doesNotContain("jev.choice", "confidence=", "certainty=");
	}

	@Test
	void hugeFieldsAndEvidenceDoNotDominateThePrimaryDiagnostic() {
		String huge = "long-value\n".repeat(2000);
		var requirement = Requirement.text(huge, huge, huge);
		var provenance = new Provenance(huge, huge, "b".repeat(64), List.of(RESPONSE), RESPONSE, List.of());
		Judgment raw = new Judgment(JudgmentStatus.ERROR, null, null, null, JudgmentReasonCode.JUDGE_REPORTED, huge,
				List.of(), provenance, null, Map.of("payload", "EVIDENCE_BODY_MUST_NOT_BE_DUMPED"));
		var result = AssertionResult.applyPolicy(requirement, binding(AcceptanceAction.RELY),
				AssertionResult.PolicySource.DEFAULT, Verdict.single("one", raw));
		var error = catchThrowableOfType(() -> RequirementAssertions.requireSatisfied(result),
				RequirementAssertionError.InstrumentFailure.class);
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
		var result = AssertionResult.applyPolicy(LOCKS, binding(AcceptanceAction.RELY),
				AssertionResult.PolicySource.DEFAULT, reopened);
		assertThat(result.interpretation().readingSupport()).isNotEqualTo(ReadingSupport.SUPPORTED);
		assertThatThrownBy(() -> RequirementAssertions.requireSatisfied(result))
			.isInstanceOf(RequirementAssertionError.UnsupportedReading.class)
			.hasMessageContaining("UNSUPPORTED_READING")
			.hasMessageContaining("Reading defect:");
	}

	private static AssertionResult result(Judgment raw, AcceptanceAction action) {
		return AssertionResult.applyPolicy(LOCKS, binding(action), AssertionResult.PolicySource.DEFAULT,
				Verdict.single("retained", raw));
	}

	private static AcceptancePolicy binding(AcceptanceAction action) {
		return Policies.recorded(POLICY, j -> new AcceptanceDecision(action, "Explicit fixture policy"));
	}

	private static Judgment choice(JudgmentStatus status, String selected) {
		List<String> alternatives = List.of("satisfied", "violated", "insufficient_evidence");
		boolean insufficient = status == JudgmentStatus.ABSTAIN;
		return new Judgment(status, new Finding(null, null, new CategoryFinding(selected, alternatives)),
				new Confidence(insufficient ? 0.61 : 0.57, "jev.choice.confidence:v1", SupportOrigin.REPORTED,
						FindingTarget.CATEGORY, null),
				new ProbabilityDistribution(FindingTarget.CATEGORY, "jev.choice.distribution:v1",
						List.of(new ProbabilityMass("satisfied", insufficient ? 0.02 : 0.71),
								new ProbabilityMass("violated", insufficient ? 0.23 : 0.27),
								new ProbabilityMass("insufficient_evidence", insufficient ? 0.75 : 0.02))),
				null, status == JudgmentStatus.ABSTAIN ? "Declared projection has no supported determination" : "",
				List.of(), PROVENANCE, null, Map.of("retained", List.of("original")));
	}

}
