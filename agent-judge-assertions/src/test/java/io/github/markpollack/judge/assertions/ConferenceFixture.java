/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.assertions;

import com.fasterxml.jackson.databind.*;
import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.context.JudgmentContext;
import io.github.markpollack.judge.jev.*;
import io.github.markpollack.judge.result.*;
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

	static final ObjectMapper JSON = new ObjectMapper();
	static final String LOCK = "Repository locks are acquired in the required order";

	final JsonNode bindings;

	final JsonNode configuration;

	final JsonNode policy;

	final PolicyBinding binding;

	final JevQuestion.Choice question;

	ConferenceFixture() throws Exception {
		bindings = JSON.readTree(resource("assertions/v1/bindings.json"));
		configuration = JSON.readTree(resource("assertions/v1/configuration.json"));
		policy = JSON.readTree(resource("assertions/v1/policy.json"));
		byte[] reviewBytes = resource("assertions/v1/binding-review.json");
		if (!sha(reviewBytes).equals("bf16d68641a1aff3574756ec464c5db74e47480ae2d84a6c4b9fd43d38b88e88"))
			throw new IllegalStateException("Unreviewed binding configuration");
		var review = JSON.readTree(reviewBytes);
		for (String name : List.of("bindings", "configuration", "policy"))
			if (!sha(resource("assertions/v1/" + name + ".json")).equals(review.path(name + "Sha256").asText()))
				throw new IllegalStateException("Stale review: " + name);
		binding = new PolicyBinding(
				new PolicyRef(policy.path("id").asText(), policy.path("revision").asText(),
						sha(resource("assertions/v1/policy.json"))),
				j -> new Acceptance(AcceptanceAction.valueOf(policy.path("action").asText()),
						policy.path("reason").asText()));
		Map<String, Object> criteria = new LinkedHashMap<>();
		configuration.path("criteria").fields().forEachRemaining(e -> criteria.put(e.getKey(), e.getValue().asText()));
		Map<String, JevQuestion.Meaning> meanings = new LinkedHashMap<>();
		configuration.path("meanings")
			.fields()
			.forEachRemaining(e -> meanings.put(e.getKey(), JevQuestion.Meaning.valueOf(e.getValue().asText())));
		question = new JevQuestion.Choice(configuration.path("instructions").asText(),
				configuration.path("projectionId").asText(), criteria, meanings);
	}

	Requirement requirement(int index) {
		var b = bindings.path("bindings").get(index);
		return new Requirement(b.path("id").asText(), b.path("revision").asText(), b.path("text").asText());
	}

	JudgmentContext context(int index) throws Exception {
		var b = bindings.path("bindings").get(index);
		var ref = b.path("bundle");
		byte[] bytes = resource(ref.path("path").asText());
		if (bytes.length != ref.path("bytes").asInt() || !sha(bytes).equals(ref.path("sha256").asText()))
			throw new IllegalStateException("Changed selected bytes");
		String goal = requirement(index).text();
		if (!sha(goal.getBytes(StandardCharsets.UTF_8)).equals(b.path("textSha256").asText()))
			throw new IllegalStateException("Changed exact goal");
		return JudgmentContext.builder()
			.goal(goal)
			.agentOutput("UNSELECTED-AGENT-OUTPUT")
			.metadata(JevEvidence.CONTEXT_KEY,
					new JevEvidence(new String(bytes, StandardCharsets.UTF_8),
							ArtifactRef.ofBytes(ref.path("path").asText(), bytes, null),
							ArtifactRef.ofBytes("assertions/v1/bindings.json", resource("assertions/v1/bindings.json"),
									null),
							b.path("textSha256").asText(), true))
			.build();
	}

	SemanticAssertions facade(Judge judge) {
		// Both named identity and exact string identity select only the reviewed text.
		return new SemanticAssertions(r -> {
			for (int i = 0; i < 2; i++) {
				var known = requirement(i);
				boolean named = r.id().equals(known.id()) && r.revision().equals(known.revision());
				boolean string = r.id().equals("text:sha256:" + sha(r.text().getBytes(StandardCharsets.UTF_8)))
						&& r.revision().equals("1");
				if (r.text().equals(known.text()) && (named || string))
					return judge;
			}
			throw new IllegalArgumentException("Requirement does not match a reviewed binding");
		}, binding);
	}

	JevJudge judge(String key, URI endpoint, HttpClient http, Path output) {
		return new JevJudge(key, configuration.path("requestedModel").asText(), endpoint,
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

	static void save(AssertionResult result, Path output, String origin) throws Exception {
		Files.createDirectories(output);
		var req = result.requirement();
		JSON.writerWithDefaultPrettyPrinter()
			.writeValue(output.resolve("resolution.json").toFile(),
					Map.of("origin", origin, "requirement",
							Map.of("id", req.id(), "revision", req.revision(), "text", req.text()), "policy",
							result.policy(), "policySource", result.policySource()));
		JSON.writerWithDefaultPrettyPrinter()
			.writeValue(output.resolve("judgment.json").toFile(), result.verdict().aggregated());
		JSON.writerWithDefaultPrettyPrinter().writeValue(output.resolve("verdict.json").toFile(), result.verdict());
		JSON.writerWithDefaultPrettyPrinter()
			.writeValue(output.resolve("interpretation.json").toFile(), result.interpretation());
		System.out.println(origin + ": " + req.id() + " => " + result.interpretation().readingSupport() + " "
				+ result.interpretation().reading());
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
