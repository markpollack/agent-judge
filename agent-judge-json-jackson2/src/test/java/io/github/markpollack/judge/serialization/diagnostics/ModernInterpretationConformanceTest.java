/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.serialization.diagnostics;

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

import io.github.markpollack.judge.completion.CompletionEvidence;
import io.github.markpollack.judge.voting.AllEligiblePassStrategy;
import io.github.markpollack.judge.voting.AverageVotingStrategy;
import io.github.markpollack.judge.jury.CascadedJury;
import io.github.markpollack.judge.voting.ConjunctiveStrategy;
import io.github.markpollack.judge.voting.ConsensusStrategy;
import io.github.markpollack.judge.voting.ErrorHandling;
import io.github.markpollack.judge.jury.Juries;
import io.github.markpollack.judge.jury.Jury;
import io.github.markpollack.judge.voting.MajorityVotingStrategy;
import io.github.markpollack.judge.voting.MedianVotingStrategy;
import io.github.markpollack.judge.jury.NamedJury;
import io.github.markpollack.judge.voting.ExclusionHandling;
import io.github.markpollack.judge.jury.SimpleJury;
import io.github.markpollack.judge.voting.TieBreakRule;
import io.github.markpollack.judge.verdict.RoutingRule;
import io.github.markpollack.judge.verdict.Verdict;
import io.github.markpollack.judge.voting.VotingStrategy;
import io.github.markpollack.judge.voting.WeightedAverageStrategy;
import io.github.markpollack.judge.judgment.BooleanFinding;
import io.github.markpollack.judge.judgment.CategoryFinding;
import io.github.markpollack.judge.judgment.Confidence;
import io.github.markpollack.judge.judgment.Finding;
import io.github.markpollack.judge.judgment.FindingTarget;
import io.github.markpollack.judge.judgment.Judgment;
import io.github.markpollack.judge.judgment.JudgmentReasonCode;
import io.github.markpollack.judge.judgment.JudgmentStatus;
import io.github.markpollack.judge.judgment.NumericFinding;
import io.github.markpollack.judge.judgment.NumericKind;
import io.github.markpollack.judge.judgment.ProbabilityDistribution;
import io.github.markpollack.judge.judgment.ProbabilityMass;
import io.github.markpollack.judge.judgment.QualityDirection;
import io.github.markpollack.judge.judgment.SupportOrigin;
import io.github.markpollack.judge.provenance.ArtifactRef;
import io.github.markpollack.judge.provenance.CalibrationClaim;
import io.github.markpollack.judge.policy.PolicyAction;
import io.github.markpollack.judge.provenance.Provenance;

import static org.assertj.core.api.Assertions.*;

class ModernInterpretationConformanceTest {

	static final ObjectMapper JSON = new ObjectMapper()
		.registerModule(io.github.markpollack.judge.serialization.ResultJson.module());
	static final CompletionEvidence CONTEXT = CompletionEvidence.builder().request("wire conformance").build();
	static final ArtifactRef ARTIFACT = ArtifactRef.ofBytes("evidence",
			"exact bytes\n".getBytes(StandardCharsets.UTF_8), "line:1");

	static final Provenance PROVENANCE = new Provenance("native-evaluator", "rev-7", ARTIFACT.sha256(),
			List.of(ARTIFACT), ARTIFACT,
			List.of(new CalibrationClaim("calibrated:v1", "provider", "declared population",
					"Provider claims native probabilities are calibrated", List.of("native-category:v1"),
					List.of(ARTIFACT))));
	static final Finding PRODUCT = new Finding(new BooleanFinding(false),
			new NumericFinding(1.5, NumericKind.ORDINAL_EXPECTATION, "violations:v1", 0, 2,
					List.of("none", "some", "many"), QualityDirection.DECREASING),
			new CategoryFinding("violated", List.of("satisfied", "violated", "unknown")));
	static final Confidence CERTAINTY = new Confidence(0.8, "native-category:v1", SupportOrigin.REPORTED,
			FindingTarget.CATEGORY, null);
	static final ProbabilityDistribution DISTRIBUTION = new ProbabilityDistribution(FindingTarget.CATEGORY,
			"native-category:v1", List.of(new ProbabilityMass("satisfied", 0.1), new ProbabilityMass("violated", 0.8),
					new ProbabilityMass("unknown", 0.1)));

