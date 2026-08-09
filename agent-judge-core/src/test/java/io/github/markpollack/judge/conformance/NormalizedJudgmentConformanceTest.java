/*
 * Copyright (c) 2026 Mark Pollack
 * See LICENSE.txt in the repository root for license terms.
 */

package io.github.markpollack.judge.conformance;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.Judges;
import io.github.markpollack.judge.context.ExecutionStatus;
import io.github.markpollack.judge.context.JudgmentContext;
import io.github.markpollack.judge.jury.AggregationEvidence;
import io.github.markpollack.judge.jury.ConsensusStrategy;
import io.github.markpollack.judge.jury.ErrorPolicy;
import io.github.markpollack.judge.jury.Jury;
import io.github.markpollack.judge.jury.SimpleJury;
import io.github.markpollack.judge.jury.Verdict;
import io.github.markpollack.judge.result.Check;
import io.github.markpollack.judge.result.Judgment;
import io.github.markpollack.judge.result.JudgmentStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The cross-cutting 0.14 contract, proved once on one artifact.
 *
 * <p>
 * M2 (declared optionality), M3 (recursively portable, recursively frozen result values and
 * the {@code elapsedMillis} timing convention) and M5 (mixed applicable votes aggregate to
 * {@code ABSTAIN}) each have their own focused corpus. Passing separately is weaker than it
 * looks: the three contracts meet on a single {@link Verdict}, and a regression that only
 * appears when they are combined — an aggregate that drops evidence, an optional that
 * becomes a JSON {@code null} once nested metadata is present, a portable value that
 * survives construction but not a round trip — is invisible to all three.
 * </p>
 *
 * <p>
 * The fixture is therefore a real jury run, not a hand-assembled document: four judges cover
 * all four statuses and all four optional score/label combinations, the aggregate is produced
 * by {@link ConsensusStrategy} rather than written by the test, and the golden resource pins
 * the resulting JSON. {@code Completeness} keeps the fixture honest by deriving what it must
 * cover from the public declarations rather than from a list a later change can forget to
 * update.
 * </p>
 *
 * @author Mark Pollack
 * @since 0.14.0
 */
@DisplayName("Normalized 0.14 contract conformance (M2 + M3 + M5)")
class NormalizedJudgmentConformanceTest {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private static final String GOLDEN_RESOURCE = "/conformance/normalized-judgment-0.14.json";

	/** The judge whose result carries the richest portable metadata in the fixture. */
	private static final String MODEL_BACKED_JUDGE = "llm-correctness";

	@Nested
	@DisplayName("Golden document")
	class Golden {

		@Test
		@DisplayName("the fixture serializes to the pinned golden document")
		void matchesTheGoldenDocument() {
			assertThat(fixtureTree())
				.as("the 0.14 wire projection changed; review the diff before repinning %s", GOLDEN_RESOURCE)
				.isEqualTo(goldenTree());
		}

		@Test
		@DisplayName("the document round-trips with every portable result fact preserved")
		void roundTripsThroughDeserialization() throws Exception {
			Verdict parsed = MAPPER.readValue(writeFixture(), Verdict.class);

			assertThat(MAPPER.readTree(MAPPER.writeValueAsString(parsed)))
				.as("re-serializing a parsed verdict must reproduce the same document")
				.isEqualTo(goldenTree());

			assertThat(parsed.aggregated().status()).isEqualTo(JudgmentStatus.ABSTAIN);
			assertThat(parsed.individual()).extracting(Judgment::status)
				.containsExactly(JudgmentStatus.PASS, JudgmentStatus.FAIL, JudgmentStatus.ABSTAIN,
						JudgmentStatus.ERROR);
			assertThat(parsed.individualByName().keySet()).containsExactlyInAnyOrderElementsOf(
					verdict().individualByName().keySet());

			Judgment modelBacked = parsed.individualByName().get(MODEL_BACKED_JUDGE);
			assertThat(modelBacked.score()).isEqualTo(0.41);
			assertThat(modelBacked.label()).isEqualTo("incorrect");
			assertThat(modelBacked.elapsed()).hasMillis(4210);
			assertThat(modelBacked.checks()).containsExactly(
					Check.fail("answer-supported", "claim 2 is unsupported by the retrieved context"));
			assertThat(usageOf(modelBacked)).containsExactly(Map.entry("inputTokens", 1820),
					Map.entry("outputTokens", 340), Map.entry("reasoningTokens", 512),
					Map.entry("cacheCreationTokens", 1024), Map.entry("cacheReadTokens", 768),
					Map.entry("reportedTotalTokens", 4464));
		}

