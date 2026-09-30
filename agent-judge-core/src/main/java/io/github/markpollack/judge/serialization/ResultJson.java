/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.serialization;

import java.io.IOException;
import java.util.*;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.github.markpollack.judge.judgment.*;
import io.github.markpollack.judge.jury.*;
import io.github.markpollack.judge.requirement.*;
import io.github.markpollack.judge.provenance.*;
import org.jspecify.annotations.Nullable;

/**
 * Jackson boundary for the current stored format. Domain conclusions do not depend on
 * this class.
 */
public final class ResultJson {

	/** Current Judgment, Verdict and EvaluationResult format. */
	public static final int VERSION = 4;
	static final String SPECIFICATIONS = ResultJson.class.getName() + ".specifications";

	private ResultJson() {
	}

	private static void version(int version) {
		if (version != VERSION)
			throw new IllegalArgumentException(
					"Unsupported result schemaVersion: " + version + "; expected " + VERSION);
	}

	/**
	 * Wire-only Judgment shape.
	 *
	 * @param schemaVersion stored format
	 * @param producerStatus producer outcome
	 * @param finding optional finding
	 * @param confidence native confidence
	 * @param probabilityDistribution native probabilities
	 * @param reasonCode producer cause
	 * @param reasoning explanation
	 * @param checks child judgments
	 * @param provenance provenance
	 * @param metadata incidental portable facts
	 */
	@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
	public record JudgmentDocument(
			@com.fasterxml.jackson.annotation.JsonProperty(required = true) @JsonDeserialize(
					using = StrictIntegerDeserializer.class) int schemaVersion,
			JudgmentStatus producerStatus, @Nullable Finding finding, @Nullable Confidence confidence,
			@Nullable ProbabilityDistribution probabilityDistribution, @Nullable JudgmentReasonCode reasonCode,
			String reasoning, List<Check> checks, @Nullable Provenance provenance, Map<String, Object> metadata) {
	}

	/**
	 * Wire-only Verdict shape.
	 *
	 * @param schemaVersion stored format
	 * @param judgment collective judgment
	 * @param individual ordered opinions
	 * @param individualByName keyed opinions
	 * @param weights weights
	 * @param seats execution seats
	 * @param provenance composition origin
	 * @param compositeAttempts complete child attempts
	 * @param declaredCardinality configured population
	 * @param requirement optional actual requirement
	 * @param reductionFailure optional failed reduction
	 */
	@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
	public record VerdictDocument(
			@com.fasterxml.jackson.annotation.JsonProperty(required = true) @JsonDeserialize(
					using = StrictIntegerDeserializer.class) int schemaVersion,
			Judgment judgment, List<Judgment> individual, Map<String, Judgment> individualByName,
			Map<String, Double> weights, List<Seat> seats, VerdictProvenance provenance,
			List<CompositeAttempt> compositeAttempts,
			@com.fasterxml.jackson.annotation.JsonProperty(required = true) @JsonDeserialize(
					using = StrictIntegerDeserializer.class) int declaredCardinality,
			@Nullable Requirement<?> requirement, @Nullable CompositeFailure reductionFailure) {
	}

	/** Writes current Judgment documents. */
	public static final class JudgmentWriter extends JsonSerializer<Judgment> {

		/** Jackson constructor. */
		public JudgmentWriter() {
		}

		@Override
		public void serialize(Judgment j, JsonGenerator g, SerializerProvider provider) throws IOException {
			provider.defaultSerializeValue(new JudgmentDocument(VERSION, j.producerStatus(), j.finding(),
					j.confidence(), j.probabilityDistribution(), j.reasonCode(), j.reasoning(), j.checks(),
					j.provenance(), j.metadata()), g);
		}

	}

	/** Reads current Judgment documents; unknown versions are refused. */
	public static final class JudgmentReader extends JsonDeserializer<Judgment> {

		/** Jackson constructor. */
		public JudgmentReader() {
		}

		@Override
		public Judgment deserialize(JsonParser parser, DeserializationContext context) throws IOException {
			JudgmentDocument j = context.readValue(parser, JudgmentDocument.class);
			version(j.schemaVersion());
			try {
				return new Judgment(j.producerStatus(), j.finding(), j.confidence(), j.probabilityDistribution(),
						j.reasonCode(), j.reasoning(), j.checks(), j.provenance(), j.metadata());
			}
			catch (RuntimeException ex) {
				throw JsonMappingException.from(parser, ex.getMessage(), ex);
			}
		}

	}