	static Judgment raw(JudgmentStatus status) {
		boolean assessed = status != JudgmentStatus.ERROR && status != JudgmentStatus.NOT_APPLICABLE;
		return new Judgment(status, assessed ? PRODUCT : null, assessed ? CERTAINTY : null,
				assessed ? DISTRIBUTION : null,
				status == JudgmentStatus.ERROR ? JudgmentReasonCode.JUDGE_FAILED
						: status == JudgmentStatus.FAIL ? JudgmentReasonCode.SUBJECT_EMPTY : null,
				"native explanation", List.of(), PROVENANCE, Map.of("native", Map.of("request", "r-1", "tokens", 7)));
	}

	static Judgment rich(PolicyAction action) {
		Judgment j = raw(JudgmentStatus.FAIL);
		return new Judgment(j.producerStatus(), j.finding(), j.confidence(), j.probabilityDistribution(),
				j.reasonCode(), j.reasoning(),
				java.util.Arrays.stream(JudgmentStatus.values())
					.map(status -> new io.github.markpollack.judge.judgment.Check(status.name(), raw(status)))
					.toList(),
				j.provenance(), j.metadata());
	}

	static SimpleJury leaf(Judgment j) {
		var builder = SimpleJury.builder()
			.votingStrategy(new AverageVotingStrategy(0.2, ErrorHandling.TREAT_AS_FAIL, ExclusionHandling.EXCLUDE));
		return builder
			.seat(io.github.markpollack.judge.jury.JudgeSeat.named("judge", new Fixtures.Conditional("judge", j))
				.notApplicableWhen(Fixtures.EXCLUSION))
			.build();
	}

	static Map<String, Object> wire(Object value) {
		return JSON.convertValue(value, new TypeReference<>() {
		});
	}

	static void unsupported(Map<String, Object> map) {
		StoredReading i = StoredVerdicts.interpret(map);
		assertThat(i.outcome()).isNull();
		assertThat(i.readingSupport()).isEqualTo(ReadingSupport.UNDETERMINED);
		assertThat(i.defects()).isNotEmpty();
	}

	@ParameterizedTest
	@EnumSource(JudgmentStatus.class)
	void everyValidSingleReturnedOutcomeSurvivesSimpleAndMetaIdentity(JudgmentStatus status) throws Exception {
		Judgment j = raw(status);
		for (Jury jury : List.of(leaf(j),
				Juries.meta(new ConsensusStrategy(ErrorHandling.TREAT_AS_FAIL, ExclusionHandling.EXCLUDE),
						new NamedJury("member", leaf(j))))) {
			Verdict v = jury.vote();
			assertThat(v.judgment()).isEqualTo(j);
			Verdict restored = JSON.readValue(JSON.writeValueAsBytes(v), Verdict.class);
			assertThat(restored).isEqualTo(v);
			StoredReading i = StoredVerdicts.interpret(restored);
			assertThat(i.defects()).isEmpty();
			assertThat(i.readingSupport()).isEqualTo(ReadingSupport.SUPPORTED);
			assertThat(i.root().judgment()).isEqualTo(JudgmentView.of(j));
			assertThat(i.root().declaredCardinality()).isEqualTo(1);
			assertThat(i.root().judges().getFirst().execution()).isEqualTo("RETURNED");
			assertThat(i.root().evidence()).isNull();
			assertThat(i).isEqualTo(StoredVerdicts.interpret(wire(v)));
		}
	}

	@ParameterizedTest
	@EnumSource(PolicyAction.class)
	void completeProductClaimsAndFiveOutcomeChecksSurviveEveryView(PolicyAction action) throws Exception {
		Judgment j = rich(action);
		Verdict v = CascadedJury.builder().tier("final", leaf(j), RoutingRule.FINAL_TIER).build().vote();
		StoredReading i = StoredVerdicts.interpret(v);
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
		assertThat(i.root().judgment().status()).isEqualTo("fail");
		assertThat(i.summary()).contains("DECREASING", "violated", "calibrated:v1", "native explanation");
		assertThat(i.summary()).isEqualTo(Summaries.of(i));
		assertThat(JSON.readValue(JSON.writeValueAsBytes(i), StoredReading.class)).isEqualTo(i);
	}

