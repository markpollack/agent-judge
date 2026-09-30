/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.assertions;

import io.github.markpollack.judge.requirement.Requirement;

import com.sun.net.httpserver.HttpServer;
import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.requirement.RequirementEvidence;
import io.github.markpollack.judge.jev.JevEvidence;
import io.github.markpollack.judge.judgment.JudgmentStatus;
import io.github.markpollack.judge.jury.interpretation.ReadingSupport;
import io.github.markpollack.judge.jury.interpretation.RequirementOutcome;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class ConferenceAssertionTest {

	ConferenceFixture fixture;

	HttpServer server;

	HttpClient http;

	RequirementAssertions facade;

	Judge<RequirementEvidence<String, JevEvidence>> judge;

	final AtomicReference<String> choice = new AtomicReference<>("violated");

	final AtomicInteger calls = new AtomicInteger();

	final List<byte[]> requests = new ArrayList<>();

	Path output;

	@BeforeEach
	void setup(TestInfo info) throws Exception {
		fixture = new ConferenceFixture();
		output = Path.of("target", "conference-loopback", info.getTestMethod().orElseThrow().getName());
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/v1/systemone", exchange -> {
			calls.incrementAndGet();
			byte[] request = exchange.getRequestBody().readAllBytes();
			requests.add(request);
			String label = choice.get();
			Map<String, Double> probabilities = new LinkedHashMap<>();
			for (String s : List.of("satisfied", "violated", "insufficient_evidence"))
				probabilities.put(s, s.equals(label) ? .9 : .05);
			byte[] response = ConferenceFixture.JSON.writeValueAsBytes(Map.of(
					"model", "jev-1.13.0", "answers", Map.of("q", Map.of("type", "choice", "choice", label,
							"confidence", .8, "probabilities", probabilities)),
					"usage", Map.of("input_tokens", 120, "output_tokens", 12)));
			exchange.getResponseHeaders().add("x-typesafe-request-id", "FAKE-loopback-" + calls.get());
			exchange.sendResponseHeaders(200, response.length);
			exchange.getResponseBody().write(response);
			exchange.close();
		});
		server.start();
		http = HttpClient.newHttpClient();
		facade = new RequirementAssertions(fixture.binding);
		judge = fixture.bind(fixture.judge("FAKE-LOCAL-KEY",
				URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/systemone"), http, output));
	}

	@AfterEach
	void close() {
		server.stop(0);
		http.close();
	}

	AssertionResult evaluate(JevEvidence evidence, Requirement<String> requirement) {
		return facade.evaluateRequirement(requirement, judge, evidence, null);
	}

	private void satisfies(JevEvidence evidence, Requirement<String> requirement) {
		RequirementAssertions.requireSatisfied(evaluate(evidence, requirement));
	}

	@Test
	void canonicalNamedRequirement() throws Exception {
		JevEvidence evidence = fixture.evidence(0);
		Requirement<String> requirement = fixture.requirement(0);
		var failure = assertThrows(RequirementAssertionError.Rejected.class, () -> {
			satisfies(evidence, requirement);
		});
		ConferenceFixture.save(failure.result(), output, "FAKE loopback; source-review expectation separate");
		assertEquals(ReadingSupport.SUPPORTED, failure.interpretation().readingSupport());
		assertEquals(RequirementOutcome.VIOLATED, failure.interpretation().outcome());
		assertEquals(1, calls.get());
		verifyRequest(evidence);
		assertSame(failure.result().verdict().judgment(), failure.result().verdict().individual().getFirst());
	}

	@Test
	void sameDisplayTextDoesNotSubstituteReviewedIdentity() throws Exception {
		var evidence = fixture.evidence(0);
		var requirement = Requirement.text("unregistered", "1", fixture.requirement(0).text());
		var failure = assertThrows(RequirementAssertionError.InstrumentFailure.class,
				() -> satisfies(evidence, requirement));
		assertEquals(JudgmentStatus.ERROR, failure.result().verdict().judgment().status());
		assertEquals(0, calls.get());
	}

	@Test
	void unchangedAc8Positive() throws Exception {
		choice.set("satisfied");
		JevEvidence evidence = fixture.evidence(1);
		Requirement<String> requirement = fixture.requirement(1);
		satisfies(evidence, requirement);
		// The second local call exposes the positive audit result; neither is live
		// inference.
		var result = evaluate(evidence, requirement);
		ConferenceFixture.save(result, output, "FAKE loopback; source-review expectation separate");
		assertEquals(RequirementOutcome.SATISFIED, result.interpretation().outcome());
		assertEquals(2, calls.get());
		verifyRequest(evidence);
	}

	@Test
	void nativeInsufficientLabelRemainsUnsuccessfulAbstention() throws Exception {
		choice.set("insufficient_evidence");
		var failure = assertThrows(RequirementAssertionError.Inconclusive.class,
				() -> satisfies(fixture.evidence(0), fixture.requirement(0)));
		ConferenceFixture.save(failure.result(), output, "FAKE loopback insufficient control");
		assertEquals(JudgmentStatus.ABSTAIN, failure.result().verdict().judgment().producerStatus());
		assertEquals(1, calls.get());
	}

	@Test
	void changingRequirementDoesNotTransferExactSufficiency() throws Exception {
		var original = fixture.evidence(0);
		var failure = assertThrows(RequirementAssertionError.InstrumentFailure.class,
				() -> satisfies(original, fixture.requirement(1)));
		assertEquals(JudgmentStatus.ERROR, failure.result().verdict().judgment().status());
		assertEquals(0, calls.get());
		assertThrows(RequirementAssertionError.InstrumentFailure.class, () -> satisfies(original,
				Requirement.text(fixture.requirement(0).id(), "2", fixture.requirement(0).text())));
		assertEquals(0, calls.get());
	}

	@Test
	void preInterruptedCallerMakesZeroHttpCalls() throws Exception {
		JevEvidence evidence = fixture.evidence(0);
		try {
			Thread.currentThread().interrupt();
			var failure = assertThrows(RequirementAssertionError.InstrumentFailure.class,
					() -> satisfies(evidence, fixture.requirement(0)));
			assertEquals(JudgmentStatus.ERROR, failure.result().verdict().judgment().status());
			assertEquals(0, calls.get());
			assertTrue(Thread.currentThread().isInterrupted());
		}
		finally {
			Thread.interrupted();
		}
	}

	private void verifyRequest(JevEvidence evidence) throws Exception {
		for (byte[] bytes : requests) {
			var request = ConferenceFixture.JSON.readTree(bytes);
			var state = request.path("state");
			assertEquals(evidence.requirementSha256(),
					ConferenceFixture.sha(state.path("requirement").asText().getBytes(StandardCharsets.UTF_8)));
			assertEquals(evidence.text(), state.path("evidence").asText());
			assertEquals(2, state.size());
			String body = new String(bytes, StandardCharsets.UTF_8);
			for (String excluded : List.of("UNSELECTED-AGENT-OUTPUT", "reviewedSubjectTruth", "reviewBasis",
					"FAKE-LOCAL-KEY", "expectedEvidenceDisposition"))
				assertFalse(body.contains(excluded), excluded);
		}
	}

}