		@Test
		@DisplayName("every judgment keeps the pinned presentation order")
		void judgmentFieldOrderIsPinned() {
			for (JsonNode judgment : allJudgmentNodes(fixtureTree())) {
				List<String> declared = List.of("status", "score", "label", "reasoning", "checks", "metadata");
				assertThat(fieldNames(judgment)).containsExactlyElementsOf(
						declared.stream().filter(judgment::has).toList());
			}
		}

		@Test
		@DisplayName("metadata keeps producer encounter order rather than being rehashed")
		void metadataEncounterOrderIsPreserved() {
			JsonNode metadata = judgmentNode(MODEL_BACKED_JUDGE).get("metadata");

			assertThat(fieldNames(metadata)).containsExactly("elapsedMillis", "model", "usage", "findings");
			assertThat(fieldNames(metadata.get("usage"))).containsExactly("inputTokens", "outputTokens",
					"reasoningTokens", "cacheCreationTokens", "cacheReadTokens", "reportedTotalTokens");
			assertThat(fieldNames(fixtureTree().at("/aggregated/metadata/" + Judgment.AGGREGATION_KEY)))
				.containsExactly(AggregationEvidence.STRATEGY, AggregationEvidence.ERROR_POLICY,
						AggregationEvidence.INPUT_COUNT, AggregationEvidence.ELIGIBLE_COUNT,
						AggregationEvidence.EXPLICIT_ABSTAIN_COUNT, AggregationEvidence.ERROR_COUNT,
						AggregationEvidence.IGNORED_ERROR_COUNT,
						AggregationEvidence.ERRORS_TREATED_AS_ABSTAIN_COUNT,
						AggregationEvidence.ERRORS_TREATED_AS_FAIL_COUNT, AggregationEvidence.PASS_COUNT,
						AggregationEvidence.FAIL_COUNT);
		}

	}

	@Nested
	@DisplayName("Wire contract")
	class Wire {

		@Test
		@DisplayName("statuses appear only as their stable lower-case wire names")
		void statusesAreStableLowerCaseNames() {
			List<String> statuses = new ArrayList<>();
			for (JsonNode judgment : allJudgmentNodes(fixtureTree())) {
				statuses.add(judgment.get("status").asText());
			}

			assertThat(statuses).isNotEmpty().allSatisfy(status -> {
				assertThat(status).isEqualTo(status.toLowerCase());
				assertThat(JudgmentStatus.fromWire(status)).isNotNull();
			});
		}

		@Test
		@DisplayName("absent optionals are omitted, never emitted as JSON null")
		void absentOptionalsAreOmitted() {
			assertThat(judgmentNode("build-success").has("label")).as("PASS carried no label").isFalse();
			assertThat(judgmentNode("security-scan").has("score")).as("ABSTAIN cannot carry a score").isFalse();
			assertThat(judgmentNode("licence-audit").has("score")).as("ERROR cannot carry a score").isFalse();
			assertThat(judgmentNode("licence-audit").has("label")).as("ERROR cannot carry a label").isFalse();

			assertThat(nullPaths(fixtureTree(), "")).as("no member of the document may serialize as JSON null")
				.isEmpty();
		}