	@Test
	void thrownInvocationIsExplicitlyDifferentFromValidReturnedIdenticalError() {
		var strategy = new AverageVotingStrategy(0.5, ErrorHandling.TREAT_AS_FAIL);
		Verdict contained = SimpleJury.builder().judge(() -> {
			throw new IllegalStateException("down");
		}).votingStrategy(strategy).build().vote();
		Judgment error = contained.individual().getFirst();
		Verdict returned = SimpleJury.builder().judge(() -> error).votingStrategy(strategy).build().vote();
		assertThat(returned.individual()).isEqualTo(contained.individual());
		assertThat(returned.judgment().status()).isEqualTo(JudgmentStatus.ERROR);
		assertThat(contained.judgment().status()).isEqualTo(JudgmentStatus.FAIL);
		assertThat(StoredVerdicts.interpret(contained).readingSupport()).isEqualTo(ReadingSupport.SUPPORTED);
		assertThat(StoredVerdicts.interpret(returned).readingSupport()).isEqualTo(ReadingSupport.SUPPORTED);
		Map<String, Object> forged = wire(contained);
		((Map<String, Object>) ((List<?>) forged.get("seats")).getFirst()).put("execution", "RETURNED");
		unsupported(forged);
		Map<String, Object> missing = wire(contained);
		((Map<?, ?>) ((List<?>) missing.get("seats")).getFirst()).remove("execution");
		unsupported(missing);
	}

	@Test
	void containedFlagCannotAuthorizeAssessmentOrPolicy() {
		Map<String, Object> forged = wire(Verdict.single("judge", rich(PolicyAction.RELY)));
		((Map<String, Object>) ((List<?>) forged.get("seats")).getFirst()).put("execution", "CONTAINED_FAILURE");
		unsupported(forged);
	}

	@Test
	void carriedReductionEvidenceDoesNotImpersonateCurrentIdentityReduction() {
		Judgment prior = new AverageVotingStrategy()
			.aggregate(io.github.markpollack.judge.voting.Ballots.of(List.of(Judgment.pass("a"), Judgment.pass("b"))));
		Verdict identity = leaf(prior).vote();
		Verdict adopted = CascadedJury.builder().tier("final", leaf(prior), RoutingRule.FINAL_TIER).build().vote();
		for (Verdict v : List.of(identity, adopted)) {
			StoredReading i = StoredVerdicts.interpret(v);
			assertThat(i.readingSupport()).isEqualTo(ReadingSupport.SUPPORTED);
			assertThat(i.root().evidence()).isNull();
			assertThat(i.root().judgment().metadata()).isEqualTo(prior.metadata());
		}
	}

	@Test
	void oneSurvivorOfTwoMetaMembersNeverBecomesIdentity() {
		Jury meta = Juries.meta(new ConsensusStrategy(ErrorHandling.PROPAGATE, ExclusionHandling.EXCLUDE),
				new NamedJury("ok", leaf(Judgment.pass("ok"))),
				new NamedJury("broken", Fixtures.throwing(new IllegalStateException("down"))));
		Verdict v = meta.vote();
		assertThat(v.declaredCardinality()).isEqualTo(2);
		assertThat(StoredVerdicts.interpret(v).outcome()).isEqualTo(RequirementOutcome.NOT_ASSESSED);
		var forged = wire(v);
		forged.put("declaredCardinality", 1);
		unsupported(forged);
	}

