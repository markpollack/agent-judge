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
import io.github.gudcks0305.jev.NoulQuestion;
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
import java.util.Map;

/** Explicit one-request SDK connectivity utility; never discovered as a JUnit test. */
public final class VercelJevConnectivity {

	private static final ObjectMapper JSON = new ObjectMapper();
	private static final String MODEL = "typesafe-ai/jev";
	private static final String STATE = "The build completed successfully with zero test failures.";
	private static final URI BASE = URI.create("https://ai-gateway.vercel.sh/typesafe");

	private VercelJevConnectivity() {
	}

	/**
	 * Runs a local self-check or one explicitly requested live evaluation.
	 * @param args --self-check or --live-one-call, followed by a new output directory
	 * @throws Exception if local setup or protected artifact writing fails
	 */
	public static void main(String[] args) throws Exception {
		if (args.length != 2 || !(args[0].equals("--self-check") || args[0].equals("--live-one-call")))
			throw new IllegalArgumentException("Use --self-check or --live-one-call and a new output directory");
		if (args[0].equals("--self-check")) {
			selfCheck(Path.of(args[1]));
			return;
		}
		String key = System.getenv("AI_GATEWAY_API_KEY");
		if (key == null || key.isBlank())
			throw new IllegalStateException("AI_GATEWAY_API_KEY must be set locally");
		run(key, BASE, Path.of(args[1]));
	}

	private static Map<String, Object> run(String key, URI base, Path output) throws Exception {
		Files.createDirectory(output); // Refuse to overwrite an earlier attempt.
		var record = new LinkedHashMap<String, Object>();
		record.put("startedAt", Instant.now().toString());
		record.put("sdk", "io.github.gudcks0305:jev-typesafe:0.2.0");
		record.put("client", "TypeSafeJevClient");
		record.put("baseUrl", base.toString());
		record.put("endpoint", base + "/v1/systemone");
		record.put("requestedModel", MODEL);
		record.put("retries", 0);
		record.put("timeoutSeconds", 30);
		var question = NoulQuestion.of("build_success", "Did the build complete successfully?");
		long start = System.nanoTime();
		try (var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
			.followRedirects(HttpClient.Redirect.NEVER).build()) {
			var observed = new ObservedHttpClient(http, 65536, body -> { });
			Evaluation evaluation = null;
			try (var client = TypeSafeJevClient.builder().apiKey(key).baseUrl(base).model(MODEL)
				.timeout(Duration.ofSeconds(30)).maxRetries(0).httpClient(observed).build()) {
				evaluation = client.evaluate(STATE, question);
				record.put("result", "SUCCESS");
				record.put("probability", evaluation.answer(question).probability());
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
				if (evaluation != null) {
					JsonNode original = JSON.readTree(response);
					record.put("rawResponseJsonEqualsOriginal", original.equals(evaluation.rawResponse()));
					record.put("reportedModel", original.get("model"));
					// Exact native response retains any additional provider fields losslessly.
					record.put("providerMetadata", original.get("provider_metadata"));
				}
			}
		}
		record.put("elapsedMillis", (System.nanoTime() - start) / 1_000_000.0);
		byte[] bytes = JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(record);
		if (!safe(new String(bytes, StandardCharsets.UTF_8), key))
			throw new IllegalStateException("Unsafe diagnostic content withheld");
		Files.write(output.resolve("result.json"), bytes);
		System.out.println("result=" + record.get("result") + " httpStatus=" + record.get("httpStatus")
			+ " httpAttempts=" + record.get("httpAttempts") + " probability=" + record.get("probability"));
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

	private static void selfCheck(Path output) throws Exception {
		if (safe("Bearer sentinel", "sentinel") || safe("authorization", "sentinel")
			|| safe("Set-Cookie", "sentinel"))
			throw new AssertionError("Secret screening failed");
		var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/typesafe/v1/systemone", exchange -> {
			JsonNode request = JSON.readTree(exchange.getRequestBody());
			if (!MODEL.equals(request.path("model").asText()) || !STATE.equals(request.path("state").asText())
				|| !"noul".equals(request.at("/questions/build_success/type").asText())) {
				exchange.sendResponseHeaders(400, -1);
				exchange.close();
				return;
			}
			byte[] response = """
				{"model":"typesafe-ai/jev","answers":{"build_success":{"type":"noul","noul":0.75}},
				"usage":{"input_tokens":10,"output_tokens":1},"provider_metadata":{"gateway":{"generationId":"fake-id"}}}
				""".getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().set("x-typesafe-request-id", "fake-request");
			exchange.sendResponseHeaders(200, response.length);
			exchange.getResponseBody().write(response);
			exchange.close();
		});
		server.start();
		try {
			var result = run("local-fake-key", URI.create("http://127.0.0.1:" + server.getAddress().getPort()
				+ "/typesafe"), output);
			if (!"SUCCESS".equals(result.get("result")) || !Double.valueOf(0.75).equals(result.get("probability"))
				|| !Integer.valueOf(1).equals(result.get("httpAttempts"))
				|| !Boolean.TRUE.equals(result.get("rawResponseJsonEqualsOriginal")))
				throw new AssertionError("SDK self-check failed");
		}
		finally {
			server.stop(0);
		}
	}

}
