/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.jev;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.github.gudcks0305.jev.Evaluation;
import io.github.gudcks0305.jev.JevException;
import io.github.gudcks0305.jev.ChoiceQuestion;
import io.github.gudcks0305.jev.Question;
import io.github.gudcks0305.jev.ScoreQuestion;
import io.github.gudcks0305.jev.typesafe.TypeSafeJevClient;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;

/** Explicit one-request SDK connectivity utility; never discovered as a JUnit test. */
public final class VercelJevConnectivity {

	private static final ObjectMapper JSON = new ObjectMapper().registerModule(io.github.markpollack.judge.serialization.ResultJson.module());

	private static final String MODEL = "typesafe-ai/jev";

	private static final String STATE = "The build completed successfully with zero test failures.";

	private static final URI BASE = URI.create("https://ai-gateway.vercel.sh/typesafe");

	private static final ChoiceQuestion<String> CHOICE;

	private static final ScoreQuestion SCORE = ScoreQuestion.of("build_success_score", "How successful was the build?",
			List.of("failed", "partially successful", "fully successful"));

	static {
		var criteria = new LinkedHashMap<String, String>();
		criteria.put("successful", "Evidence establishes that the build completed successfully.");
		criteria.put("failed", "Evidence establishes that the build did not complete successfully.");
		criteria.put("insufficient_evidence", "The supplied state does not establish either outcome.");
		CHOICE = ChoiceQuestion.of("build_outcome", "What is the build outcome?", criteria);
	}

	private VercelJevConnectivity() {
	}

	/**
	 * Runs local self-checks or one explicitly selected Choice or Score evaluation.
	 * @param args --self-check OUTPUT or --live-one-call choice|score OUTPUT
	 * @throws Exception if local setup or protected artifact writing fails
	 */
	public static void main(String[] args) throws Exception {
		if (args.length == 2 && args[0].equals("--self-check")) {
			selfCheck(Path.of(args[1]));
			return;
		}
		if (args.length != 3 || !args[0].equals("--live-one-call")
				|| !(args[1].equals("choice") || args[1].equals("score")))
			throw new IllegalArgumentException("Use --self-check OUTPUT or --live-one-call choice|score OUTPUT");
		Question<?> question = args[1].equals("choice") ? CHOICE : SCORE;
		String key = System.getenv("AI_GATEWAY_API_KEY");
		if (key == null || key.isBlank())
			throw new IllegalStateException("AI_GATEWAY_API_KEY must be set locally");
		run(key, BASE, Path.of(args[2]), question);
	}

	private static Map<String, Object> configuration(URI base, Question<?> question) {
		var config = new LinkedHashMap<String, Object>();
		config.put("sdk", "io.github.gudcks0305:jev-typesafe:0.2.0");
		config.put("client", "TypeSafeJevClient");
		config.put("baseUrl", base.toString());
		config.put("endpoint", base + "/v1/systemone");
		config.put("requestedModel", MODEL);
		config.put("retries", 0);
		config.put("timeoutSeconds", 30);
		config.put("captureLimitBytes", 65536);
		config.put("state", STATE);
		config.put("questionId", question.id());
		config.put("instructions", question.instructions());
		config.put("primitive", question == CHOICE ? "choice" : "score");
		config.put("criteria", question == CHOICE ? CHOICE.criteria() : SCORE.criteria());
		return config;
	}

