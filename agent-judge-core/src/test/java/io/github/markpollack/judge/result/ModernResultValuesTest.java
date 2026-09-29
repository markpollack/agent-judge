/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.result;

import io.github.markpollack.judge.context.JudgmentContext;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.markpollack.judge.jury.ConsensusStrategy;
import io.github.markpollack.judge.jury.ErrorPolicy;
import io.github.markpollack.judge.jury.Verdict;
import io.github.markpollack.judge.jury.interpretation.ReadingSupport;
import io.github.markpollack.judge.jury.interpretation.Verdicts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModernResultValuesTest {

	private static final ObjectMapper JSON = new ObjectMapper();

	private static final ArtifactRef CONFIG = ArtifactRef.ofBytes("config", "{}".getBytes(StandardCharsets.UTF_8),
			null);

	private static final PolicyRef POLICY = new PolicyRef("critical-requirement", "1", CONFIG.sha256());

	private static final Assessment PRODUCT = new Assessment(new Proposition(false),
			new NumericAssessment(2, NumericKind.MEASUREMENT, "violations:v1", 0, 10, List.of(),
					QualityDirection.DECREASING),
			new Category("violated", List.of("satisfied", "violated", "unknown")));

	private static Judgment raw(JudgmentStatus status, Assessment assessment, Certainty certainty,
			Distribution distribution, PolicyApplication policy) {
		return new Judgment(status, assessment, certainty, distribution,
				status == JudgmentStatus.ERROR ? JudgmentReasonCode.JUDGE_REPORTED
						: status == JudgmentStatus.FAIL ? JudgmentReasonCode.SUBJECT_EMPTY : null,
				"raw explanation", List.of(), null, policy, Map.of());
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
		assertThat(result.assessment()).isEqualTo(PRODUCT);
		assertThat(result.score()).isEqualTo(0.8);
		assertThat(result.label()).isEqualTo("violated");
		assertThat(result.certainty()).isNull();
		assertThat(result.distribution()).isNull();
		var tree = JSON.readTree(JSON.writeValueAsBytes(result));
		assertThat(tree.has("score")).isFalse();
		assertThat(tree.has("label")).isFalse();
		assertThat(tree.has("status")).isFalse();
		assertThat(tree.path("assessment").path("numeric").path("value").asDouble()).isEqualTo(2);
		assertThat(JSON.treeToValue(tree, Judgment.class)).isEqualTo(result);
	}

	@ParameterizedTest
	@EnumSource(AcceptanceAction.class)
	void usingNegativeAssessmentNeverMakesItPass(AcceptanceAction action) throws Exception {
		Judgment result = raw(JudgmentStatus.FAIL, PRODUCT, null, null,
				new AppliedPolicy(POLICY, action, "policy explanation"));
		assertThat(result.status())
			.isEqualTo(action == AcceptanceAction.USE_ASSESSMENT ? JudgmentStatus.FAIL : JudgmentStatus.ABSTAIN);
		assertThat(result.producerStatus()).isEqualTo(JudgmentStatus.FAIL);
		assertThat(result.reasonCode()).isEqualTo(JudgmentReasonCode.SUBJECT_EMPTY);
		assertThat(result.assessment()).isSameAs(PRODUCT);
		assertThat(result.reasoning()).isEqualTo("raw explanation");
		assertThat(result.operationalReasoning())
			.isEqualTo(action == AcceptanceAction.USE_ASSESSMENT ? "raw explanation" : "policy explanation");
		assertThat(result.operationalReasonCode())
			.isEqualTo(action == AcceptanceAction.USE_ASSESSMENT ? JudgmentReasonCode.SUBJECT_EMPTY : null);
		if (action != AcceptanceAction.USE_ASSESSMENT) {
			assertThat(result.effectiveScore()).isEmpty();
		}
		assertThat(JSON.readValue(JSON.writeValueAsBytes(result), Judgment.class)).isEqualTo(result);
	}

	@ParameterizedTest
	@EnumSource(AcceptanceAction.class)
	void policyCannotPromoteRawAbstention(AcceptanceAction action) {
		assertThat(
				raw(JudgmentStatus.ABSTAIN, PRODUCT, null, null, new AppliedPolicy(POLICY, action, "policy")).status())
			.isEqualTo(JudgmentStatus.ABSTAIN);
	}

	@Test
	void policyFailureRetainsRawFailButCountsAsMachinery() throws Exception {
		Judgment result = raw(JudgmentStatus.FAIL, PRODUCT, null, null,
				new PolicyFailure(POLICY, JudgmentReasonCode.POLICY_FAILED, "threshold configuration unreadable"));
		assertThat(result.status()).isEqualTo(JudgmentStatus.ERROR);
		assertThat(result.reasonCode()).isEqualTo(JudgmentReasonCode.SUBJECT_EMPTY);
		assertThat(result.operationalReasonCode()).isEqualTo(JudgmentReasonCode.POLICY_FAILED);
		assertThat(result.operationalReasoning()).isEqualTo("threshold configuration unreadable");
		assertThat(result.assessment()).isSameAs(PRODUCT);
		assertThat(result.effectiveScore()).isEmpty();
		Judgment aggregate = new ConsensusStrategy(ErrorPolicy.TREAT_AS_FAIL)
			.aggregate(List.of(result, Judgment.pass("ok")), Map.of());
		assertThat(aggregate.status()).isEqualTo(JudgmentStatus.ERROR);
		assertThat(aggregate.metadata().toString()).contains("policy_failed=1").doesNotContain("subject_empty");
		assertThat(JSON.readValue(JSON.writeValueAsBytes(result), Judgment.class)).isEqualTo(result);
	}

	@ParameterizedTest
	@EnumSource(value = JudgmentStatus.class, names = { "ERROR", "NOT_APPLICABLE" })
	void unevaluatedProducerForbidsAssessmentSupportAndPolicy(JudgmentStatus status) {
		assertThatThrownBy(() -> raw(status, PRODUCT, null, null, null)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> raw(status, null, reported(AssessmentTarget.PROPOSITION), null, null))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> raw(status, null, null, propositionDistribution(), null))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(
				() -> raw(status, null, null, null, new AppliedPolicy(POLICY, AcceptanceAction.USE_ASSESSMENT, "use")))
			.isInstanceOf(IllegalArgumentException.class);
	}

	private static Certainty reported(AssessmentTarget target) {
		return new Certainty(0.7, "native-support:v1", SupportOrigin.REPORTED, target, null);
	}

	private static Distribution propositionDistribution() {
		return new Distribution(AssessmentTarget.PROPOSITION, "truth:v1",
				List.of(new ProbabilityMass("false", 0.3), new ProbabilityMass("true", 0.7)));
	}

	@ParameterizedTest
	@EnumSource(AssessmentTarget.class)
	void supportMustTargetAnActuallyPresentComponent(AssessmentTarget target) {
		Assessment onlyTarget = switch (target) {
			case PROPOSITION -> new Assessment(new Proposition(null), null, null);
			case NUMERIC -> new Assessment(null, PRODUCT.numeric(), null);
			case CATEGORY -> new Assessment(null, null, PRODUCT.category());
		};
		Assessment withoutTarget = switch (target) {
			case PROPOSITION -> new Assessment(null, PRODUCT.numeric(), PRODUCT.category());
			case NUMERIC -> new Assessment(PRODUCT.proposition(), null, PRODUCT.category());
			case CATEGORY -> new Assessment(PRODUCT.proposition(), PRODUCT.numeric(), null);
		};
		assertThat(raw(JudgmentStatus.ABSTAIN, onlyTarget, reported(target), null, null).certainty().target())
			.isEqualTo(target);
		assertThatThrownBy(() -> raw(JudgmentStatus.PASS, withoutTarget, reported(target), null, null))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> raw(JudgmentStatus.PASS, null, reported(target), null, null))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void distributionPreservesAcceptedMassesAndCannotInventNoulConfidence() throws Exception {
		Distribution distribution = new Distribution(AssessmentTarget.PROPOSITION, "truth:v1",
				List.of(new ProbabilityMass("false", 0.2000002), new ProbabilityMass("true", 0.8)));
		Judgment result = raw(JudgmentStatus.PASS, new Assessment(new Proposition(true), null, null), null,
				distribution, null);
		assertThat(result.certainty()).isNull();
		assertThat(result.distribution().masses().get(0).probability()).isEqualTo(0.2000002);
		assertThat(JSON.readValue(JSON.writeValueAsBytes(result), Judgment.class)).isEqualTo(result);
		assertThatThrownBy(() -> raw(JudgmentStatus.PASS, null, null, distribution, null))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> raw(JudgmentStatus.PASS, new Assessment(null, null, PRODUCT.category()), null,
				distribution, null))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void distributionRequiresExactlyTheDeclaredKeysAndOrdinalLevels() {
		Distribution category = new Distribution(AssessmentTarget.CATEGORY, "classification:v1",
				List.of(new ProbabilityMass("satisfied", 0.2), new ProbabilityMass("violated", 0.7),
						new ProbabilityMass("unknown", 0.1)));
		assertThat(raw(JudgmentStatus.FAIL, PRODUCT, null, category, null).distribution()).isSameAs(category);
		Distribution wrong = new Distribution(AssessmentTarget.PROPOSITION, "truth:v1",
				List.of(new ProbabilityMass("yes", 1)));
		assertThatThrownBy(() -> raw(JudgmentStatus.PASS, PRODUCT, null, wrong, null))
			.isInstanceOf(IllegalArgumentException.class);
		Distribution incomplete = new Distribution(AssessmentTarget.CATEGORY, "classification:v1",
				List.of(new ProbabilityMass("violated", 1)));
		assertThatThrownBy(() -> raw(JudgmentStatus.FAIL, PRODUCT, null, incomplete, null))
			.isInstanceOf(IllegalArgumentException.class);
		Distribution ordinal = new Distribution(AssessmentTarget.NUMERIC, "rubric:v1",
				List.of(new ProbabilityMass("bad", 0.25), new ProbabilityMass("good", 0.75)));
		assertThatThrownBy(() -> raw(JudgmentStatus.FAIL, PRODUCT, null, ordinal, null))
			.isInstanceOf(IllegalArgumentException.class);
		Assessment ranked = new Assessment(null, new NumericAssessment(0.75, NumericKind.ORDINAL_EXPECTATION,
				"rubric:v1", 0, 1, List.of("bad", "good"), QualityDirection.INCREASING), null);
		assertThat(raw(JudgmentStatus.PASS, ranked, null, ordinal, null).distribution()).isEqualTo(ordinal);
	}

	@ParameterizedTest
	@ValueSource(doubles = { -0.1, 1.1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY })
	void invalidSupportNumbersAreRefused(double invalid) {
		assertThatThrownBy(() -> new ProbabilityMass("true", invalid)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(
				() -> new Certainty(invalid, "metric", SupportOrigin.REPORTED, AssessmentTarget.PROPOSITION, null))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void incompleteDuplicateAndNonunitDistributionsAreRefused() {
		assertThatThrownBy(() -> new Distribution(AssessmentTarget.PROPOSITION, "truth", List.of()))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new Distribution(AssessmentTarget.PROPOSITION, "truth",
				List.of(new ProbabilityMass("true", 0.5), new ProbabilityMass("true", 0.5))))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new Distribution(AssessmentTarget.PROPOSITION, "truth",
				List.of(new ProbabilityMass("true", 0.8))))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void derivedSupportHasAnExplicitVersionedDerivationAndReportedSupportDoesNot() {
		assertThat(new Certainty(0.9, "margin", SupportOrigin.DERIVED, AssessmentTarget.CATEGORY, "margin:v2")
			.derivationId()).isEqualTo("margin:v2");
		assertThatThrownBy(() -> new Certainty(0.9, "margin", SupportOrigin.DERIVED, AssessmentTarget.CATEGORY, null))
			.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(
				() -> new Certainty(0.9, "margin", SupportOrigin.DERIVED, AssessmentTarget.CATEGORY, "margin"))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(
				() -> new Certainty(0.9, "margin", SupportOrigin.REPORTED, AssessmentTarget.CATEGORY, "margin:v2"))
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

	private static NumericAssessment number(double value, double low, double high, QualityDirection direction) {
		return new NumericAssessment(value, NumericKind.MEASUREMENT, "raw:v1", low, high, List.of(), direction);
	}

	@ParameterizedTest
	@EnumSource(QualityDirection.class)
	void everyAdjacentOrdinalLevelPreservesTheDeclaredOrdering(QualityDirection direction) {
		List<String> levels = List.of("absent", "weak", "mixed", "adequate", "strong");
		double prior = direction == QualityDirection.INCREASING ? -1 : 2;
		for (int i = 0; i < levels.size(); i++) {
			NumericAssessment numeric = new NumericAssessment(i, NumericKind.ORDINAL_EXPECTATION, "rubric:v3", 0, 4,
					levels, direction);
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
		Assessment uninterpreted = new Assessment(null, number(9, 0, 10, null), null);
		Judgment failure = raw(JudgmentStatus.FAIL, uninterpreted, null, null, null);
		assertThat(failure.score()).isNull();
		assertThat(failure.effectiveScore()).hasValue(0);
		assertThat(raw(JudgmentStatus.PASS, uninterpreted, null, null, null).effectiveScore()).hasValue(1);
		assertThat(failure.certainty()).isNull();
		assertThat(failure.assessment().numeric().value()).isEqualTo(9);
	}

	@Test
	void numericAndAssessmentInvalidityIsNotRepaired() {
		assertThatThrownBy(() -> new Assessment(null, null, null)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> number(0, 0, 0, null)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> number(2, 0, 1, null)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> number(Double.NaN, 0, 1, null)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> number(0, 0, Double.POSITIVE_INFINITY, null))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new NumericAssessment(0, NumericKind.MEASUREMENT, "scale", 0, 1, List.of("a"), null))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(
				() -> new NumericAssessment(0, NumericKind.ORDINAL_EXPECTATION, "scale", 0, 1, List.of("a"), null))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(
				() -> new NumericAssessment(0, NumericKind.ORDINAL_EXPECTATION, "scale", 0, 2, List.of("a", "b"), null))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(
				() -> new NumericAssessment(0, NumericKind.ORDINAL_EXPECTATION, "scale", 0, 1, List.of("a", "a"), null))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void checksRetainFiveOutcomesAreImmutableAndForbidNestedOrDuplicateIds() throws Exception {
		List<Check> source = new ArrayList<>();
		for (JudgmentStatus status : JudgmentStatus.values()) {
			source.add(new Check(status.name(), raw(status, null, null, null, null)));
		}
		Judgment parent = new Judgment(JudgmentStatus.ABSTAIN, null, null, null, null, "incomplete", source, null, null,
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
		EvaluationProvenance provenance = new EvaluationProvenance("instrument", "revision", CONFIG.sha256(), evidence,
				artifact, List.of(claim));
		evidence.clear();
		assertThat(provenance.evidence()).containsExactly(bundle, manifest);
		Judgment result = new Judgment(JudgmentStatus.PASS, PRODUCT, reported(AssessmentTarget.PROPOSITION),
				propositionDistribution(), null, "", List.of(), provenance,
				new AppliedPolicy(POLICY, AcceptanceAction.ESCALATE, "insufficient validation"), Map.of());
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
		assertThatThrownBy(() -> new Category("absent", List.of("present")))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new Category(null, List.of(" "))).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new AppliedPolicy(POLICY, AcceptanceAction.ABSTAIN, " "))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new PolicyFailure(POLICY, JudgmentReasonCode.SUBJECT_EMPTY, "bad"))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new PolicyFailure(POLICY, JudgmentReasonCode.JUDGE_FAILED, "bad"))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new PolicyFailure(POLICY, JudgmentReasonCode.ERRORS_PROPAGATED, "bad"))
			.isInstanceOf(IllegalArgumentException.class);
		assertThat(new Acceptance(AcceptanceAction.ESCALATE, "review").reason()).isEqualTo("review");
		assertThatThrownBy(() -> new Acceptance(null, "review")).isInstanceOf(NullPointerException.class);
	}

	@Test
	void versionTwoRetainsModernPolicyFailureFacts() {
		Judgment result = raw(JudgmentStatus.FAIL, PRODUCT, reported(AssessmentTarget.PROPOSITION),
				propositionDistribution(),
				new PolicyFailure(POLICY, JudgmentReasonCode.POLICY_FAILED, "policy unavailable"));
		var reading = Verdicts.interpret(Verdict.single("judge", result));
		assertThat(reading.readingSupport()).isEqualTo(ReadingSupport.SUPPORTED);
		assertThat(reading.root().reasonCode()).isEqualTo("policy_failed");
		assertThat(reading.root().judgment().policyApplication()).isEqualTo(result.policyApplication());
		assertThat(reading.root().judgment().assessment()).isEqualTo(PRODUCT);
		assertThat(reading.defects()).isEmpty();
	}

	@Test
	void versionTwoRetainsCategoryDomainsAndNumericScaleDirections() {
		List<Assessment> assessments = List.of(new Assessment(null, null, new Category("yes", List.of("yes", "no"))),
				new Assessment(null, null, new Category(null, List.of("yes", "no"))),
				new Assessment(null,
						new NumericAssessment(0.7, NumericKind.MEASUREMENT, "normalized-quality:v1", 0, 1, List.of(),
								QualityDirection.DECREASING),
						null),
				new Assessment(null, new NumericAssessment(0.7, NumericKind.ORDINAL_EXPECTATION,
						"normalized-quality:v1", 0, 1, List.of("low", "high"), QualityDirection.INCREASING), null));
		org.assertj.core.api.SoftAssertions softly = new org.assertj.core.api.SoftAssertions();
		for (Assessment assessment : assessments) {
			Judgment judgment = raw(JudgmentStatus.PASS, assessment, null, null, null);
			var jury = io.github.markpollack.judge.jury.SimpleJury.<JudgmentContext>builder()
				.judge(context -> judgment)
				.judge(context -> Judgment.pass("other"))
				.votingStrategy(new ConsensusStrategy())
				.build();
			var reading = Verdicts.interpret(jury
				.vote(io.github.markpollack.judge.context.JudgmentContext.builder().goal("bridge test").build()));
			softly.assertThat(reading.readingSupport())
				.as("retained modern assessment %s", assessment)
				.isEqualTo(ReadingSupport.SUPPORTED);
		}
		softly.assertAll();
	}

	@Test
	void absentOrNullRequiredWireNumbersCannotBecomeZero() {
		Map<Object, List<String>> examples = Map.of(new ProbabilityMass("false", 0), List.of("probability"),
				new Certainty(0, "support", SupportOrigin.REPORTED, AssessmentTarget.PROPOSITION, null),
				List.of("value"), new NumericAssessment(0, NumericKind.MEASUREMENT, "scale", 0, 1, List.of(), null),
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
			assertThat(JSON.readValue(json, Proposition.class).value()).isNull();
		}
		assertThat(JSON.readValue("{\"value\":false}", Proposition.class).value()).isFalse();
		assertThat(JSON.readValue("{\"value\":true}", Proposition.class).value()).isTrue();
		assertThatThrownBy(() -> ArtifactRef.ofBytes("missing", null, null)).isInstanceOf(NullPointerException.class);
	}

	@Test
	void temporaryBridgeDoesNotRecurseIntoAnUnboundedHistoricalMap() {
		Map<String, Object> cyclic = new java.util.LinkedHashMap<>();
		cyclic.put("unrecognized", cyclic);
		assertThat(Verdicts.interpret(cyclic).readingSupport()).isEqualTo(ReadingSupport.UNDETERMINED);
		cyclic.put("producerStatus", "pass");
		assertThat(Verdicts.interpret(cyclic).readingSupport()).isEqualTo(ReadingSupport.UNDETERMINED);
	}

}