	@Test
	void unknownMixedFractionalMissingAndCoercedVersionsNeverReadAccepted() {
		for (Object version : List.of(1, 2, 3, "4", 4.0, 4.9, false)) {
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
		for (String edge : List.of("judgment", "individual", "individualByName", "check")) {
			Map<String, Object> map = wire(Verdict.single("seat", rich(PolicyAction.RELY)));
			Map<String, Object> child = switch (edge) {
				case "judgment" -> (Map<String, Object>) map.get(edge);
				case "individual" -> (Map<String, Object>) ((List<?>) map.get(edge)).getFirst();
				case "individualByName" -> (Map<String, Object>) ((Map<?, ?>) map.get(edge)).get("seat");
				default ->
					(Map<String, Object>) ((Map<?, ?>) ((List<?>) ((Map<?, ?>) map.get("judgment")).get("checks"))
						.getFirst()).get("judgment");
			};
			child.remove("schemaVersion");
			unsupported(map);
		}
	}

	static Stream<Arguments> textualTokens() {
		return Stream
			.of("/reasoning", "/finding/category/selected", "/finding/category/alternatives/0",
					"/finding/numeric/scaleId", "/finding/numeric/levels/0", "/confidence/metricId",
					"/probabilityDistribution/domainId", "/probabilityDistribution/masses/0/alternative",
					"/provenance/instrumentId", "/provenance/revision", "/provenance/evidence/0/id",
					"/provenance/evidence/0/selector", "/provenance/calibrationClaims/0/issuer",
					"/provenance/calibrationClaims/0/statement", "/provenance/calibrationClaims/0/signalIds/0",
					"/checks/0/id", "/checks/0/judgment/reasoning")
			.flatMap(path -> Stream.of(true, 123, 1.25).map(value -> Arguments.of(path, value)));
	}

	@ParameterizedTest
	@MethodSource("textualTokens")
	void modernTextRequiresTextTokensAcrossNestedValues(String path, Object token) {
		JsonNode original = JSON.valueToTree(Verdict.single("seat", rich(PolicyAction.RELY)));
		String text = original.at("/judgment" + path).textValue();
		assertThat(text).as(path).isNotNull();
		// Change every redundant copy and matching domain member together: otherwise an
		// identity/domain mismatch could hide the scalar-to-text coercion being tested.
		JsonNode control = replaceText(original.deepCopy(), text, JSON.valueToTree(token.toString()));
		assertThat(StoredVerdicts.interpret(wire(control)).readingSupport()).isEqualTo(ReadingSupport.SUPPORTED);
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
		Judgment j = new Judgment(JudgmentStatus.PASS,
				new Finding(null, null, new CategoryFinding(token.toString(), List.of(token.toString(), "other"))),
				null, null, null, token.toString(), List.of(), null, Map.of("opaque", token));
		JsonNode original = JSON.valueToTree(Verdict.single("seat", j));
		assertThat(StoredVerdicts.interpret(wire(original)).outcome()).isEqualTo(RequirementOutcome.SATISFIED);
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
		assertThat(StoredVerdicts.interpret(historical).sourceVersion()).isEqualTo(1);
		assertThat(StoredVerdicts.interpret(historical).readingSupport()).isEqualTo(ReadingSupport.SUPPORTED);
	}

	@Test
	void forgedSuccessfulStatusAndUnknownSemanticValuesFailClosed() {
		for (String field : List.of("status", "score", "unrecognizedSemantic")) {
			var v = wire(Verdict.single("seat", rich(PolicyAction.ESCALATE)));
			((Map<String, Object>) v.get("judgment")).put(field, "pass");
			unsupported(v);
		}
		var map = wire(Verdict.single("seat", Judgment.pass("ok")));
		((Map<String, Object>) map.get("judgment")).put("producerStatus", "future_status");
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
		assertThatThrownBy(() -> StoredVerdicts.interpret(map)).isInstanceOfSatisfying(
				io.github.markpollack.judge.portable.PreservationLimitException.class,
				e -> assertThat(e.original()).isSameAs(map));
		Map<String, Object> deep = wire(Verdict.single("seat", Judgment.pass("ok")));
		Map<String, Object> cursor = deep;
		for (int i = 0; i < 150; i++) {
			Map<String, Object> next = new LinkedHashMap<>();
			cursor.put("next", next);
			cursor = next;
		}
		assertThatThrownBy(() -> StoredVerdicts.interpret(deep)).isInstanceOfSatisfying(
				io.github.markpollack.judge.portable.PreservationLimitException.class,
				e -> assertThat(e.original()).isSameAs(deep));
	}

	@Test
	void oldFalseChecksKeepExplicitUnknownFinerStatusAndLabels() {
		StoredReading i = StoredVerdicts.interpret(Fixtures.stored(Fixtures.EXAMPLE_TWO));
		Check c = i.root().judges().get(1).checks().getFirst();
		assertThat(c.legacyPassed()).isFalse();
		assertThat(c.judgment()).isNull();
		var map = Fixtures.stored(Fixtures.EXAMPLE_ONE);
		((Map<String, Object>) map.get("aggregated")).put("label", "legacy-only-domain-unknown");
		assertThat(StoredVerdicts.interpret(map).root().judgment().legacyLabel())
			.isEqualTo("legacy-only-domain-unknown");
		assertThat(StoredVerdicts.interpret(map).root().judgment().finding()).isNull();
	}

	@Test
	void equalityRequiresFullPolicyProvenanceAndNamedValues() {
		for (String field : List.of("provenance", "finding", "checks", "confidence", "probabilityDistribution")) {
			var map = wire(Verdict.single("seat", rich(PolicyAction.RELY)));
			if (field.equals("checks"))
				((Map<String, Object>) map.get("judgment")).put(field, List.of());
			else
				((Map<?, ?>) map.get("judgment")).remove(field);
			unsupported(map);
		}
		var map = wire(Verdict.single("seat", rich(PolicyAction.RELY)));
		((Map<String, Object>) ((Map<?, ?>) map.get("individualByName")).get("seat")).put("reasoning",
				"different named result");
		unsupported(map);
	}

	@Test
	void declaredCountAndDecisionCannotDisguiseNumericReversal() {
		Verdict identity = leaf(rich(PolicyAction.RELY)).vote();
		var map = wire(identity);
		map.put("judgment", wire(Judgment.pass("numeric reversal")));
		unsupported(map);
		var count = wire(identity);
		count.put("declaredCardinality", 2);
		unsupported(count);
		var undecided = wire(identity);
		undecided.put("judgment", wire(Judgment.error(JudgmentReasonCode.AGGREGATION_FAILED, "forged")));
		undecided.put("provenance", Map.of("kind", "undecided"));
		unsupported(undecided);
	}

	@Test
	void everyBuiltInReductionAgreesWithRetainedOperationalInputsAndPolicies() {
		List<Judgment> inputs = List.of(Judgment.pass("positive"), raw(JudgmentStatus.FAIL),
				Judgment.abstain("uncertain"), Judgment.error(JudgmentReasonCode.JUDGE_FAILED, "broken"),
				Judgment.notApplicable("outside scope"));
		for (ErrorHandling errors : ErrorHandling.values())
			for (ExclusionHandling exclusions : ExclusionHandling.values()) {
				List<VotingStrategy> strategies = List.of(new ConsensusStrategy(errors, exclusions),
						new MajorityVotingStrategy(TieBreakRule.ABSTAIN, errors, exclusions),
						new AllEligiblePassStrategy(errors, exclusions),
						new AverageVotingStrategy(.5, errors, exclusions),
						new MedianVotingStrategy(.5, errors, exclusions),
						new WeightedAverageStrategy(.5, errors, exclusions),
						new ConjunctiveStrategy(.5, errors, exclusions));
				for (VotingStrategy strategy : strategies) {
					Map<String, Judgment> names = new LinkedHashMap<>();
					for (int i = 0; i < inputs.size(); i++)
						names.put("judge-" + i, inputs.get(i));
					var builder = Verdict.advancedBuilder();
					Verdict raw = Verdict.of(strategy.aggregate(io.github.markpollack.judge.voting.Ballots.of(inputs)),
							names);
					Verdict v = builder.judgment(raw.judgment())
						.individual(raw.individual())
						.individualByName(raw.individualByName())
						.seats(raw.seats())
						.provenance(raw.provenance())
						.rule(io.github.markpollack.judge.voting.RetainedRule.of(strategy))
						.build();
					StoredReading i = StoredVerdicts.interpret(v);
					assertThat(i.defects()).as("%s / %s / %s", strategy.getName(), errors, exclusions).isEmpty();
					assertThat(i.readingSupport()).as("%s / %s / %s", strategy.getName(), errors, exclusions)
						.isEqualTo(ReadingSupport.SUPPORTED);
				}
			}
	}

	@Test
	void numericReductionCannotInventAQualityValueEvenWhenStatusMatchesThreshold() {
		Verdict v = SimpleJury.builder()
			.judge(() -> Judgment.pass("a"))
			.judge(() -> Judgment.fail("b"))
			.votingStrategy(new AverageVotingStrategy(.4))
			.build()
			.vote();
		Map<String, Object> map = wire(v);
		((Map<String, Object>) ((Map<?, ?>) ((Map<?, ?>) map.get("judgment")).get("finding")).get("numeric"))
			.put("value", .9);
		unsupported(map);
	}

	@Test
	void modernViewsAndNestedMetadataAreRecursivelyImmutable() {
		StoredReading i = StoredVerdicts.interpret(Verdict.single("seat", rich(PolicyAction.RELY)));
		assertThatThrownBy(() -> ((Map<String, Object>) i.root().judgment().metadata().get("native")).put("new", "bad"))
			.isInstanceOf(UnsupportedOperationException.class);
		assertThatThrownBy(() -> i.root().judgment().checks().clear())
			.isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void aUsedMetaMemberCannotForgeContainedInvocationToEvadeIdentity() {
		Judgment returned = Judgment.error(JudgmentReasonCode.JUDGE_FAILED, "reported error");
		Jury child = SimpleJury.builder().judge(() -> returned).votingStrategy(new ConsensusStrategy()).build();
		Verdict meta = Juries
			.meta(new AverageVotingStrategy(ErrorHandling.TREAT_AS_FAIL), new NamedJury("member", child))
			.vote();
		Map<String, Object> map = wire(meta);
		((Map<String, Object>) ((List<?>) map.get("seats")).getFirst()).put("execution", "CONTAINED_FAILURE");
		map.put("judgment", wire(new AverageVotingStrategy(ErrorHandling.TREAT_AS_FAIL)
			.aggregate(io.github.markpollack.judge.voting.Ballots.of(List.of(returned)))));
		unsupported(map);
	}

}
