/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.jury.interpretation;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import java.util.stream.Stream;

import io.github.markpollack.judge.Judges;
import io.github.markpollack.judge.context.JudgmentContext;
import io.github.markpollack.judge.jury.*;
import io.github.markpollack.judge.result.*;

import static org.assertj.core.api.Assertions.*;

class ModernInterpretationConformanceTest {

	static final ObjectMapper JSON = new ObjectMapper();
	static final JudgmentContext CONTEXT = JudgmentContext.builder().goal("wire conformance").build();
	static final ArtifactRef ARTIFACT = ArtifactRef.ofBytes("evidence",
			"exact bytes\n".getBytes(StandardCharsets.UTF_8), "line:1");
	static final PolicyRef POLICY = new PolicyRef("critical", "1", ARTIFACT.sha256());
	static final EvaluationProvenance PROVENANCE = new EvaluationProvenance("native-evaluator", "rev-7",
			ARTIFACT.sha256(), List.of(ARTIFACT), ARTIFACT,
			List.of(new CalibrationClaim("calibrated:v1", "provider", "declared population",
					"Provider claims native probabilities are calibrated", List.of("native-category:v1"),
					List.of(ARTIFACT))));
	static final Assessment PRODUCT = new Assessment(new Proposition(false),
			new NumericAssessment(1.5, NumericKind.ORDINAL_EXPECTATION, "violations:v1", 0, 2,
					List.of("none", "some", "many"), QualityDirection.DECREASING),
			new Category("violated", List.of("satisfied", "violated", "unknown")));
	static final Certainty CERTAINTY = new Certainty(0.8, "native-category:v1", SupportOrigin.REPORTED,
			AssessmentTarget.CATEGORY, null);
	static final Distribution DISTRIBUTION = new Distribution(AssessmentTarget.CATEGORY, "native-category:v1",
			List.of(new ProbabilityMass("satisfied", 0.1), new ProbabilityMass("violated", 0.8),
					new ProbabilityMass("unknown", 0.1)));

	static Judgment raw(JudgmentStatus status) {
		boolean assessed = status != JudgmentStatus.ERROR && status != JudgmentStatus.NOT_APPLICABLE;
		return new Judgment(status, assessed ? PRODUCT : null, assessed ? CERTAINTY : null,
				assessed ? DISTRIBUTION : null,
				status == JudgmentStatus.ERROR ? JudgmentReasonCode.JUDGE_FAILED
						: status == JudgmentStatus.FAIL ? JudgmentReasonCode.SUBJECT_EMPTY : null,
				"native explanation", List.of(), PROVENANCE, null,
				Map.of("native", Map.of("request", "r-1", "tokens", 7)));
	}

	static Judgment rich(AcceptanceAction action) {
		Judgment j = raw(JudgmentStatus.FAIL);
		return new Judgment(j.producerStatus(), j.assessment(), j.certainty(), j.distribution(), j.reasonCode(),
				j.reasoning(),
				java.util.Arrays.stream(JudgmentStatus.values())
					.map(status -> new io.github.markpollack.judge.result.Check(status.name(), raw(status)))
					.toList(),
				j.provenance(), new AppliedPolicy(POLICY, action, "application explanation"), j.metadata());
	}

	static SimpleJury leaf(Judgment j) {
		var builder = SimpleJury.builder()
			.votingStrategy(new AverageVotingStrategy(0.2, ErrorPolicy.TREAT_AS_FAIL, NotApplicablePolicy.EXCLUDE));
		return builder.judge(new Fixtures.Conditional("judge", j)).build();
	}

	static Map<String, Object> wire(Object value) {
		return JSON.convertValue(value, new TypeReference<>() {
		});
	}

	static void unsupported(Map<String, Object> map) {
		Interpretation i = Verdicts.interpret(map);
		assertThat(i.reading()).isNull();
		assertThat(i.readingSupport()).isEqualTo(ReadingSupport.UNDETERMINED);
		assertThat(i.defects()).isNotEmpty();
	}

