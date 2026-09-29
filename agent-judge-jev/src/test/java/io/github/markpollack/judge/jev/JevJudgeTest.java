/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jev;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import io.github.markpollack.judge.context.JudgmentContext;
import io.github.markpollack.judge.result.*;
import io.github.markpollack.judge.jury.*;
import io.github.markpollack.judge.jury.interpretation.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.stream.*;
import static org.assertj.core.api.Assertions.*;

class JevJudgeTest {

	static final String GOAL = "All three required clauses shall be met.";
	static final String EVIDENCE = "Synthetic transport fixture; no real assessment was made.";
	static final ArtifactRef MANIFEST = ArtifactRef.ofBytes("protected:manifest", new byte[] { 1 }, null);
	static final ArtifactRef REVIEW = ArtifactRef.ofBytes("protected:review", new byte[] { 2 }, null);
	static final JevQuestion.Noul NOUL = new JevQuestion.Noul(
			"Evaluate state.requirement using only state.evidence. False means supported violation.", "binary:v1",
			true);

	HttpServer server;

	HttpClient http;

	ExecutorService executor;

	AtomicReference<byte[]> response = new AtomicReference<>();

	AtomicReference<String> requestId = new AtomicReference<>();

	AtomicInteger code = new AtomicInteger(200), calls = new AtomicInteger();

	ConcurrentMap<String, byte[]> captured = new ConcurrentHashMap<>();

	ConcurrentMap<String, byte[]> requests = new ConcurrentHashMap<>();

	@BeforeEach
	void setup() throws Exception {
		executor = Executors.newVirtualThreadPerTaskExecutor();
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.setExecutor(executor);
		com.sun.net.httpserver.HttpHandler handler = exchange -> {
			int call = calls.incrementAndGet();
			byte[] request = exchange.getRequestBody().readAllBytes();
			requests.put("request-" + call, request);
			exchange.getResponseHeaders().add("x-typesafe-request-id",
					requestId.get() == null ? "request-" + call : requestId.get());
			byte[] body = response.get();
			exchange.sendResponseHeaders(code.get(), body.length);
			exchange.getResponseBody().write(body);
			exchange.close();
		};
		server.createContext("/v1/systemone", handler);
		server.createContext("/typesafe/v1/systemone", handler);
		server.start();
		http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
		response.set(fixture("noul-valid"));
	}

	@AfterEach
	void cleanup() {
		server.stop(0);
		http.close();
		executor.close();
	}

	URI endpoint() {
		return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/systemone");
	}

	ArtifactRef capture(String kind, byte[] bytes) {
		ArtifactRef ref = ArtifactRef.ofBytes("protected:" + kind + ":" + UUID.randomUUID(), bytes, null);
		captured.put(ref.id(), bytes);
		return ref;
	}

	JevJudge judge(JevQuestion q) {
		return new JevJudge("fake-key", "jev-1.13.0", endpoint(), Duration.ofSeconds(3), 16000, 32000, q, http,
				this::capture);
	}

	static JudgmentContext context() {
		return context(GOAL, EVIDENCE, true);
	}

	static JudgmentContext context(String goal, String evidence, boolean complete) {
		return JudgmentContext.builder()
			.goal(goal)
			.agentOutput("UNSELECTED-SECRET")
			.metadata(JevEvidence.CONTEXT_KEY, new JevEvidence(evidence,
					ArtifactRef.ofBytes("protected:bundle", evidence.getBytes(StandardCharsets.UTF_8), null), MANIFEST,
					ArtifactRef.ofBytes("requirement", goal.getBytes(StandardCharsets.UTF_8), null).sha256(), complete))
			.build();
	}

	static byte[] fixture(String name) {
		try (var stream = JevJudgeTest.class
			.getResourceAsStream("/semantic-conformance/jev/responses/" + name + ".json")) {
			return Objects.requireNonNull(stream).readAllBytes();
		}
		catch (Exception e) {
			throw new AssertionError(e);
		}
	}

	static JevQuestion.Choice choice() {
		return new JevQuestion.Choice("Evaluate state.requirement using only state.evidence.", "choice:v1",
				new LinkedHashMap<>(Map.of("satisfied", "Satisfied", "violated", "Violated", "insufficient_evidence",
						"Insufficient evidence")),
				Map.of("satisfied", JevQuestion.Meaning.SATISFIED, "violated", JevQuestion.Meaning.VIOLATED,
						"insufficient_evidence", JevQuestion.Meaning.INSUFFICIENT));
	}

	static JevQuestion.Score score(boolean reviewed) {
		var candidate = new JevQuestion.Score("Rate the extent of required clauses met.", "clause-projection:v1",
				"clause-rubric:v1", "required clauses met",
				List.of("No required clauses met", "Some required clauses met", "All required clauses met"),
				List.of(0, 1, 2), QualityDirection.INCREASING, 0, 1, null);
		return withReview(candidate, reviewed ? new JevQuestion.Review(candidate.configurationDigest(),
				"fixture-author", "independent-reviewer", true, REVIEW) : null);
	}

	static JevQuestion.Score withReview(JevQuestion.Score s, JevQuestion.Review review) {
		return new JevQuestion.Score(s.instructions(), s.projectionId(), s.rubricId(), s.dimension(), s.criteria(),
				s.ranks(), s.direction(), s.violatedAtOrBelow(), s.satisfiedAtOrAbove(), review);
	}

	static Stream<String> malformed() {
		return Stream.of("missing-model", "missing-usage", "missing-answer", "wrong-answer-type", "noul-out-of-range",
				"choice-missing-confidence", "score-missing-confidence", "score-missing-legend", "score-changed-legend",
				"score-reversed-legend", "choice-incomplete-distribution", "choice-negative-mass", "choice-sum-not-one",
				"choice-not-maximum", "choice-unknown-selection", "score-incomplete-distribution",
				"score-out-of-range-mass", "score-incoherent-mean", "usage-negative", "confidence-out-of-range");
	}

	@ParameterizedTest
	@MethodSource("malformed")
	void rejectsFrozenMalformedOriginals(String fixture) {
		response.set(fixture(fixture));
		JevQuestion q = fixture.startsWith("score") ? score(true)
				: fixture.startsWith("choice") || fixture.equals("confidence-out-of-range") ? choice() : NOUL;
		Judgment j = judge(q).judge(context());
		assertThat(j.status()).isEqualTo(JudgmentStatus.ERROR);
		assertThat(j.assessment()).isNull();
		assertThat(calls).hasValue(1);
		assertThat(captured.get(j.provenance().response().id())).isEqualTo(response.get());
		assertThat(j.reasoning()).doesNotContain("fake-key", "UNSELECTED-SECRET");
	}

