/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.assertions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import io.github.markpollack.judge.jury.Verdict;
import io.github.markpollack.judge.jury.interpretation.Verdicts;
import io.github.markpollack.judge.judgment.Judgment;
import io.github.markpollack.judge.judgment.JudgmentStatus;
import io.github.markpollack.judge.judgment.ProbabilityMass;
import io.github.markpollack.judge.acceptance.Policies;
import io.github.markpollack.judge.provenance.ArtifactRef;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Reuses the unchanged literal conference assertions against the explicit gateway route.
 */
class VercelConferenceAssertionTest extends ConferenceAssertionTest {

	Consumer<ObjectNode> changeResponse = body -> {
	};

	byte[] lastResponse;

	@Override
	@BeforeEach
	void setup(TestInfo info) throws Exception {
		fixture = new ConferenceFixture(true);
		output = Path.of("target", "vercel-conference-loopback", info.getTestMethod().orElseThrow().getName());
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/typesafe/v1/systemone", exchange -> {
			calls.incrementAndGet();
			requests.add(exchange.getRequestBody().readAllBytes());
			ObjectNode body = ConferenceFixture.JSON.createObjectNode();
			body.put("model", "typesafe-ai/jev");
			body.putObject("usage").put("input_tokens", 120).put("output_tokens", 12);
			var answer = body.putObject("answers").putObject("q");
			answer.put("type", "choice").put("choice", choice.get()).put("confidence", .8);
			var probabilities = answer.putObject("probabilities");
			for (String label : List.of("satisfied", "violated", "insufficient_evidence"))
				probabilities.put(label, label.equals(choice.get()) ? .9 : .05);
			var metadata = body.putObject("provider_metadata");
			metadata.putObject("typesafe").putObject("confidence").put("q", .123);
			var gateway = metadata.putObject("gateway");
			gateway.putObject("routing").put("resolvedProvider", "typesafe-ai").put("finalProvider", "typesafe-ai");
			gateway.put("generationId", "FAKE-generation").put("cost", "0").put("marketCost", "0.00001");
			changeResponse.accept(body);
			lastResponse = ConferenceFixture.JSON.writeValueAsBytes(body);
			exchange.getResponseHeaders().add("x-typesafe-request-id", "FAKE-vercel-loopback-" + calls.get());
			exchange.sendResponseHeaders(200, lastResponse.length);
			exchange.getResponseBody().write(lastResponse);
			exchange.close();
		});
		server.start();
		http = HttpClient.newHttpClient();
		facade = new RequirementAssertions(fixture.binding);
		judge = fixture.bind(fixture.judge("FAKE-LOCAL-KEY",
				URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/typesafe/v1/systemone"), http,
				output));
	}

	@ParameterizedTest
	@ValueSource(strings = { "satisfied", "violated", "insufficient_evidence" })
	void exactConferenceChoiceAndPortableProvenance(String label) throws Exception {
		choice.set(label);
		int index = label.equals("satisfied") ? 1 : 0;
		var evidence = fixture.evidence(index);
		var result = evaluate(evidence, fixture.requirement(index));
		Judgment j = result.verdict().judgment();
		assertEquals(label, j.finding().category().selected());
		assertEquals(.8, j.confidence().value());
		assertTrue(j.probabilityDistribution().masses().contains(new ProbabilityMass(label, .9)));
		assertEquals(1, calls.get());
		JsonNode request = ConferenceFixture.JSON.readTree(requests.getFirst());
		assertEquals("typesafe-ai/jev", request.path("model").asText());
		assertEquals(fixture.requirement(index).text(), request.at("/state/requirement").asText());
		assertEquals(evidence.text(), request.at("/state/evidence").asText());
		assertEquals(fixture.configuration.path("instructions"), request.at("/questions/q/instructions"));
		assertEquals(fixture.configuration.path("criteria"), request.at("/questions/q/criteria"));
		assertEquals("choice", request.at("/questions/q/type").asText());
		assertEquals(1, request.path("questions").size());
		assertEquals(2, request.path("state").size());
		assertTrue(requests.getFirst().length <= fixture.configuration.path("maxBodyBytes").asInt());
		assertArrayEquals(lastResponse, Files.readAllBytes(output.resolve(j.provenance().response().id())));
		JsonNode trace = trace(j);
		assertEquals(ConferenceFixture.JSON.readTree(lastResponse).path("provider_metadata"), providerMetadata(j));
		assertEquals("unknown", trace.path("underlyingModelVersion").asText());
		assertEquals("typesafe-ai/jev", trace.path("reportedModel").asText());
		assertEquals("vercel-typesafe", trace.path("route").asText());
		Verdict restored = ConferenceFixture.JSON.readValue(ConferenceFixture.JSON.writeValueAsBytes(result.verdict()),
				Verdict.class);
		assertEquals(result.verdict(), restored);
		assertEquals(result.interpretation(), Verdicts.interpret(restored));
		assertEquals(Policies.referenceOf(fixture.binding), result.policy());
		ConferenceFixture.save(result, output, "FAKE Vercel conference Choice " + label);
	}

	@ParameterizedTest
	@ValueSource(strings = { "confidence", "probabilities", "model", "usage" })
	void missingRequiredNativeFactsAreRetainedInstrumentErrors(String field) throws Exception {
		changeResponse = body -> {
			if (field.equals("model") || field.equals("usage"))
				body.remove(field);
			else
				((ObjectNode) body.at("/answers/q")).remove(field);
		};
		var result = evaluate(fixture.evidence(0), fixture.requirement(0));
		Judgment j = result.verdict().judgment();
		assertEquals(JudgmentStatus.ERROR, j.status());
		assertNull(j.finding());
		assertNull(j.confidence());
		assertNull(j.probabilityDistribution());
		assertThrows(RequirementAssertionError.InstrumentFailure.class,
				() -> RequirementAssertions.requireSatisfied(result));
		assertEquals(1, calls.get());
		assertArrayEquals(lastResponse, Files.readAllBytes(output.resolve(j.provenance().response().id())));
	}

	@Test
	void optionalProviderMetadataAbsenceIsNotInvented() throws Exception {
		changeResponse = body -> body.remove("provider_metadata");
		var result = evaluate(fixture.evidence(0), fixture.requirement(0));
		assertEquals(JudgmentStatus.FAIL, result.verdict().judgment().status());
		assertFalse(trace(result.verdict().judgment()).has("providerMetadata"));
		assertEquals("unknown", trace(result.verdict().judgment()).path("underlyingModelVersion").asText());
	}

	@Test
	void routingOverlayChangesOnlyRequestedModelAndIsIndependentlyBound() throws Exception {
		var original = new ConferenceFixture();
		ObjectNode restored = ((ObjectNode) fixture.configuration).deepCopy();
		restored.set("requestedModel", original.configuration.path("requestedModel"));
		assertEquals(original.configuration, restored);
		assertEquals(original.bindings, fixture.bindings);
		assertEquals(original.policy, fixture.policy);
		assertEquals(original.question, fixture.question);
		assertEquals("https://ai-gateway.vercel.sh/typesafe/v1/systemone", fixture.routing.path("endpoint").asText());
	}

	private JsonNode trace(Judgment judgment) throws Exception {
		ArtifactRef ref = judgment.provenance()
			.evidence()
			.stream()
			.filter(r -> r.id().startsWith("trace-"))
			.findFirst()
			.orElseThrow();
		return ConferenceFixture.JSON.readTree(Files.readAllBytes(output.resolve(ref.id())));
	}

	private JsonNode providerMetadata(Judgment judgment) throws Exception {
		ArtifactRef ref = ConferenceFixture.JSON.treeToValue(trace(judgment).path("providerMetadata"),
				ArtifactRef.class);
		assertEquals(judgment.provenance().response().id(), ref.id());
		assertEquals(judgment.provenance().response().sha256(), ref.sha256());
		assertEquals("/provider_metadata", ref.selector());
		byte[] original = Files.readAllBytes(output.resolve(ref.id()));
		assertEquals(ref.sha256(), ConferenceFixture.sha(original));
		return ConferenceFixture.JSON.readTree(original).at(ref.selector());
	}

}