		@Test
		@DisplayName("no polymorphic type metadata and no Throwable transport")
		void noTypeTagsOrThrowables() throws Exception {
			assertThat(writeFixture()).doesNotContain("@class")
				.doesNotContain("@type")
				.doesNotContain("stackTrace")
				.doesNotContain("Exception");
		}

	}

	@Nested
	@DisplayName("Completeness against the public wire contract")
	class Completeness {

		@Test
		@DisplayName("every Judgment record component appears in the fixture")
		void coversEveryJudgmentComponent() {
			Set<String> present = new LinkedHashSet<>();
			allJudgmentNodes(fixtureTree()).forEach(judgment -> present.addAll(fieldNames(judgment)));

			assertThat(present).containsExactlyInAnyOrderElementsOf(componentNames(Judgment.class));
		}

		@Test
		@DisplayName("every Verdict record component appears in the fixture")
		void coversEveryVerdictComponent() {
			assertThat(fieldNames(fixtureTree())).containsExactlyInAnyOrderElementsOf(componentNames(Verdict.class));
		}

		@Test
		@DisplayName("every JudgmentStatus appears in the fixture")
		void coversEveryStatus() {
			Set<String> present = new LinkedHashSet<>();
			allJudgmentNodes(fixtureTree()).forEach(judgment -> present.add(judgment.get("status").asText()));

			assertThat(present).containsExactlyInAnyOrderElementsOf(
					Arrays.stream(JudgmentStatus.values()).map(JudgmentStatus::wireName).toList());
		}

		@Test
		@DisplayName("every combination of optional score and label appears in the fixture")
		void coversEveryOptionalCombination() {
			Set<String> shapes = new LinkedHashSet<>();
			for (Judgment judgment : verdict().individual()) {
				shapes.add((judgment.score() != null ? "score" : "-") + "/" + (judgment.label() != null ? "label" : "-"));
			}

			assertThat(shapes).containsExactlyInAnyOrder("score/-", "score/label", "-/label", "-/-");
		}

		@Test
		@DisplayName("every declared aggregation-evidence key is accounted for, present or deliberately absent")
		void coversEveryEvidenceKey() {
			JsonNode evidence = fixtureTree().at("/aggregated/metadata/" + Judgment.AGGREGATION_KEY);

			// Weight keys belong to weighted strategies; a status-counting aggregate that
			// emitted them would be reporting a reduction it never performed.
			Set<String> weightedOnly = Set.of(AggregationEvidence.INPUT_WEIGHT, AggregationEvidence.ELIGIBLE_WEIGHT);
			Set<String> declared = evidenceKeyConstants();

			assertThat(fieldNames(evidence)).containsExactlyInAnyOrderElementsOf(
					declared.stream().filter(key -> !weightedOnly.contains(key)).toList());
			assertThat(declared).as("a new evidence constant must be classified here before the fixture is trusted")
				.containsAll(weightedOnly);
		}

		@Test
		@DisplayName("the reserved and timing metadata keys both appear")
		void coversTheReservedAndTimingKeys() {
			assertThat(fixtureTree().at("/aggregated/metadata").has(Judgment.AGGREGATION_KEY)).isTrue();
			assertThat(judgmentNode(MODEL_BACKED_JUDGE).get("metadata").has(Judgment.ELAPSED_MILLIS_KEY)).isTrue();
		}

	}

	@Nested
	@DisplayName("M2, M3 and M5 on one artifact")
	class CombinedContract {

		@Test
		@DisplayName("M2: declared optionality holds for every judgment the fixture contains")
		void declaredOptionality() {
			for (RecordComponent component : Judgment.class.getRecordComponents()) {
				boolean declaredNullable = component.getAnnotatedType().getAnnotation(Nullable.class) != null;
				assertThat(declaredNullable).as("Judgment.%s nullability declaration", component.getName())
					.isEqualTo(List.of("score", "label").contains(component.getName()));
			}

			for (Judgment judgment : allJudgments()) {
				assertThat(judgment.status()).isNotNull();
				assertThat(judgment.reasoning()).isNotNull();
				assertThat(judgment.checks()).isNotNull();
				assertThat(judgment.metadata()).isNotNull();
			}
		}