	@ParameterizedTest
	@EnumSource(JudgmentStatus.class)
	void everyValidSingleReturnedOutcomeSurvivesSimpleAndMetaIdentity(JudgmentStatus status) throws Exception {
		Judgment j = raw(status);
		for (Jury jury : List.of(leaf(j),
				Juries.meta(new ConsensusStrategy(ErrorPolicy.TREAT_AS_FAIL, NotApplicablePolicy.EXCLUDE),
						new NamedJury("member", leaf(j))))) {
			Verdict v = jury.vote(CONTEXT);
			assertThat(v.aggregated()).isEqualTo(j);
			Verdict restored = JSON.readValue(JSON.writeValueAsBytes(v), Verdict.class);
			assertThat(restored).isEqualTo(v);
			Interpretation i = Verdicts.interpret(restored);
			assertThat(i.defects()).isEmpty();
			assertThat(i.readingSupport()).isEqualTo(ReadingSupport.SUPPORTED);
			assertThat(i.root().judgment()).isEqualTo(JudgmentView.of(j));
			assertThat(i.root().declaredCardinality()).isEqualTo(1);
			assertThat(i.root().judges().getFirst().execution()).isEqualTo("RETURNED");
			assertThat(i.root().evidence()).isNull();
			assertThat(i).isEqualTo(Verdicts.interpret(wire(v)));
		}
	}

	@ParameterizedTest
	@EnumSource(AcceptanceAction.class)
	void completeProductClaimsAndFiveOutcomeChecksSurviveEveryView(AcceptanceAction action) throws Exception {
		Judgment j = rich(action);
		Verdict v = CascadedJury.builder().tier("final", leaf(j), TierPolicy.FINAL_TIER).build().vote(CONTEXT);
		Interpretation i = Verdicts.interpret(v);
		assertThat(i.readingSupport()).isEqualTo(ReadingSupport.SUPPORTED);
		for (Stage stage : List.of(i.root(), i.stages().getFirst())) {
			for (JudgmentView view : List.of(stage.judgment(), stage.judges().getFirst().judgment())) {
				assertThat(view).isEqualTo(JudgmentView.of(j));
				assertThat(view.provenance().calibrationClaims()).isEqualTo(PROVENANCE.calibrationClaims());
				assertThat(view.checks()).extracting(c -> c.judgment().status())
					.containsExactly("pass", "fail", "abstain", "not_applicable", "error");
				assertThat(view.checks()).allSatisfy(c -> {
					assertThat(c.legacyPassed()).isNull();
					assertThat(c.judgment().provenance()).isEqualTo(PROVENANCE);
				});
			}
		}
		assertThat(i.root().judgment().producerStatus()).isEqualTo("fail");
		assertThat(i.root().judgment().status())
			.isEqualTo(action == AcceptanceAction.USE_ASSESSMENT ? "fail" : "abstain");
		assertThat(i.summary()).contains("DECREASING", "violated", "calibrated:v1", action.name(),
				"native explanation");
		assertThat(i.summary()).isEqualTo(Summaries.of(i));
		assertThat(JSON.readValue(JSON.writeValueAsBytes(i), Interpretation.class)).isEqualTo(i);
	}

	@Test
	void policyFailureRetainsRawCauseAndCompleteFacts() {
		Judgment j = Policies.apply(rich(AcceptanceAction.USE_ASSESSMENT), POLICY, input -> {
			throw new IllegalStateException("unavailable");
		});
		Interpretation i = Verdicts.interpret(leaf(j).vote(CONTEXT));
		assertThat(i.reading()).isEqualTo(VerdictReading.NOT_ASSESSED);
		assertThat(i.root().reasonCode()).isEqualTo("policy_failed");
		assertThat(i.root().judgment().producerReasonCode()).isEqualTo("subject_empty");
		assertThat(i.root().judgment().assessment()).isEqualTo(PRODUCT);
		assertThat(i.defects()).isEmpty();
	}

