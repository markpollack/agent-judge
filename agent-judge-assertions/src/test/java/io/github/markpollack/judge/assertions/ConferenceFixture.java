/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.assertions;

import io.github.markpollack.judge.requirement.Requirement;

import com.fasterxml.jackson.databind.*;
import io.github.markpollack.judge.construction.*;
import io.github.markpollack.judge.jev.JevRuntime;
import io.github.markpollack.judge.evaluation.*;
import io.github.markpollack.judge.policy.*;
import io.github.markpollack.judge.serialization.VerdictCodec;
import io.github.markpollack.judge.jev.JevEvidence;
import io.github.markpollack.judge.jev.JevJudge;
import io.github.markpollack.judge.jev.JevQuestion;
import io.github.markpollack.judge.judgment.Judgment;
import io.github.markpollack.judge.provenance.ArtifactRef;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

/**
 * Exact caller-thread fixture setup; no repository scan, environment or classloader
 * lookup.
 */
final class ConferenceFixture {

	static final ObjectMapper JSON = new ObjectMapper().registerModule(io.github.markpollack.judge.serialization.ResultJson.module());
	static final String LOCK = "Repository locks are acquired in the required order";

	final JsonNode bindings;

	final JsonNode configuration;

	final JsonNode policy;

	final JsonNode routing;

	final Policy binding;

	final JevQuestion.Choice question;

	ConferenceFixture() throws Exception {
		this(false);
	}

	ConferenceFixture(boolean vercel) throws Exception {
		bindings = JSON.readTree(resource("assertions/v1/bindings.json"));
		var originalConfiguration = JSON.readTree(resource("assertions/v1/configuration.json"));
		policy = JSON.readTree(resource("assertions/v1/policy.json"));
		byte[] reviewBytes = resource("assertions/v1/binding-review.json");
		if (!sha(reviewBytes).equals("bf16d68641a1aff3574756ec464c5db74e47480ae2d84a6c4b9fd43d38b88e88"))
			throw new IllegalStateException("Unreviewed binding configuration");
		var review = JSON.readTree(reviewBytes);
		for (String name : List.of("bindings", "configuration", "policy"))
			if (!sha(resource("assertions/v1/" + name + ".json")).equals(review.path(name + "Sha256").asText()))
				throw new IllegalStateException("Stale review: " + name);
		routing = vercel ? reviewedRouting() : null;
		configuration = originalConfiguration.deepCopy();
		if (routing != null) {
			((com.fasterxml.jackson.databind.node.ObjectNode) configuration).put("requestedModel",
					routing.path("requestedModel").asText());
			if (!sha(JSON.writeValueAsBytes(configuration))
				.equals(routing.path("composedConfigurationSha256").asText()))
				throw new IllegalStateException("Changed composed configuration");
		}
		binding = verdict -> new PolicyDecision(historicalAction(policy.path("action").asText()),
				policy.path("reason").asText());
		Map<String, Object> criteria = new LinkedHashMap<>();
		configuration.path("criteria").fields().forEachRemaining(e -> criteria.put(e.getKey(), e.getValue().asText()));
		Map<String, JevQuestion.Meaning> meanings = new LinkedHashMap<>();
		configuration.path("meanings")
			.fields()
			.forEachRemaining(e -> meanings.put(e.getKey(), JevQuestion.Meaning.valueOf(e.getValue().asText())));
		question = new JevQuestion.Choice(configuration.path("instructions").asText(),
				configuration.path("projectionId").asText(), criteria, meanings);
	}

	// The reviewed fixture bytes remain frozen. Translate their historical policy
	// vocabulary only here; the production V3 reader does not accept V2 actions.
	private static PolicyAction historicalAction(String action) {
		return "USE_ASSESSMENT".equals(action) ? PolicyAction.RELY : PolicyAction.valueOf(action);
	}

