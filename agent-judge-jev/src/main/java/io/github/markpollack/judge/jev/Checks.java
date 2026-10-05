/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jev;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import io.github.markpollack.judge.judgment.Judgment;
import java.util.*;

final class Checks {

	static final ObjectMapper JSON = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
		.enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

	private Checks() {
	}

	static void text(String s) {
		if (s.isBlank())
			throw new IllegalArgumentException("Nonblank text required");
	}

	static void versioned(String s) {
		text(s);
		if (!s.matches(".*(?:[:/@]|-v)[^ ]+.*"))
			throw new IllegalArgumentException("Versioned identity required");
	}

	@SuppressWarnings("unchecked") // A neutral wrapper avoids result-metadata reserved
									// keys in native JSON objects.
	static Map<String, Object> portable(Map<String, Object> m) {
		return (Map<String, Object>) Objects
			.requireNonNull(Judgment.pass("").toBuilder().metadata("value", m).build().metadata().get("value"));
	}

	@SuppressWarnings("unchecked") // Portable result normalization freezes recursively
									// and preserves list shape.
	static List<Object> portableList(List<Object> v) {
		return (List<Object>) Objects.requireNonNull(portable(Map.of("v", v)).get("v"));
	}

	static void criterion(Object v) {
		if (!(v instanceof String || v instanceof Map || v instanceof List))
			throw new IllegalArgumentException("Criterion must be string, object or array");
	}

	static byte[] json(Object o) {
		try {
			return JSON.writeValueAsBytes(o);
		}
		catch (Exception e) {
			io.github.markpollack.judge.portable.PreservationLimitException.propagate(e);
			throw new IllegalArgumentException("Invalid portable configuration");
		}
	}

	static JsonNode parse(byte[] b) {
		try {
			return Objects.requireNonNull(JSON.readTree(b));
		}
		catch (Exception e) {
			io.github.markpollack.judge.portable.PreservationLimitException.propagate(e);
			throw new IllegalArgumentException("Invalid provider JSON");
		}
	}

}
