/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.jury;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.markpollack.judge.Judges;
import io.github.markpollack.judge.context.JudgmentContext;
import io.github.markpollack.judge.jury.interpretation.Verdicts;
import io.github.markpollack.judge.result.AcceptanceAction;
import io.github.markpollack.judge.result.AppliedPolicy;
import io.github.markpollack.judge.result.ArtifactRef;
import io.github.markpollack.judge.result.Assessment;
import io.github.markpollack.judge.result.AssessmentTarget;
import io.github.markpollack.judge.result.CalibrationClaim;
import io.github.markpollack.judge.result.Category;
import io.github.markpollack.judge.result.Certainty;
import io.github.markpollack.judge.result.Check;
import io.github.markpollack.judge.result.Distribution;
import io.github.markpollack.judge.result.EvaluationProvenance;
import io.github.markpollack.judge.result.Judgment;
import io.github.markpollack.judge.result.JudgmentReasonCode;
import io.github.markpollack.judge.result.JudgmentStatus;
import io.github.markpollack.judge.result.NumericAssessment;
import io.github.markpollack.judge.result.NumericKind;
import io.github.markpollack.judge.result.PolicyApplication;
import io.github.markpollack.judge.result.PolicyFailure;
import io.github.markpollack.judge.result.PolicyRef;
import io.github.markpollack.judge.result.ProbabilityMass;
import io.github.markpollack.judge.result.Proposition;
import io.github.markpollack.judge.result.QualityDirection;
import io.github.markpollack.judge.result.SupportOrigin;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ModernProducerMigrationTest {

	private static final ObjectMapper JSON = new ObjectMapper();

	private static final JudgmentContext CONTEXT = JudgmentContext.builder().goal("migration fidelity").build();

	private static final ArtifactRef ARTIFACT = ArtifactRef.ofBytes("retained",
			"exact bytes".getBytes(StandardCharsets.UTF_8), null);

	private static final PolicyRef POLICY = new PolicyRef("application", "1", ARTIFACT.sha256());

	private static Judgment rich(PolicyApplication policy) {
		Assessment assessment = new Assessment(new Proposition(false), new NumericAssessment(0.3,
				NumericKind.MEASUREMENT, "quality:v1", 0, 1, List.of(), QualityDirection.INCREASING),
				new Category("bad", List.of("good", "bad")));
		Certainty certainty = new Certainty(0.9, "provider-support:v1", SupportOrigin.REPORTED,
				AssessmentTarget.PROPOSITION, null);
		Distribution distribution = new Distribution(AssessmentTarget.PROPOSITION, "truth:v1",
				List.of(new ProbabilityMass("false", 0.9), new ProbabilityMass("true", 0.1)));
		EvaluationProvenance provenance = new EvaluationProvenance("provider", "revision", ARTIFACT.sha256(),
				List.of(ARTIFACT), ARTIFACT,
				List.of(new CalibrationClaim("declared-calibration:v1", "provider", "declared population",
						"provider declaration", List.of("provider-support:v1"), List.of(ARTIFACT))));
		return new Judgment(JudgmentStatus.FAIL, assessment, certainty, distribution, JudgmentReasonCode.SUBJECT_EMPTY,
				"raw subject rejection", List.of(new Check("child", Judgment.abstain("child lacks evidence"))),
				provenance, policy, Map.of("elapsedMillis", 7));
	}

	@Test
	void enrichmentCopiesEveryFactAndOnlyChangesItsOwnMetadata() {
		Judgment original = rich(
				new AppliedPolicy(POLICY, AcceptanceAction.ESCALATE, "escalate for independent review"));
		Judgment enriched = original.toBuilder().metadata("trace", List.of("a", "b")).build();
		Judgment attached = AggregationEvidence.attach(original, Map.of("strategy", "explicit-reduction"));
		for (Judgment copy : List.of(enriched, attached)) {
			ObjectNode expected = JSON.valueToTree(original);
			ObjectNode actual = JSON.valueToTree(copy);
			expected.remove("metadata");
			actual.remove("metadata");
			assertThat(actual).isEqualTo(expected);
			assertThat(copy.metadata()).containsEntry("elapsedMillis", 7);
			assertThat(copy.status()).isEqualTo(JudgmentStatus.ABSTAIN);
		}
		assertThat(original.metadata()).containsOnlyKeys("elapsedMillis");
		assertThat(enriched.metadata()).containsEntry("trace", List.of("a", "b"));
		assertThat(attached.metadata()).containsKey(Judgment.AGGREGATION_KEY);
		assertThat(Judges.named(context -> original, "named").judge(CONTEXT)).isSameAs(original);
	}

	@Test
	void multiSeatAggregationRetainsCompleteInputsWithoutInheritingNativeSupport() {
		Judgment original = rich(new AppliedPolicy(POLICY, AcceptanceAction.USE_ASSESSMENT, "use assessment"));
		Verdict verdict = SimpleJury.<JudgmentContext>builder()
			.judge(Judges.named(context -> original, "negative"))
			.judge(Judges.named(context -> Judgment.pass("other"), "positive"))
			.votingStrategy(new ConsensusStrategy())
			.build()
			.vote(CONTEXT);
		assertThat(verdict.individual().get(0)).isSameAs(original);
		assertThat(verdict.individualByName().get("negative")).isSameAs(original);
		assertThat(verdict.aggregated().certainty()).isNull();
		assertThat(verdict.aggregated().distribution()).isNull();
		assertThat(verdict.aggregated().provenance()).isNull();
		assertThat(verdict.aggregated().policyApplication()).isNull();
	}

	@Test
	void policyErrorIsReportedAndCountedAsMachineryRatherThanRawSubjectFailure() {
		Judgment original = rich(
				new PolicyFailure(POLICY, JudgmentReasonCode.POLICY_FAILED, "policy configuration unavailable"));
		Verdict verdict = SimpleJury.<JudgmentContext>builder()
			.judge(Judges.named(context -> original, "policy"))
			.judge(Judges.named(context -> Judgment.pass("other"), "positive"))
			.votingStrategy(new ConsensusStrategy(ErrorPolicy.TREAT_AS_FAIL))
			.build()
			.vote(CONTEXT);
		assertThat(verdict.individual().get(0)).isSameAs(original);
		assertThat(original.reasonCode()).isEqualTo(JudgmentReasonCode.SUBJECT_EMPTY);
		assertThat(original.reasoning()).isEqualTo("raw subject rejection");
		assertThat(verdict.aggregated().status()).isEqualTo(JudgmentStatus.ERROR);
		assertThat(verdict.aggregated().metadata().toString()).contains("policy_failed=1", "errorsTreatedAsFailCount=0")
			.doesNotContain("subject_empty");
		var interpretation = Verdicts.interpret(verdict);
		var policySeat = interpretation.root()
			.judges()
			.stream()
			.filter(seat -> seat.name().equals("policy"))
			.findFirst()
			.orElseThrow();
		assertThat(policySeat.reasonCode()).isEqualTo("policy_failed");
		assertThat(policySeat.reasoning()).isEqualTo("policy configuration unavailable");
		assertThat(interpretation.summary()).contains("rawReason=raw subject rejection");
	}

}
