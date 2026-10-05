/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.serialization;

import io.github.markpollack.judge.judgment.RefusedReturn;
import io.github.markpollack.judge.verdict.CompositeAttempt;
import io.github.markpollack.judge.verdict.CompositeFailure;
import io.github.markpollack.judge.verdict.Seat;
import io.github.markpollack.judge.verdict.Verdict;
import io.github.markpollack.judge.verdict.VerdictProvenance;

import java.io.IOException;
import java.util.*;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.github.markpollack.judge.judgment.*;
import io.github.markpollack.judge.jury.*;
import io.github.markpollack.judge.verdict.*;
import io.github.markpollack.judge.voting.*;
import io.github.markpollack.judge.requirement.*;
import io.github.markpollack.judge.provenance.*;
import org.jspecify.annotations.Nullable;

/**
 * Jackson boundary for the current stored format. Domain conclusions do not depend on
 * this class.
 */
public final class ResultJson {

	/** Current Judgment, Verdict and EvaluationResult format. */
	public static final int VERSION = 6;
	static final String SPECIFICATIONS = ResultJson.class.getName() + ".specifications";

	/**
	 * Explicit domain converters; raw POJO binding is not the storage contract.
	 * @return converter module
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static com.fasterxml.jackson.databind.Module module() {
		var module = new com.fasterxml.jackson.databind.module.SimpleModule("agent-eval-results");
		module.addSerializer(Judgment.class, new JudgmentWriter());
		module.addDeserializer(Judgment.class, new JudgmentReader());
		module.addSerializer(Verdict.class, new VerdictWriter());
		module.addDeserializer(Verdict.class, new VerdictReader());
		module.addSerializer((Class) Requirement.class, new RequirementWriter());
		module.addDeserializer(Requirement.class, new RequirementReader());
		module.setMixInAnnotation(Seat.class, SeatMixin.class);
		return module;
	}

	/** Engine-owned strict seat integer registration. */
	public abstract static class SeatMixin {

		/** Strict integer mixin registration. */
		protected SeatMixin() {
		}

		/**
		 * Seat position.
		 * @return explicit integral position
		 */
		@JsonDeserialize(using = StrictIntegerDeserializer.class)
		public abstract int position();

	}

	/** Context attribute containing the explicitly trusted rule vocabulary. */
	public static final String RULES = "agent-eval.votingRules";

	/**
	 * Wire-only complete rule declaration.
	 *
	 * @param token stable reconstruction token
	 * @param configuration complete portable settings
	 */
	public record RuleDocument(String token, Map<String, Object> configuration) {
	}

	@SuppressWarnings("unchecked")
	private static Map<String, io.github.markpollack.judge.voting.VotingRuleFactory> rules(@Nullable Object attribute) {
		return attribute instanceof Map<?, ?> map
				? (Map<String, io.github.markpollack.judge.voting.VotingRuleFactory>) map
				: io.github.markpollack.judge.voting.VotingRules.builtIns();
	}

	private ResultJson() {
	}

	private static void version(int version) {
		if (version != VERSION)
			throw new IllegalArgumentException("Unsupported result schemaVersion: " + version + "; expected " + VERSION
					+ ". V5 requires archival reading with c0ae61dda4a65e9278485cb528925d71f27a1007; V2/3/4 use 7387aab1bf9d3bd56e4d9a932f2f40e2978d676d; no current typed migration is provided");
	}

