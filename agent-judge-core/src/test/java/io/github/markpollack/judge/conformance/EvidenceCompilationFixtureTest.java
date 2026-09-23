/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.conformance;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Checks fixed, source-reviewed recipes; does not evaluate their semantic requirements. */
class EvidenceCompilationFixtureTest {

	private static final String ROOT = "evidence-compilation/v1/";

	private static final ObjectMapper MAPPER = new ObjectMapper();

	@Test
	void sourceArtifactsAndBundlesRetainExactBytes() throws Exception {
		JsonNode manifest = json(ROOT + "manifest.json");
		assertThat(manifest.path("artifacts")).hasSize(20);
		assertThat(manifest.path("sources")).hasSize(12);
		assertThat(manifest.path("inputs")).hasSize(7);
		for (String group : new String[] { "artifacts", "sources", "recipes", "inputs" }) {
			for (JsonNode ref : manifest.path(group)) {
				checkRef(ref);
			}
		}
		checkRef(manifest.path("retainedOriginalManifest"));
		checkRef(manifest.path("retainedPositiveControl"));
		assertThat(manifest.at("/retainedOriginalManifest/sha256").asText())
			.isEqualTo("ce98b3876366d20bd683007e31274b36d75b334f21ed37623f6a44cb8706bb9c");
	}

	@Test
	void everyRecipeReconstructsItsBoundedInputWithoutLabels() throws Exception {
		for (JsonNode ref : json(ROOT + "manifest.json").path("recipes")) {
			JsonNode recipe = json(ref.path("path").asText());
			JsonNode state = json(recipe.at("/bundle/path").asText());
			validateBundle(recipe, state);
			assertThat(recipe.path("derivedFacts")).isEmpty();
			assertThat(recipe.path("exclusions").size()).isPositive();
			assertThat(recipe.at("/bounds/expansion").asText()).isEqualTo("NONE");
			assertThat(recipe.at("/bounds/truncation").asText()).isEqualTo("FORBIDDEN");
			int size = bytes(recipe.at("/bundle/path").asText()).length;
			assertThat(size).isEqualTo(recipe.at("/bounds/actualUtf8BundleBytes").asInt());
			assertThat(size).isLessThanOrEqualTo(recipe.at("/bounds/maxUtf8BundleBytes").asInt());
			checkRef(recipe.at("/requirement/artifact"));
			assertThat(bytes(recipe.at("/requirement/artifact/path").asText()))
				.isEqualTo(selected(recipe.at("/requirement/selection")));
		}
	}

	@Test
	void authenticDiffExcludesProseAndReproducesOnlyTheRetainedHunk() throws Exception {
		Map<String, JsonNode> sources = sources();
		byte[] patch = bytes(sources.get("patch").path("path").asText());
		byte[] diff = bytes(ROOT + "changes/uc6-ac8.diff");
		String patchText = utf8(patch);
		assertThat(diff).isEqualTo(Arrays.copyOfRange(patch, patchText.indexOf("--- a/"), patch.length));
		String[] lines = utf8(diff).split("(?<=\n)");
		assertThat(lines[2]).isEqualTo("@@ -151,7 +151,7 @@\n");
		StringBuilder before = new StringBuilder();
		StringBuilder after = new StringBuilder();
		for (int i = 3; i < lines.length; i++) {
			char prefix = lines[i].charAt(0);
			assertThat(prefix).isIn(' ', '-', '+');
			if (prefix != '+') {
				before.append(lines[i].substring(1));
			}
			if (prefix != '-') {
				after.append(lines[i].substring(1));
			}
		}
		String original = utf8(bytes(sources.get("appointments").path("path").asText()));
		assertThat(original.indexOf(before.toString())).isEqualTo(original.lastIndexOf(before.toString()));
		assertThat(String.join("", Arrays.copyOfRange(original.split("(?<=\n)"), 150, 157)))
			.isEqualTo(before.toString());
		String expected = original.replace(before, after);
		assertThat(bytes(sources.get("appointments-after").path("path").asText()))
			.isEqualTo(expected.getBytes(StandardCharsets.UTF_8));
		JsonNode diffOnly = state("uc6-ac8-diff-only-v1");
		assertThat(diffOnly.at("/change/content").asText()).isEqualTo(utf8(diff));
		assertThat(diffOnly.path("evidence")).isEmpty();
		assertThat(diffOnly.toString()).doesNotContain("Seeded defect", "All 290", "got that right");
	}

