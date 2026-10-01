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
import io.github.markpollack.judge.completion.CompletionEvidence;
import io.github.markpollack.judge.provenance.ArtifactRef;
import io.github.markpollack.judge.judgment.Finding;
import io.github.markpollack.judge.judgment.FindingTarget;
import io.github.markpollack.judge.provenance.CalibrationClaim;
import io.github.markpollack.judge.judgment.CategoryFinding;
import io.github.markpollack.judge.judgment.Confidence;
import io.github.markpollack.judge.judgment.Check;
import io.github.markpollack.judge.judgment.ProbabilityDistribution;
import io.github.markpollack.judge.provenance.Provenance;
import io.github.markpollack.judge.judgment.Judgment;
import io.github.markpollack.judge.judgment.JudgmentReasonCode;
import io.github.markpollack.judge.judgment.JudgmentStatus;
import io.github.markpollack.judge.judgment.NumericFinding;
import io.github.markpollack.judge.judgment.NumericKind;
import io.github.markpollack.judge.provenance.PolicyRef;
import io.github.markpollack.judge.judgment.ProbabilityMass;
import io.github.markpollack.judge.judgment.BooleanFinding;
import io.github.markpollack.judge.judgment.QualityDirection;
import io.github.markpollack.judge.judgment.SupportOrigin;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ModernProducerMigrationTest {

	private static final ObjectMapper JSON = new ObjectMapper();

	private static final CompletionEvidence CONTEXT = CompletionEvidence.builder()
		.request("migration fidelity")
		.build();

	private static final ArtifactRef ARTIFACT = ArtifactRef.ofBytes("retained",
			"exact bytes".getBytes(StandardCharsets.UTF_8), null);

	private static final PolicyRef POLICY = new PolicyRef("application", "1", ARTIFACT.sha256());

	private static Judgment rich() {
		Finding finding = new Finding(new BooleanFinding(false), new NumericFinding(0.3, NumericKind.MEASUREMENT,
				"quality:v1", 0, 1, List.of(), QualityDirection.INCREASING),
				new CategoryFinding("bad", List.of("good", "bad")));
		Confidence confidence = new Confidence(0.9, "provider-support:v1", SupportOrigin.REPORTED,
				FindingTarget.BOOLEAN, null);
		ProbabilityDistribution probabilityDistribution = new ProbabilityDistribution(FindingTarget.BOOLEAN, "truth:v1",
				List.of(new ProbabilityMass("false", 0.9), new ProbabilityMass("true", 0.1)));
		Provenance provenance = new Provenance("provider", "revision", ARTIFACT.sha256(), List.of(ARTIFACT), ARTIFACT,
				List.of(new CalibrationClaim("declared-calibration:v1", "provider", "declared population",
						"provider declaration", List.of("provider-support:v1"), List.of(ARTIFACT))));
		return new Judgment(JudgmentStatus.FAIL, finding, confidence, probabilityDistribution,
				JudgmentReasonCode.SUBJECT_EMPTY, "raw subject rejection",
				List.of(new Check("child", Judgment.abstain("child lacks evidence"))), provenance,
				Map.of("elapsedMillis", 7));
	}

	@Test
	void enrichmentCopiesEveryFactAndOnlyChangesItsOwnMetadata() {
		Judgment original = rich();
		Judgment enriched = original.toBuilder().metadata("trace", List.of("a", "b")).build();
		Judgment attached = AggregationEvidence.attach(original, Map.of("strategy", "explicit-reduction"));
		for (Judgment copy : List.of(enriched, attached)) {
			ObjectNode expected = JSON.valueToTree(original);
			ObjectNode actual = JSON.valueToTree(copy);
			expected.remove("metadata");
			actual.remove("metadata");
			assertThat(actual).isEqualTo(expected);
			assertThat(copy.metadata()).containsEntry("elapsedMillis", 7);
			assertThat(copy.status()).isEqualTo(JudgmentStatus.FAIL);
		}
		assertThat(original.metadata()).containsOnlyKeys("elapsedMillis");
		assertThat(enriched.metadata()).containsEntry("trace", List.of("a", "b"));
		assertThat(attached.metadata()).containsKey(Judgment.AGGREGATION_KEY);
		assertThat(Judges.named(() -> original, "named").judge()).isSameAs(original);
	}

	@Test
	void multiSeatAggregationRetainsCompleteInputsWithoutInheritingNativeSupport() {
		Judgment original = rich();
		Verdict verdict = SimpleJury.builder()
			.judge(Judges.named(() -> original, "negative"))
			.judge(Judges.named(() -> Judgment.pass("other"), "positive"))
			.votingStrategy(new ConsensusStrategy())
			.build()
			.vote();
		assertThat(verdict.individual().get(0)).isSameAs(original);
		assertThat(verdict.individualByName().get("negative")).isSameAs(original);
		assertThat(verdict.judgment().confidence()).isNull();
		assertThat(verdict.judgment().probabilityDistribution()).isNull();
		assertThat(verdict.judgment().provenance()).isNull();
	}

}
