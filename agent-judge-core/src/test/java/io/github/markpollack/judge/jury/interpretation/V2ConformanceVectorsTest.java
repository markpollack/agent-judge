/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.jury.interpretation;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

/** Consumes committed bytes only; never regenerates expectations during verification. */
class V2ConformanceVectorsTest {

	private static final String BASE = "/conformance/v2/portable-results";

	private static final ObjectMapper JSON = new ObjectMapper();

	static byte[] resource(String suffix) throws Exception {
		try (InputStream input = V2ConformanceVectorsTest.class.getResourceAsStream(BASE + suffix)) {
			assertThat(input).isNotNull();
			return input.readAllBytes();
		}
	}

	static Stream<JsonNode> vectors() throws Exception {
		return StreamSupport.stream(JSON.readTree(resource(".json")).spliterator(), false);
	}

	@ParameterizedTest(name = "vector {index}")
	@MethodSource("vectors")
	void committedWireVectorAgreesWithAuthoritativeReading(JsonNode vector) {
		Map<String, Object> source = JSON.convertValue(vector.get("verdict"), new TypeReference<>() {
		});
		Interpretation interpretation = Verdicts.interpret(source);
		JsonNode actual = JSON.valueToTree(interpretation);
		assertThat(actual.get("schemaVersion").asInt()).isEqualTo(2);
		assertThat(actual.get("reading")).as(vector.get("id").asText()).isEqualTo(vector.get("reading"));
		assertThat(actual.get("readingSupport")).isEqualTo(vector.get("readingSupport"));
		vector.get("expectedPaths")
			.fields()
			.forEachRemaining(
					entry -> assertThat(actual.at(entry.getKey())).as("%s %s", vector.get("id"), entry.getKey())
						.isEqualTo(entry.getValue()));
		assertThat(interpretation.summary()).isEqualTo(Summaries.of(interpretation));
		if (interpretation.readingSupport() == ReadingSupport.SUPPORTED)
			assertThat(interpretation.defects()).isEmpty();
	}

	@Test
	void committedVectorDigestMatchesExactBytes() throws Exception {
		String expected = new String(resource(".sha256"), StandardCharsets.UTF_8).split(" ")[0];
		assertThat(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(resource(".json"))))
			.isEqualTo(expected);
	}

}
