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
import io.github.markpollack.judge.RequirementJudge;
import io.github.markpollack.judge.evaluation.Evaluations;
import io.github.markpollack.judge.requirement.RequirementSource;
import io.github.markpollack.judge.provenance.ArtifactRef;
import io.github.markpollack.judge.judgment.Judgment;
import io.github.markpollack.judge.judgment.JudgmentStatus;
import io.github.markpollack.judge.jury.Verdict;
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

/** Executable native-input adapters. Every provider answer is a local fixture. */
class NativeRequirementConsumerTest {

	private static final Rfc2119Constraint RFC = new Rfc2119Constraint("RULE-4", "MUST", "Acquire Owner before Pet",
			"Prevent lock-order inversion", "Persistence exists");

	private static final EarsCriterion EARS = new EarsCriterion("UC6-AC8", "Reject cancellation at start",
			"When cancellation is requested at the start, the system shall reject it.", "Appointments exist");

	private static final String SELECTED_EVIDENCE = "Source.java:4 acquires Pet before Owner; no other context selected.";

	@Test
	void rfcNativeSpecificationReachesBothProvidersThroughActualInvocationInput() {
		var actual = requirement(RFC, RFC.id());
		var model = new CapturingModel();
		var seen = new ArrayList<Requirement<Rfc2119Constraint>>();
		try (var transport = new FixtureHttp()) {
			var artifacts = new LinkedHashMap<String, byte[]>();
			var provider = jev(transport, artifacts).rendering(Rfc2119Constraint::asPrompt);
			RequirementJudge<Rfc2119Constraint, JevEvidence> observed = (requirement, evidence) -> {
				seen.add(requirement);
				return provider.judge(requirement, evidence);
			};
			var evidence = evidence(RFC.asPrompt());
			var nativeCompletion = completion(NativeRequirementConsumerTest::completionRfc,
					Rfc2119Constraint::applicability, model, Witness.APPLICABLE, seen);
			var first = Evaluations.evaluate(actual, observed, evidence);
			var second = Evaluations.evaluate(actual, nativeCompletion, evidence);
			assertThat(first.verdict().conclusion()).isEqualTo(Verdict.Conclusion.FAIL);
			assertThat(second.verdict().conclusion()).isEqualTo(Verdict.Conclusion.FAIL);
			assertThat(first.verdict().requirement()).isSameAs(actual);
			assertThat(seen).hasSize(2).allSatisfy(r -> assertThat(r).isSameAs(actual));
			assertThat(requestRequirement(artifacts)).isEqualTo(RFC.asPrompt());
			assertThat(model.text()).contains(actual.id(), actual.revision(), actual.source().artifact().sha256(),
					RFC.keyword(), RFC.requirement(), RFC.reason(), RFC.applicability());
			assertThat(Checks.parse(artifacts.get("request")).path("state").path("evidence").asText())
				.isEqualTo(SELECTED_EVIDENCE);
			assertThat(transport.calls).hasValue(1);
			assertThat(model.requests).hasSize(1);
		}
	}

	@Test
	void earsTitleSentenceAndApplicabilityReachBothProviders() {
		var actual = requirement(EARS, EARS.id());
		var model = new CapturingModel();
		try (var transport = new FixtureHttp()) {
			var artifacts = new LinkedHashMap<String, byte[]>();
			Function<EarsCriterion, String> render = s -> "Title: " + s.title() + "\n" + s.asPrompt();
			var provider = jev(transport, artifacts).rendering(render);
			var evidence = evidence(render.apply(EARS));
			assertThat(provider.judge(actual, evidence).status()).isEqualTo(JudgmentStatus.FAIL);
			var completion = completion(NativeRequirementConsumerTest::completionEars, EarsCriterion::applicability,
					model, Witness.APPLICABLE, new ArrayList<>());
			assertThat(completion.judge(actual, evidence).status()).isEqualTo(JudgmentStatus.FAIL);
			for (String field : List.of(EARS.title(), EARS.requirement(), EARS.applicability())) {
				assertThat(requestRequirement(artifacts)).contains(field);
				assertThat(model.text()).contains(field);
			}
		}
	}