	private static Map<String, Object> run(String key, URI base, Path output, Question<?> question) throws Exception {
		Files.createDirectory(output); // Refuse to overwrite an earlier attempt.
		var record = new LinkedHashMap<String, Object>();
		record.put("startedAt", Instant.now().toString());
		record.putAll(configuration(base, question));
		retain(output, "configuration.json",
				JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(configuration(base, question)), key, record);
		long start = System.nanoTime();
		try (var http = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.followRedirects(HttpClient.Redirect.NEVER)
			.build()) {
			var observed = new ObservedHttpClient(http, 65536, body -> {
			});
			Evaluation evaluation = null;
			try (var client = TypeSafeJevClient.builder()
				.apiKey(key)
				.baseUrl(base)
				.model(MODEL)
				.timeout(Duration.ofSeconds(30))
				.maxRetries(0)
				.httpClient(observed)
				.build()) {
				evaluation = client.evaluate(STATE, question);
				record.put("result", "SUCCESS");
				var typed = new LinkedHashMap<String, Object>();
				if (question == CHOICE) {
					var answer = evaluation.answer(CHOICE);
					typed.put("choice", answer.choice());
					typed.put("probabilities", answer.probabilities());
					typed.put("confidence", answer.confidence().isPresent() ? answer.confidence().getAsDouble() : null);
				}
				else {
					var answer = evaluation.answer(SCORE);
					typed.put("score", answer.score());
					typed.put("probabilities", answer.probabilities());
					typed.put("legend", answer.legend());
					typed.put("confidence", answer.confidence().isPresent() ? answer.confidence().getAsDouble() : null);
				}
				record.put("sdkTypedAnswer", typed);
				record.put("sdkModel", evaluation.model());
				record.put("inputTokens", evaluation.usage().inputTokens().isPresent()
						? evaluation.usage().inputTokens().getAsLong() : null);
				record.put("outputTokens", evaluation.usage().outputTokens().isPresent()
						? evaluation.usage().outputTokens().getAsLong() : null);
			}
			catch (JevException failure) {
				record.put("result", "FAILURE");
				record.put("exceptionType", JevException.class.getName());
				record.put("sdkFailureKind", failure.kind().name());
				// Inspected SDK diagnostics are constant strings, still screened below.
				record.put("safeMessage", safe(failure.getMessage(), key) ? failure.getMessage() : "withheld");
				record.put("sdkHttpStatus", failure.statusCode());
			}
			finally {
				observed.cancelPending();
			}
			var trace = observed.snapshot();
			record.put("httpAttempts", trace.attempts());
			record.put("httpStatus", trace.status());
			record.put("typesafeRequestId", trace.requestId());
			record.put("cancelled", trace.cancelled());
			record.put("httpElapsedMillis", trace.elapsedNanos() / 1_000_000.0);
			retain(output, "request.json", trace.request(), key, record);
			if (trace.response() != null) {
				byte[] response = trace.response();
				retain(output, "response.json", response, key, record);
				try {
					JsonNode original = JSON.readTree(response);
					if (original == null || original.isMissingNode()) {
						record.put("originalJsonParse", "EMPTY");
						original = com.fasterxml.jackson.databind.node.MissingNode.getInstance();
					}
					record.put("rawResponseJsonEqualsOriginal",
							evaluation == null ? null : original.equals(evaluation.rawResponse()));
					record.put("nativeAnswer", original.at("/answers/" + question.id()));
					record.put("providerMetadata", original.get("provider_metadata"));
					record.put("nativeFields", nativeFields(original, question));
					if (question == SCORE)
						record.put("returnedLegendCheck",
								legendCheck(original.at("/answers/" + question.id() + "/legend")));
				}
				catch (com.fasterxml.jackson.core.JsonProcessingException malformed) {
					record.put("originalJsonParse", "INVALID_JSON");
				}
			}
		}
		record.put("elapsedMillis", (System.nanoTime() - start) / 1_000_000.0);
		byte[] bytes = JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(record);
		if (!safe(new String(bytes, StandardCharsets.UTF_8), key))
			throw new IllegalStateException("Unsafe diagnostic content withheld");
		Files.write(output.resolve("result.json"), bytes);
		System.out.println("result=" + record.get("result") + " httpStatus=" + record.get("httpStatus")
				+ " httpAttempts=" + record.get("httpAttempts") + " primitive=" + record.get("primitive"));
		return record;
	}