	@Test
	void thrownInvocationIsExplicitlyDifferentFromValidReturnedIdenticalError() {
		var strategy = new AverageVotingStrategy(0.5, ErrorPolicy.TREAT_AS_FAIL);
		Verdict contained = SimpleJury.builder().judge(c -> {
			throw new IllegalStateException("down");
		}).votingStrategy(strategy).build().vote(CONTEXT);
		Judgment error = contained.individual().getFirst();
		Verdict returned = SimpleJury.builder().judge(c -> error).votingStrategy(strategy).build().vote(CONTEXT);
		assertThat(returned.individual()).isEqualTo(contained.individual());
		assertThat(returned.aggregated().status()).isEqualTo(JudgmentStatus.ERROR);
		assertThat(contained.aggregated().status()).isEqualTo(JudgmentStatus.FAIL);
		assertThat(Verdicts.interpret(contained).readingSupport()).isEqualTo(ReadingSupport.SUPPORTED);
		assertThat(Verdicts.interpret(returned).readingSupport()).isEqualTo(ReadingSupport.SUPPORTED);
		Map<String, Object> forged = wire(contained);
		((Map<String, Object>) ((List<?>) forged.get("seats")).getFirst()).put("execution", "RETURNED");
		unsupported(forged);
		Map<String, Object> missing = wire(contained);
		((Map<?, ?>) ((List<?>) missing.get("seats")).getFirst()).remove("execution");
		unsupported(missing);
	}

	@Test
	void containedFlagCannotAuthorizeAssessmentOrPolicy() {
		Map<String, Object> forged = wire(Verdict.single("judge", rich(AcceptanceAction.USE_ASSESSMENT)));
		((Map<String, Object>) ((List<?>) forged.get("seats")).getFirst()).put("execution", "CONTAINED_FAILURE");
		unsupported(forged);
	}

	@Test
	void carriedReductionEvidenceDoesNotImpersonateCurrentIdentityReduction() {
		Judgment prior = new AverageVotingStrategy().aggregate(List.of(Judgment.pass("a"), Judgment.pass("b")),
				Map.of());
		Verdict identity = leaf(prior).vote(CONTEXT);
		Verdict adopted = CascadedJury.builder()
			.tier("final", leaf(prior), TierPolicy.FINAL_TIER)
			.build()
			.vote(CONTEXT);
		for (Verdict v : List.of(identity, adopted)) {
			Interpretation i = Verdicts.interpret(v);
			assertThat(i.readingSupport()).isEqualTo(ReadingSupport.SUPPORTED);
			assertThat(i.root().evidence()).isNull();
			assertThat(i.root().judgment().metadata()).isEqualTo(prior.metadata());
		}
	}

	@Test
	void oneSurvivorOfTwoMetaMembersNeverBecomesIdentity() {
		Jury meta = Juries.meta(new ConsensusStrategy(ErrorPolicy.PROPAGATE, NotApplicablePolicy.EXCLUDE),
				new NamedJury("ok", leaf(Judgment.pass("ok"))),
				new NamedJury("broken", Fixtures.throwing(new IllegalStateException("down"))));
		Verdict v = meta.vote(CONTEXT);
		assertThat(v.declaredCardinality()).isEqualTo(2);
		assertThat(Verdicts.interpret(v).reading()).isEqualTo(VerdictReading.NOT_ASSESSED);
		var forged = wire(v);
		forged.put("declaredCardinality", 1);
		unsupported(forged);
	}

	@Test
	void unknownMixedFractionalMissingAndCoercedVersionsNeverReadAccepted() {
		for (Object version : List.of(1, 3, "2", 2.0, 2.9, false)) {
			Map<String, Object> map = wire(Verdict.single("seat", Judgment.pass("ok")));
			map.put("schemaVersion", version);
			unsupported(map);
			assertThatThrownBy(() -> JSON.convertValue(map, Verdict.class))
				.isInstanceOf(IllegalArgumentException.class);
		}
		for (String field : List.of("schemaVersion", "declaredCardinality")) {
			Map<String, Object> map = wire(Verdict.single("seat", Judgment.pass("ok")));
			map.remove(field);
			unsupported(map);
			assertThatThrownBy(() -> JSON.convertValue(map, Verdict.class))
				.isInstanceOf(IllegalArgumentException.class);
		}
		for (String edge : List.of("aggregated", "individual", "individualByName", "check")) {
			Map<String, Object> map = wire(Verdict.single("seat", rich(AcceptanceAction.USE_ASSESSMENT)));
			Map<String, Object> child = switch (edge) {
				case "aggregated" -> (Map<String, Object>) map.get(edge);
				case "individual" -> (Map<String, Object>) ((List<?>) map.get(edge)).getFirst();
				case "individualByName" -> (Map<String, Object>) ((Map<?, ?>) map.get(edge)).get("seat");
				default ->
					(Map<String, Object>) ((Map<?, ?>) ((List<?>) ((Map<?, ?>) map.get("aggregated")).get("checks"))
						.getFirst()).get("judgment");
			};
			child.remove("schemaVersion");
			unsupported(map);
		}
	}

