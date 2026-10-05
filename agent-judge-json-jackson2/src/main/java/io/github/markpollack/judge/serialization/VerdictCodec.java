/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.serialization;

import java.util.*;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.cfg.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.type.LogicalType;
import io.github.markpollack.judge.verdict.Verdict;
import io.github.markpollack.judge.requirement.AllOf;
import io.github.markpollack.judge.evaluation.*;
import io.github.markpollack.judge.policy.*;

/**
 * Strict current-format storage. Historical diagnostic reading is a separate explicit
 * API.
 */
public final class VerdictCodec {

	private final io.github.markpollack.judge.json.EvalJsonMapper json;

	/** Configure only built-in text and all-of specifications. */
	public VerdictCodec() {
		this(Map.of());
	}

	/**
	 * Register stable wire names for native specification types with explicit Jackson
	 * codecs. Types use their declared Jackson shape, never class-name polymorphism or
	 * toString().
	 * @param specifications additional unique names and concrete native types
	 */
	public VerdictCodec(Map<String, Class<?>> specifications) {
		this(generalTypes(specifications), true);
	}

	private static Map<String, SpecificationCodec<?>> generalTypes(Map<String, Class<?>> specifications) {
		Map<String, SpecificationCodec<?>> result = new LinkedHashMap<>();
		specifications.forEach((name, type) -> result.put(name, SpecificationCodec.general(type)));
		return result;
	}

	/**
	 * Registers exact pure native Requirement factories.
	 * @param specifications additional stable wire names and trusted reconstruction
	 * @return current-format codec
	 */
	public static VerdictCodec withSpecifications(Map<String, SpecificationCodec<?>> specifications) {
		return new VerdictCodec(specifications, true);
	}