	private static JsonNode currentTree(JsonParser parser, DeserializationContext context) throws IOException {
		JsonNode node = context.readTree(parser);
		JsonNode format = node.get("schemaVersion");
		if (format == null || !format.isIntegralNumber() || !format.canConvertToInt())
			throw JsonMappingException.from(parser,
					"Result schemaVersion must be an explicit integer; expected " + VERSION);
		version(format.intValue());
		return node;
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
	 * @param requirement actual configured requirement, absent for rule-only producers
	 * @param invocations owned immutable native execution observations
	 * @param invocationIds references to shared native execution observations
	 * @param refusedReturn complete separately refused original, or null
	 */
	@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
	public record JudgmentDocument(
			@com.fasterxml.jackson.annotation.JsonProperty(required = true) @JsonDeserialize(
					using = StrictIntegerDeserializer.class) int schemaVersion,
			JudgmentStatus producerStatus, @Nullable Finding finding, @Nullable Confidence confidence,
			@Nullable ProbabilityDistribution probabilityDistribution, @Nullable JudgmentReasonCode reasonCode,
			String reasoning, List<Check> checks, @Nullable Provenance provenance, Map<String, Object> metadata,
			@Nullable Requirement<?> requirement, List<Invocation> invocations, List<String> invocationIds,
			@Nullable RefusedReturn refusedReturn) {
	}

	/**
	 * Wire-only Verdict shape.
	 *
	 * @param schemaVersion stored format
	 * @param judgment collective judgment
	 * @param individual ordered opinions
	 * @param individualByName keyed opinions
	 * @param seats execution seats
	 * @param provenance composition origin
	 * @param compositeAttempts complete child attempts
	 * @param declaredCardinality configured population
	 * @param requirement optional actual requirement
	 * @param reductionFailure optional failed reduction
	 * @param roster complete ordered independent requirements, empty for ordinary
	 * composition
	 * @param invocations owned immutable native execution observations
	 * @param rule complete stable rule identity/configuration for a captured reduction
	 * attempt, including a failed attempt; null when no declaration was captured
	 */
	@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
	public record VerdictDocument(
			@com.fasterxml.jackson.annotation.JsonProperty(required = true) @JsonDeserialize(
					using = StrictIntegerDeserializer.class) int schemaVersion,
			Judgment judgment, List<Judgment> individual, Map<String, Judgment> individualByName, List<Seat> seats,
			VerdictProvenance provenance, List<CompositeAttempt> compositeAttempts,
			@com.fasterxml.jackson.annotation.JsonProperty(required = true) @JsonDeserialize(
					using = StrictIntegerDeserializer.class) int declaredCardinality,
			@Nullable Requirement<?> requirement, @Nullable CompositeFailure reductionFailure,
			List<Requirement<?>> roster, List<Invocation> invocations, @Nullable RuleDocument rule) {
	}

	/** Writes current Judgment documents. */
	public static final class JudgmentWriter extends JsonSerializer<Judgment> {

		/** Jackson constructor. */
		public JudgmentWriter() {
		}

		@Override
		public void serialize(Judgment j, JsonGenerator g, SerializerProvider provider) throws IOException {
			provider
				.defaultSerializeValue(
						new JudgmentDocument(VERSION, j.producerStatus(), j.finding(), j.confidence(),
								j.probabilityDistribution(), j.reasonCode(), j.reasoning(), j.checks(), j.provenance(),
								j.metadata(), j.requirement(), j.invocations(), j.invocationIds(), j.refusedReturn()),
						g);
		}

	}

	/** Reads current Judgment documents; unknown versions are refused. */
	public static final class JudgmentReader extends JsonDeserializer<Judgment> {

		/** Jackson constructor. */
		public JudgmentReader() {
		}

		@Override
		public Judgment deserialize(JsonParser parser, DeserializationContext context) throws IOException {
			JudgmentDocument j = context.readTreeAsValue(currentTree(parser, context), JudgmentDocument.class);
			version(j.schemaVersion());
			try {
				return new Judgment(j.producerStatus(), j.finding(), j.confidence(), j.probabilityDistribution(),
						j.reasonCode(), j.reasoning(), j.checks(), j.provenance(), j.metadata(), j.requirement(),
						j.invocations(), j.invocationIds(), j.refusedReturn());
			}
			catch (RuntimeException ex) {
				io.github.markpollack.judge.portable.PreservationLimitException.propagate(ex);
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
			if (v.rule() != null)
				io.github.markpollack.judge.voting.VotingRules.reconstruct(v.rule().token(), v.rule().configuration(),
						rules(provider.getAttribute(RULES)));
			provider.defaultSerializeValue(new VerdictDocument(VERSION, v.judgment(), v.individual(),
					v.individualByName(), v.seats(), v.provenance(), v.compositeAttempts(), v.declaredCardinality(),
					v.requirement(), v.reductionFailure(), v.roster(), v.invocations(),
					v.rule() == null ? null : new RuleDocument(v.rule().token(), v.rule().configuration())), g);
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
			VerdictDocument v = context.readTreeAsValue(currentTree(parser, context), VerdictDocument.class);
			version(v.schemaVersion());
			try {
				var aliases = retainedAliases(v);
				Verdict result = new Verdict(aliases.judgment(), aliases.individual(), aliases.names(), aliases.seats(),
						v.provenance(), v.compositeAttempts(), v.declaredCardinality(), v.requirement(),
						v.reductionFailure(), v.roster(), v.invocations(),
						v.rule() == null ? null : io.github.markpollack.judge.voting.VotingRules.reconstruct(
								v.rule().token(), v.rule().configuration(), rules(context.getAttribute(RULES))));
				return result;
			}
			catch (RuntimeException ex) {
				io.github.markpollack.judge.portable.PreservationLimitException.propagate(ex);
				throw JsonMappingException.from(parser, ex.getMessage(), ex);
			}
		}

	}

	// V6 repeats complete values at declared joins. Restore only those joins, never
	// intern equal independent opinions or Check nodes: those still count separately.
	private record AliasInputs(Judgment judgment, List<Judgment> individual, Map<String, Judgment> names,
			List<Seat> seats) {
	}

	private static AliasInputs retainedAliases(VerdictDocument v) {
		var individual = new ArrayList<>(v.individual());
		var seats = new ArrayList<>(v.seats());
		Judgment aggregate = v.judgment();
		for (int position = 0; position < v.compositeAttempts().size(); position++) {
			var attempt = v.compositeAttempts().get(position);
			Verdict child = attempt.verdict();
			if (child == null || attempt.disposition() != AttemptDisposition.USED)
				continue;
			if (attempt.relation() == CompositeRelation.CASCADE_TIER
					&& v.provenance().kind() == VerdictProvenanceKind.TIER
					&& attempt.name().equals(v.provenance().tier())) {
				if (aggregate.equals(child.judgment()))
					aggregate = child.judgment();
				if (individual.equals(child.individual()))
					individual = new ArrayList<>(child.individual());
				if (seats.equals(child.seats()))
					seats = new ArrayList<>(child.seats());
			}
			if (attempt.relation() == CompositeRelation.META_MEMBER) {
				for (int i = 0; i < seats.size() && i < individual.size(); i++)
					if (seats.get(i).position() == position && individual.get(i).equals(child.judgment()))
						individual.set(i, child.judgment());
			}
		}
		if (v.provenance().kind() == VerdictProvenanceKind.OWN && v.declaredCardinality() == 1
				&& individual.size() == 1 && aggregate.equals(individual.getFirst()))
			aggregate = individual.getFirst();
		for (int i = 0; i < seats.size() && i < individual.size(); i++) {
			Seat seat = seats.get(i);
			Judgment rejection = seat.rejection();
			if (rejection == null || rejection.refusedReturn() == null)
				continue;
			RefusedReturn refusal = rejection.refusedReturn();
			if (refusal.original() == individual.get(i) || !refusal.original().equals(individual.get(i)))
				continue;
			var joined = new Judgment(rejection.producerStatus(), rejection.finding(), rejection.confidence(),
					rejection.probabilityDistribution(), rejection.reasonCode(), rejection.reasoning(), rejection.checks(),
					rejection.provenance(), rejection.metadata(), rejection.requirement(), rejection.invocations(),
					rejection.invocationIds(), new RefusedReturn(individual.get(i), refusal.expected(), refusal.reason()));
			seats.set(i, new Seat(seat.position(), seat.verdictKey(), seat.keySource(), seat.execution(),
					seat.participation(), seat.cause(), seat.notApplicableWhen(), joined, seat.declaredWeight()));
		}
		var names = new LinkedHashMap<>(v.individualByName());
		for (int i = 0; i < seats.size() && i < individual.size(); i++) {
			String key = seats.get(i).verdictKey();
			if (individual.get(i).equals(names.get(key)))
				names.put(key, individual.get(i));
		}
		return new AliasInputs(aggregate, individual, names, seats);
	}

	@SuppressWarnings("unchecked")
	private static Map<String, SpecificationCodec<?>> specifications(@Nullable Object attribute) {
		return attribute instanceof Map<?, ?> map ? (Map<String, SpecificationCodec<?>>) map : Map.of("text",
				SpecificationCodec.general(String.class), "allOf", SpecificationCodec.general(AllOf.class));
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
				.filter(e -> e.getValue().specificationType().equals(r.specification().getClass()))
				.map(Map.Entry::getKey)
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"No registered specification codec for " + r.specification().getClass().getName()));
			Requirement<?> reconstructed = Objects.requireNonNull(registry.get(type))
				.reconstruct(r.id(), r.revision(), r.text(), r.specification(), r.source());
			if (!reconstructed.getClass().equals(r.getClass()) || !Requirement.equivalent(r, reconstructed))
				throw new IllegalArgumentException(
						"Unregistered Requirement reconstruction for " + r.getClass().getName());
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
			SpecificationCodec<?> registration = specifications(context.getAttribute(SPECIFICATIONS)).get(type);
			if (registration == null)
				throw new IllegalArgumentException("Unsupported specification type: " + type);
			Object specification = context.readTreeAsValue(
					Objects.requireNonNull(node.get("specification"), "specification"),
					registration.specificationType());
			RequirementSource source = context.readTreeAsValue(Objects.requireNonNull(node.get("source"), "source"),
					RequirementSource.class);
			return registration.reconstruct(text(node, "id"), text(node, "revision"), text(node, "text"), specification,
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