	@Test
	void oneRendererUsesEachActualSpecificationWithoutASecondConfiguredRequirement() {
		var changed = new Rfc2119Constraint("RULE-5", "SHOULD", "Acquire Pet before Owner", "New rationale", "Always");
		try (var transport = new FixtureHttp()) {
			var artifacts = new LinkedHashMap<String, byte[]>();
			var provider = jev(transport, artifacts).rendering(Rfc2119Constraint::asPrompt);
			for (var spec : List.of(RFC, changed)) {
				var actual = requirement(spec, spec.id());
				assertThat(provider.judge(actual, evidence(spec.asPrompt())).status()).isEqualTo(JudgmentStatus.FAIL);
				assertThat(requestRequirement(artifacts)).isEqualTo(spec.asPrompt());
			}
			assertThat(transport.calls).hasValue(2);
		}
	}

	@Test
	void eachInvocationRendersOnceAndNeverRebindsStaleEvidence() {
		var actual = requirement(RFC, RFC.id());
		var renderCalls = new AtomicInteger();
		try (var transport = new FixtureHttp()) {
			var provider = jev(transport, new LinkedHashMap<>()).<Rfc2119Constraint>rendering(spec -> {
				renderCalls.incrementAndGet();
				return spec.asPrompt();
			});
			var stale = evidence("Earlier rendered requirement");
			String digest = stale.requirementSha256();
			assertThat(provider.judge(actual, stale).status()).isEqualTo(JudgmentStatus.ERROR);
			assertThat(provider.judge(actual, stale).status()).isEqualTo(JudgmentStatus.ERROR);
			assertThat(stale.requirementSha256()).isEqualTo(digest);
			assertThat(renderCalls).hasValue(2);
			assertThat(transport.calls).hasValue(0);
			assertThat(provider.judge(actual, evidence(RFC.asPrompt())).status()).isEqualTo(JudgmentStatus.FAIL);
			assertThat(renderCalls).hasValue(3);
			assertThat(transport.calls).hasValue(1);
		}
	}

	@Test
	void explicitApplicabilityPreflightRetainsExclusionAndUncertaintyWithoutProviderCalls() {
		var actual = requirement(RFC, RFC.id());
		try (var transport = new FixtureHttp()) {
			var provider = jev(transport, new LinkedHashMap<>()).rendering(Rfc2119Constraint::asPrompt);
			var excluded = preflight(provider, Rfc2119Constraint::applicability, Witness.EXCLUDED);
			var result = Evaluations.evaluate(actual, excluded, evidence(RFC.asPrompt()));
			assertThat(result.verdict().conclusion()).isEqualTo(Verdict.Conclusion.NOT_APPLICABLE);
			assertThat(result.verdict().judgment().finding()).isNull();
			assertThat(result.verdict().judgment().reasoning()).contains("No persistence");
			var unresolved = preflight(provider, Rfc2119Constraint::applicability, Witness.TO_BE_JUDGED);
			assertThat(unresolved.judge(actual, evidence(RFC.asPrompt())).status()).isEqualTo(JudgmentStatus.ABSTAIN);
			assertThat(transport.calls).hasValue(0);
		}
	}

	@Test
	void unconditionalSpecificationCannotBeExcluded() {
		var spec = new Rfc2119Constraint(RFC.id(), RFC.keyword(), RFC.requirement(), RFC.reason());
		var actual = requirement(spec, spec.id());
		try (var transport = new FixtureHttp()) {
			var provider = jev(transport, new LinkedHashMap<>()).rendering(Rfc2119Constraint::asPrompt);
			assertThat(preflight(provider, Rfc2119Constraint::applicability, Witness.EXCLUDED)
				.judge(actual, evidence(spec.asPrompt()))
				.status()).isEqualTo(JudgmentStatus.ERROR);
			assertThat(transport.calls).hasValue(0);
		}
	}