	@Test
	void noulRetainsFalseProbabilityWithoutInventingCertainty() {
		Judgment j = judge(NOUL).judge(context());
		assertThat(j.status()).isEqualTo(JudgmentStatus.FAIL);
		assertThat(j.assessment().proposition().value()).isFalse();
		assertThat(j.certainty()).isNull();
		assertThat(j.distribution().masses()).containsExactly(new ProbabilityMass("false", .95),
				new ProbabilityMass("true", .05));
		assertThat(j.metadata().get("usage")).isEqualTo(Map.of("inputTokens", 120, "outputTokens", 12));
		assertThat(j.provenance().calibrationClaims().getFirst().signalIds())
			.containsExactly("jev.noul.probability-of-true:v1");
		assertThat(new String(requests.get("request-1"), StandardCharsets.UTF_8)).contains(GOAL, EVIDENCE)
			.doesNotContain("UNSELECTED-SECRET", "protected:manifest");
	}

	@Test
	void choiceConfidenceIsNotWinningMass() {
		response.set(fixture("choice-valid"));
		Judgment j = judge(choice()).judge(context());
		assertThat(j.status()).isEqualTo(JudgmentStatus.FAIL);
		assertThat(j.certainty().value()).isEqualTo(.8);
		assertThat(j.distribution().masses()).contains(new ProbabilityMass("violated", .9));
		assertThat(j.assessment().category().alternatives()).hasSize(3);
	}

	@Test
	void scorePreservesFractionalOrdinalAndReviewedLegend() {
		response.set(fixture("score-valid"));
		Judgment j = judge(score(true)).judge(context());
		assertThat(j.status()).isEqualTo(JudgmentStatus.ABSTAIN);
		assertThat(j.assessment().numeric().value()).isEqualTo(1.7);
		assertThat(j.assessment().numeric().kind()).isEqualTo(NumericKind.ORDINAL_EXPECTATION);
		assertThat(j.certainty().value()).isEqualTo(.8);
		assertThat(j.provenance().evidence()).contains(REVIEW);
		assertThat(j.provenance().calibrationClaims().getFirst().signalIds())
			.containsExactly("jev.score.level-distribution:v1", "jev.score.confidence:v1");
	}