	private VerdictCodec(Map<String, SpecificationCodec<?>> specifications, boolean registered) {
		var types = new LinkedHashMap<String, SpecificationCodec<?>>();
		types.put("text", SpecificationCodec.general(String.class));
		types.put("allOf", SpecificationCodec.general(AllOf.class));
		for (var entry : specifications.entrySet()) {
			Objects.requireNonNull(entry.getValue());
			if (entry.getKey().isBlank() || types.containsKey(entry.getKey())
					|| types.values()
						.stream()
						.anyMatch(value -> value.specificationType().equals(entry.getValue().specificationType())))
				throw new IllegalArgumentException("Ambiguous specification codec: " + entry.getKey());
			types.put(entry.getKey(), entry.getValue());
		}
		ObjectMapper mapper = JsonMapper.builder().addModule(io.github.markpollack.judge.serialization.ResultJson.module())
			.disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
			.withCoercionConfig(LogicalType.Textual,
					c -> c.setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail)
						.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
						.setCoercion(CoercionInputShape.Float, CoercionAction.Fail))
			.disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
			.enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
			.enable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
			.enable(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES)
			.enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
			.enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
			.build();
		mapper.setDefaultAttributes(
				ContextAttributes.getEmpty().withSharedAttribute(ResultJson.SPECIFICATIONS, Map.copyOf(types)));
		json = new JacksonEvalJsonMapper(mapper);
	}

	/**
	 * Write a usable complete verdict.
	 * @param verdict retained verdict
	 * @return current JSON
	 */
	public String write(Verdict verdict) {
		verdict.conclusion();
		io.github.markpollack.judge.verdict.InvocationRecords.of(verdict);
		return encode(verdict);
	}

	/**
	 * Read current JSON or throw, without fabricating an inconclusive verdict.
	 * @param json stored document
	 * @return complete validated verdict
	 */
	public Verdict read(String json) {
		Verdict verdict = decode(json, Verdict.class);
		verdict.conclusion();
		io.github.markpollack.judge.verdict.InvocationRecords.of(verdict);
		return verdict;
	}

	/**
	 * Read a parsed current document, enforcing the same domain rules.
	 * @param stored parsed JSON
	 * @return complete validated verdict
	 */
	public Verdict read(Map<String, Object> stored) {
		return read(encode(stored));
	}

	/**
	 * Write execution facts; Throwable graphs are never serialized.
	 * @param result retained result
	 * @return current JSON
	 */
	public String write(EvaluationResult result) {
		result.verdict().conclusion();
		io.github.markpollack.judge.verdict.InvocationRecords.of(result.verdict());
		var policy = new LinkedHashMap<String, Object>();
		switch (result.policyResult()) {
			case PolicyResult.NotRequested ignored -> policy.put("kind", "notRequested");
			case PolicyResult.Decided d -> {
				policy.put("kind", "decided");
				policy.put("decision", d.decision());
			}
			case PolicyResult.Failed f -> {
				policy.put("kind", "failed");
				policy.put("failure",
						Map.of("type",
								f.cause() instanceof StoredPolicyFailure stored ? stored.originalType()
										: f.cause().getClass().getName(),
								"message", Objects.toString(f.cause().getMessage(), "")));
			}
		}
		return encode(Map.of("schemaVersion", ResultJson.VERSION, "verdict", result.verdict(), "policyResult", policy));
	}

	/**
	 * Reopen execution without running a judge or policy.
	 * @param json stored evaluation
	 * @return retained result with explicit stored failure data
	 */
	public EvaluationResult readEvaluation(String json) {
		JsonNode root = decode(json, JsonNode.class);
		fields(root, Set.of("schemaVersion", "verdict", "policyResult"));
		if (!root.path("schemaVersion").isIntegralNumber() || !root.path("schemaVersion").canConvertToInt()
				|| root.path("schemaVersion").intValue() != ResultJson.VERSION)
			throw new IllegalArgumentException("Unsupported evaluation schemaVersion; expected " + ResultJson.VERSION
					+ "; older typed schemas require pinned archival reading");
		Verdict verdict = read(root.path("verdict").toString());
		JsonNode policy = root.path("policyResult");
		PolicyResult result = switch (policy.path("kind").asText()) {
			case "notRequested" -> {
				fields(policy, Set.of("kind"));
				yield new PolicyResult.NotRequested();
			}
			case "decided" -> {
				fields(policy, Set.of("kind", "decision"));
				yield new PolicyResult.Decided(decode(policy.path("decision").toString(), PolicyDecision.class));
			}
			case "failed" -> {
				fields(policy, Set.of("kind", "failure"));
				JsonNode failure = policy.path("failure");
				fields(failure, Set.of("type", "message"));
				if (!failure.path("type").isTextual() || !failure.path("message").isTextual())
					throw new IllegalArgumentException("Invalid stored failure details");
				yield new PolicyResult.Failed(
						new StoredPolicyFailure(failure.path("type").textValue(), failure.path("message").textValue()));
			}
			default -> throw new IllegalArgumentException("Unsupported policy-result kind");
		};
		return new EvaluationResult(verdict, result);
	}

	private static void fields(JsonNode node, Set<String> fields) {
		if (!node.isObject() || node.size() != fields.size())
			throw new IllegalArgumentException("Incomplete or contradictory stored object");
		node.fieldNames().forEachRemaining(key -> {
			if (!fields.contains(key))
				throw new IllegalArgumentException("Unknown field: " + key);
		});
	}

	private String encode(Object value) { return json.write(value); }
 private <T> T decode(String value, Class<T> type) { return json.read(value,type); }

	/**
	 * Stored data representing a failed policy; does not load or instantiate the original
	 * exception class.
	 */
	public static final class StoredPolicyFailure extends RuntimeException {

		/** Recorded exception type name; treated only as text. */
		private final String originalType;

		/**
		 * Construct explicit failure data.
		 * @param originalType recorded class name, treated only as text
		 * @param message recorded message
		 */
		public StoredPolicyFailure(String originalType, String message) {
			super(message);
			this.originalType = Objects.requireNonNull(originalType);
		}

		/**
		 * Original exception class name as recorded data.
		 * @return recorded name
		 */
		public String originalType() {
			return originalType;
		}

	}

}
