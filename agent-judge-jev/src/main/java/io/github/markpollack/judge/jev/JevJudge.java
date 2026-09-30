/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jev;

import io.github.gudcks0305.jev.*;
import io.github.gudcks0305.jev.typesafe.TypeSafeJevClient;
import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.requirement.Requirement;
import io.github.markpollack.judge.requirement.RequirementEvidence;
import io.github.markpollack.judge.judgment.Judgment;
import io.github.markpollack.judge.judgment.JudgmentReasonCode;
import io.github.markpollack.judge.judgment.JudgmentStatus;
import io.github.markpollack.judge.provenance.ArtifactRef;
import io.github.markpollack.judge.provenance.CalibrationClaim;
import io.github.markpollack.judge.provenance.Provenance;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;

/**
 * Immutable, concurrently reusable Judge. Sends only the declared requirement and
 * explicitly supplied JevEvidence from the typed input pair. Uses the released Java SDK
 * with exactly one HTTP attempt. Caller owns HTTP client lifecycle and protected artifact
 * storage. No environment credentials, implicit retrieval, truncation, live calibration
 * guarantee or acceptance policy.
 * <p>
 * Request usage is retained in {@code Judgment.metadata().get("usage")}. On the Vercel
 * route, a valid gateway-reported charge adds {@code cost} (a Double in USD),
 * {@code currency} and {@code costSource}; missing or invalid cost stays absent. This is
 * reported request cost, not a token-price calculation or evidence-preparation cost. A
 * malformed finding can still retain valid request usage and cost.
 */
public final class JevJudge implements Judge<RequirementEvidence<String, JevEvidence>> {

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
	 * @param model versioned direct model or typesafe-ai/jev for the explicit Vercel
	 * route
	 * @param endpoint official https://api.typesafe.ai/v1/systemone, the paired Vercel
	 * https://ai-gateway.vercel.sh/typesafe/v1/systemone route, or loopback HTTP with the
	 * corresponding path for local tests
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

	/**
	 * Bind a stable native requirement snapshot to its provider-specific rendering.
	 * Rendering happens once on the caller thread. Every invocation validates the
	 * complete envelope before reusing that rendering; the evidence's existing rendered
	 * digest is checked by the adapter, never recomputed to make a stale input match.
	 * @param <S> native specification type
	 * @param requirement stable native requirement snapshot
	 * @param render provider-specific native specification renderer
	 * @return ordinary typed requirement-aware Judge
	 */
	public <S> Judge<RequirementEvidence<S, JevEvidence>> bind(Requirement<S> requirement,
			java.util.function.Function<? super S, String> render) {
		Objects.requireNonNull(requirement, "requirement");
		String rendered = Objects.requireNonNull(render.apply(requirement.specification()), "rendered requirement");
		Checks.text(rendered);
		return input -> {
			Requirement<S> supplied = input.requirement();
			if (!requirement.id().equals(supplied.id()) || !requirement.revision().equals(supplied.revision())
					|| !requirement.specification().equals(supplied.specification())
					|| !requirement.source().equals(supplied.source())) {
				return Judgment.error(io.github.markpollack.judge.judgment.JudgmentReasonCode.JUDGE_REPORTED,
						"Native requirement snapshot differs from the configured provider binding");
			}
			return evaluate(rendered, input.evidence());
		};
	}

	@Override
	public Judgment judge(RequirementEvidence<String, JevEvidence> input) {
		if (input == null)
			return Judgment.error("Requirement and evidence are required");
		return evaluate(input.requirement().specification(), input.evidence());
	}

