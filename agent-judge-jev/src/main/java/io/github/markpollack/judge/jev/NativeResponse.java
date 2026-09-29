/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jev;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.DeserializationFeature;
import io.github.markpollack.judge.result.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import org.jspecify.annotations.Nullable;

record NativeResponse(String model, long inputTokens, long outputTokens, JudgmentStatus status, Assessment assessment,
		@Nullable Certainty certainty, Distribution distribution) {
	record Envelope(String model, long inputTokens, long outputTokens, boolean providerMetadataPresent,
			@Nullable Double cost) {

		void addCostTo(Map<String, Object> usage) {
			if (cost != null) {
				usage.put("cost", cost);
				usage.put("currency", "USD");
				usage.put("costSource", "vercel-gateway-reported:v1:/provider_metadata/gateway/cost");
			}
		}
	}

	static Envelope envelope(byte[] bytes, boolean gateway) {
		JsonNode root = Checks.parse(bytes);
		String model = text(root.path("model"));
		if (!(model.matches("jev-[0-9]+\\.[0-9]+\\.[0-9]+") || gateway && model.equals("typesafe-ai/jev")))
			throw invalid();
		JsonNode usage = root.path("usage");
		return new Envelope(model, tokens(usage.path("input_tokens")), tokens(usage.path("output_tokens")),
				root.has("provider_metadata"), gateway ? gatewayCost(bytes) : null);
	}

	/** Read optional billing independently of assessment validity and token pricing. */
	private static @Nullable Double gatewayCost(byte[] bytes) {
		try {
			// Read only this decimal at full precision before projecting to portable
			// Double, so an out-of-range charge cannot silently underflow to zero.
			JsonNode node = Checks.JSON.readerFor(JsonNode.class)
				.with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
				.at("/provider_metadata/gateway/cost")
				.readValue(bytes);
			if (node == null || !(node.isNumber() || node.isTextual()))
				return null;
			BigDecimal decimal = new BigDecimal(node.asText());
			double amount = decimal.doubleValue();
			return decimal.signum() < 0 || !Double.isFinite(amount) || amount == 0 && decimal.signum() != 0
					? null : amount;
		}
		catch (IOException | NumberFormatException e) {
			// Missing or invalid optional billing is unknown, not a free request or
			// an invalid assessment. Exact native bytes remain in protected capture.
			return null;
		}
	}

	static NativeResponse read(byte[] bytes, JevQuestion question) {
		JsonNode root = Checks.parse(bytes);
		String model = text(root.path("model"));
		JsonNode usage = root.path("usage");
		long in = tokens(usage.path("input_tokens")), out = tokens(usage.path("output_tokens"));
		JsonNode answers = root.path("answers");
		if (!answers.isObject() || answers.size() != 1 || !answers.has("q"))
			throw invalid();
		JsonNode answer = answers.path("q");
		String type = text(answer.path("type"));
		if (question instanceof JevQuestion.Noul) {
			if (!type.equals("noul"))
				throw invalid();
			double p = probability(answer.path("noul"));
			Boolean selected = p == .5 ? null : p > .5;
			return new NativeResponse(model, in, out,
					selected == null ? JudgmentStatus.ABSTAIN : selected ? JudgmentStatus.PASS : JudgmentStatus.FAIL,
					new Assessment(new Proposition(selected), null, null), null,
					new Distribution(AssessmentTarget.PROPOSITION, "jev.noul.probability-of-true:v1",
							List.of(new ProbabilityMass("false", 1 - p), new ProbabilityMass("true", p))));
		}
		double confidence = probability(answer.path("confidence"));
		if (question instanceof JevQuestion.Choice choice) {
			if (!type.equals("choice"))
				throw invalid();
			String selected = text(answer.path("choice"));
			List<String> domain = List.copyOf(choice.criteria().keySet());
			List<ProbabilityMass> masses = masses(answer.path("probabilities"), domain);
			if (!domain.contains(selected))
				throw invalid();
			double maximum = masses.stream().mapToDouble(ProbabilityMass::probability).max().orElseThrow();
			double selectedMass = masses.get(domain.indexOf(selected)).probability();
			if (maximum - selectedMass > 1e-6)
				throw invalid();
			JevQuestion.Meaning meaning = Objects.requireNonNull(choice.meanings().get(selected));
			boolean tie = masses.stream()
				.anyMatch(m -> maximum - m.probability() <= 1e-6 && choice.meanings().get(m.alternative()) != meaning);
			JudgmentStatus status = tie ? JudgmentStatus.ABSTAIN : switch (meaning) {
				case SATISFIED -> JudgmentStatus.PASS;
				case VIOLATED -> JudgmentStatus.FAIL;
				case INSUFFICIENT -> JudgmentStatus.ABSTAIN;
			};
			return new NativeResponse(model, in, out, status,
					new Assessment(null, null, new Category(selected, domain)),
					new Certainty(confidence, "jev.choice.confidence:v1", SupportOrigin.REPORTED,
							AssessmentTarget.CATEGORY, null),
					new Distribution(AssessmentTarget.CATEGORY, "jev.choice.distribution:v1", masses));
		}
		JevQuestion.Score score = (JevQuestion.Score) question;
		if (!type.equals("score"))
			throw invalid();
		List<String> domain = java.util.stream.IntStream.range(0, score.criteria().size())
			.mapToObj(Integer::toString)
			.toList();
		JsonNode legend = answer.path("legend");
		if (!legend.isObject() || legend.size() != domain.size())
			throw invalid();
		for (int i = 0; i < domain.size(); i++)
			if (!legend.path(domain.get(i)).equals(Checks.parse(Checks.json(score.criteria().get(i)))))
				throw invalid();
		List<ProbabilityMass> masses = masses(answer.path("probabilities"), domain);
		double mean = number(answer.path("score")), expected = 0;
		for (int i = 0; i < masses.size(); i++)
			expected += i * masses.get(i).probability();
		if (mean < 0 || mean > domain.size() - 1 || Math.abs(mean - expected) > 1e-6 * (domain.size() - 1))
			throw invalid();
		NumericAssessment numeric = new NumericAssessment(mean, NumericKind.ORDINAL_EXPECTATION, score.rubricId(), 0,
				domain.size() - 1, domain, score.direction());
		double quality = numeric.qualityScore().orElseThrow();
		JudgmentStatus status = quality <= score.violatedAtOrBelow() ? JudgmentStatus.FAIL
				: quality >= score.satisfiedAtOrAbove() ? JudgmentStatus.PASS : JudgmentStatus.ABSTAIN;
		return new NativeResponse(
				model, in, out, status, new Assessment(null, numeric, null), new Certainty(confidence,
						"jev.score.confidence:v1", SupportOrigin.REPORTED, AssessmentTarget.NUMERIC, null),
				new Distribution(AssessmentTarget.NUMERIC, "jev.score.level-distribution:v1", masses));
	}

	private static List<ProbabilityMass> masses(JsonNode node, List<String> domain) {
		if (!node.isObject() || node.size() != domain.size())
			throw invalid();
		List<ProbabilityMass> masses = new ArrayList<>();
		for (String label : domain)
			masses.add(new ProbabilityMass(label, probability(node.path(label))));
		if (Math.abs(masses.stream().mapToDouble(ProbabilityMass::probability).sum() - 1) > 1e-6)
			throw invalid();
		return List.copyOf(masses);
	}

	private static double number(JsonNode n) {
		if (!n.isNumber() || !Double.isFinite(n.doubleValue()))
			throw invalid();
		return n.doubleValue();
	}

	private static double probability(JsonNode n) {
		double v = number(n);
		if (v < 0 || v > 1)
			throw invalid();
		return v;
	}

	private static String text(JsonNode n) {
		if (!n.isTextual() || n.textValue().isBlank())
			throw invalid();
		return n.textValue();
	}

	private static long tokens(JsonNode n) {
		if (!n.isIntegralNumber() || !n.canConvertToLong() || n.longValue() < 0 || n.longValue() > 9007199254740991L)
			throw invalid();
		return n.longValue();
	}

	private static IllegalArgumentException invalid() {
		return new IllegalArgumentException("Invalid native response");
	}
}
