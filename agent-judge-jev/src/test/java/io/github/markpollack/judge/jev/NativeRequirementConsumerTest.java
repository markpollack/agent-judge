/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jev;

import io.github.markpollack.judge.ai.model.JudgeMessage;
import io.github.markpollack.judge.ai.model.JudgeMessageRole;
import io.github.markpollack.judge.ai.model.JudgeModel;
import io.github.markpollack.judge.ai.model.JudgeModelOptions;
import io.github.markpollack.judge.ai.model.JudgeModelRequest;
import io.github.markpollack.judge.ai.model.JudgeModelResponse;
import io.github.markpollack.judge.ai.requirements.EarsCriterion;
import io.github.markpollack.judge.ai.requirements.Rfc2119Constraint;
import io.github.markpollack.judge.requirement.Requirement;
import io.github.markpollack.judge.ai.requirements.*;
import io.github.markpollack.judge.evaluation.Evaluations;
import io.github.markpollack.judge.requirement.RequirementSource;
import io.github.markpollack.judge.provenance.ArtifactRef;
import io.github.markpollack.judge.judgment.Judgment;
import io.github.markpollack.judge.judgment.JudgmentStatus;
import io.github.markpollack.judge.verdict.Verdict;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

/** Actual public native producers with local protocol fixtures only. */
class NativeRequirementConsumerTest {

	private static final String SELECTED_EVIDENCE = "Source.java:4 acquires Pet before Owner; no other context selected.";

	private static final Rfc2119Requirement RFC = Rfc2119Requirement.of("RULE-4", "revision-7", "MUST",
			"Acquire Owner before Pet", "Prevent lock-order inversion", "Persistence exists");

	private static final EarsRequirement EARS = EarsRequirement.of("UC6-AC8", "revision-7",
			"Reject cancellation at start", "When cancellation is requested at the start, the system shall reject it.",
			"Appointments exist");

	private static ArtifactRef ref(String id, String content) {
		return ArtifactRef.ofBytes(id, content.getBytes(UTF_8), null);
	}

	private static JevEvidence evidence(String render) {
		return new JevEvidence(SELECTED_EVIDENCE, ref("selected:bundle", SELECTED_EVIDENCE),
				ref("selected:manifest", "Synthetic protocol fixture; no sufficiency claim"),
				ref("rendered:requirement", render).sha256(), false);
	}

	@Test
	void rfcNativeSpecificationReachesBothProvidersThroughActualInvocationInput() {
		try (var transport = new FixtureHttp()) {
			var artifacts = new LinkedHashMap<String, byte[]>();
			var nativeRuntime = jev(transport, artifacts).rendering(Rfc2119Specification::asPrompt);
			var actual = Rfc2119Judge.builder()
				.runtime(nativeRuntime)
				.requirement(RFC)
				.evidence(evidence(RFC.specification().asPrompt()))
				.build()
				.judge();
			assertThat(actual.status()).isEqualTo(JudgmentStatus.FAIL);
			assertThat(actual.requirement()).isSameAs(RFC);
			assertThat(actual.invocations()).hasSize(1);
			assertThat(actual.probabilityDistribution()).isNotNull();
			assertThat(requestRequirement(artifacts)).isEqualTo(RFC.specification().asPrompt());
			assertThat(transport.calls).hasValue(1);
			JudgeModel model = request -> {
				assertThat(request.messages().getFirst().content()).contains(RFC.id(), RFC.specification().asPrompt(),
						SELECTED_EVIDENCE);
				return new JudgeModelResponse("RULE-4: FAIL - Source.java:4 violates", "fixture-model", null, Map.of());
			};
			var generated = Rfc2119Judge.builder()
				.runtime(model)
				.requirement(RFC)
				.evidence(SELECTED_EVIDENCE)
				.build()
				.judge();
			assertThat(generated.status()).isEqualTo(JudgmentStatus.FAIL);
			assertThat(generated.requirement()).isSameAs(RFC);
			assertThat(generated.invocations()).hasSize(1);
		}
	}

	@Test
	void earsTitleSentenceAndApplicabilityReachBothProviders() {
		try (var transport = new FixtureHttp()) {
			var artifacts = new LinkedHashMap<String, byte[]>();
			var runtime = jev(transport, artifacts).rendering(EarsSpecification::asPrompt);
			var result = EarsJudge.builder()
				.runtime(runtime)
				.requirement(EARS)
				.evidence(evidence(EARS.specification().asPrompt()))
				.build()
				.judge();
			assertThat(result.status()).isEqualTo(JudgmentStatus.FAIL);
			assertThat(result.requirement()).isSameAs(EARS);
			assertThat(requestRequirement(artifacts)).contains(EARS.specification().title(),
					EARS.specification().requirement(), EARS.specification().applicability());
			JudgeModel model = request -> new JudgeModelResponse("UC6-AC8: FAIL - Source.java:4 violates", null, null,
					Map.of());
			assertThat(EarsJudge.builder()
				.runtime(model)
				.requirement(EARS)
				.evidence(SELECTED_EVIDENCE)
				.build()
				.judge()
				.status()).isEqualTo(JudgmentStatus.FAIL);
		}
	}