	static Stream<Arguments> textualTokens() {
		return Stream.of("/reasoning", "/assessment/category/selected", "/assessment/category/alternatives/0",
				"/assessment/numeric/scaleId", "/assessment/numeric/levels/0", "/certainty/metricId",
				"/distribution/domainId", "/distribution/masses/0/alternative", "/provenance/instrumentId",
				"/provenance/revision", "/provenance/evidence/0/id", "/provenance/evidence/0/selector",
				"/provenance/calibrationClaims/0/issuer", "/provenance/calibrationClaims/0/statement",
				"/provenance/calibrationClaims/0/signalIds/0", "/policyApplication/policy/id",
				"/policyApplication/policy/revision", "/policyApplication/reason", "/checks/0/id",
				"/checks/0/judgment/reasoning")
			.flatMap(path -> Stream.of(true, 123, 1.25).map(value -> Arguments.of(path, value)));
	}

	@ParameterizedTest
	@MethodSource("textualTokens")
	void modernTextRequiresTextTokensAcrossNestedValues(String path, Object token) {
		JsonNode original = JSON.valueToTree(Verdict.single("seat", rich(AcceptanceAction.USE_ASSESSMENT)));
		String text = original.at("/aggregated" + path).textValue();
		assertThat(text).as(path).isNotNull();
		// Change every redundant copy and matching domain member together: otherwise an
		// identity/domain mismatch could hide the scalar-to-text coercion being tested.
		JsonNode control = replaceText(original.deepCopy(), text, JSON.valueToTree(token.toString()));
		assertThat(Verdicts.interpret(wire(control)).readingSupport()).isEqualTo(ReadingSupport.SUPPORTED);
		JsonNode malformed = replaceText(original.deepCopy(), text, JSON.valueToTree(token));
		unsupported(wire(malformed));
	}

	static JsonNode replaceText(JsonNode value, String text, JsonNode replacement) {
		if (value.isTextual() && value.textValue().equals(text))
			return replacement;
		if (value instanceof ObjectNode object) {
			List<String> names = new ArrayList<>();
			object.fieldNames().forEachRemaining(names::add);
			for (String name : names)
				object.set(name, replaceText(object.get(name), text, replacement));
		}
		else if (value instanceof ArrayNode array) {
			for (int index = 0; index < array.size(); index++)
				array.set(index, replaceText(array.get(index), text, replacement));
		}
		return value;
	}

	@ParameterizedTest
	@MethodSource("scalarTokens")
	void scalarCategoryAndReasoningCannotBecomeAccepted(Object token) {
		Judgment j = new Judgment(JudgmentStatus.PASS, new Assessment(null, null,
				new Category(token.toString(), List.of(token.toString(), "other"))), null, null, null,
				token.toString(), List.of(), null, null, Map.of("opaque", token));
		JsonNode original = JSON.valueToTree(Verdict.single("seat", j));
		assertThat(Verdicts.interpret(wire(original)).reading()).isEqualTo(VerdictReading.ACCEPTED);
		unsupported(wire(replaceText(original, token.toString(), JSON.valueToTree(token))));
	}

	static Stream<Object> scalarTokens() {
		return Stream.of(true, 123, 1.25);
	}

	@Test
	void unversionedModernFieldsCannotHideBehindLegacyPass() {
		Map<String, Object> legacy = Fixtures.stored(Fixtures.EXAMPLE_ONE);
		((Map<String, Object>) legacy.get("aggregated")).put("producerStatus", "pass");
		unsupported(legacy);
		Map<String, Object> other = Fixtures.stored(Fixtures.EXAMPLE_ONE);
		((Map<String, Object>) other.get("aggregated")).put("policyApplication", Map.of());
		unsupported(other);
	}