	@Test
	void completionApplicabilityRequiresAnAuditableReasonAndDoesNotInventSatisfaction() {
		var actual = requirement(EARS, EARS.id());
		var model = new CapturingModel();
		var provider = completion(NativeRequirementConsumerTest::completionEars, EarsCriterion::applicability, model,
				Witness.TO_BE_JUDGED, new ArrayList<>());
		model.answer = "NOT_APPLICABLE: Source.java:4 has no appointments";
		assertThat(provider.judge(actual, evidence(EARS.asPrompt())).status()).isEqualTo(JudgmentStatus.NOT_APPLICABLE);
		assertThat(model.text()).contains(EARS.applicability(), "TO_BE_JUDGED");
		model.answer = "INSUFFICIENT: evidence cannot establish appointment support";
		assertThat(provider.judge(actual, evidence(EARS.asPrompt())).status()).isEqualTo(JudgmentStatus.ABSTAIN);
		model.answer = "VIOLATED: Source.java:4 permits cancellation";
		assertThat(provider.judge(actual, evidence(EARS.asPrompt())).status()).isEqualTo(JudgmentStatus.FAIL);
	}

	@Test
	void completionExclusionCannotContradictApplicableWitness() {
		var model = new CapturingModel();
		model.answer = "NOT_APPLICABLE: claimed exclusion";
		var provider = completion(NativeRequirementConsumerTest::completionRfc, Rfc2119Constraint::applicability, model,
				Witness.APPLICABLE, new ArrayList<>());
		assertThat(provider.judge(requirement(RFC, RFC.id()), evidence(RFC.asPrompt())).status())
			.isEqualTo(JudgmentStatus.ERROR);
	}

	@Test
	void completionProtocolErrorsRemainInstrumentFailures() {
		var actual = requirement(RFC, RFC.id());
		var model = new CapturingModel();
		var provider = completion(NativeRequirementConsumerTest::completionRfc, Rfc2119Constraint::applicability, model,
				Witness.TO_BE_JUDGED, new ArrayList<>());
		for (String answer : List.of("NOT_APPLICABLE:", "NOT_APPLICABLE", "PROBABLY: maybe", "SATISFIED:")) {
			model.answer = answer;
			assertThat(provider.judge(actual, evidence(RFC.asPrompt())).status()).isEqualTo(JudgmentStatus.ERROR);
		}
		model.failure = true;
		assertThat(provider.judge(actual, evidence(RFC.asPrompt())).status()).isEqualTo(JudgmentStatus.ERROR);
	}

	private enum Witness {

		APPLICABLE, EXCLUDED, TO_BE_JUDGED

	}

	private static <S> RequirementJudge<S, JevEvidence> preflight(RequirementJudge<S, JevEvidence> delegate,
			Function<S, String> applicability, Witness witness) {
		return (requirement, evidence) -> {
			String condition = applicability.apply(requirement.specification());
			if (witness == Witness.EXCLUDED)
				return condition == null ? Judgment.error("Cannot exclude unconditional requirement")
						: Judgment.notApplicable("No persistence in selected source; " + condition + " does not hold");
			if (condition != null && witness == Witness.TO_BE_JUDGED)
				return Judgment.abstain("Unresolved applicability");
			return delegate.judge(requirement, evidence);
		};
	}