		@Test
		@DisplayName("M3: every metadata value in the fixture is portable and recursively frozen")
		void portableAndFrozen() {
			for (Judgment judgment : allJudgments()) {
				assertPortableAndFrozen(judgment.metadata(), "metadata");
			}
		}

		@Test
		@DisplayName("M3: a non-portable value is still refused when the rest of the fixture is present")
		void nonPortableValueStillRefused() {
			Judgment modelBacked = verdict().individualByName().get(MODEL_BACKED_JUDGE);
			Map<String, Object> poisoned = new LinkedHashMap<>(modelBacked.metadata());
			poisoned.put("sdkResponse", new Object());

			assertThatThrownBy(() -> new Judgment(modelBacked.status(), modelBacked.score(), modelBacked.label(),
					modelBacked.reasoning(), modelBacked.checks(), poisoned))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("metadata.sdkResponse");
		}

		@Test
		@DisplayName("M5: disagreement aggregates to ABSTAIN and stays distinguishable from a no-result abstention")
		void disagreementIsNotUnanimityAndNotEmptiness() {
			Judgment disagreement = verdict().aggregated();
			Judgment noResult = new ConsensusStrategy(ErrorPolicy.IGNORE)
				.aggregate(List.of(Judgment.abstain("not applicable")), Map.of());

			assertThat(disagreement.status()).isEqualTo(JudgmentStatus.ABSTAIN);
			assertThat(noResult.status()).isEqualTo(JudgmentStatus.ABSTAIN);

			Map<String, Object> disagreementEvidence = evidenceOf(disagreement);
			assertThat(disagreementEvidence).containsEntry(AggregationEvidence.PASS_COUNT, 1)
				.containsEntry(AggregationEvidence.FAIL_COUNT, 1)
				.containsEntry(AggregationEvidence.ELIGIBLE_COUNT, 2)
				.containsEntry(AggregationEvidence.INPUT_COUNT, 4)
				.containsEntry(AggregationEvidence.EXPLICIT_ABSTAIN_COUNT, 1)
				.containsEntry(AggregationEvidence.ERROR_COUNT, 1)
				.containsEntry(AggregationEvidence.IGNORED_ERROR_COUNT, 1);
			assertThat(evidenceOf(noResult)).doesNotContainKeys(AggregationEvidence.PASS_COUNT,
					AggregationEvidence.FAIL_COUNT);
			assertThat(disagreement.reasoning()).isNotEqualTo(noResult.reasoning());
		}

		@Test
		@DisplayName("M5: the aggregate never erases the individual outcomes it reduced")
		void individualOutcomesSurviveAggregation() {
			assertThat(verdict().individual()).extracting(Judgment::status)
				.containsExactly(JudgmentStatus.PASS, JudgmentStatus.FAIL, JudgmentStatus.ABSTAIN,
						JudgmentStatus.ERROR);
		}

	}

	// ==================== The fixture ====================

	/**
	 * A real four-judge consensus run. The aggregate is whatever
	 * {@link ConsensusStrategy} produces, so the fixture cannot assert a conclusion the
	 * implementation does not reach.
	 */
	private static Verdict verdict() {
		Jury jury = SimpleJury.builder()
			.votingStrategy(new ConsensusStrategy(ErrorPolicy.IGNORE))
			.parallel(false)
			.judge(Judges.named(context -> buildSuccess(), "build-success"))
			.judge(Judges.named(context -> modelBackedCorrectness(), MODEL_BACKED_JUDGE))
			.judge(Judges.named(context -> securityScan(), "security-scan"))
			.judge(Judges.named(context -> licenceAudit(), "licence-audit"))
			.build();

		return jury.vote(JudgmentContext.builder()
			.goal("Add the portable token-usage projection")
			.status(ExecutionStatus.SUCCESS)
			.agentOutput("Implemented Usage.toPortableMap()")
			.build());
	}