	@Test
	void opaqueMetadataCannotSelectModernProtocol() {
		Map<String, Object> historical = Fixtures.stored(Fixtures.EXAMPLE_ONE);
		((Map<String, Object>) ((Map<?, ?>) historical.get("aggregated")).get("metadata")).put("opaque",
				Map.of("schemaVersion", 99, "producerStatus", "anything"));
		Map<String, Object> child = (Map<String, Object>) ((Map<?, ?>) ((List<?>) historical.get("compositeAttempts"))
			.getFirst()).get("verdict");
		((Map<String, Object>) ((Map<?, ?>) child.get("aggregated")).get("metadata")).put("opaque",
				Map.of("schemaVersion", 99, "producerStatus", "anything"));
		assertThat(Verdicts.interpret(historical).sourceVersion()).isEqualTo(1);
		assertThat(Verdicts.interpret(historical).readingSupport()).isEqualTo(ReadingSupport.SUPPORTED);
	}

	@Test
	void forgedSuccessfulStatusAndUnknownSemanticValuesFailClosed() {
		for (String field : List.of("status", "score", "unrecognizedSemantic")) {
			var v = wire(Verdict.single("seat", rich(AcceptanceAction.ESCALATE)));
			((Map<String, Object>) v.get("aggregated")).put(field, "pass");
			unsupported(v);
		}
		var map = wire(Verdict.single("seat", Judgment.pass("ok")));
		((Map<String, Object>) map.get("aggregated")).put("producerStatus", "future_status");
		unsupported(map);
	}

	@Test
	void fractionalAndMissingPositionsAndCardinalityAreRejectedByOrdinaryMapper() {
		for (Object value : List.of(0.5, "0", false)) {
			var map = wire(Verdict.single("seat", Judgment.pass("ok")));
			((Map<String, Object>) ((List<?>) map.get("seats")).getFirst()).put("position", value);
			unsupported(map);
			assertThatThrownBy(() -> JSON.convertValue(map, Verdict.class))
				.isInstanceOf(IllegalArgumentException.class);
			var count = wire(Verdict.single("seat", Judgment.pass("ok")));
			count.put("declaredCardinality", value);
			unsupported(count);
			assertThatThrownBy(() -> JSON.convertValue(count, Verdict.class))
				.isInstanceOf(IllegalArgumentException.class);
		}
	}

	@Test
	void cyclesAndExcessDepthNeverOverflowTheReader() {
		Map<String, Object> map = wire(Verdict.single("seat", Judgment.pass("ok")));
		map.put("loop", map);
		unsupported(map);
		Map<String, Object> deep = wire(Verdict.single("seat", Judgment.pass("ok")));
		Map<String, Object> cursor = deep;
		for (int i = 0; i < 150; i++) {
			Map<String, Object> next = new LinkedHashMap<>();
			cursor.put("next", next);
			cursor = next;
		}
		unsupported(deep);
	}

	@Test
	void oldFalseChecksKeepExplicitUnknownFinerStatusAndLabels() {
		Interpretation i = Verdicts.interpret(Fixtures.stored(Fixtures.EXAMPLE_TWO));
		Check c = i.root().judges().get(1).checks().getFirst();
		assertThat(c.legacyPassed()).isFalse();
		assertThat(c.judgment()).isNull();
		var map = Fixtures.stored(Fixtures.EXAMPLE_ONE);
		((Map<String, Object>) map.get("aggregated")).put("label", "legacy-only-domain-unknown");
		assertThat(Verdicts.interpret(map).root().judgment().legacyLabel()).isEqualTo("legacy-only-domain-unknown");
		assertThat(Verdicts.interpret(map).root().judgment().assessment()).isNull();
	}

	static Verdict cascade(AcceptanceAction first, AcceptanceAction last) {
		return CascadedJury.builder()
			.tier("fast", leaf(rich(first)), TierPolicy.STOP_ON_USABLE_ASSESSMENT)
			.tier("final", leaf(rich(last)), TierPolicy.FINAL_TIER)
			.build()
			.vote(CONTEXT);
	}

