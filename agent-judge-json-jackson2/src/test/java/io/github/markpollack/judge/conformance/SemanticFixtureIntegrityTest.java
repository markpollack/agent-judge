/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.conformance;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Integrity of retained evidence, not a semantic evaluator or provider adapter. */
class SemanticFixtureIntegrityTest {

	private static final String ROOT = "/semantic-conformance/";

	private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(io.github.markpollack.judge.serialization.ResultJson.module());

	@Test
	void allManifestArtifactsRetainExactBytes() throws Exception {
		JsonNode manifest = json("manifest.json");
		assertThat(manifest.path("tutorial").path("commit").asText())
			.isEqualTo("27756e9f1d4248bb0229c182f23d403a8bc5d2ec");
		List<String> paths = new ArrayList<>();
		for (JsonNode artifact : manifest.path("artifacts")) {
			String path = artifact.path("path").asText();
			paths.add(path);
			byte[] bytes = bytes(path);
			assertThat(bytes.length).as(path).isEqualTo(artifact.path("bytes").asInt());
			assertThat(digest(bytes)).as(path).isEqualTo(artifact.path("sha256").asText());
		}
		assertThat(paths).hasSize(50).doesNotHaveDuplicates();
	}

	@Test
	void inferenceStatesContainOnlyReproducibleRequirementsAndSource() throws Exception {
		JsonNode expectations = json("petclinic/expectations.json");
		assertThat(expectations.path("cases")).hasSize(2);
		for (JsonNode fixture : expectations.path("cases")) {
			String statePath = fixture.path("state").asText();
			assertThat(statePath).startsWith("petclinic/inputs/");
			JsonNode state = json(statePath);
			assertThat(fields(state)).containsExactlyInAnyOrder("requirement", "evidence");
			checkExcerpt(state.path("requirement"));
			assertThat(state.path("evidence").size()).isPositive();
			for (JsonNode excerpt : state.path("evidence")) {
				checkExcerpt(excerpt);
				assertThat(excerpt.path("path").asText()).startsWith("src/main/java/");
			}
			assertThat(fixture.path("basis").asText()).isEqualTo("SOURCE_REVIEW");
			assertThat(fixture.path("executionEvidence").isNull()).isTrue();
		}
		// A closed input schema plus exact source equality excludes labels and old responses.
		assertThat(expectations.at("/cases/0/expected").asText()).isEqualTo("FAIL");
		assertThat(expectations.at("/cases/1/expected").asText()).isEqualTo("PASS");
		assertThat(expectations.at("/mutation/patch").asText()).startsWith("petclinic/mutation/");
	}

	@Test
	void sourceContextIncludesTheActualLockOrderAndEqualityGuard() throws Exception {
		JsonNode rule = json("petclinic/inputs/rule-4.json");
		assertThat(rule.at("/requirement/content").asText()).contains(
			"Owner, Pet, Vet, SchedulingRequest, Appointment, Reservation", "identifiers ascending within a type");
		String caller = rule.at("/evidence/0/content").asText();
		assertThat(caller).contains("@Transactional", "public Reservation createStaffOffer(", "return offer;");
		assertThat(caller.indexOf("lockCoordinator.lockRequest(requestId)"))
			.isLessThan(caller.indexOf("lockCoordinator.lockResources("));
		assertThat(rule.at("/evidence/1/content").asText()).contains(
			"entityManager.find(Owner.class, id, LockModeType.PESSIMISTIC_WRITE)",
			"entityManager.find(SchedulingRequest.class, requestId, LockModeType.PESSIMISTIC_WRITE)",
			"private TreeSet<Integer> sortIds(");
		String baseline = json("petclinic/inputs/uc6-ac8.json").at("/evidence/0/content").asText();
		assertThat(baseline).contains("!now.isBefore(appointment.getStartTime())", "return AppointmentDto.from(saved);");
		assertThat(baseline.indexOf("!now.isBefore("))
			.isLessThan(baseline.indexOf("appointment.setStatus(AppointmentStatus.CANCELLED)"));
		String patch = new String(bytes("petclinic/mutation/uc6-ac8-boundary.patch"), StandardCharsets.UTF_8);
		assertThat(patch).contains("-\t\tif (!now.isBefore(appointment.getStartTime())) {",
			"+\t\tif (now.isAfter(appointment.getStartTime())) {");
	}