	@ParameterizedTest
	@ValueSource(strings = { "noul", "choice", "score" })
	void distinctRequestedObservedModelsAndExactTraceSurviveComposition(String primitive) throws Exception {
		response
			.set(new String(fixture(primitive + "-valid"), StandardCharsets.UTF_8).replace("jev-1.13.0", "jev-1.13.1")
				.getBytes(StandardCharsets.UTF_8));
		JevQuestion q = primitive.equals("noul") ? NOUL : primitive.equals("choice") ? choice() : score(true);
		Judgment j = judge(q).judge(context());
		Verdict verdict = SimpleJury.builder()
			.judge(c -> j)
			.votingStrategy(new ConsensusStrategy())
			.build()
			.vote(context());
		assertThat(verdict.aggregated()).isEqualTo(j);
		Verdict restored = Checks.JSON.readValue(Checks.JSON.writeValueAsBytes(verdict), Verdict.class);
		assertThat(restored).isEqualTo(verdict);
		var interpreted = Verdicts.interpret(restored);
		assertThat(interpreted.readingSupport()).isEqualTo(ReadingSupport.SUPPORTED);
		assertThat(interpreted.root().judgment().assessment()).isEqualTo(j.assessment());
		assertThat(interpreted.root().judgment().provenance()).isEqualTo(j.provenance());
		assertThat(interpreted.root().judgment().distribution()).isEqualTo(j.distribution());
		assertThat(interpreted.root().judgment().certainty()).isEqualTo(j.certainty());
		var wire = Checks.JSON.convertValue(verdict,
				new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
				});
		assertThat(Verdicts.interpret(wire)).isEqualTo(interpreted);
		assertThat(j.provenance().revision()).contains("requested=jev-1.13.0", "reported=jev-1.13.1");
		JsonNode trace = trace(j);
		assertThat(trace.path("attempts").asInt()).isEqualTo(1);
		assertThat(trace.path("requestId").asText()).isEqualTo("request-1");
		assertThat(trace.path("elapsedNanos").asLong()).isPositive();
		assertThat(artifact(j, "request")).isEqualTo(requests.get("request-1"));
		assertThat(captured.get(j.provenance().response().id())).isEqualTo(response.get());
	}

	byte[] artifact(Judgment j, String kind) {
		return captured.get(j.provenance()
			.evidence()
			.stream()
			.filter(r -> r.id().startsWith("protected:" + kind + ":"))
			.findFirst()
			.orElseThrow()
			.id());
	}

	JsonNode trace(Judgment j) {
		return Checks.parse(artifact(j, "trace"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "model", "requestId", "both" })
	void boundedNativeAnswersSurviveLargeDiagnosticStrings(String field) throws Exception {
		ObjectNode body = (ObjectNode) Checks.parse(fixture("noul-valid"));
		String reportedModel = field.equals("requestId") ? "jev-1.13.1" : "jev-" + "1".repeat(33000) + ".1.0";
		String id = field.equals("model") ? "request-1" : "r".repeat(65000);
		body.put("model", reportedModel);
		requestId.set(id);
		response.set(Checks.json(body));
		assertThat(response.get().length).isLessThanOrEqualTo(65536);
		Judgment j = new JevJudge("fake-key", "jev-1.13.0", endpoint(), Duration.ofSeconds(3), 24576, 65536,
				NOUL, http, this::capture).judge(context());
		assertThat(j.status()).isEqualTo(JudgmentStatus.FAIL);
		assertThat(j.provenance()).isNotNull();
		assertThat(j.assessment().proposition().value()).isFalse();
		assertThat(j.distribution().masses()).containsExactly(new ProbabilityMass("false", 0.95),
				new ProbabilityMass("true", 0.05));
		assertThat(captured.get(j.provenance().response().id())).isEqualTo(response.get());
		assertThat(diagnosticText(j, "reportedModel")).isEqualTo(reportedModel);
		assertThat(diagnosticText(j, "underlyingModelVersion")).isEqualTo(reportedModel.substring(4));
		assertThat(diagnosticText(j, "requestId")).isEqualTo(id);
		assertThat(diagnosticText(j, "requestedModel")).isEqualTo("jev-1.13.0");
		assertThat(captured.values()).allSatisfy(bytes -> assertThat(bytes.length).isLessThanOrEqualTo(65536));
		PolicyRef policy = new PolicyRef("use", "1", MANIFEST.sha256());
		Judgment applied = Policies.apply(j, policy,
				input -> new Acceptance(AcceptanceAction.USE_ASSESSMENT, "Use retained assessment"));
		var verdict = io.github.markpollack.judge.jury.SimpleJury.builder().judge(context -> applied)
			.votingStrategy(new io.github.markpollack.judge.jury.AverageVotingStrategy()).build()
			.vote(context());
		assertThat(verdict.aggregated()).isEqualTo(applied);
		assertThat(applied.provenance()).isEqualTo(j.provenance());
		assertThat(applied.assessment()).isEqualTo(j.assessment());
		assertThat(io.github.markpollack.judge.jury.interpretation.Verdicts.interpret(verdict).reading())
			.isEqualTo(io.github.markpollack.judge.jury.interpretation.VerdictReading.REJECTED);
		assertThat(calls).hasValue(1);
	}

	String diagnosticText(Judgment judgment, String field) throws Exception {
		JsonNode value = trace(judgment).path(field);
		if (value.isTextual())
			return value.textValue();
		ArtifactRef ref = Checks.JSON.treeToValue(value, ArtifactRef.class);
		assertThat(judgment.provenance().evidence()).contains(ref);
		assertThat(ref.selector()).isNull();
		byte[] bytes = captured.get(ref.id());
		assertThat(ArtifactRef.ofBytes(ref.id(), bytes, null).sha256()).isEqualTo(ref.sha256());
		return new String(bytes, StandardCharsets.UTF_8);
	}

	@Test
	void concurrentCallsRetainTheirOwnExactRequestIdsAndBytes() throws Exception {
		JevJudge judge = judge(NOUL);
		List<Future<Judgment>> futures = new ArrayList<>();
		for (int i = 0; i < 24; i++) {
			int index = i;
			futures.add(executor.submit(() -> judge.judge(context("requirement-" + index, "evidence-" + index, true))));
		}
		Set<String> ids = new HashSet<>();
		for (int i = 0; i < futures.size(); i++) {
			Judgment j = futures.get(i).get(5, TimeUnit.SECONDS);
			assertThat(j.status()).isEqualTo(JudgmentStatus.FAIL);
			String id = trace(j).path("requestId").asText();
			ids.add(id);
			assertThat(artifact(j, "request")).isEqualTo(requests.get(id));
			assertThat(Checks.parse(artifact(j, "request")).path("state").path("requirement").asText())
				.isEqualTo("requirement-" + i);
		}
		assertThat(ids).hasSize(24);
		assertThat(calls).hasValue(24);
	}

	@Test
	void noRetryOnRateLimitAndErrorEchoOnlyInProtectedArtifact() {
		code.set(429);
		response.set("fake-key UNSELECTED-SECRET".getBytes(StandardCharsets.UTF_8));
		Judgment j = judge(NOUL).judge(context());
		assertThat(j.status()).isEqualTo(JudgmentStatus.ERROR);
		assertThat(calls).hasValue(1);
		assertThat(j.reasoning()).doesNotContain("fake-key", "UNSELECTED-SECRET");
		assertThat(j.metadata()).doesNotContainKey("usage");
		assertThat(captured.get(j.provenance().response().id())).isEqualTo(response.get());
	}

	@Test
	void missingStaleUnresolvedAndNonIndependentReviewsMakeZeroCalls() {
		var candidate = score(false);
		for (var s : List.of(candidate, withReview(candidate, new JevQuestion.Review("stale", "a", "b", true, REVIEW)),
				withReview(candidate, new JevQuestion.Review(candidate.configurationDigest(), "a", "b", false, REVIEW)),
				withReview(candidate, new JevQuestion.Review(candidate.configurationDigest(), "a", "a", true, REVIEW))))
			assertThat(judge(s).judge(context()).status()).isEqualTo(JudgmentStatus.ERROR);
		assertThat(calls).hasValue(0);
	}

	@Test
	void contradictoryRankSequenceIsRejectedBeforeHttp() {
		var candidate = new JevQuestion.Score("Evaluate requirement", "ordering:v1", "ordering:v1", "satisfaction",
				List.of("fully satisfied", "partially satisfied", "violated", "mostly satisfied"), List.of(3, 1, 0, 2),
				QualityDirection.DECREASING, 0, 1, null);
		var reviewed = withReview(candidate,
				new JevQuestion.Review(candidate.configurationDigest(), "a", "b", true, REVIEW));
		assertThat(judge(reviewed).judge(context()).status()).isEqualTo(JudgmentStatus.ERROR);
		assertThat(calls).hasValue(0);
	}

	@Test
	void rejectsMissingEvidenceAndIncompleteBinaryWithoutCalls() {
		assertThat(judge(NOUL).judge(JudgmentContext.builder().goal(GOAL).build()).status())
			.isEqualTo(JudgmentStatus.ERROR);
		assertThat(judge(NOUL).judge(context(GOAL, EVIDENCE, false)).status()).isEqualTo(JudgmentStatus.ERROR);
		assertThat(calls).hasValue(0);
	}

	@Test
	void oversizedEvidenceHasNoCallOrTruncation() {
		var j = new JevJudge("fake-key", "jev-1.13.0", endpoint(), Duration.ofSeconds(3), 10, 32000, NOUL, http,
				this::capture);
		assertThat(j.judge(context()).status()).isEqualTo(JudgmentStatus.ERROR);
		assertThat(calls).hasValue(0);
	}

	@Test
	void reservedMetadataNamesAreOrdinaryNativeChoiceLabels() {
		assertThatCode(
				() -> new JevQuestion.Choice("instruction", "mapping:v1",
						Map.of("aggregation", "a", "elapsedMillis", "b"), Map.of("aggregation",
								JevQuestion.Meaning.SATISFIED, "elapsedMillis", JevQuestion.Meaning.VIOLATED)))
			.doesNotThrowAnyException();
	}

	@Test
	void evidenceCompletenessCannotTransferToAnotherRequirement() {
		var original = context();
		JudgmentContext changed = JudgmentContext.builder()
			.goal("Unrelated requirement")
			.metadata(original.metadata())
			.build();
		assertThat(judge(NOUL).judge(changed).status()).isEqualTo(JudgmentStatus.ERROR);
		assertThat(calls).hasValue(0);
	}

	@Test
	void matchingStructuredNumericLegendAndReorderedObjectMembersAreAccepted() {
		var levels = List.<Object>of(Map.of("meaning", "none", "clauses", 0L), Map.of("meaning", "all", "clauses", 3L));
		var s = new JevQuestion.Score("Evaluate state.requirement", "numeric:v1", "numeric:v1", "clauses", levels,
				List.of(0, 1), QualityDirection.INCREASING, 0, 1, null);
		var reviewed = withReview(s, new JevQuestion.Review(s.configurationDigest(), "a", "b", true, REVIEW));
		response.set(
				"{\"model\":\"jev-1.13.0\",\"answers\":{\"q\":{\"type\":\"score\",\"score\":1,\"confidence\":0.9,\"legend\":{\"0\":{\"clauses\":0,\"meaning\":\"none\"},\"1\":{\"clauses\":3,\"meaning\":\"all\"}},\"probabilities\":{\"0\":0,\"1\":1}}},\"usage\":{\"input_tokens\":12,\"output_tokens\":2}}"
					.getBytes(StandardCharsets.UTF_8));
		assertThat(judge(reviewed).judge(context()).status()).isEqualTo(JudgmentStatus.PASS);
	}

	@Test
	void noulTieAndBothEndpointsAreFaithful() {
		for (double p : new double[] { 0, .5, 1 }) {
			ObjectNode body = (ObjectNode) Checks.parse(fixture("noul-valid"));
			((ObjectNode) body.path("answers").path("q")).put("noul", p);
			response.set(Checks.json(body));
			Judgment j = judge(NOUL).judge(context());
			assertThat(j.status())
				.isEqualTo(p == .5 ? JudgmentStatus.ABSTAIN : p == 0 ? JudgmentStatus.FAIL : JudgmentStatus.PASS);
			assertThat(j.assessment().proposition().value()).isEqualTo(p == .5 ? null : p == 1);
			assertThat(j.certainty()).isNull();
		}
	}

	@Test
	void choiceInsufficiencyAndConflictingTieAbstain() {
		ObjectNode body = (ObjectNode) Checks.parse(fixture("choice-valid"));
		ObjectNode answer = (ObjectNode) body.path("answers").path("q");
		answer.put("choice", "insufficient_evidence");
		answer.set("probabilities",
				Checks.JSON.valueToTree(Map.of("satisfied", 0, "violated", 0, "insufficient_evidence", 1)));
		response.set(Checks.json(body));
		assertThat(judge(choice()).judge(context()).status()).isEqualTo(JudgmentStatus.ABSTAIN);
		answer.put("choice", "satisfied");
		answer.set("probabilities",
				Checks.JSON.valueToTree(Map.of("satisfied", .5, "violated", .5, "insufficient_evidence", 0)));
		response.set(Checks.json(body));
		assertThat(judge(choice()).judge(context()).status()).isEqualTo(JudgmentStatus.ABSTAIN);
	}

	@Test
	void choiceTieWithSameMeaningRetainsFinding() {
		var q = new JevQuestion.Choice("Use state.requirement", "same:v1", Map.of("a", "first", "b", "second"),
				Map.of("a", JevQuestion.Meaning.SATISFIED, "b", JevQuestion.Meaning.SATISFIED));
		ObjectNode body = (ObjectNode) Checks.parse(fixture("choice-valid"));
		ObjectNode answer = (ObjectNode) body.path("answers").path("q");
		answer.put("choice", "a");
		answer.set("probabilities", Checks.JSON.valueToTree(Map.of("a", .5, "b", .5)));
		response.set(Checks.json(body));
		assertThat(judge(q).judge(context()).status()).isEqualTo(JudgmentStatus.PASS);
	}

	@Test
	void decreasingRubricProjectsQualityWithoutReversingNativeIndex() {
		var s = new JevQuestion.Score("Rate unmet required clauses in state.requirement.", "unmet:v1", "unmet:v1",
				"unmet clauses",
				List.of("All required clauses met", "Some required clauses unmet", "No required clauses met"),
				List.of(2, 1, 0), QualityDirection.DECREASING, 0, 1, null);
		var q = withReview(s, new JevQuestion.Review(s.configurationDigest(), "a", "b", true, REVIEW));
		for (int index : new int[] { 0, 2 }) {
			ObjectNode body = (ObjectNode) Checks.parse(fixture("score-valid"));
			ObjectNode answer = (ObjectNode) body.path("answers").path("q");
			answer.put("score", index);
			answer.set("legend", Checks.JSON
				.valueToTree(Map.of("0", s.criteria().get(0), "1", s.criteria().get(1), "2", s.criteria().get(2))));
			answer.set("probabilities",
					Checks.JSON.valueToTree(Map.of("0", index == 0 ? 1 : 0, "1", 0, "2", index == 2 ? 1 : 0)));
			response.set(Checks.json(body));
			Judgment j = judge(q).judge(context());
			assertThat(j.status()).isEqualTo(index == 0 ? JudgmentStatus.PASS : JudgmentStatus.FAIL);
			assertThat(j.assessment().numeric().value()).isEqualTo(index);
			assertThat(j.assessment().numeric().qualityScore().orElseThrow()).isEqualTo(index == 0 ? 1 : 0);
		}
	}

	static Stream<String> additionalProtocolCases() {
		return Stream.of("duplicate", "trailing", "huge-usage", "fractional-usage", "text-usage", "null-model",
				"extra-id", "wrong-id", "text-probability", "non-finite", "null-confidence", "extra-mass",
				"unknown-type", "nonobject-answers");
	}

	@ParameterizedTest
	@MethodSource("additionalProtocolCases")
	void rejectsAdditionalProtocolDefects(String kind) {
		ObjectNode body = (ObjectNode) Checks.parse(fixture("choice-valid"));
		ObjectNode answer = (ObjectNode) body.path("answers").path("q");
		switch (kind) {
			case "huge-usage" -> ((ObjectNode) body.path("usage")).put("input_tokens", 9007199254740992L);
			case "fractional-usage" -> ((ObjectNode) body.path("usage")).put("input_tokens", 1.5);
			case "text-usage" -> ((ObjectNode) body.path("usage")).put("input_tokens", "12");
			case "null-model" -> body.putNull("model");
			case "extra-id" -> ((ObjectNode) body.path("answers")).set("extra", answer.deepCopy());
			case "wrong-id" -> {
				((ObjectNode) body.path("answers")).remove("q");
				((ObjectNode) body.path("answers")).set("other", answer);
			}
			case "text-probability" -> ((ObjectNode) answer.path("probabilities")).put("satisfied", "0.05");
			case "non-finite" -> answer.put("confidence", Double.NaN);
			case "null-confidence" -> answer.putNull("confidence");
			case "extra-mass" -> ((ObjectNode) answer.path("probabilities")).put("extra", 0);
			case "unknown-type" -> answer.put("type", "unknown");
			case "nonobject-answers" -> body.putArray("answers");
		}
		byte[] bytes = Checks.json(body);
		String text = new String(bytes, StandardCharsets.UTF_8);
		if (kind.equals("duplicate"))
			bytes = ("{\"model\":\"jev-1.13.0\"," + text.substring(1)).getBytes(StandardCharsets.UTF_8);
		if (kind.equals("trailing"))
			bytes = (text + "{}").getBytes(StandardCharsets.UTF_8);
		response.set(bytes);
		Judgment j = judge(choice()).judge(context());
		assertThat(j.status()).isEqualTo(JudgmentStatus.ERROR);
		assertThat(calls).hasValue(1);
		assertThat(captured.get(j.provenance().response().id())).isEqualTo(bytes);
	}

	@Test
	void oversizedResponseIsAnInstrumentError() {
		response.set(new byte[33000]);
		Judgment j = judge(NOUL).judge(context());
		assertThat(j.status()).isEqualTo(JudgmentStatus.ERROR);
		assertThat(j.assessment()).isNull();
		assertThat(calls).hasValue(1);
	}

	@Test
	void capturedArtifactsAreHashVerifiedAndStorageFailureIsContained() {
		for (ArtifactCapture sink : List.<ArtifactCapture>of((kind, bytes) -> MANIFEST, (kind, bytes) -> {
			throw new IllegalStateException("sensitive storage failure");
		})) {
			var j = new JevJudge("fake-key", "jev-1.13.0", endpoint(), Duration.ofSeconds(1), 16000, 32000, NOUL, http,
					sink)
				.judge(context());
			assertThat(j.status()).isEqualTo(JudgmentStatus.ERROR);
			assertThat(j.reasoning()).doesNotContain("sensitive");
		}
		assertThat(calls).hasValue(0);
	}

	@Test
	void missingVersionAuthenticationBadEndpointAndDeadlineAreErrorsWithZeroCalls() {
		for (var j : List.of(
				new JevJudge("", "jev-1.13.0", endpoint(), Duration.ofSeconds(1), 16000, 32000, NOUL, http,
						this::capture),
				new JevJudge("fake-key", "jev-latest", endpoint(), Duration.ofSeconds(1), 16000, 32000, NOUL, http,
						this::capture),
				new JevJudge("fake-key", "jev-1.13.0", URI.create("https://example.invalid/v1/systemone"),
						Duration.ofSeconds(1), 16000, 32000, NOUL, http, this::capture),
				new JevJudge("fake-key", "jev-1.13.0", endpoint(), Duration.ZERO, 16000, 32000, NOUL, http,
						this::capture)))
			assertThat(j.judge(context()).status()).isEqualTo(JudgmentStatus.ERROR);
		assertThat(calls).hasValue(0);
	}

	@Test
	void boundsAndPortableInputValidation() {
		assertThatThrownBy(() -> new JevQuestion.Choice("instruction", "v:1", Map.of(), Map.of()))
			.isInstanceOf(IllegalArgumentException.class);
		var many = new LinkedHashMap<String, Object>();
		var meanings = new LinkedHashMap<String, JevQuestion.Meaning>();
		for (int i = 0; i < 256; i++) {
			many.put("label" + i, "description");
			meanings.put("label" + i, JevQuestion.Meaning.SATISFIED);
		}
		assertThatThrownBy(() -> new JevQuestion.Choice("instruction", "v:1", many, meanings))
			.isInstanceOf(IllegalArgumentException.class);
		many.remove("label255");
		meanings.remove("label255");
		assertThat(new JevQuestion.Choice("instruction", "v:1", many, meanings).criteria()).hasSize(255);
		assertThatThrownBy(() -> new JevQuestion.Score("i", "p:v1", "r:v1", "d", List.of("one"), List.of(0),
				QualityDirection.INCREASING, 0, 1, null))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new JevQuestion.Score("i", "p:v1", "r:v1", "d", Collections.nCopies(11, "a"),
				Collections.nCopies(11, 0), QualityDirection.INCREASING, 0, 1, null))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new JevQuestion.Score("i", "p:v1", "r:v1", "d", List.of(0, 1), List.of(0, 1),
				QualityDirection.INCREASING, 0, 1, null))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new JevQuestion.Score("i", "p:v1", "r:v1", "d", List.of("a", "b"), List.of(0, 1),
				QualityDirection.INCREASING, .8, .2, null))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new JevEvidence("changed", MANIFEST, MANIFEST, MANIFEST.sha256(), true))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new JevQuestion.Noul("", "unversioned", true))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new JevQuestion.Noul("instructions", "unversioned", true))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void structuredConfigIsDeeplyImmutable() {
		var nested = new ArrayList<Object>();
		nested.add("one");
		var criteria = new LinkedHashMap<String, Object>();
		criteria.put("a", nested);
		var c = new JevQuestion.Choice("i", "v:1", criteria, Map.of("a", JevQuestion.Meaning.SATISFIED));
		nested.add("later");
		criteria.clear();
		assertThat(c.criteria()).isEqualTo(Map.of("a", List.of("one")));
		assertThatThrownBy(() -> c.criteria().put("b", "bad")).isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void malformedAssessmentRetainsValidatedRequestUsageOnce() {
		response.set(fixture("score-changed-legend"));
		Judgment j = judge(score(true)).judge(context());
		assertThat(j.status()).isEqualTo(JudgmentStatus.ERROR);
		assertThat(j.metadata().get("usage")).isEqualTo(Map.of("inputTokens", 120, "outputTokens", 12));
		assertThat(j.checks()).isEmpty();
		assertThat(trace(j).path("reportedModel").asText()).isEqualTo("jev-1.13.0");
	}

	@Test
	void maliciousReportedModelIsKeptOnlyInProtectedBody() {
		ObjectNode body = (ObjectNode) Checks.parse(fixture("noul-valid"));
		body.put("model", "echoed SECRET");
		response.set(Checks.json(body));
		Judgment j = judge(NOUL).judge(context());
		assertThat(j.status()).isEqualTo(JudgmentStatus.ERROR);
		assertThat(Checks.JSON.valueToTree(j).toString()).doesNotContain("SECRET");
	}

	@Test
	void independentlyReviewedDemonstrationBindsActualRecord() throws Exception {
		byte[] bytes;
		try (var stream = getClass().getResourceAsStream("/score-review.json")) {
			bytes = Objects.requireNonNull(stream).readAllBytes();
		}
		ArtifactRef record = ArtifactRef.ofBytes("test:clause-rubric-order-review:v1", bytes, null);
		assertThat(record.sha256()).isEqualTo("aa38f0d502ad2defe37bf64f072577ea39a31108dd534ecfd2d060d807d8d2e8");
		JsonNode review = Checks.parse(bytes);
		var candidate = score(false);
		assertThat(candidate.configurationDigest())
			.isEqualTo("0ab51c18e62d58b6961750619ae51a21fa3f57bd96086505dba045e162f54d7f");
		var approved = withReview(candidate,
				new JevQuestion.Review(review.path("configurationDigest").asText(), review.path("author").asText(),
						review.path("reviewer").asText(), review.path("approved").asBoolean(), record));
		response.set(fixture("score-valid"));
		Judgment result = judge(approved).judge(context());
		assertThat(result.status()).isEqualTo(JudgmentStatus.ABSTAIN);
		assertThat(result.provenance().evidence()).contains(record);
		assertThat(calls).hasValue(1);
	}

	@ParameterizedTest
	@ValueSource(longs = { 2147483647L, 2147483648L, 9007199254740991L })
	void requestUsageSurvivesPortableIntegerWidthBoundaries(long count) throws Exception {
		ObjectNode body = (ObjectNode) Checks.parse(fixture("choice-valid"));
		((ObjectNode) body.path("usage")).put("input_tokens", count);
		response.set(Checks.json(body));
		Judgment j = judge(choice()).judge(context());
		assertThat(j.status()).isEqualTo(JudgmentStatus.FAIL);
		Judgment restored = Checks.JSON.readValue(Checks.JSON.writeValueAsBytes(j), Judgment.class);
		assertThat(restored).isEqualTo(j);
		var verdict = SimpleJury.builder()
			.judge(c -> j)
			.votingStrategy(new ConsensusStrategy())
			.build()
			.vote(context());
		var decoded = Checks.JSON.readValue(Checks.JSON.writeValueAsBytes(verdict), Verdict.class);
		assertThat(decoded).isEqualTo(verdict);
		assertThat(Verdicts.interpret(decoded).root().judgment().metadata()).isEqualTo(j.metadata());
	}

	URI gatewayEndpoint() {
		return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/typesafe/v1/systemone");
	}

	JevJudge gatewayJudge(JevQuestion q) {
		return new JevJudge("fake-key", "typesafe-ai/jev", gatewayEndpoint(), Duration.ofSeconds(3), 16000, 32000, q,
				http, this::capture);
	}

	ObjectNode gatewayResponse(String name) {
		ObjectNode body = (ObjectNode) Checks.parse(fixture(name));
		body.put("model", "typesafe-ai/jev");
		var metadata = body.putObject("provider_metadata");
		metadata.putObject("typesafe").putObject("confidence").put("q", .123);
		var gateway = metadata.putObject("gateway");
		gateway.putObject("routing").put("resolvedProvider", "typesafe-ai").put("finalProvider", "typesafe-ai");
		gateway.put("generationId", "fake-generation").put("cost", "0").put("marketCost", "0.00001");
		return body;
	}

	@Test
	void gatewayRetainsNativeSupportAndRoutingWithoutInventingVersion() throws Exception {
		ObjectNode body = gatewayResponse("choice-valid");
		response.set(Checks.json(body));
		Judgment j = gatewayJudge(choice()).judge(context());
		assertThat(j.status()).isEqualTo(JudgmentStatus.FAIL);
		assertThat(j.certainty().value()).isEqualTo(.8);
		assertThat(j.distribution().masses()).contains(new ProbabilityMass("violated", .9));
		assertThat(captured.get(j.provenance().response().id())).isEqualTo(response.get());
		assertThat(providerMetadata(j)).isEqualTo(body.path("provider_metadata"));
		assertThat(trace(j).path("underlyingModelVersion").asText()).isEqualTo("unknown");
		assertThat(trace(j).path("endpoint").asText()).isEqualTo(gatewayEndpoint().toString());
		assertThat(trace(j).path("route").asText()).isEqualTo("vercel-typesafe");
		assertThat(Checks.parse(artifact(j, "configuration")).path("endpoint").asText())
			.isEqualTo(gatewayEndpoint().toString());
		assertThat(j.provenance().revision())
			.contains("requested=typesafe-ai/jev", "reported=typesafe-ai/jev", "underlyingModelVersion=unknown")
			.doesNotContain("jev-1.13.0");
		assertThat(Checks.JSON.readValue(Checks.json(j), Judgment.class)).isEqualTo(j);
		assertThat(calls).hasValue(1);
	}

	@Test
	void gatewayOptionalMetadataStaysAbsent() {
		ObjectNode body = gatewayResponse("choice-valid");
		body.remove("provider_metadata");
		response.set(Checks.json(body));
		Judgment j = gatewayJudge(choice()).judge(context());
		assertThat(j.status()).isEqualTo(JudgmentStatus.FAIL);
		assertThat(trace(j).has("providerMetadata")).isFalse();
		assertThat(trace(j).path("underlyingModelVersion").asText()).isEqualTo("unknown");
		assertThat((Map<?, ?>) j.metadata().get("usage")).hasSize(2);
	}

	@ParameterizedTest
	@ValueSource(strings = { "\"0.000182742\"", "0.000182742", "\"1.82742e-4\"", "\"0\"", "0" })
	void gatewayCostIsReportedOnceAndSurvivesVerdictPersistence(String nativeCost) throws Exception {
		ObjectNode body = gatewayResponse("choice-valid");
		((ObjectNode) body.at("/provider_metadata/gateway")).set("cost",
				Checks.parse(nativeCost.getBytes(StandardCharsets.UTF_8)));
		response.set(Checks.json(body));
		Judgment j = gatewayJudge(choice()).judge(context());
		double expected = new java.math.BigDecimal(nativeCost.replace("\"", "")).doubleValue();
		assertThat(j.status()).isEqualTo(JudgmentStatus.FAIL);
		assertThat(j.certainty().value()).isEqualTo(.8);
		assertThat(j.distribution().masses()).contains(new ProbabilityMass("violated", .9));
		JsonNode usage = Checks.JSON.valueToTree(j.metadata().get("usage"));
		assertThat(usage.path("cost").isNumber()).isTrue();
		assertThat(usage.path("cost").doubleValue()).isEqualTo(expected);
		assertThat(usage.path("currency").asText()).isEqualTo("USD");
		assertThat(usage.path("costSource").asText())
			.isEqualTo("vercel-gateway-reported:v1:/provider_metadata/gateway/cost");
		assertThat(usage.has("priceRuleId")).isFalse();
		assertThat(trace(j).at("/usage/cost").doubleValue()).isEqualTo(expected);
		assertThat(trace(j).at("/usage/currency").asText()).isEqualTo("USD");
		assertThat(trace(j).at("/usage/costSource")).isEqualTo(usage.path("costSource"));
		assertThat(j.checks()).isEmpty();
		assertThat(captured.get(j.provenance().response().id())).isEqualTo(response.get());
		assertThat(Checks.JSON.readValue(Checks.json(j), Judgment.class)).isEqualTo(j);
		var verdict = SimpleJury.builder().judge(c -> j).votingStrategy(new ConsensusStrategy()).build().vote(context());
		var reopened = Checks.JSON.readValue(Checks.json(verdict), Verdict.class);
		assertThat(reopened).isEqualTo(verdict);
		assertThat(Verdicts.interpret(reopened).root().judgment().metadata()).isEqualTo(j.metadata());
		assertThat(calls).hasValue(1);
	}

	@ParameterizedTest
	@ValueSource(strings = { "null", "true", "{}", "[]", "\"\"", "\"unknown\"", "-1", "\"-0.01\"", "\"NaN\"",
			"\"Infinity\"", "\"0x1.0p0\"", "\"1e9999\"", "\"1e-9999\"", "1e9999", "1e-9999", "-1e-9999" })
	void gatewayCostInvalidValuesStayUnknownWithoutChangingAssessment(String nativeCost) {
		String body = new String(Checks.json(gatewayResponse("choice-valid")), StandardCharsets.UTF_8);
		response.set(body.replace("\"cost\":\"0\"", "\"cost\":" + nativeCost).getBytes(StandardCharsets.UTF_8));
		Judgment j = gatewayJudge(choice()).judge(context());
		assertThat(j.status()).isEqualTo(JudgmentStatus.FAIL);
		assertThat(j.certainty().value()).isEqualTo(.8);
		assertThat(j.metadata().get("usage")).isEqualTo(Map.of("inputTokens", 120, "outputTokens", 12));
		assertThat(trace(j).path("usage").has("cost")).isFalse();
		assertThat(captured.get(j.provenance().response().id())).isEqualTo(response.get());
		assertThat(calls).hasValue(1);
	}

	@Test
	void gatewayCostNeverUsesMarketPriceOrDirectRouteMetadata() {
		ObjectNode body = gatewayResponse("choice-valid");
		((ObjectNode) body.at("/provider_metadata/gateway")).remove("cost");
		response.set(Checks.json(body));
		Judgment missing = gatewayJudge(choice()).judge(context());
		assertThat(missing.status()).isEqualTo(JudgmentStatus.FAIL);
		assertThat((Map<?, ?>) missing.metadata().get("usage")).hasSize(2);
		body.put("model", "jev-1.13.0");
		((ObjectNode) body.at("/provider_metadata/gateway")).put("cost", "0.000182742");
		response.set(Checks.json(body));
		Judgment direct = judge(choice()).judge(context());
		assertThat(direct.status()).isEqualTo(JudgmentStatus.FAIL);
		assertThat((Map<?, ?>) direct.metadata().get("usage")).hasSize(2);
		assertThat(trace(direct).path("usage").has("cost")).isFalse();
		assertThat(calls).hasValue(2);
	}

	@ParameterizedTest
	@ValueSource(strings = { "choice-not-maximum", "choice-insufficient", "noul-valid", "score-valid" })
	void gatewayCostBelongsToRequestRegardlessOfAssessment(String fixture) {
		ObjectNode body = gatewayResponse(fixture.equals("choice-insufficient") ? "choice-valid" : fixture);
		if (fixture.equals("choice-insufficient")) {
			ObjectNode answer = (ObjectNode) body.at("/answers/q");
			answer.put("choice", "insufficient_evidence");
			answer.putObject("probabilities").put("satisfied", .05).put("violated", .05).put("insufficient_evidence", .9);
		}
		((ObjectNode) body.at("/provider_metadata/gateway")).put("cost", "0.000182742");
		response.set(Checks.json(body));
		JevQuestion question = fixture.startsWith("noul") ? NOUL : fixture.startsWith("score") ? score(true) : choice();
		Judgment j = gatewayJudge(question).judge(context());
		JudgmentStatus expected = switch (fixture) {
			case "choice-not-maximum" -> JudgmentStatus.ERROR;
			case "noul-valid" -> JudgmentStatus.FAIL;
			default -> JudgmentStatus.ABSTAIN;
		};
		assertThat(j.status()).isEqualTo(expected);
		if (expected == JudgmentStatus.ERROR) {
			assertThat(j.assessment()).isNull();
			assertThat(j.certainty()).isNull();
		}
		assertThat(Checks.JSON.valueToTree(j.metadata()).at("/usage/cost").isNumber()).isTrue();
		assertThat(Checks.JSON.valueToTree(j.metadata()).at("/usage/cost").doubleValue()).isEqualTo(.000182742);
		assertThat(trace(j).at("/usage/cost").doubleValue()).isEqualTo(.000182742);
		assertThat(j.checks()).isEmpty();
		assertThat(calls).hasValue(1);
	}

	@ParameterizedTest
	@ValueSource(strings = { "confidence", "probabilities", "model", "usage" })
	void gatewayMissingRequiredFactsRemainErrorsWithRawAbsence(String field) {
		ObjectNode body = gatewayResponse("choice-valid");
		if (field.equals("model") || field.equals("usage"))
			body.remove(field);
		else
			((ObjectNode) body.path("answers").path("q")).remove(field);
		response.set(Checks.json(body));
		Judgment j = gatewayJudge(choice()).judge(context());
		assertThat(j.status()).isEqualTo(JudgmentStatus.ERROR);
		assertThat(j.assessment()).isNull();
		assertThat(j.certainty()).isNull();
		assertThat(calls).hasValue(1);
		assertThat(captured.get(j.provenance().response().id())).isEqualTo(response.get());
	}

	@ParameterizedTest
	@ValueSource(strings = { "score-valid", "score-missing-legend", "score-reversed-legend", "score-changed-legend" })
	void gatewayScoreKeepsExistingLegendValidation(String fixture) {
		response.set(Checks.json(gatewayResponse(fixture)));
		Judgment j = gatewayJudge(score(true)).judge(context());
		assertThat(j.status()).isEqualTo(fixture.equals("score-valid") ? JudgmentStatus.ABSTAIN : JudgmentStatus.ERROR);
		assertThat(calls).hasValue(1);
		assertThat(captured.get(j.provenance().response().id())).isEqualTo(response.get());
	}

	@Test
	void routeAndModelMustBePairedAndReportedAliasIsRouteBound() {
		for (String[] pair : List.of(new String[] { "typesafe-ai/jev", endpoint().toString() },
				new String[] { "jev-1.13.0", gatewayEndpoint().toString() },
				new String[] { "typesafe-ai/jev", "https://api.typesafe.ai/v1/systemone" },
				new String[] { "jev-1.13.0", "https://ai-gateway.vercel.sh/typesafe/v1/systemone" },
				new String[] { "typesafe-ai/jev", "https://ai-gateway.vercel.sh/typesafe/v1/systemone?x=1" },
				new String[] { "typesafe-ai/jev", "https://user@ai-gateway.vercel.sh/typesafe/v1/systemone" },
				new String[] { "typesafe-ai/jev", "https://ai-gateway.vercel.sh/typesafe/v1/systemone#fragment" },
				new String[] { "typesafe-ai/jev", "http://ai-gateway.vercel.sh/typesafe/v1/systemone" }, new String[] {
						"typesafe-ai/jev", "https://ai-gateway.vercel.sh.evil.invalid/typesafe/v1/systemone" })) {
			var judge = new JevJudge("fake-key", pair[0], URI.create(pair[1]), Duration.ofSeconds(1), 16000, 32000,
					choice(), http, this::capture);
			assertThat(judge.judge(context()).status()).isEqualTo(JudgmentStatus.ERROR);
		}
		assertThat(calls).hasValue(0);
		response.set(Checks.json(gatewayResponse("choice-valid")));
		assertThat(judge(choice()).judge(context()).status()).isEqualTo(JudgmentStatus.ERROR);
		response.set(fixture("choice-valid"));
		Judgment versioned = gatewayJudge(choice()).judge(context());
		assertThat(versioned.status()).isEqualTo(JudgmentStatus.FAIL);
		assertThat(trace(versioned).path("reportedModel").asText()).isEqualTo("jev-1.13.0");
		assertThat(trace(versioned).path("underlyingModelVersion").asText()).isEqualTo("1.13.0");
		assertThat(calls).hasValue(2);
	}

	@Test
	void configurationDigestIncludesExactEndpoint() {
		Judgment first = judge(NOUL).judge(context());
		URI alternate = URI.create("http://localhost:" + server.getAddress().getPort() + "/v1/systemone");
		Judgment second = new JevJudge("fake-key", "jev-1.13.0", alternate, Duration.ofSeconds(3), 16000, 32000, NOUL,
				http, this::capture)
			.judge(context());
		assertThat(second.status()).isEqualTo(JudgmentStatus.FAIL);
		assertThat(first.provenance().configurationDigest()).isNotEqualTo(second.provenance().configurationDigest());
	}

	@Test
	void rejectedEndpointSecretsNeverEnterCapturedProvenance() {
		for (String url : List.of("https://sentinel-secret@ai-gateway.vercel.sh/typesafe/v1/systemone",
				"https://ai-gateway.vercel.sh/typesafe/v1/systemone?api_key=sentinel-secret")) {
			Judgment j = new JevJudge("fake-key", "typesafe-ai/jev", URI.create(url), Duration.ofSeconds(1), 16000,
					32000, choice(), http, this::capture)
				.judge(context());
			assertThat(j.status()).isEqualTo(JudgmentStatus.ERROR);
			assertThat(trace(j).has("endpoint")).isFalse();
			assertThat(trace(j).path("route").asText()).isEqualTo("unvalidated");
			for (byte[] bytes : captured.values())
				assertThat(new String(bytes, StandardCharsets.UTF_8)).doesNotContain("sentinel-secret");
		}
		assertThat(calls).hasValue(0);
	}

	@ParameterizedTest
	@ValueSource(ints = { 0, 64000, 65000 })
	void boundedMetadataNeverErasesValidAssessmentOrProvenance(int padding) throws Exception {
		ObjectNode body = gatewayResponse("choice-valid");
		((ObjectNode) body.path("provider_metadata")).put("padding", "x".repeat(padding));
		response.set(Checks.json(body));
		assertThat(response.get().length).isLessThanOrEqualTo(65536);
		Judgment j = new JevJudge("fake-key", "typesafe-ai/jev", gatewayEndpoint(), Duration.ofSeconds(3), 24576, 65536,
				choice(), http, this::capture)
			.judge(context());
		assertThat(j.status()).isEqualTo(JudgmentStatus.FAIL);
		assertThat(j.provenance()).isNotNull();
		assertThat(j.assessment().category().selected()).isEqualTo("violated");
		assertThat(j.certainty().value()).isEqualTo(.8);
		assertThat(j.distribution().masses()).contains(new ProbabilityMass("violated", .9));
		assertThat(captured.get(j.provenance().response().id())).isEqualTo(response.get());
		assertThat(calls).hasValue(1);
		assertThat(Checks.JSON.readValue(Checks.json(j), Judgment.class)).isEqualTo(j);
		for (byte[] artifact : captured.values())
			assertThat(artifact.length).isLessThanOrEqualTo(65536);
		assertThat(artifact(j, "trace").length).isLessThan(4096);
		assertThat(providerMetadata(j)).isEqualTo(body.path("provider_metadata"));
	}

	JsonNode providerMetadata(Judgment judgment) throws Exception {
		ArtifactRef metadata = Checks.JSON.treeToValue(trace(judgment).path("providerMetadata"), ArtifactRef.class);
		assertThat(metadata.id()).isEqualTo(judgment.provenance().response().id());
		assertThat(metadata.sha256()).isEqualTo(judgment.provenance().response().sha256());
		assertThat(metadata.selector()).isEqualTo("/provider_metadata");
		byte[] original = captured.get(metadata.id());
		assertThat(ArtifactRef.ofBytes(metadata.id(), original, null).sha256()).isEqualTo(metadata.sha256());
		return Checks.parse(original).at(metadata.selector());
	}

	@ParameterizedTest
	@ValueSource(strings = { "confidence", "answers" })
	void nearLimitInvalidAnswerKeepsExactDiagnosticResponseAndProvenance(String missing) throws Exception {
		ObjectNode body = gatewayResponse("choice-valid");
		((ObjectNode) body.path("provider_metadata")).put("padding", "x".repeat(65000));
		if (missing.equals("answers"))
			body.remove("answers");
		else
			((ObjectNode) body.at("/answers/q")).remove("confidence");
		response.set(Checks.json(body));
		assertThat(response.get().length).isLessThanOrEqualTo(65536);
		Judgment j = new JevJudge("fake-key", "typesafe-ai/jev", gatewayEndpoint(), Duration.ofSeconds(3), 24576, 65536,
				choice(), http, this::capture)
			.judge(context());
		assertThat(j.status()).isEqualTo(JudgmentStatus.ERROR);
		assertThat(j.provenance()).isNotNull();
		assertThat(j.assessment()).isNull();
		assertThat(j.certainty()).isNull();
		assertThat(j.distribution()).isNull();
		assertThat(captured.get(j.provenance().response().id())).isEqualTo(response.get());
		assertThat(providerMetadata(j)).isEqualTo(body.path("provider_metadata"));
		assertThat(artifact(j, "trace").length).isLessThan(4096);
		assertThat(Checks.JSON.readValue(Checks.json(j), Judgment.class)).isEqualTo(j);
		assertThat(calls).hasValue(1);
	}

	@Test
	void explicitNullProviderMetadataRetainsAResolvableReference() throws Exception {
		ObjectNode body = gatewayResponse("choice-valid");
		body.putNull("provider_metadata");
		response.set(Checks.json(body));
		Judgment j = gatewayJudge(choice()).judge(context());
		assertThat(j.status()).isEqualTo(JudgmentStatus.FAIL);
		assertThat(trace(j).has("providerMetadata")).isTrue();
		assertThat(providerMetadata(j).isNull()).isTrue();
		assertThat(captured.get(j.provenance().response().id())).isEqualTo(response.get());
	}

}