	@ParameterizedTest
	@EnumSource(AcceptanceAction.class)
	void cascadeContinuesOnlyExplicitEscalationAndRetainsFinalEscalation(AcceptanceAction action) {
		Verdict v = cascade(action, AcceptanceAction.ESCALATE);
		Interpretation i = Verdicts.interpret(v);
		assertThat(i.defects()).isEmpty();
		assertThat(i.readingSupport()).isEqualTo(ReadingSupport.SUPPORTED);
		assertThat(i.stages()).hasSize(action == AcceptanceAction.ESCALATE ? 2 : 1);
		assertThat(i.decidedBy().stage()).isEqualTo(action == AcceptanceAction.ESCALATE ? "final" : "fast");
		assertThat(i.reading())
			.isEqualTo(action == AcceptanceAction.USE_ASSESSMENT ? VerdictReading.REJECTED : VerdictReading.UNDECIDED);
	}

	@Test
	void routingTamperingSelectedCopyAndFailedTierCannotPass() {
		var wire = wire(cascade(AcceptanceAction.ESCALATE, AcceptanceAction.USE_ASSESSMENT));
		var first = (Map<String, Object>) ((List<?>) wire.get("compositeAttempts")).getFirst();
		first.put("policy", "FINAL_TIER");
		unsupported(wire);
		var copy = wire(cascade(AcceptanceAction.ESCALATE, AcceptanceAction.USE_ASSESSMENT));
		((Map<String, Object>) copy.get("aggregated")).put("reasoning", "altered selected copy");
		unsupported(copy);
		var missing = wire(cascade(AcceptanceAction.ESCALATE, AcceptanceAction.USE_ASSESSMENT));
		((Map<String, Object>) missing.get("decision")).put("tier", "absent");
		unsupported(missing);
	}

	@Test
	void missingPolicyAndContainedTierFailureAreTerminalAndRetained() {
		for (Jury first : List.of(leaf(Judgment.pass("no policy")), SimpleJury.builder().judge(c -> {
			throw new IllegalStateException("down");
		}).votingStrategy(new AverageVotingStrategy(ErrorPolicy.TREAT_AS_FAIL)).build())) {
			Verdict v = CascadedJury.builder()
				.tier("fast", first, TierPolicy.STOP_ON_USABLE_ASSESSMENT)
				.tier("final", leaf(rich(AcceptanceAction.USE_ASSESSMENT)), TierPolicy.FINAL_TIER)
				.build()
				.vote(CONTEXT);
			Interpretation i = Verdicts.interpret(v);
			assertThat(i.defects()).isEmpty();
			assertThat(i.reading()).isEqualTo(VerdictReading.NOT_ASSESSED);
			assertThat(i.root().reasonCode()).isEqualTo("stage_failed");
			assertThat(i.stages()).hasSize(1);
			assertThat(i.stages().getFirst().reason()).isEqualTo("invalid_tier_result");
		}
	}

	@Test
	void equalityRequiresFullPolicyProvenanceAndNamedValues() {
		for (String field : List.of("policyApplication", "provenance", "assessment", "checks", "certainty",
				"distribution")) {
			var map = wire(Verdict.single("seat", rich(AcceptanceAction.USE_ASSESSMENT)));
			if (field.equals("checks"))
				((Map<String, Object>) map.get("aggregated")).put(field, List.of());
			else
				((Map<?, ?>) map.get("aggregated")).remove(field);
			unsupported(map);
		}
		var map = wire(Verdict.single("seat", rich(AcceptanceAction.USE_ASSESSMENT)));
		((Map<String, Object>) ((Map<?, ?>) map.get("individualByName")).get("seat")).put("reasoning",
				"different named result");
		unsupported(map);
	}

