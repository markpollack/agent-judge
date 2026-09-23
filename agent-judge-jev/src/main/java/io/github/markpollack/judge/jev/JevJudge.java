/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jev;

import io.github.gudcks0305.jev.*;
import io.github.gudcks0305.jev.typesafe.TypeSafeJevClient;
import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.context.JudgmentContext;
import io.github.markpollack.judge.result.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;

/**
 * Immutable, concurrently reusable Judge. Sends only context.goal (the declared
 * requirement) and the explicitly supplied JevEvidence. Uses the released Java SDK with
 * exactly one HTTP attempt. Caller owns HTTP client lifecycle and protected artifact
 * storage. No environment credentials, implicit retrieval, truncation, live calibration
 * guarantee or acceptance policy.
 */
public final class JevJudge implements Judge {

	private final String apiKey;

	private final String model;

	private final URI endpoint;

	private final Duration timeout;

	private final int maxEvidenceBytes;

	private final int maxBodyBytes;

	private final JevQuestion question;

	private final HttpClient http;

	private final ArtifactCapture capture;

	/**
	 * Configure a bounded evaluator. Invalid runtime credentials/configuration are
	 * reported as instrument ERROR by judge; structurally invalid limits fail
	 * construction.
	 * @param apiKey explicitly supplied credential, never captured or logged
	 * @param model explicit versioned model, never the moving latest alias
	 * @param endpoint official https://api.typesafe.ai/v1/systemone, or loopback HTTP for
	 * local tests
	 * @param timeout total SDK request deadline, positive and at most one day
	 * @param maxEvidenceBytes maximum UTF-8 requirement plus evidence bytes
	 * @param maxBodyBytes maximum request or response capture bytes
	 * @param question explicit primitive and projection configuration
	 * @param http caller-owned client, redirects disabled
	 * @param capture caller-owned protected artifact capture
	 */
	public JevJudge(String apiKey, String model, URI endpoint, Duration timeout, int maxEvidenceBytes, int maxBodyBytes,
			JevQuestion question, HttpClient http, ArtifactCapture capture) {
		this.apiKey = Objects.requireNonNull(apiKey);
		this.model = Objects.requireNonNull(model);
		this.endpoint = Objects.requireNonNull(endpoint);
		this.timeout = Objects.requireNonNull(timeout);
		this.question = Objects.requireNonNull(question);
		this.http = Objects.requireNonNull(http);
		this.capture = Objects.requireNonNull(capture);
		if (maxEvidenceBytes < 1 || maxBodyBytes < maxEvidenceBytes)
			throw new IllegalArgumentException("Positive evidence and capture bounds required");
		this.maxEvidenceBytes = maxEvidenceBytes;
		this.maxBodyBytes = maxBodyBytes;
	}

