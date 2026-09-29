/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.assertions;

import io.github.markpollack.judge.requirement.Requirement;

import com.sun.net.httpserver.HttpServer;
import io.github.markpollack.judge.context.JudgmentContext;
import io.github.markpollack.judge.jev.JevEvidence;
import io.github.markpollack.judge.result.*;
import io.github.markpollack.judge.jury.interpretation.*;
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

	SemanticAssertions facade;

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
		facade = fixture.facade(fixture.judge("FAKE-LOCAL-KEY",
				URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/systemone"), http, output));
	}

	@AfterEach
	void close() {
		server.stop(0);
		http.close();
	}

	private SemanticAssertion assertThat(JudgmentContext evidence) {
		return facade.assertThat(evidence);
	}

	@Test
	void canonicalNamedRequirement() throws Exception {
		JudgmentContext evidence = fixture.context(0);
		Requirement<?> requirement = fixture.requirement(0);
		var failure = assertThrows(SemanticAssertionError.Rejected.class, () -> {
			assertThat(evidence).satisfies(requirement);
		});
		ConferenceFixture.save(failure.result(), output, "FAKE loopback; source-review expectation separate");
		assertEquals(ReadingSupport.SUPPORTED, failure.interpretation().readingSupport());
		assertEquals(VerdictReading.REJECTED, failure.interpretation().reading());
		assertEquals(1, calls.get());
		verifyRequest(evidence);
		assertSame(failure.result().verdict().aggregated(), failure.result().verdict().individual().getFirst());
	}

	@Test
	void canonicalStringRequirement() throws Exception {
		JudgmentContext evidence = fixture.context(0);
		var failure = assertThrows(SemanticAssertionError.Rejected.class, () -> {
			assertThat(evidence).satisfies("Repository locks are acquired in the required order");
		});
		ConferenceFixture.save(failure.result(), output, "FAKE loopback; source-review expectation separate");
		assertEquals(1, calls.get());
		verifyRequest(evidence);
		assertTrue(failure.result().requirement().id().startsWith("text:sha256:"));
	}

	@Test
	void unchangedAc8Positive() throws Exception {
		choice.set("satisfied");
		JudgmentContext evidence = fixture.context(1);
		Requirement<?> requirement = fixture.requirement(1);
		assertThat(evidence).satisfies(requirement);
		// The second local call exposes the positive audit result; neither is live
		// inference.
		var result = facade.evaluate(evidence, requirement);
		ConferenceFixture.save(result, output, "FAKE loopback; source-review expectation separate");
		assertEquals(VerdictReading.ACCEPTED, result.interpretation().reading());
		assertEquals(2, calls.get());
		verifyRequest(evidence);
	}

	@Test
	void nativeInsufficientLabelRemainsUnsuccessfulAbstention() throws Exception {
		choice.set("insufficient_evidence");
		var failure = assertThrows(SemanticAssertionError.Inconclusive.class,
				() -> assertThat(fixture.context(0)).satisfies(fixture.requirement(0)));
		ConferenceFixture.save(failure.result(), output, "FAKE loopback insufficient control");
		assertEquals(JudgmentStatus.ABSTAIN, failure.result().verdict().aggregated().producerStatus());
		assertEquals(1, calls.get());
	}

	@Test
	void changingGoalDoesNotTransferExactSufficiency() throws Exception {
		var original = fixture.context(0);
		var changed = JudgmentContext.builder()
			.goal(fixture.requirement(1).text())
			.metadata(original.metadata())
			.build();
		var failure = assertThrows(SemanticAssertionError.InstrumentFailure.class,
				() -> assertThat(changed).satisfies(fixture.requirement(1)));
		assertEquals(JudgmentStatus.ERROR, failure.result().verdict().aggregated().status());
		assertEquals(0, calls.get());
		assertThrows(IllegalArgumentException.class, () -> facade.evaluate(original,
				Requirement.text("RULE-4-lock-order", "2", fixture.requirement(0).text())));
		assertEquals(0, calls.get());
	}

	@Test
	void preInterruptedCallerMakesZeroHttpCalls() throws Exception {
		JudgmentContext evidence = fixture.context(0);
		try {
			Thread.currentThread().interrupt();
			var failure = assertThrows(SemanticAssertionError.InstrumentFailure.class,
					() -> assertThat(evidence).satisfies(fixture.requirement(0)));
			assertEquals(JudgmentStatus.ERROR, failure.result().verdict().aggregated().status());
			assertEquals(0, calls.get());
			assertTrue(Thread.currentThread().isInterrupted());
		}
		finally {
			Thread.interrupted();
		}
	}

	private void verifyRequest(JudgmentContext evidence) throws Exception {
		for (byte[] bytes : requests) {
			var request = ConferenceFixture.JSON.readTree(bytes);
			var state = request.path("state");
			assertEquals(evidence.goal(), state.path("requirement").asText());
			assertEquals(((JevEvidence) evidence.metadata().get(JevEvidence.CONTEXT_KEY)).text(),
					state.path("evidence").asText());
			assertEquals(2, state.size());
			String body = new String(bytes, StandardCharsets.UTF_8);
			for (String excluded : List.of("UNSELECTED-AGENT-OUTPUT", "reviewedSubjectTruth", "reviewBasis",
					"FAKE-LOCAL-KEY", "expectedEvidenceDisposition"))
				assertFalse(body.contains(excluded), excluded);
		}
	}

}
