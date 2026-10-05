/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.judgment;

import io.github.markpollack.judge.provenance.ArtifactRef;
import io.github.markpollack.judge.provenance.CalibrationClaim;
import io.github.markpollack.judge.provenance.PolicyRef;
import io.github.markpollack.judge.provenance.Provenance;

import io.github.markpollack.judge.completion.CompletionEvidence;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.markpollack.judge.voting.ConsensusStrategy;
import io.github.markpollack.judge.serialization.diagnostics.ReadingSupport;
import io.github.markpollack.judge.serialization.diagnostics.StoredVerdicts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModernResultValuesTest {

	private static final ObjectMapper JSON = new ObjectMapper().registerModule(io.github.markpollack.judge.serialization.ResultJson.module());

	private static final ArtifactRef CONFIG = ArtifactRef.ofBytes("config", "{}".getBytes(StandardCharsets.UTF_8),
			null);

	private static final PolicyRef POLICY = new PolicyRef("critical-requirement", "1", CONFIG.sha256());

	private static final Finding PRODUCT = new Finding(new BooleanFinding(false),
			new NumericFinding(2, NumericKind.MEASUREMENT, "violations:v1", 0, 10, List.of(),
					QualityDirection.DECREASING),
			new CategoryFinding("violated", List.of("satisfied", "violated", "unknown")));

	private static Judgment raw(JudgmentStatus status, Finding finding, Confidence confidence,
			ProbabilityDistribution probabilityDistribution, Object unused) {
		return new Judgment(status, finding, confidence, probabilityDistribution,
				status == JudgmentStatus.ERROR ? JudgmentReasonCode.JUDGE_REPORTED
						: status == JudgmentStatus.FAIL ? JudgmentReasonCode.SUBJECT_EMPTY : null,
				"raw explanation", List.of(), null, Map.of());
	}

	@ParameterizedTest
	@EnumSource(JudgmentStatus.class)
	void allFiveProducerOutcomesRemainDistinct(JudgmentStatus status) throws Exception {
		Judgment result = raw(status, null, null, null, null);
		assertThat(result.producerStatus()).isEqualTo(status);
		assertThat(result.status()).isEqualTo(status);
		assertThat(JSON.readValue(JSON.writeValueAsBytes(result), Judgment.class)).isEqualTo(result);
	}

	@Test
	void productRetainsNumericAndCategoryAlongsidePropositionWithoutInventingSupport() throws Exception {
		Judgment result = raw(JudgmentStatus.FAIL, PRODUCT, null, null, null);
		assertThat(result.finding()).isEqualTo(PRODUCT);
		assertThat(result.score()).isEqualTo(0.8);
		assertThat(result.label()).isEqualTo("violated");
		assertThat(result.confidence()).isNull();
		assertThat(result.probabilityDistribution()).isNull();
		var tree = JSON.readTree(JSON.writeValueAsBytes(result));
		assertThat(tree.has("score")).isFalse();
		assertThat(tree.has("label")).isFalse();
		assertThat(tree.has("status")).isFalse();
		assertThat(tree.path("finding").path("numeric").path("value").asDouble()).isEqualTo(2);
		assertThat(JSON.treeToValue(tree, Judgment.class)).isEqualTo(result);
	}

	@ParameterizedTest
	@EnumSource(value = JudgmentStatus.class, names = { "ERROR", "NOT_APPLICABLE" })
	void unevaluatedProducerForbidsAssessmentSupportAndPolicy(JudgmentStatus status) {
		assertThatThrownBy(() -> raw(status, PRODUCT, null, null, null)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> raw(status, null, reported(FindingTarget.BOOLEAN), null, null))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> raw(status, null, null, propositionDistribution(), null))
			.isInstanceOf(IllegalArgumentException.class);
	}

	private static Confidence reported(FindingTarget target) {
		return new Confidence(0.7, "native-support:v1", SupportOrigin.REPORTED, target, null);
	}

	private static ProbabilityDistribution propositionDistribution() {
		return new ProbabilityDistribution(FindingTarget.BOOLEAN, "truth:v1",
				List.of(new ProbabilityMass("false", 0.3), new ProbabilityMass("true", 0.7)));
	}

	@ParameterizedTest
	@EnumSource(FindingTarget.class)
	void supportMustTargetAnActuallyPresentComponent(FindingTarget target) {
		Finding onlyTarget = switch (target) {
			case BOOLEAN -> new Finding(new BooleanFinding(null), null, null);
			case NUMERIC -> new Finding(null, PRODUCT.numeric(), null);
			case CATEGORY -> new Finding(null, null, PRODUCT.category());
		};
		Finding withoutTarget = switch (target) {
			case BOOLEAN -> new Finding(null, PRODUCT.numeric(), PRODUCT.category());
			case NUMERIC -> new Finding(PRODUCT.booleanFinding(), null, PRODUCT.category());
			case CATEGORY -> new Finding(PRODUCT.booleanFinding(), PRODUCT.numeric(), null);
		};
		assertThat(raw(JudgmentStatus.ABSTAIN, onlyTarget, reported(target), null, null).confidence().target())
			.isEqualTo(target);
		assertThatThrownBy(() -> raw(JudgmentStatus.PASS, withoutTarget, reported(target), null, null))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> raw(JudgmentStatus.PASS, null, reported(target), null, null))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void distributionPreservesAcceptedMassesAndCannotInventNoulConfidence() throws Exception {
		ProbabilityDistribution probabilityDistribution = new ProbabilityDistribution(FindingTarget.BOOLEAN, "truth:v1",
				List.of(new ProbabilityMass("false", 0.2000002), new ProbabilityMass("true", 0.8)));
		Judgment result = raw(JudgmentStatus.PASS, new Finding(new BooleanFinding(true), null, null), null,
				probabilityDistribution, null);
		assertThat(result.confidence()).isNull();
		assertThat(result.probabilityDistribution().masses().get(0).probability()).isEqualTo(0.2000002);
		assertThat(JSON.readValue(JSON.writeValueAsBytes(result), Judgment.class)).isEqualTo(result);
		assertThatThrownBy(() -> raw(JudgmentStatus.PASS, null, null, probabilityDistribution, null))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> raw(JudgmentStatus.PASS, new Finding(null, null, PRODUCT.category()), null,
				probabilityDistribution, null))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void distributionRequiresExactlyTheDeclaredKeysAndOrdinalLevels() {
		ProbabilityDistribution category = new ProbabilityDistribution(FindingTarget.CATEGORY, "classification:v1",
				List.of(new ProbabilityMass("satisfied", 0.2), new ProbabilityMass("violated", 0.7),
						new ProbabilityMass("unknown", 0.1)));
		assertThat(raw(JudgmentStatus.FAIL, PRODUCT, null, category, null).probabilityDistribution())
			.isSameAs(category);
		ProbabilityDistribution wrong = new ProbabilityDistribution(FindingTarget.BOOLEAN, "truth:v1",
				List.of(new ProbabilityMass("yes", 1)));
		assertThatThrownBy(() -> raw(JudgmentStatus.PASS, PRODUCT, null, wrong, null))
			.isInstanceOf(IllegalArgumentException.class);
		ProbabilityDistribution incomplete = new ProbabilityDistribution(FindingTarget.CATEGORY, "classification:v1",
				List.of(new ProbabilityMass("violated", 1)));
		assertThatThrownBy(() -> raw(JudgmentStatus.FAIL, PRODUCT, null, incomplete, null))
			.isInstanceOf(IllegalArgumentException.class);
		ProbabilityDistribution ordinal = new ProbabilityDistribution(FindingTarget.NUMERIC, "rubric:v1",
				List.of(new ProbabilityMass("bad", 0.25), new ProbabilityMass("good", 0.75)));
		assertThatThrownBy(() -> raw(JudgmentStatus.FAIL, PRODUCT, null, ordinal, null))
			.isInstanceOf(IllegalArgumentException.class);
		Finding ranked = new Finding(null, new NumericFinding(0.75, NumericKind.ORDINAL_EXPECTATION, "rubric:v1", 0, 1,
				List.of("bad", "good"), QualityDirection.INCREASING), null);
		assertThat(raw(JudgmentStatus.PASS, ranked, null, ordinal, null).probabilityDistribution()).isEqualTo(ordinal);
	}

	@ParameterizedTest
	@ValueSource(doubles = { -0.1, 1.1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY })
	void invalidSupportNumbersAreRefused(double invalid) {
		assertThatThrownBy(() -> new ProbabilityMass("true", invalid)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new Confidence(invalid, "metric", SupportOrigin.REPORTED, FindingTarget.BOOLEAN, null))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void incompleteDuplicateAndNonunitDistributionsAreRefused() {
		assertThatThrownBy(() -> new ProbabilityDistribution(FindingTarget.BOOLEAN, "truth", List.of()))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new ProbabilityDistribution(FindingTarget.BOOLEAN, "truth",
				List.of(new ProbabilityMass("true", 0.5), new ProbabilityMass("true", 0.5))))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new ProbabilityDistribution(FindingTarget.BOOLEAN, "truth",
				List.of(new ProbabilityMass("true", 0.8))))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void derivedSupportHasAnExplicitVersionedDerivationAndReportedSupportDoesNot() {
		assertThat(new Confidence(0.9, "margin", SupportOrigin.DERIVED, FindingTarget.CATEGORY, "margin:v2")
			.derivationId()).isEqualTo("margin:v2");
		assertThatThrownBy(() -> new Confidence(0.9, "margin", SupportOrigin.DERIVED, FindingTarget.CATEGORY, null))
			.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new Confidence(0.9, "margin", SupportOrigin.DERIVED, FindingTarget.CATEGORY, "margin"))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(
				() -> new Confidence(0.9, "margin", SupportOrigin.REPORTED, FindingTarget.CATEGORY, "margin:v2"))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@ParameterizedTest
	@EnumSource(QualityDirection.class)
	void extremeFiniteScaleHasExactEndpointsAndCorrectMidpoint(QualityDirection direction) {
		assertThat(number(-1e308, -1e308, 1e308, direction).qualityScore().getAsDouble())
			.isEqualTo(direction == QualityDirection.INCREASING ? 0 : 1);
		assertThat(number(0, -1e308, 1e308, direction).qualityScore().getAsDouble()).isEqualTo(0.5);
		assertThat(number(1e308, -1e308, 1e308, direction).qualityScore().getAsDouble())
			.isEqualTo(direction == QualityDirection.INCREASING ? 1 : 0);
		assertThat(number(5e307, -1e308, 1e308, direction).qualityScore().getAsDouble())
			.isEqualTo(direction == QualityDirection.INCREASING ? 0.75 : 0.25);
		assertThat(number(Double.MIN_VALUE, 0, Double.MIN_VALUE * 2, direction).qualityScore().getAsDouble())
			.isEqualTo(0.5);
		assertThat(number(Math.nextUp(1.0), 1, Math.nextUp(1.0), direction).qualityScore().getAsDouble())
			.isEqualTo(direction == QualityDirection.INCREASING ? 1 : 0);
	}

	private static NumericFinding number(double value, double low, double high, QualityDirection direction) {
		return new NumericFinding(value, NumericKind.MEASUREMENT, "raw:v1", low, high, List.of(), direction);
	}

	@ParameterizedTest
	@EnumSource(QualityDirection.class)
	void everyAdjacentOrdinalLevelPreservesTheDeclaredOrdering(QualityDirection direction) {
		List<String> levels = List.of("absent", "weak", "mixed", "adequate", "strong");
		double prior = direction == QualityDirection.INCREASING ? -1 : 2;
		for (int i = 0; i < levels.size(); i++) {
			NumericFinding numeric = new NumericFinding(i, NumericKind.ORDINAL_EXPECTATION, "rubric:v3", 0, 4, levels,
					direction);
			double quality = numeric.qualityScore().getAsDouble();
			assertThat(quality).isEqualTo(direction == QualityDirection.INCREASING ? i / 4.0 : 1 - i / 4.0);
			if (direction == QualityDirection.INCREASING) {
				assertThat(quality).isGreaterThan(prior);
			}
			else {
				assertThat(quality).isLessThan(prior);
			}
			prior = quality;
		}
	}

	@Test
	void directionlessMeasurementCannotVoteAsQualityAndNormalizationCreatesNoCertainty() {
		Finding uninterpreted = new Finding(null, number(9, 0, 10, null), null);
		Judgment failure = raw(JudgmentStatus.FAIL, uninterpreted, null, null, null);
		assertThat(failure.score()).isNull();
		assertThat(failure.effectiveScore()).hasValue(0);
		assertThat(raw(JudgmentStatus.PASS, uninterpreted, null, null, null).effectiveScore()).hasValue(1);
		assertThat(failure.confidence()).isNull();
		assertThat(failure.finding().numeric().value()).isEqualTo(9);
	}

	@Test
	void numericAndAssessmentInvalidityIsNotRepaired() {
		assertThatThrownBy(() -> new Finding(null, null, null)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> number(0, 0, 0, null)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> number(2, 0, 1, null)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> number(Double.NaN, 0, 1, null)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> number(0, 0, Double.POSITIVE_INFINITY, null))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new NumericFinding(0, NumericKind.MEASUREMENT, "scale", 0, 1, List.of("a"), null))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(
				() -> new NumericFinding(0, NumericKind.ORDINAL_EXPECTATION, "scale", 0, 1, List.of("a"), null))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(
				() -> new NumericFinding(0, NumericKind.ORDINAL_EXPECTATION, "scale", 0, 2, List.of("a", "b"), null))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(
				() -> new NumericFinding(0, NumericKind.ORDINAL_EXPECTATION, "scale", 0, 1, List.of("a", "a"), null))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void checksRetainFiveOutcomesAreImmutableAndForbidNestedOrDuplicateIds() throws Exception {
		List<Check> source = new ArrayList<>();
		for (JudgmentStatus status : JudgmentStatus.values()) {
			source.add(new Check(status.name(), raw(status, null, null, null, null)));
		}
		Judgment parent = new Judgment(JudgmentStatus.ABSTAIN, null, null, null, null, "incomplete", source, null,
				Map.of());
		source.clear();
		assertThat(parent.checks()).extracting(check -> check.judgment().status())
			.containsExactly(JudgmentStatus.values());
		assertThatThrownBy(() -> parent.checks().clear()).isInstanceOf(UnsupportedOperationException.class);
		assertThatThrownBy(() -> new Check("nested", parent)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Judgment.pass("ok")
			.toBuilder()
			.checks(List.of(Check.pass("duplicate"), Check.fail("duplicate", "bad")))
			.build()).isInstanceOf(IllegalArgumentException.class);
		assertThat(JSON.readValue(JSON.writeValueAsBytes(parent), Judgment.class)).isEqualTo(parent);
	}

	@Test
	void calibrationDeclarationsPersistWithoutBecomingCertification() throws Exception {
		byte[] original = "native bytes\r\n".getBytes(StandardCharsets.UTF_8);
		ArtifactRef artifact = ArtifactRef.ofBytes("response", original, "$.answer");
		ArtifactRef different = ArtifactRef.ofBytes("response", "native bytes\n".getBytes(StandardCharsets.UTF_8),
				"$.answer");
		assertThat(artifact.sha256()).isNotEqualTo(different.sha256());
		original[0] = 0;
		List<String> signals = new ArrayList<>(List.of("probability-true:v1"));
		List<ArtifactRef> sources = new ArrayList<>(List.of(CONFIG));
		CalibrationClaim claim = new CalibrationClaim("provider-calibration:v1", "Provider", "native probabilities",
				"Provider declares calibrated probabilities", signals, sources);
		signals.clear();
		sources.clear();
		ArtifactRef bundle = ArtifactRef.ofBytes("bundle", "evidence".getBytes(StandardCharsets.UTF_8), null);
		ArtifactRef manifest = ArtifactRef.ofBytes("manifest", "recipe".getBytes(StandardCharsets.UTF_8), null);
		List<ArtifactRef> evidence = new ArrayList<>(List.of(bundle, manifest));
		Provenance provenance = new Provenance("instrument", "revision", CONFIG.sha256(), evidence, artifact,
				List.of(claim));
		evidence.clear();
		assertThat(provenance.evidence()).containsExactly(bundle, manifest);
		Judgment result = new Judgment(JudgmentStatus.PASS, PRODUCT, reported(FindingTarget.BOOLEAN),
				propositionDistribution(), null, "", List.of(), provenance, Map.of());
		Judgment restored = JSON.readValue(JSON.writeValueAsBytes(result), Judgment.class);
		assertThat(restored).isEqualTo(result);
		assertThat(restored.provenance().calibrationClaims()).containsExactly(claim);
		assertThat(restored.provenance().response().sha256()).isEqualTo(artifact.sha256());
		assertThat(claim.signalIds()).containsExactly("probability-true:v1");
		assertThatThrownBy(() -> claim.sources().clear()).isInstanceOf(UnsupportedOperationException.class);
		assertThat(result.toBuilder().metadata("elapsedMillis", 12).build().provenance()).isSameAs(provenance);
		assertThat(result.toBuilder().build()).isEqualTo(result);
		assertThat(JSON.writeValueAsString(result)).doesNotContain("calibrated\":true");
	}

	@Test
	void claimsRequireVersionedIdentityNonemptySignalsAndUniqueExactSources() {
		assertThatThrownBy(
				() -> new CalibrationClaim("claim", "issuer", "scope", "text", List.of("signal"), List.of(CONFIG)))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(
				() -> new CalibrationClaim("claim:v1", " ", "scope", "text", List.of("signal"), List.of(CONFIG)))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(
				() -> new CalibrationClaim("claim:v1", "issuer", "scope", "text", List.of(), List.of(CONFIG)))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new CalibrationClaim("claim:v1", "issuer", "scope", "text",
				List.of("signal", "signal"), List.of(CONFIG)))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(
				() -> new CalibrationClaim("claim:v1", "issuer", "scope", "text", List.of("signal"), List.of()))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new CalibrationClaim("claim:v1", "issuer", "scope", "text", List.of("signal"),
				List.of(CONFIG, CONFIG)))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new ArtifactRef("source", "not-a-hash", null))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new ArtifactRef("source", CONFIG.sha256(), " "))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void domainAndPolicyInvalidityIsRefused() {
		assertThatThrownBy(() -> new CategoryFinding("absent", List.of("present")))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new CategoryFinding(null, List.of(" "))).isInstanceOf(IllegalArgumentException.class);
		assertThat(new io.github.markpollack.judge.policy.PolicyDecision(
				io.github.markpollack.judge.policy.PolicyAction.ESCALATE, "review")
			.reason()).isEqualTo("review");
		assertThatThrownBy(() -> new io.github.markpollack.judge.policy.PolicyDecision(null, "review"))
			.isInstanceOf(NullPointerException.class);
	}

	@Test
	void versionTwoRetainsCategoryDomainsAndNumericScaleDirections() {
		List<Finding> assessments = List.of(new Finding(null, null, new CategoryFinding("yes", List.of("yes", "no"))),
				new Finding(null, null, new CategoryFinding(null, List.of("yes", "no"))),
				new Finding(null,
						new NumericFinding(0.7, NumericKind.MEASUREMENT, "normalized-quality:v1", 0, 1, List.of(),
								QualityDirection.DECREASING),
						null),
				new Finding(null, new NumericFinding(0.7, NumericKind.ORDINAL_EXPECTATION, "normalized-quality:v1", 0,
						1, List.of("low", "high"), QualityDirection.INCREASING), null));
		org.assertj.core.api.SoftAssertions softly = new org.assertj.core.api.SoftAssertions();
		for (Finding finding : assessments) {
			Judgment judgment = raw(JudgmentStatus.PASS, finding, null, null, null);
			var jury = io.github.markpollack.judge.jury.SimpleJury.builder()
				.judge(() -> judgment)
				.judge(() -> Judgment.pass("other"))
				.votingStrategy(new ConsensusStrategy())
				.build();
			var reading = StoredVerdicts.interpret(jury.vote());
			softly.assertThat(reading.readingSupport())
				.as("retained modern assessment %s", finding)
				.isEqualTo(ReadingSupport.SUPPORTED);
		}
		softly.assertAll();
	}

	@Test
	void absentOrNullRequiredWireNumbersCannotBecomeZero() {
		Map<Object, List<String>> examples = Map.of(new ProbabilityMass("false", 0), List.of("probability"),
				new Confidence(0, "support", SupportOrigin.REPORTED, FindingTarget.BOOLEAN, null), List.of("value"),
				new NumericFinding(0, NumericKind.MEASUREMENT, "scale", 0, 1, List.of(), null),
				List.of("value", "lower", "upper"));
		examples.forEach((example, fields) -> {
			for (String field : fields) {
				com.fasterxml.jackson.databind.node.ObjectNode missing = JSON.valueToTree(example);
				missing.remove(field);
				assertThatThrownBy(() -> JSON.treeToValue(missing, example.getClass()))
					.as("missing %s in %s", field, example)
					.isInstanceOf(Exception.class);
				missing.putNull(field);
				assertThatThrownBy(() -> JSON.treeToValue(missing, example.getClass()))
					.as("null %s in %s", field, example)
					.isInstanceOf(Exception.class);
			}
		});
	}

	@Test
	void propositionUnknownIsNullableAndNeverDefaultsToFalse() throws Exception {
		for (String json : List.of("{}", "{\"value\":null}")) {
			assertThat(JSON.readValue(json, BooleanFinding.class).value()).isNull();
		}
		assertThat(JSON.readValue("{\"value\":false}", BooleanFinding.class).value()).isFalse();
		assertThat(JSON.readValue("{\"value\":true}", BooleanFinding.class).value()).isTrue();
		assertThatThrownBy(() -> ArtifactRef.ofBytes("missing", null, null)).isInstanceOf(NullPointerException.class);
	}

	@Test
	void temporaryBridgeDoesNotRecurseIntoAnUnboundedHistoricalMap() {
		Map<String, Object> cyclic = new java.util.LinkedHashMap<>();
		cyclic.put("unrecognized", cyclic);
		assertThat(StoredVerdicts.interpret(cyclic).readingSupport()).isEqualTo(ReadingSupport.UNDETERMINED);
		cyclic.put("producerStatus", "pass");
		assertThat(StoredVerdicts.interpret(cyclic).readingSupport()).isEqualTo(ReadingSupport.UNDETERMINED);
	}

}