	@Test
	void digestVectorsDistinguishUnicodeNewlinesAndJsonSerialization() throws Exception {
		JsonNode vectors = json("digests/vectors.json").path("vectors");
		assertThat(vectors).hasSize(9);
		List<String> hashes = new ArrayList<>();
		for (JsonNode vector : vectors) {
			byte[] bytes = bytes(vector.path("path").asText());
			assertThat(bytes).isEqualTo(HexFormat.of().parseHex(vector.path("hex").asText()));
			assertThat(bytes.length).isEqualTo(vector.path("bytes").asInt());
			String hash = digest(bytes);
			assertThat(hash).isEqualTo(vector.path("sha256").asText());
			hashes.add(hash);
		}
		assertThat(hashes).doesNotHaveDuplicates();
		String nfc = new String(bytes("digests/utf8-nfc.bin"), StandardCharsets.UTF_8);
		String nfd = new String(bytes("digests/utf8-nfd.bin"), StandardCharsets.UTF_8);
		assertThat(Normalizer.isNormalized(nfc, Normalizer.Form.NFC)).isTrue();
		assertThat(Normalizer.isNormalized(nfd, Normalizer.Form.NFD)).isTrue();
		assertThat(Normalizer.normalize(nfd, Normalizer.Form.NFC)).isEqualTo(nfc);
		assertThat(MAPPER.readTree(bytes("digests/json-compact.bin")))
			.isEqualTo(MAPPER.readTree(bytes("digests/json-reordered.bin")))
			.isEqualTo(MAPPER.readTree(bytes("digests/json-spaced.bin")));
		assertThat(digest(bytes("digests/ascii.bin")))
			.isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
	}

	@Test
	void nativeTemplatesAndDeclaredMalformedVariantsLoadWithoutResultTypes() throws Exception {
		JsonNode catalog = json("jev/cases.json");
		int valid = 0;
		int invalid = 0;
		for (JsonNode fixture : catalog.path("cases")) {
			JsonNode request = json(fixture.path("request").asText());
			JsonNode response = json(fixture.path("response").asText());
			assertThat(request.path("questions").has("q")).isTrue();
			if (fixture.path("expected").asText().equals("VALID_NATIVE")) {
				valid++;
				assertThat(fields(response)).containsExactlyInAnyOrder("model", "answers", "usage");
				assertThat(response.at("/answers/q/type")).isEqualTo(request.at("/questions/q/type"));
			}
			else {
				invalid++;
				ObjectNode reconstructed = (ObjectNode) json(fixture.path("base").asText());
				JsonNode mutation = fixture.path("mutation");
				String pointer = mutation.path("pointer").asText();
				int slash = pointer.lastIndexOf('/');
				ObjectNode parent = (ObjectNode) reconstructed.at(pointer.substring(0, slash));
				String key = pointer.substring(slash + 1);
				if (mutation.path("op").asText().equals("remove")) {
					assertThat(parent.remove(key)).isNotNull();
				}
				else {
					parent.set(key, mutation.path("value"));
				}
				assertThat(response).as(fixture.path("id").asText()).isEqualTo(reconstructed);
				assertThat(response).isNotEqualTo(json(fixture.path("base").asText()));
			}
		}
		assertThat(valid).isEqualTo(3);
		assertThat(invalid).isEqualTo(20);
		JsonNode noul = json("jev/responses/noul-valid.json").at("/answers/q");
		assertThat(fields(noul)).containsExactlyInAnyOrder("type", "noul");
		assertThat(noul.path("noul").asDouble()).isEqualTo(0.05);
		JsonNode choice = json("jev/responses/choice-valid.json").at("/answers/q");
		assertThat(choice.path("confidence").asDouble()).isEqualTo(0.8);
		assertThat(choice.at("/probabilities/violated").asDouble()).isEqualTo(0.9);
		JsonNode score = json("jev/responses/score-valid.json").at("/answers/q");
		assertThat(score.path("score").asDouble()).isEqualTo(1.7);
		JsonNode levels = json("jev/requests/score.json").at("/questions/q/criteria");
		for (int i = 0; i < levels.size(); i++) {
			assertThat(score.path("legend").path(Integer.toString(i))).isEqualTo(levels.get(i));
		}
	}

	private static void checkExcerpt(JsonNode excerpt) throws IOException {
		assertThat(fields(excerpt)).containsExactlyInAnyOrder("path", "firstLine", "lastLine", "content");
		String path = excerpt.path("path").asText();
		assertThat(path).doesNotContain("..", "recording", "expectation", "mutation");
		String source = new String(bytes("petclinic/source/" + path), StandardCharsets.UTF_8);
		String[] lines = source.split("(?<=\n)");
		int first = excerpt.path("firstLine").asInt();
		int last = excerpt.path("lastLine").asInt();
		assertThat(first).isPositive();
		assertThat(last).isBetween(first, lines.length);
		assertThat(excerpt.path("content").asText())
			.isEqualTo(String.join("", Arrays.copyOfRange(lines, first - 1, last)));
	}

	private static List<String> fields(JsonNode node) {
		List<String> fields = new ArrayList<>();
		node.fieldNames().forEachRemaining(fields::add);
		return fields;
	}

	private static JsonNode json(String path) throws IOException {
		return MAPPER.readTree(bytes(path));
	}

	private static byte[] bytes(String path) throws IOException {
		try (var stream = SemanticFixtureIntegrityTest.class.getResourceAsStream(ROOT + path)) {
			assertThat(stream).as(path).isNotNull();
			return stream.readAllBytes();
		}
	}

	private static String digest(byte[] bytes) throws NoSuchAlgorithmException {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
	}

}