	private Judgment evaluate(String requirement, JevEvidence evidence) {
		List<ArtifactRef> refs = new ArrayList<>();
		AtomicReference<NativeResponse> nativeResult = new AtomicReference<>();
		AtomicReference<NativeResponse.Envelope> reported = new AtomicReference<>();
		ObservedHttpClient observer = new ObservedHttpClient(http, maxBodyBytes, bytes -> {
			reported.set(NativeResponse.envelope(bytes, gatewayRoute()));
			nativeResult.set(NativeResponse.read(bytes, question));
		});
		String digest = ArtifactRef.ofBytes("empty", new byte[0], null).sha256();
		@Nullable ArtifactRef responseRef = null;
		String problem = "Jev configuration or evidence is invalid";
		boolean success = false;
		boolean configured = false;
		try {
			preflight();
			configured = true;
			Checks.text(requirement);
			Checks.portable(Map.of("requirement", requirement));
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
			config.put("adapter", "jev-adapter:2");
			config.put("sdk", "jev-java:0.2.0");
			config.put("requestedModel", model);
			config.put("endpoint", endpoint.toString());
			config.put("route", gatewayRoute() ? "vercel-typesafe" : "typesafe-direct");
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
			if (configured)
				facts.put("endpoint", endpoint.toString());
			String route = configured ? gatewayRoute() ? "vercel-typesafe" : "typesafe-direct" : "unvalidated";
			facts.put("route", route);
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
				if (envelope.providerMetadataPresent() && responseRef != null)
					// Refer to exact native bytes; duplicating a near-limit metadata tree
					// would overflow the trace bound after adding transport facts.
					facts.put("providerMetadata",
							new ArtifactRef(responseRef.id(), responseRef.sha256(), "/provider_metadata"));
				Map<String, Object> usage = new LinkedHashMap<>();
				usage.put("input_tokens", envelope.inputTokens());
				usage.put("output_tokens", envelope.outputTokens());
				envelope.addCostTo(usage);
				facts.put("usage", usage);
			}
			String underlyingModelVersion = envelope == null || envelope.model().equals("typesafe-ai/jev") ? "unknown"
					: envelope.model().substring(4);
			facts.put("underlyingModelVersion", underlyingModelVersion);
			byte[] traceBytes = Checks.json(facts);
			if (traceBytes.length > maxBodyBytes) {
				// Native strings may individually fit while their diagnostic copies do
				// not. Preserve exact UTF-8 bytes through bounded references instead.
				for (String field : List.of("requestedModel", "reportedModel", "underlyingModelVersion", "requestId")) {
					if (facts.get(field) instanceof String text) {
						ArtifactRef ref = retain("diagnostic-" + field, text.getBytes(StandardCharsets.UTF_8));
						refs.add(ref);
						facts.put(field, ref);
					}
				}
				traceBytes = Checks.json(facts);
			}
			refs.add(retain("trace", traceBytes));
			String revision = "jev-adapter:2;jev-java:0.2.0;requested=" + model
					+ (envelope == null ? "" : ";reported=" + envelope.model()) + ";route=" + route
					+ ";underlyingModelVersion=" + underlyingModelVersion;
			Provenance provenance = new Provenance("typesafe.jev", revision, digest, refs, responseRef,
					success ? List.of(calibrationClaim()) : List.of());
			Map<String, Object> metadata = new LinkedHashMap<>();
			if (envelope != null) {
				Map<String, Object> usage = new LinkedHashMap<>();
				usage.put("inputTokens", portableInteger(envelope.inputTokens()));
				usage.put("outputTokens", portableInteger(envelope.outputTokens()));
				envelope.addCostTo(usage);
				metadata.put("usage", usage);
			}
			if (success)
				metadata.put(Judgment.ELAPSED_MILLIS_KEY, portableInteger(trace.elapsedNanos() / 1000000));
			if (success && value != null)
				return new Judgment(value.status(), value.finding(), value.confidence(),
						value.probabilityDistribution(), null, value.status() == JudgmentStatus.ABSTAIN
								? "Declared projection has no supported determination" : "",
						List.of(), provenance, null, metadata);
			return new Judgment(JudgmentStatus.ERROR, null, null, null, JudgmentReasonCode.JUDGE_REPORTED, problem,
					List.of(), provenance, null, metadata);
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

	private boolean localEndpoint(String path) {
		return "http".equals(endpoint.getScheme())
				&& Set.of("localhost", "127.0.0.1", "[::1]").contains(endpoint.getHost())
				&& path.equals(endpoint.getPath()) && endpoint.getUserInfo() == null && endpoint.getQuery() == null
				&& endpoint.getFragment() == null;
	}

	private boolean gatewayRoute() {
		return endpoint.equals(URI.create("https://ai-gateway.vercel.sh/typesafe/v1/systemone"))
				|| localEndpoint("/typesafe/v1/systemone");
	}

	private void preflight() {
		boolean gateway = gatewayRoute();
		boolean direct = endpoint.equals(URI.create("https://api.typesafe.ai/v1/systemone"))
				|| localEndpoint("/v1/systemone");
		if (!(gateway ? model.equals("typesafe-ai/jev") : model.matches("jev-[0-9]+\\.[0-9]+\\.[0-9]+")))
			throw new IllegalArgumentException("Model must match the explicit provider route");
		if ((!gateway && !direct) || http.followRedirects() != HttpClient.Redirect.NEVER)
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