	/** PASS carrying a score, no label, checks, and nested portable metadata. */
	private static Judgment buildSuccess() {
		Map<String, Object> coverage = new LinkedHashMap<>();
		coverage.put("linePercent", 95.63);
		coverage.put("branchPercent", 92.99);

		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put(Judgment.ELAPSED_MILLIS_KEY, 1325L);
		metadata.put("modules", List.of("agent-judge-core", "agent-judge-ai-core"));
		metadata.put("coverage", coverage);
		metadata.put("offline", true);

		return Judgment.builder()
			.pass()
			.score(0.94)
			.reasoning("The full reactor built and both coverage gates were met")
			.check(Check.pass("compiles"))
			.check(Check.pass("coverage", "line and branch gates met"))
			.metadata(metadata)
			.build();
	}

	/** FAIL carrying both optionals, and the token-usage projection at depth. */
	private static Judgment modelBackedCorrectness() {
		Map<String, Object> usage = new LinkedHashMap<>();
		usage.put("inputTokens", 1820L);
		usage.put("outputTokens", 340L);
		usage.put("reasoningTokens", 512L);
		usage.put("cacheCreationTokens", 1024L);
		usage.put("cacheReadTokens", 768L);
		usage.put("reportedTotalTokens", 4464L);

		Map<String, Object> finding = new LinkedHashMap<>();
		finding.put("path", "src/main/java/io/github/markpollack/judge/ai/model/Usage.java");
		finding.put("line", 42);
		finding.put("tags", List.of("unsupported-claim", "boundary"));

		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put(Judgment.ELAPSED_MILLIS_KEY, 4210L);
		metadata.put("model", "test-model");
		metadata.put("usage", usage);
		metadata.put("findings", List.of(finding));

		return Judgment.builder()
			.fail()
			.score(0.41)
			.label("incorrect")
			.reasoning("The answer asserts a claim the retrieved context does not support")
			.check(Check.fail("answer-supported", "claim 2 is unsupported by the retrieved context"))
			.metadata(metadata)
			.build();
	}

	/** ABSTAIN carrying a label but no score: a completed classification that casts no vote. */
	private static Judgment securityScan() {
		return Judgment.builder()
			.abstain()
			.reasoning("No dependency change in this run, so the scan does not apply")
			.label("not_applicable")
			.build();
	}

	/** ERROR carrying neither optional and no Throwable. */
	private static Judgment licenceAudit() {
		return Judgment.error("The licence index was unreachable, so no finding was reached");
	}

	// ==================== Helpers ====================

	private static String writeFixture() {
		try {
			return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(verdict());
		}
		catch (Exception ex) {
			throw new AssertionError("could not serialize the conformance fixture", ex);
		}
	}

	private static JsonNode fixtureTree() {
		try {
			return MAPPER.readTree(writeFixture());
		}
		catch (Exception ex) {
			throw new AssertionError("could not parse the conformance fixture", ex);
		}
	}

	private static JsonNode goldenTree() {
		try (InputStream golden = NormalizedJudgmentConformanceTest.class.getResourceAsStream(GOLDEN_RESOURCE)) {
			assertThat(golden).as("missing golden resource %s", GOLDEN_RESOURCE).isNotNull();
			return MAPPER.readTree(new String(golden.readAllBytes(), StandardCharsets.UTF_8));
		}
		catch (Exception ex) {
			throw new AssertionError("could not read " + GOLDEN_RESOURCE, ex);
		}
	}

	private static List<Judgment> allJudgments() {
		List<Judgment> judgments = new ArrayList<>(verdict().individual());
		judgments.add(verdict().aggregated());
		return judgments;
	}

	private static List<JsonNode> allJudgmentNodes(JsonNode document) {
		List<JsonNode> nodes = new ArrayList<>();
		nodes.add(document.get("aggregated"));
		document.get("individual").forEach(nodes::add);
		return nodes;
	}