	private static void retain(Path output, String name, byte[] bytes, String key, Map<String, Object> record)
			throws Exception {
		if (!safe(new String(bytes, StandardCharsets.UTF_8), key)) {
			record.put(name + "Withheld", true);
			return;
		}
		Files.write(output.resolve(name), bytes);
		record.put(name + "Bytes", bytes.length);
		record.put(name + "Sha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
	}

	private static boolean safe(String text, String key) {
		String lower = text.toLowerCase(Locale.ROOT);
		return !text.contains(key) && !lower.contains("authorization") && !lower.contains("cookie")
				&& !lower.contains("bearer ");
	}

	private static Map<String, Object> nativeFields(JsonNode original, Question<?> question) {
		var fields = new LinkedHashMap<String, Object>();
		String answer = "/answers/" + question.id();
		for (String path : List.of("/model", "/usage", "/usage/input_tokens", "/usage/output_tokens",
				"/provider_metadata", "/warnings", answer + "/type", answer + "/choice", answer + "/score",
				answer + "/probabilities", answer + "/confidence", answer + "/legend",
				"/provider_metadata/typesafe/confidence/" + question.id(),
				"/provider_metadata/typesafe/probabilities/" + question.id(),
				"/provider_metadata/typesafe/legend/" + question.id())) {
			JsonNode value = original.at(path);
			var field = new LinkedHashMap<String, Object>();
			field.put("presence", value.isMissingNode() ? "ABSENT" : value.isNull() ? "NULL" : "PRESENT");
			if (!value.isMissingNode())
				field.put("value", value);
			fields.put(path, field);
		}
		return fields;
	}

	private static String legendCheck(JsonNode legend) {
		if (legend.isMissingNode())
			return "ABSENT";
		if (legend.isNull())
			return "NULL";
		if (!legend.isObject() || legend.size() != SCORE.levelCount())
			return "CONTRADICTORY";
		for (int i = 0; i < SCORE.levelCount(); i++)
			if (!legend.path(Integer.toString(i)).equals(SCORE.criteria().get(i)))
				return "CONTRADICTORY";
		return "EXACT_MATCH";
	}

	private static void selfCheck(Path output) throws Exception {
		Files.createDirectory(output);
		if (safe("Bearer sentinel", "sentinel") || safe("authorization", "sentinel") || safe("Set-Cookie", "sentinel"))
			throw new AssertionError("Secret screening failed");
		Files.write(output.resolve("choice-configuration.json"),
				JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(configuration(BASE, CHOICE)));
		Files.write(output.resolve("score-configuration.json"),
				JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(configuration(BASE, SCORE)));
		for (String scenario : List.of("choice-full", "choice-gateway-confidence", "choice-missing-distribution",
				"score-fractional", "score-missing-legend", "score-reversed-legend", "score-changed-legend",
				"score-missing-facts", "score-null-confidence", "score-missing-distribution")) {
			Question<?> question = scenario.startsWith("choice") ? CHOICE : SCORE;
			var body = JSON.createObjectNode();
			body.put("model", MODEL);
			body.putObject("usage").put("input_tokens", 10).put("output_tokens", 1);
			body.putObject("provider_metadata").putObject("gateway").put("generationId", "fake-id");
			var answer = body.putObject("answers").putObject(question.id());
			answer.put("type", question == CHOICE ? "choice" : "score");
			answer.put("confidence", 0.8);
			if (question == CHOICE) {
				answer.put("choice", "successful");
				answer.putObject("probabilities")
					.put("successful", 0.75)
					.put("failed", 0.125)
					.put("insufficient_evidence", 0.125);
			}
			else {
				answer.put("score", 1.625);
				answer.putObject("probabilities").put("0", 0.125).put("1", 0.125).put("2", 0.75);
				answer.putObject("legend")
					.put("0", "failed")
					.put("1", "partially successful")
					.put("2", "fully successful");
			}
			if (scenario.endsWith("missing-distribution"))
				answer.remove("probabilities");
			if (scenario.equals("choice-gateway-confidence")) {
				answer.remove("confidence");
				((com.fasterxml.jackson.databind.node.ObjectNode) body.get("provider_metadata")).putObject("typesafe")
					.putObject("confidence")
					.put(question.id(), 0.625);
			}
			if (scenario.equals("score-missing-legend"))
				answer.remove("legend");
			if (scenario.equals("score-reversed-legend"))
				answer.putObject("legend")
					.put("0", "fully successful")
					.put("1", "partially successful")
					.put("2", "failed");
			if (scenario.equals("score-changed-legend"))
				((com.fasterxml.jackson.databind.node.ObjectNode) answer.get("legend")).put("1", "unrelated meaning");
			if (scenario.equals("score-missing-facts")) {
				answer.remove(List.of("confidence", "legend"));
				body.remove(List.of("model", "usage"));
			}
			if (scenario.equals("score-null-confidence"))
				answer.putNull("confidence");
			var result = localRun(output.resolve(scenario), question, JSON.writeValueAsBytes(body));
			JsonNode saved = JSON.valueToTree(result);
			boolean failure = scenario.endsWith("missing-distribution");
			require((failure ? "FAILURE" : "SUCCESS").equals(result.get("result")), scenario + " result");
			require(Integer.valueOf(1).equals(result.get("httpAttempts")), scenario + " attempts");
			require(java.util.Arrays.equals(JSON.writeValueAsBytes(body),
					Files.readAllBytes(output.resolve(scenario).resolve("response.json"))), scenario + " raw bytes");
			if (failure) {
				require("PROTOCOL".equals(result.get("sdkFailureKind")), scenario + " diagnostic");
				continue;
			}
			require(Boolean.TRUE.equals(result.get("rawResponseJsonEqualsOriginal")), scenario + " raw tree equality");
			require(saved.at("/sdkTypedAnswer/probabilities").equals(answer.get("probabilities")),
					scenario + " distribution fidelity");
			require(saved.at("/sdkTypedAnswer/confidence")
				.equals(answer.has("confidence") ? answer.get("confidence") : JSON.nullNode()),
					scenario + " confidence fidelity");
			if (question == SCORE) {
				require(saved.at("/sdkTypedAnswer/score").doubleValue() == 1.625, scenario + " fractional score");
				String legend = scenario.equals("score-missing-legend") || scenario.equals("score-missing-facts")
						? "ABSENT" : scenario.contains("reversed") || scenario.contains("changed") ? "CONTRADICTORY"
								: "EXACT_MATCH";
				require(legend.equals(result.get("returnedLegendCheck")), scenario + " legend check");
				if (answer.has("legend"))
					require(saved.at("/sdkTypedAnswer/legend").equals(answer.get("legend")),
							scenario + " legend fidelity");
				else
					require(saved.at("/sdkTypedAnswer/legend/2").asText().equals("fully successful"),
							scenario + " SDK default observed");
			}
			if (scenario.equals("choice-gateway-confidence")) {
				require(saved.at("/sdkTypedAnswer/confidence").isNull(), scenario + " no invented typed confidence");
				require(saved.path("nativeFields")
					.path("/provider_metadata/typesafe/confidence/" + question.id())
					.path("value")
					.doubleValue() == 0.625, scenario + " native confidence");
			}
			if (scenario.equals("score-missing-facts")) {
				require(saved.path("nativeFields").path("/model").path("presence").asText().equals("ABSENT"),
						scenario + " model absent");
				require(saved.path("sdkModel").asText().equals(MODEL), scenario + " SDK model default observed");
				require(saved.at("/sdkTypedAnswer/confidence").isNull(), scenario + " confidence absent");
				require(saved.path("inputTokens").isNull() && saved.path("outputTokens").isNull(),
						scenario + " usage absent");
			}
			if (scenario.equals("score-null-confidence"))
				require(saved.path("nativeFields")
					.path("/answers/" + question.id() + "/confidence")
					.path("presence")
					.asText()
					.equals("NULL"), scenario + " null distinct from absence");
		}
		var empty = localRun(output.resolve("empty-response"), CHOICE, new byte[0]);
		require("FAILURE".equals(empty.get("result")) && "EMPTY".equals(empty.get("originalJsonParse")),
				"empty response retained");
		Files.writeString(output.resolve("summary.json"), "{\"scenarios\":11,\"failures\":0,\"providerCalls\":0}\n");
	}

	private static void require(boolean condition, String message) {
		if (!condition)
			throw new AssertionError(message);
	}

	private static Map<String, Object> localRun(Path output, Question<?> question, byte[] response) throws Exception {
		var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/typesafe/v1/systemone", exchange -> {
			JsonNode request = JSON.readTree(exchange.getRequestBody());
			JsonNode wire = request.at("/questions/" + question.id());
			if (request.size() != 3 || request.path("questions").size() != 1
					|| !MODEL.equals(request.path("model").asText()) || !STATE.equals(request.path("state").asText())
					|| !(question == CHOICE ? "choice" : "score").equals(wire.path("type").asText())
					|| !wire.path("instructions").equals(question.instructions())
					|| !wire.path("criteria").equals(question == CHOICE ? CHOICE.criteria() : SCORE.criteria())) {
				exchange.sendResponseHeaders(400, -1);
				exchange.close();
				return;
			}
			exchange.getResponseHeaders().set("x-typesafe-request-id", "fake-request");
			exchange.sendResponseHeaders(200, response.length);
			exchange.getResponseBody().write(response);
			exchange.close();
		});
		server.start();
		try {
			return run("local-fake-key", URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/typesafe"),
					output, question);
		}
		finally {
			server.stop(0);
		}
	}

}