	/** Writes current Verdict documents without storing a second conclusion. */
	public static final class VerdictWriter extends JsonSerializer<Verdict> {

		/** Jackson constructor. */
		public VerdictWriter() {
		}

		@Override
		public void serialize(Verdict v, JsonGenerator g, SerializerProvider provider) throws IOException {
			provider.defaultSerializeValue(new VerdictDocument(VERSION, v.judgment(), v.individual(),
					v.individualByName(), v.weights(), v.seats(), v.provenance(), v.compositeAttempts(),
					v.declaredCardinality(), v.requirement(), v.reductionFailure()), g);
		}

	}

	/**
	 * Reads a complete Verdict; semantic validation occurs before the codec yields it.
	 */
	public static final class VerdictReader extends JsonDeserializer<Verdict> {

		/** Jackson constructor. */
		public VerdictReader() {
		}

		@Override
		public Verdict deserialize(JsonParser parser, DeserializationContext context) throws IOException {
			VerdictDocument v = context.readValue(parser, VerdictDocument.class);
			version(v.schemaVersion());
			try {
				Verdict result = new Verdict(v.judgment(), v.individual(), v.individualByName(), v.weights(), v.seats(),
						v.provenance(), v.compositeAttempts(), v.declaredCardinality(), v.requirement(),
						v.reductionFailure());
				return result;
			}
			catch (RuntimeException ex) {
				throw JsonMappingException.from(parser, ex.getMessage(), ex);
			}
		}

	}

	@SuppressWarnings("unchecked")
	private static Map<String, Class<?>> specifications(@Nullable Object attribute) {
		return attribute instanceof Map<?, ?> map ? (Map<String, Class<?>>) map
				: Map.of("text", String.class, "allOf", AllOf.class);
	}

	/**
	 * Writes an explicitly identified native specification, never arbitrary toString().
	 */
	public static final class RequirementWriter extends JsonSerializer<Requirement<?>> {

		/** Jackson constructor. */
		public RequirementWriter() {
		}

		@Override
		public void serialize(Requirement<?> r, JsonGenerator g, SerializerProvider provider) throws IOException {
			var registry = specifications(provider.getAttribute(SPECIFICATIONS));
			String type = registry.entrySet()
				.stream()
				.filter(e -> e.getValue().equals(r.specification().getClass()))
				.map(Map.Entry::getKey)
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"No registered specification codec for " + r.specification().getClass().getName()));
			g.writeStartObject();
			g.writeStringField("id", r.id());
			g.writeStringField("revision", r.revision());
			g.writeStringField("text", r.text());
			g.writeStringField("specificationType", type);
			provider.defaultSerializeField("specification", r.specification(), g);
			provider.defaultSerializeField("source", r.source(), g);
			g.writeEndObject();
		}

	}

	/** Reads only explicitly registered native specification types. */
	public static final class RequirementReader extends JsonDeserializer<Requirement<?>> {

		/** Jackson constructor. */
		public RequirementReader() {
		}

		@Override
		public Requirement<?> deserialize(JsonParser parser, DeserializationContext context) throws IOException {
			JsonNode node = context.readTree(parser);
			Set<String> allowed = Set.of("id", "revision", "text", "specificationType", "specification", "source");
			node.fieldNames().forEachRemaining(key -> {
				if (!allowed.contains(key))
					throw new IllegalArgumentException("Unknown requirement field: " + key);
			});
			String type = text(node, "specificationType");
			Class<?> target = specifications(context.getAttribute(SPECIFICATIONS)).get(type);
			if (target == null)
				throw new IllegalArgumentException("Unsupported specification type: " + type);
			Object specification = context
				.readTreeAsValue(Objects.requireNonNull(node.get("specification"), "specification"), target);
			RequirementSource source = context.readTreeAsValue(Objects.requireNonNull(node.get("source"), "source"),
					RequirementSource.class);
			return new Requirement<>(text(node, "id"), text(node, "revision"), text(node, "text"), specification,
					source);
		}

		private static String text(JsonNode node, String key) {
			JsonNode value = node.get(key);
			if (value == null || !value.isTextual())
				throw new IllegalArgumentException("Requirement " + key + " must be a string");
			return value.textValue();
		}

	}

}