	private static JsonNode judgmentNode(String judgeName) {
		JsonNode node = fixtureTree().at("/individualByName/" + judgeName);
		assertThat(node.isObject()).as("fixture has no judgment named %s", judgeName).isTrue();
		return node;
	}

	private static List<String> fieldNames(JsonNode node) {
		List<String> names = new ArrayList<>();
		node.fieldNames().forEachRemaining(names::add);
		return names;
	}

	private static Map<String, Object> usageOf(Judgment judgment) {
		return asMap(judgment.metadata().get("usage"));
	}

	private static Map<String, Object> evidenceOf(Judgment judgment) {
		return asMap(judgment.metadata().get(Judgment.AGGREGATION_KEY));
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> asMap(Object value) {
		assertThat(value).isInstanceOf(Map.class);
		return (Map<String, Object>) value;
	}

	private static List<String> componentNames(Class<?> recordType) {
		return Arrays.stream(recordType.getRecordComponents()).map(RecordComponent::getName).toList();
	}

	/** Every public {@code String} constant {@link AggregationEvidence} declares. */
	private static Set<String> evidenceKeyConstants() {
		Set<String> keys = new LinkedHashSet<>();
		for (Field field : AggregationEvidence.class.getDeclaredFields()) {
			if (Modifier.isPublic(field.getModifiers()) && Modifier.isStatic(field.getModifiers())
					&& field.getType() == String.class) {
				try {
					keys.add((String) field.get(null));
				}
				catch (IllegalAccessException ex) {
					throw new AssertionError("could not read AggregationEvidence." + field.getName(), ex);
				}
			}
		}
		return keys;
	}

	/** Paths at which the document carries an explicit JSON null. */
	private static List<String> nullPaths(JsonNode node, String path) {
		List<String> paths = new ArrayList<>();
		if (node.isNull()) {
			paths.add(path);
		}
		else if (node.isObject()) {
			fieldNames(node).forEach(name -> paths.addAll(nullPaths(node.get(name), path + "/" + name)));
		}
		else if (node.isArray()) {
			for (int index = 0; index < node.size(); index++) {
				paths.addAll(nullPaths(node.get(index), path + "[" + index + "]"));
			}
		}
		return paths;
	}

	/**
	 * Assert the portable value profile and recursive immutability over a live metadata
	 * value, rather than over its JSON projection. Reading the projection would prove only
	 * that Jackson could write something; the claim is about what the judgment holds.
	 */
	@SuppressWarnings("unchecked")
	private static void assertPortableAndFrozen(Object value, String path) {
		if (value instanceof Map<?, ?> map) {
			assertThat(map.keySet()).as("%s keys must all be strings", path).allSatisfy(
					key -> assertThat(key).isInstanceOf(String.class));
			assertThatThrownBy(() -> ((Map<String, Object>) map).put("mutated", "x"))
				.as("%s must be frozen", path)
				.isInstanceOf(UnsupportedOperationException.class);
			map.forEach((key, nested) -> assertPortableAndFrozen(nested, path + "." + key));
			return;
		}
		if (value instanceof List<?> list) {
			assertThatThrownBy(() -> ((List<Object>) list).add("mutated")).as("%s must be frozen", path)
				.isInstanceOf(UnsupportedOperationException.class);
			for (int index = 0; index < list.size(); index++) {
				assertPortableAndFrozen(list.get(index), path + "[" + index + "]");
			}
			return;
		}
		assertThat(value).as("%s must be a portable scalar", path)
			.isNotNull()
			.isInstanceOfAny(String.class, Boolean.class, Byte.class, Short.class, Integer.class, Long.class,
					Float.class, Double.class);
		if (value instanceof Double number) {
			assertThat(Double.isFinite(number)).as("%s must be finite", path).isTrue();
		}
		if (value instanceof Long number) {
			assertThat(Math.abs(number)).as("%s must be an interoperable integer", path)
				.isLessThanOrEqualTo(9007199254740991L);
		}
	}

}