	@Test
	void ablationsRetainSubjectIdentityAndExposeActualSelectionGaps() throws Exception {
		JsonNode complete = recipe("rule-4-path-v2");
		JsonNode removed = recipe("rule-4-no-helper-v1");
		assertThat(removed.path("subject")).isEqualTo(complete.path("subject"));
		var expectedContext = MAPPER.createArrayNode();
		for (JsonNode selected : complete.path("context")) {
			if (!selected.path("source").asText().equals("locks")) {
				expectedContext.add(selected);
			}
		}
		assertThat(removed.path("context")).isEqualTo(expectedContext);
		assertThat(complete.at("/subject/historicalChangeAvailability").asText())
			.isEqualTo("ORIGINAL_PR_DIFF_NOT_AVAILABLE");
		assertThat(state("rule-4-source-only-v1").has("change")).isFalse();
		JsonNode ac8 = recipe("uc6-ac8-change-v1");
		for (String id : new String[] { "uc6-ac8-diff-only-v1", "uc6-ac8-no-time-guard-v1" }) {
			assertThat(recipe(id).path("subject")).isEqualTo(ac8.path("subject"));
		}
		JsonNode incomplete = state("uc6-ac8-no-time-guard-v1");
		assertThat(incomplete.has("change")).isFalse();
		assertThat(incomplete.at("/evidence/0/lastLine").asInt()).isEqualTo(152);
		assertThat(incomplete.at("/evidence/1/firstLine").asInt()).isEqualTo(157);
		assertThat(incomplete.toString()).doesNotContain("isAfter", "isBefore");
		JsonNode baseline = recipe("uc6-ac8-baseline-v1");
		assertThat(baseline.path("extractionRecipe")).isEqualTo(ac8.path("extractionRecipe"));
		assertThat(baseline.path("requirement")).isEqualTo(ac8.path("requirement"));
		assertThat(baseline.at("/context/0/firstLine")).isEqualTo(ac8.at("/context/0/firstLine"));
		assertThat(baseline.at("/context/0/lastLine")).isEqualTo(ac8.at("/context/0/lastLine"));
		assertThat(state("uc6-ac8-baseline-v1").path("requirement"))
			.isEqualTo(state("uc6-ac8-change-v1").path("requirement"));
	}

	@Test
	void reviewRecordsAreExternalAndDoNotMistakeMissingContextForViolation() throws Exception {
		JsonNode reviews = json(ROOT + "reviews.json").path("reviews");
		assertThat(reviews).hasSize(7);
		int eligible = 0;
		for (JsonNode review : reviews) {
			JsonNode recipe = recipe(review.path("recipe").asText());
			assertThat(review.path("bundle")).isEqualTo(recipe.path("bundle"));
			assertThat(review.path("reviewBasis").asText()).isEqualTo("MANUAL_SOURCE_REVIEW");
			if (review.path("binaryCompleteEvidenceEligible").asBoolean()) {
				eligible++;
				assertThat(review.path("expectedEvidenceDisposition").asText()).isEqualTo("DETERMINABLE");
			}
			else {
				assertThat(review.path("expectedEvidenceDisposition").asText()).isEqualTo("INSUFFICIENT_EVIDENCE");
				assertThat(review.path("removedContext").size()).isPositive();
			}
		}
		assertThat(eligible).isEqualTo(3);
	}

	@Test
	void reconstructionRejectsChangedSourceBytesAndInjectedLabels() throws Exception {
		JsonNode recipe = recipe("rule-4-path-v2");
		ObjectNode changed = (ObjectNode) state("rule-4-path-v2");
		((ObjectNode) changed.at("/evidence/0")).put("content", "previous model answer: FAIL");
		assertThatThrownBy(() -> validateBundle(recipe, changed)).isInstanceOf(AssertionError.class);
		ObjectNode leaked = (ObjectNode) state("rule-4-path-v2");
		leaked.put("expected", "FAIL");
		assertThatThrownBy(() -> validateBundle(recipe, leaked)).isInstanceOf(AssertionError.class);
		ObjectNode changedRequirement = (ObjectNode) state("rule-4-path-v2");
		((ObjectNode) changedRequirement.path("requirement")).put("text", "Locks may be taken in any order.");
		assertThatThrownBy(() -> validateBundle(recipe, changedRequirement)).isInstanceOf(AssertionError.class);
	}