	@Override
	public Judgment judge(JudgmentContext context) {
		List<ArtifactRef> refs = new ArrayList<>();
		AtomicReference<NativeResponse> nativeResult = new AtomicReference<>();
		AtomicReference<NativeResponse.Envelope> reported = new AtomicReference<>();
		ObservedHttpClient observer = new ObservedHttpClient(http, maxBodyBytes, bytes -> {
			reported.set(NativeResponse.envelope(bytes));
			nativeResult.set(NativeResponse.read(bytes, question));
		});
		String digest = ArtifactRef.ofBytes("empty", new byte[0], null).sha256();
		@Nullable ArtifactRef responseRef = null;
		String problem = "Jev configuration or evidence is invalid";
		boolean success = false;
		try {
			preflight();
			Objects.requireNonNull(context);
			String requirement = Objects.requireNonNull(context.goal());
			Checks.text(requirement);
			Checks.portable(Map.of("requirement", requirement));
			Object supplied = context.metadata().get(JevEvidence.CONTEXT_KEY);
			if (!(supplied instanceof JevEvidence evidence))
				throw new IllegalArgumentException("Explicit evidence required");
			if ((long) requirement.getBytes(StandardCharsets.UTF_8).length
					+ evidence.text().getBytes(StandardCharsets.UTF_8).length > maxEvidenceBytes)
				throw new IllegalArgumentException("Evidence bound exceeded");
			if (question instanceof JevQuestion.Noul n && (!n.completeEvidenceBinary() || !evidence.complete()))
				throw new IllegalArgumentException("Complete binary evidence required");
			if (!ArtifactRef.ofBytes("requirement", requirement.getBytes(StandardCharsets.UTF_8), null)
				.sha256()
				.equals(evidence.requirementSha256()))
				throw new IllegalArgumentException("Evidence sufficiency is bound to another requirement");
			refs.add(evidence.bundle());
			refs.add(evidence.manifest());
			refs.add(retain("requirement", requirement.getBytes(StandardCharsets.UTF_8)));
			Map<String, Object> config = new LinkedHashMap<>();
			config.put("adapter", "jev-adapter:1");
			config.put("sdk", "jev-java:0.2.0");
			config.put("requestedModel", model);
			config.put("question", question);
			config.put("requirement", requirement);
			config.put("attemptLimit", 1);
			config.put("timeoutNanos", timeout.toNanos());
			config.put("maxEvidenceBytes", maxEvidenceBytes);
			config.put("maxBodyBytes", maxBodyBytes);
			ArtifactRef configRef = retain("configuration", Checks.json(config));
			refs.add(configRef);
			digest = configRef.sha256();
			if (question instanceof JevQuestion.Score s && s.review() != null)
				refs.add(Objects.requireNonNull(s.review()).evidence());
			if (Thread.currentThread().isInterrupted())
				throw new IllegalStateException("Interrupted before call");
			problem = "Jev request failed or returned invalid protocol";
			try (TypeSafeJevClient client = TypeSafeJevClient.builder()
				.apiKey(apiKey)
				.model(model)
				.endpoint(endpoint)
				.httpClient(observer)
				.timeout(timeout)
				.maxRetries(0)
				.build()) {
				client.evaluate(Map.of("requirement", requirement, "evidence", evidence.text()), sdkQuestion());
			}
			success = true;
		}
		catch (RuntimeException e) {
			if (Thread.currentThread().isInterrupted())
				problem = "Jev evaluation interrupted";
		}
		finally {
			observer.cancelPending();
		}
		try {
			ObservedHttpClient.Snapshot trace = observer.snapshot();
			@Nullable ArtifactRef requestRef = null;
			if (trace.request().length > 0) {
				requestRef = retain("request", trace.request());
				refs.add(requestRef);
			}
			byte[] response = trace.response();
			if (response != null)
				responseRef = retain("response", response);
			Map<String, Object> facts = new LinkedHashMap<>();
			facts.put("configurationDigest", digest);
			if (requestRef != null)
				facts.put("request", requestRef);
			if (responseRef != null)
				facts.put("response", responseRef);
			facts.put("requestedModel", model);
			facts.put("attempts", trace.attempts());
			facts.put("status", trace.status());
			facts.put("elapsedNanos", trace.elapsedNanos());
			facts.put("cancelled", trace.cancelled());
			if (trace.requestId() != null)
				facts.put("requestId", trace.requestId());
			NativeResponse value = nativeResult.get();
			NativeResponse.Envelope envelope = reported.get();
			if (envelope != null) {
				facts.put("reportedModel", envelope.model());
				facts.put("usage",
						Map.of("input_tokens", envelope.inputTokens(), "output_tokens", envelope.outputTokens()));
			}
			refs.add(retain("trace", Checks.json(facts)));
			String revision = "jev-adapter:1;jev-java:0.2.0;requested=" + model
					+ (envelope == null ? "" : ";reported=" + envelope.model());
			EvaluationProvenance provenance = new EvaluationProvenance("typesafe.jev", revision, digest, refs,
					responseRef, success ? List.of(calibrationClaim()) : List.of());
			if (success && value != null)
				return new Judgment(value.status(), value.assessment(), value.certainty(), value.distribution(), null,
						value.status() == JudgmentStatus.ABSTAIN
								? "Declared projection has no supported determination" : "",
						List.of(), provenance, null,
						Map.of("usage",
								Map.of("inputTokens", portableInteger(value.inputTokens()), "outputTokens",
										portableInteger(value.outputTokens())),
								Judgment.ELAPSED_MILLIS_KEY, portableInteger(trace.elapsedNanos() / 1000000)));
			return new Judgment(JudgmentStatus.ERROR, null, null, null, JudgmentReasonCode.JUDGE_REPORTED, problem,
					List.of(), provenance, null,
					envelope == null ? Map.of()
							: Map.of("usage", Map.of("inputTokens", portableInteger(envelope.inputTokens()),
									"outputTokens", portableInteger(envelope.outputTokens()))));
		}
		catch (RuntimeException e) {
			return Judgment.error(JudgmentReasonCode.JUDGE_REPORTED, "Jev artifact capture failed");
		}
	}

