/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.serialization.diagnostics;

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
class V3ConformanceVectorsTest {

	private static final String BASE = "/conformance/v3/portable-results";

	private static final ObjectMapper JSON = new ObjectMapper();

	static byte[] resource(String suffix) throws Exception {
		try (InputStream input = V3ConformanceVectorsTest.class.getResourceAsStream(BASE + suffix)) {
			assertThat(input).isNotNull();
			return input.readAllBytes();
		}
	}

	static Stream<JsonNode> vectors() throws Exception {
		return StreamSupport.stream(JSON.readTree(resource(".json")).spliterator(), false);
	}

	@ParameterizedTest(name = "vector {index}")
	@MethodSource("vectors")
	void committedVersionThreeVectorIsExplicitlyRefused(JsonNode vector) {
		Map<String, Object> source = JSON.convertValue(vector.get("verdict"), new TypeReference<>() {
		});
		StoredReading interpretation = StoredVerdicts.interpret(source);
		// Version 3 stored policy-modified Judgment values. Version 4 deliberately
		// refuses that contract instead of guessing a verdict-level policy decision.
		if (!ModernReader.containsModern(source)) {
			assertThat(JSON.valueToTree(interpretation).get("outcome")).isEqualTo(vector.get("outcome"));
			return;
		}
		assertThat(interpretation.outcome()).isNull();
		assertThat(interpretation.readingSupport()).isEqualTo(ReadingSupport.UNDETERMINED);
		assertThat(interpretation.defects()).isNotEmpty();
		org.assertj.core.api.Assertions
			.assertThatThrownBy(() -> new io.github.markpollack.judge.serialization.VerdictCodec().read(source))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void committedVectorDigestMatchesExactBytes() throws Exception {
		String expected = new String(resource(".sha256"), StandardCharsets.UTF_8).split(" ")[0];
		assertThat(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(resource(".json"))))
			.isEqualTo(expected);
	}

	@Test
	void everyStampedV2VectorIsExplicitlyUnsupported() throws Exception {
		try (var input = getClass().getResourceAsStream("/conformance/v2/portable-results.json")) {
			var old = JSON.readTree(input);
			int stamped = 0;
			for (var vector : old) {
				var verdict = vector.get("verdict");
				if (!verdict.path("schemaVersion").isInt() || verdict.path("schemaVersion").asInt() != 2)
					continue;
				stamped++;
				var reading = StoredVerdicts
					.interpret(JSON.convertValue(verdict, new TypeReference<Map<String, Object>>() {
					}));
				assertThat(reading.sourceVersion()).isEqualTo(2);
				assertThat(reading.outcome()).isNull();
				assertThat(reading.readingSupport()).isEqualTo(ReadingSupport.UNDETERMINED);
				assertThat(reading.defects()).isNotEmpty();
			}
			assertThat(stamped).isGreaterThan(0);
		}
	}

}