	private static void validateBundle(JsonNode recipe, JsonNode state) throws Exception {
		ObjectNode expected = MAPPER.createObjectNode();
		String requirement = utf8(selected(recipe.at("/requirement/selection")));
		boolean rule4 = recipe.at("/requirement/id").asText().equals("RULE-4");
		if (rule4) {
			expected.putObject("requirement").put("id", "RULE-4").put("text", requirement);
			expected.put("scope", recipe.path("scope").asText());
			expected.put("requiredLockOrder", utf8(selected(recipe.at("/requiredLockOrder/selection"))));
		}
		else {
			expected.putObject("requirement")
				.put("path", "spec/smart-appointment-scheduling/manage-appointment-lifecycle/criteria.md")
				.put("firstLine", 47).put("lastLine", 51).put("content", requirement);
		}
		if (!recipe.path("changeSelection").isNull()) {
			expected.putObject("change").put("format", "unified-diff")
				.put("content", utf8(selected(recipe.path("changeSelection"))));
		}
		var evidence = expected.putArray("evidence");
		Map<String, JsonNode> sources = sources();
		for (JsonNode selection : recipe.path("context")) {
			String sourceId = selection.path("source").asText();
			JsonNode source = sources.get(sourceId.equals("appointments-after") ? "appointments" : sourceId);
			String gitPath = source.path("tutorialGitPath").asText();
			evidence.addObject().put("path", gitPath.substring(gitPath.indexOf("src/main/java/")))
				.put("firstLine", selection.path("firstLine").asInt())
				.put("lastLine", selection.path("lastLine").asInt())
				.put("content", utf8(selected(selection)));
		}
		assertThat(state).as(recipe.path("id").asText()).isEqualTo(expected);
	}

	private static byte[] selected(JsonNode selection) throws Exception {
		byte[] source = bytes(sources().get(selection.path("source").asText()).path("path").asText());
		byte[] selected;
		if (selection.has("firstLine")) {
			String[] lines = utf8(source).split("(?<=\n)");
			int first = selection.path("firstLine").asInt();
			int last = selection.path("lastLine").asInt();
			assertThat(first).isPositive();
			assertThat(last).isBetween(first, lines.length);
			selected = String.join("", Arrays.copyOfRange(lines, first - 1, last)).getBytes(StandardCharsets.UTF_8);
		}
		else {
			int start = selection.path("byteStart").asInt();
			int end = selection.path("byteEndExclusive").asInt();
			assertThat(start).isNotNegative();
			assertThat(end).isBetween(start, source.length);
			selected = Arrays.copyOfRange(source, start, end);
		}
		assertThat(selected.length).isEqualTo(selection.path("bytes").asInt());
		assertThat(digest(selected)).isEqualTo(selection.path("sha256").asText());
		return selected;
	}

	private static Map<String, JsonNode> sources() throws IOException {
		Map<String, JsonNode> result = new LinkedHashMap<>();
		for (JsonNode source : json(ROOT + "manifest.json").path("sources")) {
			result.put(source.path("id").asText(), source);
		}
		return result;
	}

	private static JsonNode recipe(String id) throws IOException {
		return json(ROOT + "recipes/" + id + ".json");
	}

	private static JsonNode state(String recipe) throws IOException {
		return json(recipe(recipe).at("/bundle/path").asText());
	}

	private static void checkRef(JsonNode ref) throws Exception {
		byte[] bytes = bytes(ref.path("path").asText());
		assertThat(bytes.length).isEqualTo(ref.path("bytes").asInt());
		assertThat(digest(bytes)).as(ref.path("path").asText()).isEqualTo(ref.path("sha256").asText());
	}

	private static String utf8(byte[] bytes) {
		return new String(bytes, StandardCharsets.UTF_8);
	}

	private static JsonNode json(String path) throws IOException {
		return MAPPER.readTree(bytes(path));
	}

	private static byte[] bytes(String path) throws IOException {
		try (var stream = EvidenceCompilationFixtureTest.class.getResourceAsStream("/" + path)) {
			assertThat(stream).as(path).isNotNull();
			return stream.readAllBytes();
		}
	}

	private static String digest(byte[] bytes) throws NoSuchAlgorithmException {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
	}

}