	private static Number portableInteger(long n) {
		if (n <= Integer.MAX_VALUE)
			return Integer.valueOf((int) n);
		return Long.valueOf(n);
	}

	private void preflight() {
		if (model.isBlank() || model.contains("latest") || !model.matches("jev-[0-9]+\\.[0-9]+\\.[0-9]+"))
			throw new IllegalArgumentException("Pinned Jev model required");
		boolean official = endpoint.equals(URI.create("https://api.typesafe.ai/v1/systemone"));
		boolean local = "http".equals(endpoint.getScheme())
				&& Set.of("localhost", "127.0.0.1", "[::1]").contains(endpoint.getHost())
				&& "/v1/systemone".equals(endpoint.getPath()) && endpoint.getUserInfo() == null
				&& endpoint.getQuery() == null && endpoint.getFragment() == null;
		if ((!official && !local) || http.followRedirects() != HttpClient.Redirect.NEVER)
			throw new IllegalArgumentException("Invalid endpoint or redirect policy");
		if (timeout.isNegative() || timeout.isZero() || timeout.compareTo(Duration.ofDays(1)) > 0)
			throw new IllegalArgumentException("Invalid deadline");
		if (question instanceof JevQuestion.Score score)
			score.preflight();
	}

	private Question<?> sdkQuestion() {
		if (question instanceof JevQuestion.Noul n)
			return NoulQuestion.of("q", n.instructions());
		if (question instanceof JevQuestion.Choice c)
			return ChoiceQuestion.of("q", c.instructions(), c.criteria());
		JevQuestion.Score s = (JevQuestion.Score) question;
		return ScoreQuestion.of("q", s.instructions(), s.criteria());
	}

	private ArtifactRef retain(String kind, byte[] bytes) {
		if (bytes.length > maxBodyBytes)
			throw new IllegalArgumentException("Artifact exceeds capture bound");
		ArtifactRef expected = ArtifactRef.ofBytes(kind, bytes, null);
		ArtifactRef actual = Objects.requireNonNull(capture.retain(kind, bytes.clone()));
		if (!expected.sha256().equals(actual.sha256()))
			throw new IllegalArgumentException("Capture digest mismatch");
		return actual;
	}

	private CalibrationClaim calibrationClaim() {
		List<String> signals = question instanceof JevQuestion.Noul ? List.of("jev.noul.probability-of-true:v1")
				: question instanceof JevQuestion.Choice
						? List.of("jev.choice.distribution:v1", "jev.choice.confidence:v1")
						: List.of("jev.score.level-distribution:v1", "jev.score.confidence:v1");
		return new CalibrationClaim("typesafe.system-one.calibration:2026-09-15", "TypeSafe",
				"TypeSafe System One native predictive signals",
				"TypeSafe declares calibrated probabilities and confidence for its System One models; this declaration is not local empirical validation.",
				signals,
				List.of(new ArtifactRef("https://typesafe.ai/blog/introducing-system-one-models-and-jev",
						"19a428f4d388bb5f218cdb950f11d28d67677e887c01a753010258c621c5cb27", null),
						new ArtifactRef("https://typesafe.ai/",
								"f4c726c5d209df1c5d870913172c32d0d40bedf9d57d64179cdd8ca2e4f45d93", null)));
	}

}