	@Test
	void directJevAndRfcReuseNativeBoundaryWithoutAnotherDomainExecution() {
		try (var transport = new FixtureHttp()) {
			var runtime = jev(transport, new LinkedHashMap<>());
			var direct = JevJudge.builder()
				.runtime(runtime.rendering(Rfc2119Specification::asPrompt))
				.requirement(RFC)
				.evidence(evidence(RFC.specification().asPrompt()))
				.build()
				.judge();
			assertThat(direct.status()).isEqualTo(JudgmentStatus.FAIL);
			assertThat(direct.requirement()).isSameAs(RFC);
			assertThat(transport.calls).hasValue(1);
		}
	}

	@Test
	void eachInvocationRendersOnceAndNeverRebindsStaleEvidence() {
		var calls = new AtomicInteger();
		try (var transport = new FixtureHttp()) {
			var runtime = jev(transport, new LinkedHashMap<>()).<Rfc2119Specification>rendering(spec -> {
				calls.incrementAndGet();
				return spec.asPrompt();
			});
			var stale = evidence("Earlier rendered requirement");
			var ready = Rfc2119Judge.builder().runtime(runtime).requirement(RFC).evidence(stale).build();
			assertThat(ready.judge().status()).isEqualTo(JudgmentStatus.ERROR);
			assertThat(ready.judge().status()).isEqualTo(JudgmentStatus.ERROR);
			assertThat(calls).hasValue(2);
			assertThat(transport.calls).hasValue(0);
		}
	}

	@Test
	void freshAcquisitionOccursAtOperationBoundary() {
		var acquired = new AtomicInteger();
		try (var transport = new FixtureHttp()) {
			var ready = Rfc2119Judge.builder()
				.runtime(jev(transport, new LinkedHashMap<>()).rendering(Rfc2119Specification::asPrompt))
				.requirement(RFC)
				.evidenceSupplier(() -> {
					acquired.incrementAndGet();
					return evidence(RFC.specification().asPrompt());
				})
				.build();
			assertThat(acquired).hasValue(0);
			ready.judge();
			ready.judge();
			assertThat(acquired).hasValue(2);
			assertThat(transport.calls).hasValue(2);
		}
	}

	@Test
	void completionApplicabilityRequiresReasonAndNeverInventsSatisfaction() {
		for (var value : Map
			.of("NOT_APPLICABLE - no appointments", JudgmentStatus.NOT_APPLICABLE,
					"CANNOT_DETERMINE - evidence insufficient", JudgmentStatus.ABSTAIN,
					"FAIL - Source.java:4 permits cancellation", JudgmentStatus.FAIL)
			.entrySet()) {
			JudgeModel model = req -> new JudgeModelResponse(EARS.id() + ": " + value.getKey(), null, null, Map.of());
			assertThat(EarsJudge.builder().runtime(model).requirement(EARS).build().judge().status())
				.isEqualTo(value.getValue());
		}
	}

	@Test
	void unconditionalExclusionRetainsOriginalAndLocalRejection() {
		var unconditional = Rfc2119Requirement.of("RULE-4", "7", "MUST", "lock", "why", null);
		JudgeModel model = req -> new JudgeModelResponse("RULE-4: NOT_APPLICABLE - inconvenient", null, null, Map.of());
		var result = Rfc2119Jury.builder().runtime(model).requirements(List.of(unconditional)).build().vote();
		assertThat(result.conclusion()).isEqualTo(Verdict.Conclusion.INCONCLUSIVE);
		assertThat(result.compositeAttempts().getFirst().verdict().individual().getFirst().status())
			.isEqualTo(JudgmentStatus.NOT_APPLICABLE);
		assertThat(result.compositeAttempts().getFirst().verdict().seats().getFirst().rejection()).isNotNull();
	}

	@Test
	void malformedCompletionRemainsInstrumentFailureWithNativeAnswer() {
		for (String answer : List.of("RULE-4: NOT_APPLICABLE", "RULE-4: PROBABLY - maybe", "RULE-4: PASS")) {
			JudgeModel model = req -> new JudgeModelResponse(answer, "fixture", null, Map.of());
			var result = Rfc2119Judge.builder().runtime(model).requirement(RFC).build().judge();
			assertThat(result.status()).isEqualTo(JudgmentStatus.ERROR);
			assertThat(result.invocations().getFirst().nativeFacts()).containsEntry("text", answer);
		}
	}