	@Test
	void declaredCountAndDecisionCannotDisguiseNumericReversal() {
		Verdict identity = leaf(rich(AcceptanceAction.USE_ASSESSMENT)).vote(CONTEXT);
		var map = wire(identity);
		map.put("aggregated", wire(Judgment.pass("numeric reversal")));
		unsupported(map);
		var count = wire(identity);
		count.put("declaredCardinality", 2);
		unsupported(count);
		var undecided = wire(identity);
		undecided.put("aggregated", wire(Judgment.error(JudgmentReasonCode.AGGREGATION_FAILED, "forged")));
		undecided.put("decision", Map.of("kind", "undecided"));
		unsupported(undecided);
	}

	@Test
	void everyBuiltInReductionAgreesWithRetainedOperationalInputsAndPolicies() {
		List<Judgment> inputs = List.of(Judgment.pass("positive"), raw(JudgmentStatus.FAIL),
				Judgment.abstain("uncertain"), Judgment.error(JudgmentReasonCode.JUDGE_FAILED, "broken"),
				Judgment.notApplicable("outside scope"));
		for (ErrorPolicy errors : ErrorPolicy.values())
			for (NotApplicablePolicy exclusions : NotApplicablePolicy.values()) {
				List<VotingStrategy> strategies = List.of(new ConsensusStrategy(errors, exclusions),
						new MajorityVotingStrategy(TiePolicy.ABSTAIN, errors, exclusions),
						new AllMustPassStrategy(errors, exclusions), new AverageVotingStrategy(.5, errors, exclusions),
						new MedianVotingStrategy(.5, errors, exclusions),
						new WeightedAverageStrategy(.5, errors, exclusions),
						new ConjunctiveStrategy(.5, errors, exclusions));
				for (VotingStrategy strategy : strategies) {
					Map<String, Judgment> names = new LinkedHashMap<>();
					for (int i = 0; i < inputs.size(); i++)
						names.put("judge-" + i, inputs.get(i));
					Verdict v = Verdict.of(strategy.aggregate(inputs, Map.of()), names);
					Interpretation i = Verdicts.interpret(v);
					assertThat(i.defects()).as("%s / %s / %s", strategy.getName(), errors, exclusions).isEmpty();
					assertThat(i.readingSupport()).as("%s / %s / %s", strategy.getName(), errors, exclusions)
						.isEqualTo(ReadingSupport.SUPPORTED);
				}
			}
	}

	@Test
	void numericReductionCannotInventAQualityValueEvenWhenStatusMatchesThreshold() {
		Verdict v = SimpleJury.builder()
			.judge(c -> Judgment.pass("a"))
			.judge(c -> Judgment.fail("b"))
			.votingStrategy(new AverageVotingStrategy(.4))
			.build()
			.vote(CONTEXT);
		Map<String, Object> map = wire(v);
		((Map<String, Object>) ((Map<?, ?>) ((Map<?, ?>) map.get("aggregated")).get("assessment")).get("numeric"))
			.put("value", .9);
		unsupported(map);
	}

	@Test
	void modernViewsAndNestedMetadataAreRecursivelyImmutable() {
		Interpretation i = Verdicts.interpret(Verdict.single("seat", rich(AcceptanceAction.USE_ASSESSMENT)));
		assertThatThrownBy(() -> ((Map<String, Object>) i.root().judgment().metadata().get("native")).put("new", "bad"))
			.isInstanceOf(UnsupportedOperationException.class);
		assertThatThrownBy(() -> i.root().judgment().checks().clear())
			.isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void aUsedMetaMemberCannotForgeContainedInvocationToEvadeIdentity() {
		Judgment returned = Judgment.error(JudgmentReasonCode.JUDGE_FAILED, "reported error");
		Jury child = SimpleJury.builder().judge(c -> returned).votingStrategy(new ConsensusStrategy()).build();
		Verdict meta = Juries.meta(new AverageVotingStrategy(ErrorPolicy.TREAT_AS_FAIL), new NamedJury("member", child))
			.vote(CONTEXT);
		Map<String, Object> map = wire(meta);
		((Map<String, Object>) ((List<?>) map.get("seats")).getFirst()).put("execution", "CONTAINED_FAILURE");
		map.put("aggregated",
				wire(new AverageVotingStrategy(ErrorPolicy.TREAT_AS_FAIL).aggregate(List.of(returned), Map.of())));
		unsupported(map);
	}

}