	private static JsonNode reviewedRouting() throws Exception {
		byte[] bytes = resource("assertions/vercel-v1/routing-overlay.json");
		byte[] reviewBytes = resource("assertions/vercel-v1/routing-review.json");
		if (!sha(reviewBytes).equals("3191dcb8804eaa90e13dfa7f1d96e52bc12e57ffc430e9fab4b9b0c2dcfdb44e"))
			throw new IllegalStateException("Unreviewed provider routing");
		var review = JSON.readTree(reviewBytes);
		var overlay = JSON.readTree(bytes);
		if (!review.path("approved").asBoolean() || !sha(bytes).equals(review.path("overlaySha256").asText())
				|| !overlay.path("composedConfigurationSha256").equals(review.path("composedConfigurationSha256")))
			throw new IllegalStateException("Stale routing review");
		for (String name : List.of("configuration", "bindings", "policy", "binding-review")) {
			var ref = overlay.path("baseArtifacts").path(name);
			if (!ref.path("path").asText().equals("assertions/v1/" + name + ".json")
					|| !sha(resource(ref.path("path").asText())).equals(ref.path("sha256").asText()))
				throw new IllegalStateException("Changed base artifact: " + name);
		}
		return overlay;
	}

	void saveRouting(Path output) throws Exception {
		if (routing == null)
			return;
		Files.write(output.resolve("routing-overlay.json"), resource("assertions/vercel-v1/routing-overlay.json"));
		Files.write(output.resolve("routing-review.json"), resource("assertions/vercel-v1/routing-review.json"));
		Files.write(output.resolve("composed-configuration.json"), JSON.writeValueAsBytes(configuration));
	}

	Requirement<String> requirement(int index) {
		var b = bindings.path("bindings").get(index);
		return Requirement.text(b.path("id").asText(), b.path("revision").asText(), b.path("text").asText());
	}

	JevEvidence evidence(int index) throws Exception {
		var b = bindings.path("bindings").get(index);
		var ref = b.path("bundle");
		byte[] bytes = resource(ref.path("path").asText());
		if (bytes.length != ref.path("bytes").asInt() || !sha(bytes).equals(ref.path("sha256").asText()))
			throw new IllegalStateException("Changed selected bytes");
		String goal = requirement(index).text();
		if (!sha(goal.getBytes(StandardCharsets.UTF_8)).equals(b.path("textSha256").asText()))
			throw new IllegalStateException("Changed exact goal");
		return new JevEvidence(new String(bytes, StandardCharsets.UTF_8),
				ArtifactRef.ofBytes(ref.path("path").asText(), bytes, null),
				ArtifactRef.ofBytes("assertions/v1/bindings.json", resource("assertions/v1/bindings.json"), null),
				b.path("textSha256").asText(), true);
	}

	JudgeRecipe<String, JevEvidence> bind(JevRuntime runtime) {
		return actual -> {
			Requirement.validate(actual);
			if (!List.of(requirement(0), requirement(1)).contains(actual))
				return EvidenceSteps
					.of(evidence -> () -> Judgment.error("Requirement is outside this reviewed fixture roster")
						.forRequirement(actual));
			return JevJudge.builder().runtime(runtime).requirement(actual);
		};
	}

	JevRuntime judge(String key, URI endpoint, HttpClient http, Path output) {
		return new JevRuntime(key, configuration.path("requestedModel").asText(), endpoint,
				Duration.ofSeconds(configuration.path("timeoutSeconds").asLong()),
				configuration.path("maxEvidenceBytes").asInt(), configuration.path("maxBodyBytes").asInt(), question,
				http, (kind, bytes) -> {
					String name = kind + "-" + sha(bytes) + ".json";
					try {
						Files.createDirectories(output);
						Files.write(output.resolve(name), bytes);
					}
					catch (Exception ex) {
						throw new IllegalStateException("Artifact capture failed", ex);
					}
					return ArtifactRef.ofBytes(name, bytes, null);
				});
	}

	static void save(EvaluationResult result, Path output, String origin) throws Exception {
		Files.createDirectories(output);
		Files.writeString(output.resolve("evaluation.json"), new VerdictCodec().write(result));
		Files.writeString(output.resolve("verdict.json"), new VerdictCodec().write(result.verdict()));
		Files.writeString(output.resolve("report.txt"),
				origin + "\n" + io.github.markpollack.judge.reporting.VerdictReport.of(result.verdict()).summary());
	}

	static byte[] resource(String path) throws Exception {
		try (var stream = ConferenceFixture.class.getResourceAsStream("/" + path)) {
			return Objects.requireNonNull(stream, path).readAllBytes();
		}
	}

	static String sha(byte[] bytes) {
		return ArtifactRef.ofBytes("bytes", bytes, null).sha256();
	}

}