	@Test
	void failedCompletionRetainsAttemptAndConfiguredRequirement() {
		JudgeModel model = req -> {
			throw new IllegalStateException("transport unavailable");
		};
		var result = Rfc2119Judge.builder().runtime(model).requirement(RFC).build().judge();
		assertThat(result.status()).isEqualTo(JudgmentStatus.ERROR);
		assertThat(result.requirement()).isSameAs(RFC);
		assertThat(result.invocations().getFirst().completed()).isFalse();
	}

	@Test
	void jevRosterCountsEveryNativeExecutionAndRoundTripsOriginalSignals() {
		try (var transport = new FixtureHttp()) {
			var artifacts = new LinkedHashMap<String, byte[]>();
			var second = Rfc2119Requirement.of("RULE-5", "revision-8", "SHOULD", "Retain source facts", "Auditability",
					null);
			var nativeRuntime = jev(transport, artifacts).rendering(Rfc2119Specification::asPrompt);
			var ready = Rfc2119Jury.builder()
				.runtime(nativeRuntime)
				.requirements(List.of(RFC, second))
				.evidenceByRequirement(Map.of(RFC.id(), evidence(RFC.specification().asPrompt()), second.id(),
						evidence(second.specification().asPrompt())))
				.build();
			assertThat(transport.calls).hasValue(0);
			var original = ready.vote();
			assertThat(transport.calls).hasValue(2);
			assertThat(original.conclusion()).isEqualTo(Verdict.Conclusion.FAIL);
			assertThat(io.github.markpollack.judge.verdict.InvocationRecords.of(original)).hasSize(2);
			for (var item : original.compositeAttempts()) {
				var answer = item.verdict().individual().getFirst();
				assertThat(answer.probabilityDistribution()).isNotNull();
				assertThat(answer.confidence()).isNotNull();
				assertThat(answer.provenance().response()).isNotNull();
				assertThat(answer.requirement()).isNotNull();
				assertThat(answer.invocationIds()).hasSize(1);
			}
			var codec = NativeRequirementCodecs.codec();
			var restored = codec.read(codec.write(original));
			assertThat(restored).isEqualTo(original);
			assertThat(transport.calls).hasValue(2);
		}
	}

	private static JevRuntime jev(FixtureHttp transport, Map<String, byte[]> artifacts) {
		return new JevRuntime("fixture-key", "jev-1.13.0", TransportTest.ENDPOINT, Duration.ofSeconds(1), 16000, 32000,
				JevJudgeTest.choice(), transport, (kind, bytes) -> {
					artifacts.put(kind, bytes.clone());
					return ArtifactRef.ofBytes("fixture:" + kind, bytes, null);
				});
	}

	private static String requestRequirement(Map<String, byte[]> artifacts) {
		return Checks.parse(artifacts.get("request")).path("state").path("requirement").asText();
	}

	private static final class FixtureHttp extends TransportTest.PendingHttp {

		@Override
		public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
				HttpResponse.BodyHandler<T> handler) {
			calls.incrementAndGet();
			var headers = HttpHeaders.of(Map.of("content-type", List.of("application/json")), (a, b) -> true);
			var subscriber = handler.apply(new HttpResponse.ResponseInfo() {
				@Override
				public int statusCode() {
					return 200;
				}

				@Override
				public HttpHeaders headers() {
					return headers;
				}

				@Override
				public HttpClient.Version version() {
					return HttpClient.Version.HTTP_1_1;
				}
			});
			subscriber.onSubscribe(new Flow.Subscription() {
				@Override
				public void request(long n) {
				}

				@Override
				public void cancel() {
				}
			});
			subscriber.onNext(List.of(ByteBuffer.wrap(JevJudgeTest.fixture("choice-valid"))));
			subscriber.onComplete();
			return subscriber.getBody()
				.thenApply(body -> (HttpResponse<T>) new FixtureResponse<>(request, headers, body))
				.toCompletableFuture();
		}

	}

	private record FixtureResponse<T>(HttpRequest request, HttpHeaders headers, T body) implements HttpResponse<T> {
		@Override
		public int statusCode() {
			return 200;
		}

		@Override
		public Optional<HttpResponse<T>> previousResponse() {
			return Optional.empty();
		}

		@Override
		public Optional<SSLSession> sslSession() {
			return Optional.empty();
		}

		@Override
		public URI uri() {
			return request.uri();
		}

		@Override
		public HttpClient.Version version() {
			return HttpClient.Version.HTTP_1_1;
		}
	}

}