	private static <S> RequirementJudge<S, JevEvidence> completion(Function<S, String> render,
			Function<S, String> applicability, CapturingModel model, Witness witness, List<Requirement<S>> seen) {
		return (supplied, evidence) -> {
			seen.add(supplied);
			String condition = applicability.apply(supplied.specification());
			if (witness == Witness.EXCLUDED)
				return condition == null ? Judgment.error("Cannot exclude unconditional requirement")
						: Judgment.notApplicable("Declared condition is absent");
			String protocol = "Return SATISFIED, VIOLATED, INSUFFICIENT or NOT_APPLICABLE followed by colon and reason. "
					+ "NOT_APPLICABLE requires the declared condition to be false and a reason.";
			try {
				var response = model.generate(new JudgeModelRequest(
						List.of(new JudgeMessage(JudgeMessageRole.SYSTEM, protocol), new JudgeMessage(
								JudgeMessageRole.USER,
								"Application ID: " + supplied.id() + " Revision: " + supplied.revision() + " Source: "
										+ supplied.source() + "\n" + render.apply(supplied.specification())),
								new JudgeMessage(JudgeMessageRole.USER, evidence.text()),
								new JudgeMessage(JudgeMessageRole.USER, "Applicability witness: " + witness)),
						JudgeModelOptions.defaults(), Map.of()));
				String[] parts = response.text().split(":", 2);
				if (parts.length != 2 || parts[1].isBlank())
					return Judgment.error("Missing protocol reason");
				String reason = parts[1].strip();
				return switch (parts[0]) {
					case "SATISFIED" -> Judgment.pass(reason);
					case "VIOLATED" -> Judgment.fail(reason);
					case "INSUFFICIENT" -> Judgment.abstain(reason);
					case "NOT_APPLICABLE" -> condition != null && witness == Witness.TO_BE_JUDGED
							? Judgment.notApplicable(reason) : Judgment.error("Exclusion contradicts applicability");
					default -> Judgment.error("Unknown protocol answer");
				};
			}
			catch (RuntimeException ex) {
				return Judgment.error("Completion instrument failed");
			}
		};
	}

	private static <S> Requirement<S> requirement(S specification, String nativeId) {
		return new Requirement<>("scheduling/" + nativeId, "revision-7", "Display sentence", specification,
				new RequirementSource(ref("specification:source", specification.toString()), nativeId));
	}

	private static ArtifactRef ref(String id, String content) {
		return ArtifactRef.ofBytes(id, content.getBytes(UTF_8), null);
	}

	private static JevEvidence evidence(String renderedRequirement) {
		return new JevEvidence(SELECTED_EVIDENCE, ref("selected:bundle", SELECTED_EVIDENCE),
				ref("selected:manifest", "Synthetic protocol fixture; no sufficiency claim"),
				ref("rendered:requirement", renderedRequirement).sha256(), false);
	}

	private static String jevIdentity(Requirement<?> requirement) {
		return "Requirement " + requirement.id() + " revision " + requirement.revision() + "\nSource "
				+ requirement.source().artifact().id() + " sha256 " + requirement.source().artifact().sha256() + "\n";
	}

	private static String completionRfc(Rfc2119Constraint specification) {
		return "RFC2119 document ID: " + specification.id() + "\nKeyword: " + specification.keyword() + "\nConstraint: "
				+ specification.requirement() + "\nRationale: " + specification.reason() + "\nApplicability: "
				+ specification.applicability();
	}

	private static String completionEars(EarsCriterion specification) {
		return "EARS document ID: " + specification.id() + "\nHeading: " + specification.title()
				+ "\nVerbatim sentence: " + specification.requirement() + "\nApplicability: "
				+ specification.applicability();
	}

	private static JevJudge jev(FixtureHttp transport, Map<String, byte[]> artifacts) {
		return new JevJudge("fixture-key", "jev-1.13.0", TransportTest.ENDPOINT, Duration.ofSeconds(1), 16000, 32000,
				JevJudgeTest.choice(), transport, (kind, bytes) -> {
					artifacts.put(kind, bytes.clone());
					return ArtifactRef.ofBytes("fixture:" + kind, bytes, null);
				});
	}

	private static String requestRequirement(Map<String, byte[]> artifacts) {
		return Checks.parse(artifacts.get("request")).path("state").path("requirement").asText();
	}

	private static final class CapturingModel implements JudgeModel {

		final List<JudgeModelRequest> requests = new ArrayList<>();

		String answer = "VIOLATED: Source.java:4 violates the supplied requirement";

		boolean failure;

		@Override
		public JudgeModelResponse generate(JudgeModelRequest request) {
			requests.add(request);
			if (failure)
				throw new IllegalStateException("Fixture backend failure");
			return new JudgeModelResponse(answer, "fixture-model", null, Map.of());
		}

		String text() {
			return requests.getLast().messages().stream().map(JudgeMessage::content).reduce("", (a, b) -> a + "\n" + b);
		}

	}

	/** Injects captured bytes without opening a socket; honors the SDK's body handler. */
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
