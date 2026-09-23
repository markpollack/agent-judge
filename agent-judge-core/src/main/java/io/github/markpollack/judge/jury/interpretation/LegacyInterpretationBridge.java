/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.jury.interpretation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.markpollack.judge.result.Judgment;
import io.github.markpollack.judge.result.JudgmentStatus;
import org.jspecify.annotations.Nullable;

/**
 * Temporary projection into the v1 interpreter. This is not serialization and never
 * changes the retained result. Modern-only semantics explicitly make the reading
 * undetermined. Replace with the complete version-2 reader when that contract is
 * implemented.
 */
final class LegacyInterpretationBridge {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private LegacyInterpretationBridge() {
	}

	static Map<String, Object> project(Map<String, Object> input, Consumer<String> unsupported) {
		if (!hasModern(input, 0)) {
			return input;
		}
		if (!bounded(input, 0)) {
			unsupported.accept("verdict");
			return Map.of();
		}
		return object(input, "verdict", unsupported);
	}

	private static boolean hasModern(@Nullable Object value, int depth) {
		if (depth > 64) {
			return false;
		}
		if (value instanceof Map<?, ?> map) {
			if (map.containsKey("producerStatus")) {
				return true;
			}
			return map.entrySet()
				.stream()
				.anyMatch(entry -> !"metadata".equals(entry.getKey()) && hasModern(entry.getValue(), depth + 1));
		}
		return value instanceof List<?> list && list.stream().anyMatch(item -> hasModern(item, depth + 1));
	}

	private static boolean bounded(@Nullable Object value, int depth) {
		if (depth > 64) {
			return false;
		}
		if (value instanceof Map<?, ?> map) {
			return map.values().stream().allMatch(item -> bounded(item, depth + 1));
		}
		return !(value instanceof List<?> list) || list.stream().allMatch(item -> bounded(item, depth + 1));
	}

	private static boolean legacyCompatible(Judgment judgment) {
		if (judgment.certainty() != null || judgment.distribution() != null || judgment.provenance() != null
				|| judgment.policyApplication() != null) {
			return false;
		}
		var assessment = judgment.assessment();
		if (assessment == null) {
			return true;
		}
		if (assessment.proposition() != null) {
			return false;
		}
		var category = assessment.category();
		if (category != null && (category.selected() == null || category.alternatives().size() != 1)) {
			return false;
		}
		var numeric = assessment.numeric();
		return numeric == null || ((judgment.producerStatus() == JudgmentStatus.PASS
				|| judgment.producerStatus() == JudgmentStatus.FAIL)
				&& numeric.kind() == io.github.markpollack.judge.result.NumericKind.MEASUREMENT
				&& numeric.scaleId().equals("normalized-quality:v1") && numeric.lower() == 0 && numeric.upper() == 1
				&& numeric.qualityDirection() == io.github.markpollack.judge.result.QualityDirection.INCREASING);
	}

	private static Map<String, Object> object(Map<?, ?> input, String path, Consumer<String> unsupported) {
		Map<String, Object> result = new LinkedHashMap<>();
		input.forEach((key, value) -> {
			if (key instanceof String name && value != null) {
				// Incidental metadata and retained modern fields are opaque to v1.
				result.put(name, name.equals("metadata") ? value : value(value, path + "." + name, unsupported));
			}
		});
		if (input.containsKey("producerStatus")) {
			try {
				Judgment judgment = MAPPER.convertValue(input, Judgment.class);
				result.put("status", judgment.status().wireName());
				result.remove("reasonCode");
				if (judgment.operationalReasonCode() != null) {
					result.put("reasonCode", judgment.operationalReasonCode().wireName());
				}
				result.put("reasoning", judgment.operationalReasoning());
				if (judgment.score() != null) {
					result.put("score", judgment.score());
				}
				if (judgment.label() != null) {
					result.put("label", judgment.label());
				}
				if (!legacyCompatible(judgment)) {
					unsupported.accept(path);
				}
				List<Map<String, Object>> checks = new ArrayList<>();
				for (var check : judgment.checks()) {
					Judgment child = check.judgment();
					if (child.status() != JudgmentStatus.PASS && child.status() != JudgmentStatus.FAIL) {
						unsupported.accept(path + ".checks." + check.id());
						// Never encode an inconclusive child as a Boolean violation.
						continue;
					}
					checks.add(Map.of("name", check.id(), "passed", child.pass(), "message",
							child.operationalReasoning()));
					if (child.assessment() != null || child.reasonCode() != null || !child.metadata().isEmpty()) {
						unsupported.accept(path + ".checks." + check.id());
					}
				}
				result.put("checks", checks);
			}
			catch (IllegalArgumentException invalid) {
				unsupported.accept(path);
			}
		}
		return result;
	}

	private static Object value(Object value, String path, Consumer<String> unsupported) {
		if (value instanceof Map<?, ?> map) {
			return object(map, path, unsupported);
		}
		if (value instanceof List<?> list) {
			List<@Nullable Object> copy = new ArrayList<>();
			for (int i = 0; i < list.size(); i++) {
				Object item = list.get(i);
				copy.add(item == null ? null : value(item, path + "[" + i + "]", unsupported));
			}
			return copy;
		}
		return value;
	}

}
