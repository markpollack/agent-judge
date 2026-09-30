/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jev;

import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.JudgeMetadata;
import io.github.markpollack.judge.JudgeType;
import io.github.markpollack.judge.JudgeWithMetadata;
import io.github.markpollack.judge.ai.model.JudgeMessage;
import io.github.markpollack.judge.ai.model.JudgeMessageRole;
import io.github.markpollack.judge.ai.model.JudgeModel;
import io.github.markpollack.judge.ai.model.JudgeModelOptions;
import io.github.markpollack.judge.ai.model.JudgeModelRequest;
import io.github.markpollack.judge.ai.model.JudgeModelResponse;
import io.github.markpollack.judge.ai.requirements.EarsCriterion;
import io.github.markpollack.judge.ai.requirements.Rfc2119Constraint;
import io.github.markpollack.judge.jury.AllMustPassStrategy;
import io.github.markpollack.judge.jury.ErrorPolicy;
import io.github.markpollack.judge.jury.NotApplicablePolicy;
import io.github.markpollack.judge.jury.SimpleJury;
import io.github.markpollack.judge.requirement.Requirement;
import io.github.markpollack.judge.requirement.RequirementEvidence;
import io.github.markpollack.judge.requirement.RequirementSource;
import io.github.markpollack.judge.result.ArtifactRef;
import io.github.markpollack.judge.result.Judgment;
import io.github.markpollack.judge.result.JudgmentStatus;
import io.github.markpollack.judge.jury.Verdict;
import io.github.markpollack.judge.jury.interpretation.VerdictReading;
import io.github.markpollack.judge.jury.interpretation.Verdicts;
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

/**
 * Executable application adapters, not a shared rendering or applicability API. Both
 * providers receive the very same typed envelope; every provider response is a fixture.
 */
class NativeRequirementConsumerTest {

	private static final Rfc2119Constraint RFC = new Rfc2119Constraint("RULE-4", "MUST",
			"Acquire Owner before Pet", "Prevent lock-order inversion", "Persistence exists");

	private static final EarsCriterion EARS = new EarsCriterion("UC6-AC8", "Reject cancellation at start",
			"When cancellation is requested at the start, the system shall reject it.", "Appointments exist");

	private static final String SELECTED_EVIDENCE = "Source.java:4 acquires Pet before Owner; no other context selected.";

	@Test
	void rfcNativeEnvelopeReachesBothConsumersWithoutACommonRendering() {
		var requirement = requirement(RFC, RFC.id());
		String rendered = jevIdentity(requirement) + RFC.asPrompt();
		var pair = new RequirementEvidence<>(requirement, evidence(rendered));
		var model = new CapturingModel();
		var seen = new ArrayList<RequirementEvidence<Requirement<Rfc2119Constraint>, JevEvidence>>();
		try (var transport = new FixtureHttp()) {
			var artifacts = new LinkedHashMap<String, byte[]>();
			var bound = jev(transport, artifacts).bind(requirement, spec -> jevIdentity(requirement) + spec.asPrompt());
			Judge<RequirementEvidence<Requirement<Rfc2119Constraint>, JevEvidence>> jevConsumer = input -> {
				seen.add(input);
				return bound.judge(input);
			};
			var completion = completion(requirement, RFC.applicability(), NativeRequirementConsumerTest::completionRfc,
					model, Witness.APPLICABLE, seen);
			assertThat(jevConsumer.judge(pair).status()).isEqualTo(JudgmentStatus.FAIL);
			assertThat(completion.judge(pair).status()).isEqualTo(JudgmentStatus.FAIL);
			assertThat(seen).hasSize(2).allSatisfy(input -> {
				assertThat(input).isSameAs(pair);
				assertThat(input.requirement()).isSameAs(requirement);
				assertThat(input.requirement().specification()).isSameAs(RFC);
				assertThat(input.evidence()).isSameAs(pair.evidence());
			});
			String request = requestRequirement(artifacts);
			assertThat(request).isEqualTo(rendered).isNotEqualTo(model.requests.getFirst().messages().get(1).content());
			for (String field : List.of(requirement.id(), requirement.revision(), requirement.source().artifact().id(),
					requirement.source().artifact().sha256(), RFC.id(), RFC.keyword(), RFC.requirement(), RFC.reason(),
					RFC.applicability())) {
				assertThat(request).contains(field);
				assertThat(model.text()).contains(field);
			}
			assertThat(Checks.parse(artifacts.get("request")).path("state").path("evidence").asText())
				.isEqualTo(SELECTED_EVIDENCE);
			assertThat(model.requests.getFirst().messages().get(2).content()).isEqualTo(SELECTED_EVIDENCE);
			assertThat(transport.calls).hasValue(1);
			assertThat(model.requests).hasSize(1);
		}
	}

	@Test
	void earsNativeTitleSentenceAndApplicabilityReachBothConsumers() {
		var requirement = requirement(EARS, EARS.id());
		String rendered = jevIdentity(requirement) + "Title: " + EARS.title() + "\n" + EARS.asPrompt();
		var pair = new RequirementEvidence<>(requirement, evidence(rendered));
		var model = new CapturingModel();
		var seen = new ArrayList<RequirementEvidence<Requirement<EarsCriterion>, JevEvidence>>();
		try (var transport = new FixtureHttp()) {
			var artifacts = new LinkedHashMap<String, byte[]>();
			var bound = jev(transport, artifacts).bind(requirement,
					spec -> jevIdentity(requirement) + "Title: " + spec.title() + "\n" + spec.asPrompt());
			var completion = completion(requirement, EARS.applicability(), NativeRequirementConsumerTest::completionEars,
					model, Witness.APPLICABLE, seen);
			assertThat(bound.judge(pair).status()).isEqualTo(JudgmentStatus.FAIL);
			assertThat(completion.judge(pair).status()).isEqualTo(JudgmentStatus.FAIL);
			assertThat(seen.getFirst()).isSameAs(pair);
			assertThat(pair.requirement().specification()).isSameAs(EARS);
			for (String field : List.of(requirement.id(), requirement.revision(), requirement.source().artifact().id(),
					requirement.source().artifact().sha256(), EARS.id(), EARS.title(), EARS.requirement(), EARS.applicability())) {
				assertThat(requestRequirement(artifacts)).contains(field);
				assertThat(model.text()).contains(field);
			}
			assertThat(transport.calls).hasValue(1);
			assertThat(model.requests).hasSize(1);
		}
	}

	@Test
	void sameDisplaySentenceCannotHideRfcSemanticChanges() {
		var requirement = requirement(RFC, RFC.id());
		var variants = List.of(new Rfc2119Constraint("RULE-5", RFC.keyword(), RFC.requirement(), RFC.reason(), RFC.applicability()),
				new Rfc2119Constraint(RFC.id(), "SHOULD", RFC.requirement(), RFC.reason(), RFC.applicability()),
				new Rfc2119Constraint(RFC.id(), RFC.keyword(), RFC.requirement(), "Different rationale", RFC.applicability()),
				new Rfc2119Constraint(RFC.id(), RFC.keyword(), RFC.requirement(), RFC.reason(), "Always"));
		assertNativeMismatches(requirement, variants, Rfc2119Constraint::asPrompt, NativeRequirementConsumerTest::completionRfc);
	}

	@Test
	void sameDisplaySentenceCannotHideEarsTitleOrApplicabilityChanges() {
		var requirement = requirement(EARS, EARS.id());
		var variants = List.of(new EarsCriterion("UC6-AC9", EARS.title(), EARS.requirement(), EARS.applicability()),
				new EarsCriterion(EARS.id(), "Different title", EARS.requirement(), EARS.applicability()),
				new EarsCriterion(EARS.id(), EARS.title(), EARS.requirement(), "Always"));
		assertNativeMismatches(requirement, variants, EarsCriterion::asPrompt, NativeRequirementConsumerTest::completionEars);
	}

	@Test
	void identityRevisionAndSourceChangesAreRejectedBeforeEitherProvider() {
		var original = requirement(RFC, RFC.id());
		var variants = List.of(new Requirement<>("another/requirement", original.revision(), original.text(), RFC, original.source()),
				new Requirement<>(original.id(), "revision-8", original.text(), RFC, original.source()),
				new Requirement<>(original.id(), original.revision(), original.text(), RFC,
						new RequirementSource(ref("source:changed", "changed source bytes"), RFC.id())),
				new Requirement<>(original.id(), original.revision(), original.text(), RFC,
						new RequirementSource(original.source().artifact(), "different-native-id")));
		try (var transport = new FixtureHttp()) {
			var bound = jev(transport, new LinkedHashMap<>()).bind(original, Rfc2119Constraint::asPrompt);
			var model = new CapturingModel();
			var completion = completion(original, RFC.applicability(), NativeRequirementConsumerTest::completionRfc,
					model, Witness.APPLICABLE, new ArrayList<>());
			for (var variant : variants) {
				var pair = new RequirementEvidence<>(variant, evidence(RFC.asPrompt()));
				assertThat(bound.judge(pair).status()).isEqualTo(JudgmentStatus.ERROR);
				assertThat(completion.judge(pair).status()).isEqualTo(JudgmentStatus.ERROR);
			}
			assertThat(transport.calls).hasValue(0);
			assertThat(model.requests).isEmpty();
		}
	}

	@Test
	void providerRenderingIsCapturedOnceAndStaleEvidenceIsNeverRebound() {
		var requirement = requirement(RFC, RFC.id());
		AtomicInteger renderCalls = new AtomicInteger();
		try (var transport = new FixtureHttp()) {
			var bound = jev(transport, new LinkedHashMap<>()).bind(requirement, spec -> {
				renderCalls.incrementAndGet();
				assertThat(spec).isSameAs(RFC);
				return spec.asPrompt();
			});
			var stale = evidence("An earlier provider rendering");
			String originalDigest = stale.requirementSha256();
			var pair = new RequirementEvidence<>(requirement, stale);
			assertThat(bound.judge(pair).status()).isEqualTo(JudgmentStatus.ERROR);
			assertThat(bound.judge(pair).status()).isEqualTo(JudgmentStatus.ERROR);
			assertThat(stale.requirementSha256()).isEqualTo(originalDigest);
			assertThat(transport.calls).hasValue(0);
			assertThat(renderCalls).hasValue(1);
			assertThat(bound.judge(new RequirementEvidence<>(requirement, evidence(RFC.asPrompt()))).status())
				.isEqualTo(JudgmentStatus.FAIL);
			assertThat(renderCalls).hasValue(1);
			assertThat(transport.calls).hasValue(1);
		}
	}

	@Test
	void explicitExclusionIsRetainedBeforeJevAndSurvivesTheJuryCapabilityGuard() {
		var requirement = requirement(RFC, RFC.id());
		var pair = new RequirementEvidence<>(requirement, evidence(RFC.asPrompt()));
		try (var transport = new FixtureHttp()) {
			var bound = jev(transport, new LinkedHashMap<>()).bind(requirement, Rfc2119Constraint::asPrompt);
			var excluded = preflight(requirement, bound, RFC.applicability(), Witness.EXCLUDED);
			Verdict verdict = jury(excluded).vote(pair);
			assertThat(verdict.aggregated().status()).isEqualTo(JudgmentStatus.NOT_APPLICABLE);
			assertThat(verdict.individual()).hasSize(1);
			assertThat(verdict.individual().getFirst().producerStatus()).isEqualTo(JudgmentStatus.NOT_APPLICABLE);
			assertThat(verdict.individual().getFirst().assessment()).isNull();
			assertThat(verdict.individual().getFirst().reasoning()).contains("No persistence in selected source");
			assertThat(verdict.seats().getFirst().verdictKey()).isEqualTo("preflight");
			assertThat(Verdicts.interpret(verdict).reading()).isEqualTo(VerdictReading.NOT_APPLICABLE);
			assertThat(transport.calls).hasValue(0);
			var unconditional = new Rfc2119Constraint(RFC.id(), RFC.keyword(), RFC.requirement(), RFC.reason());
			var changed = new Requirement<>(requirement.id(), requirement.revision(), requirement.text(), unconditional,
					requirement.source());
			assertThat(excluded.judge(new RequirementEvidence<>(changed, pair.evidence())).status())
				.isEqualTo(JudgmentStatus.ERROR);
			// Removing the declaration is observable: the real engine rejects the same exclusion.
			Judge<RequirementEvidence<Requirement<Rfc2119Constraint>, JevEvidence>> undeclared = excluded::judge;
			assertThat(jury(undeclared).vote(pair).aggregated().status()).isEqualTo(JudgmentStatus.ERROR);
			assertThat(transport.calls).hasValue(0);
		}
	}

	@Test
	void boundedJevRequiresApplicabilityToBeResolvedBeforeItsThreeWayQuestion() {
		var requirement = requirement(EARS, EARS.id());
		var pair = new RequirementEvidence<>(requirement, evidence(EARS.asPrompt()));
		try (var transport = new FixtureHttp()) {
			var bound = jev(transport, new LinkedHashMap<>()).bind(requirement, EarsCriterion::asPrompt);
			var unresolved = preflight(requirement, bound, EARS.applicability(), Witness.TO_BE_JUDGED);
			assertThat(unresolved.judge(pair).status()).isEqualTo(JudgmentStatus.ABSTAIN);
			assertThat(Verdicts.interpret(jury(unresolved).vote(pair)).reading()).isEqualTo(VerdictReading.UNDECIDED);
			assertThat(transport.calls).hasValue(0);
			assertThat(preflight(requirement, bound, EARS.applicability(), Witness.APPLICABLE).judge(pair).status())
				.isEqualTo(JudgmentStatus.FAIL);
			assertThat(transport.calls).hasValue(1);
		}
	}

	@Test
	void anUnconditionalRequirementCannotBeExcludedInPreflight() {
		var specification = new Rfc2119Constraint(RFC.id(), RFC.keyword(), RFC.requirement(), RFC.reason());
		var requirement = requirement(specification, specification.id());
		var pair = new RequirementEvidence<>(requirement, evidence(specification.asPrompt()));
		try (var transport = new FixtureHttp()) {
			var bound = jev(transport, new LinkedHashMap<>()).bind(requirement, Rfc2119Constraint::asPrompt);
			assertThat(preflight(requirement, bound, specification.applicability(), Witness.EXCLUDED).judge(pair).status())
				.isEqualTo(JudgmentStatus.ERROR);
			var model = new CapturingModel();
			var completion = completion(requirement, null, NativeRequirementConsumerTest::completionRfc, model,
					Witness.EXCLUDED, new ArrayList<>());
			assertThat(completion.judge(pair).status()).isEqualTo(JudgmentStatus.ERROR);
			assertThat(transport.calls).hasValue(0);
			assertThat(model.requests).isEmpty();
		}
	}

	@Test
	void judgedApplicabilityIsAnExplicitCompletionProtocolWithAnAuditableReason() {
		var requirement = requirement(EARS, EARS.id());
		var pair = new RequirementEvidence<>(requirement, evidence(EARS.asPrompt()));
		var model = new CapturingModel();
		model.answer = "NOT_APPLICABLE: Source.java:4 has no appointments";
		var completion = completion(requirement, EARS.applicability(), NativeRequirementConsumerTest::completionEars,
				model, Witness.TO_BE_JUDGED, new ArrayList<>());
		Verdict verdict = jury(completion).vote(pair);
		assertThat(verdict.aggregated().status()).isEqualTo(JudgmentStatus.NOT_APPLICABLE);
		assertThat(verdict.aggregated().reasoning()).contains("Source.java:4 has no appointments");
		assertThat(Verdicts.interpret(verdict).reading()).isEqualTo(VerdictReading.NOT_APPLICABLE);
		assertThat(model.text()).contains("NOT_APPLICABLE requires the declared condition to be false and a reason",
				EARS.applicability(), "TO_BE_JUDGED");
		model.answer = "INSUFFICIENT: evidence does not establish appointment support";
		assertThat(Verdicts.interpret(jury(completion).vote(pair)).reading()).isEqualTo(VerdictReading.UNDECIDED);
		model.answer = "VIOLATED: Source.java:4 permits cancellation";
		assertThat(Verdicts.interpret(jury(completion).vote(pair)).reading()).isEqualTo(VerdictReading.REJECTED);
	}

	@Test
	void completionExclusionCannotContradictAnApplicableWitnessOrAnUnconditionalRequirement() {
		var conditional = requirement(RFC, RFC.id());
		var model = new CapturingModel();
		model.answer = "NOT_APPLICABLE: claimed exclusion";
		var completion = completion(conditional, RFC.applicability(), NativeRequirementConsumerTest::completionRfc,
				model, Witness.APPLICABLE, new ArrayList<>());
		assertThat(completion.judge(new RequirementEvidence<>(conditional, evidence(RFC.asPrompt()))).status())
			.isEqualTo(JudgmentStatus.ERROR);
		var unconditionalSpec = new EarsCriterion(EARS.id(), EARS.title(), EARS.requirement());
		var unconditional = requirement(unconditionalSpec, unconditionalSpec.id());
		var unconditionalCompletion = completion(unconditional, null, NativeRequirementConsumerTest::completionEars,
				model, Witness.TO_BE_JUDGED, new ArrayList<>());
		assertThat(unconditionalCompletion.judge(new RequirementEvidence<>(unconditional, evidence(unconditionalSpec.asPrompt()))).status())
			.isEqualTo(JudgmentStatus.ERROR);
	}

	@Test
	void completionProtocolErrorsRemainInstrumentFailures() {
		var requirement = requirement(RFC, RFC.id());
		var pair = new RequirementEvidence<>(requirement, evidence(RFC.asPrompt()));
		var model = new CapturingModel();
		var completion = completion(requirement, RFC.applicability(), NativeRequirementConsumerTest::completionRfc,
				model, Witness.TO_BE_JUDGED, new ArrayList<>());
		for (String answer : List.of("NOT_APPLICABLE:", "NOT_APPLICABLE", "PROBABLY: maybe", "SATISFIED:")) {
			model.answer = answer;
			assertThat(completion.judge(pair).status()).as(answer).isEqualTo(JudgmentStatus.ERROR);
		}
		model.failure = true;
		assertThat(completion.judge(pair).status()).isEqualTo(JudgmentStatus.ERROR);
	}

	private static <S> void assertNativeMismatches(Requirement<S> original, List<S> variants,
			Function<S, String> jevRender, Function<S, String> completionRender) {
		try (var transport = new FixtureHttp()) {
			var bound = jev(transport, new LinkedHashMap<>()).bind(original, jevRender);
			var model = new CapturingModel();
			var completion = completion(original, "Conditional native specification", completionRender, model,
					Witness.APPLICABLE, new ArrayList<>());
			for (var specification : variants) {
				var changed = new Requirement<>(original.id(), original.revision(), original.text(), specification, original.source());
				var pair = new RequirementEvidence<>(changed, evidence(jevRender.apply(original.specification())));
				assertThat(changed.text()).isEqualTo(original.text());
				assertThat(bound.judge(pair).status()).as(specification.toString()).isEqualTo(JudgmentStatus.ERROR);
				assertThat(completion.judge(pair).status()).as(specification.toString()).isEqualTo(JudgmentStatus.ERROR);
			}
			assertThat(transport.calls).hasValue(0);
			assertThat(model.requests).isEmpty();
		}
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
		return "RFC2119 document ID: " + specification.id() + "\nKeyword: " + specification.keyword()
				+ "\nConstraint: " + specification.requirement() + "\nRationale: " + specification.reason()
				+ "\nApplicability: " + specification.applicability();
	}

	private static String completionEars(EarsCriterion specification) {
		return "EARS document ID: " + specification.id() + "\nHeading: " + specification.title()
				+ "\nVerbatim sentence: " + specification.requirement() + "\nApplicability: " + specification.applicability();
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

	private static <E> SimpleJury<E> jury(Judge<E> judge) {
		return SimpleJury.<E>builder().judge(judge).parallel(false)
			.votingStrategy(new AllMustPassStrategy(ErrorPolicy.PROPAGATE, NotApplicablePolicy.EXCLUDE)).build();
	}

	private enum Witness { APPLICABLE, EXCLUDED, TO_BE_JUDGED }

	/** Caller-owned preflight; it never asks Jev to invent a fourth Choice meaning. */
	private static <S> JudgeWithMetadata<RequirementEvidence<Requirement<S>, JevEvidence>> preflight(
			Requirement<S> expected, Judge<RequirementEvidence<Requirement<S>, JevEvidence>> delegate,
			String condition, Witness witness) {
		return declared("preflight", condition, input -> {
			if (!sameRequirement(expected, input.requirement())) {
				return Judgment.error("Applicability preflight binds another native requirement");
			}
			if (witness == Witness.EXCLUDED) {
				return condition == null ? Judgment.error("Cannot exclude an unconditional requirement")
						: Judgment.notApplicable("No persistence in selected source; " + condition + " does not hold");
			}
			if (condition != null && witness == Witness.TO_BE_JUDGED) {
				return Judgment.abstain("Applicability is unresolved before bounded Jev evaluation");
			}
			return delegate.judge(input);
		});
	}

	/** Independent completion adapter with its own native rendering and response protocol. */
	private static <S> JudgeWithMetadata<RequirementEvidence<Requirement<S>, JevEvidence>> completion(
			Requirement<S> expected, String condition, Function<S, String> render, CapturingModel model, Witness witness,
			List<RequirementEvidence<Requirement<S>, JevEvidence>> seen) {
		return declared("completion", condition, input -> {
			seen.add(input);
			var supplied = input.requirement();
			if (!sameRequirement(expected, supplied)) {
				return Judgment.error("Completion binding differs from native snapshot");
			}
			if (witness == Witness.EXCLUDED) {
				return condition == null ? Judgment.error("Cannot exclude an unconditional requirement")
						: Judgment.notApplicable("Caller preflight established the declared condition is absent");
			}
			String protocol = "Return SATISFIED, VIOLATED, INSUFFICIENT or NOT_APPLICABLE followed by colon and reason. "
					+ "NOT_APPLICABLE requires the declared condition to be false and a reason. "
					+ "It is prohibited for unconditional requirements or an APPLICABLE witness.";
			String identity = "Application ID: " + supplied.id() + "\nRevision: " + supplied.revision()
					+ "\nSource: " + supplied.source() + "\n";
			try {
				var response = model.generate(new JudgeModelRequest(List.of(
						new JudgeMessage(JudgeMessageRole.SYSTEM, protocol),
						new JudgeMessage(JudgeMessageRole.USER, identity + render.apply(supplied.specification())),
						new JudgeMessage(JudgeMessageRole.USER, input.evidence().text()),
						new JudgeMessage(JudgeMessageRole.USER, "Applicability witness: " + witness)),
						JudgeModelOptions.defaults(), Map.of()));
				String[] parts = response.text().split(":", 2);
				if (parts.length != 2 || parts[1].isBlank()) return Judgment.error("Missing protocol reason");
				String reason = parts[1].strip();
				return switch (parts[0]) {
					case "SATISFIED" -> Judgment.pass(reason);
					case "VIOLATED" -> Judgment.fail(reason);
					case "INSUFFICIENT" -> Judgment.abstain(reason);
					case "NOT_APPLICABLE" -> condition != null && witness == Witness.TO_BE_JUDGED
							? Judgment.notApplicable(reason) : Judgment.error("Exclusion contradicts declared applicability");
					default -> Judgment.error("Unrecognized completion protocol answer");
				};
			}
			catch (RuntimeException failure) {
				return Judgment.error("Completion instrument failed");
			}
		});
	}

	private static boolean sameRequirement(Requirement<?> expected, Requirement<?> supplied) {
		return expected.id().equals(supplied.id()) && expected.revision().equals(supplied.revision())
				&& expected.source().equals(supplied.source()) && expected.specification().equals(supplied.specification());
	}

	private static <E> JudgeWithMetadata<E> declared(String name, String condition, Judge<E> delegate) {
		return new JudgeWithMetadata<>() {
			@Override
			public JudgeMetadata metadata() {
				return new JudgeMetadata(name, "Native requirement fixture adapter", JudgeType.DETERMINISTIC,
						condition == null ? null : "Declared condition does not hold: " + condition);
			}
			@Override
			public Judgment judge(E input) {
				return delegate.judge(input);
			}
		};
	}

	private static final class CapturingModel implements JudgeModel {
		final List<JudgeModelRequest> requests = new ArrayList<>();
		String answer = "VIOLATED: Source.java:4 violates the supplied requirement";
		boolean failure;
		@Override
		public JudgeModelResponse generate(JudgeModelRequest request) {
			requests.add(request);
			if (failure) throw new IllegalStateException("Fixture backend failure");
			return new JudgeModelResponse(answer, "fixture-model", null, Map.of());
		}
		String text() {
			return requests.getLast().messages().stream().map(JudgeMessage::content).reduce("", (a, b) -> a + "\n" + b);
		}
	}

	/** Injects captured bytes without opening a socket; honors the SDK's body handler. */
	private static final class FixtureHttp extends TransportTest.PendingHttp {
		@Override
		public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
			calls.incrementAndGet();
			var headers = HttpHeaders.of(Map.of("content-type", List.of("application/json")), (a, b) -> true);
			var subscriber = handler.apply(new HttpResponse.ResponseInfo() {
				@Override public int statusCode() { return 200; }
				@Override public HttpHeaders headers() { return headers; }
				@Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
			});
			subscriber.onSubscribe(new Flow.Subscription() {
				@Override public void request(long n) { }
				@Override public void cancel() { }
			});
			subscriber.onNext(List.of(ByteBuffer.wrap(JevJudgeTest.fixture("choice-valid"))));
			subscriber.onComplete();
			return subscriber.getBody().thenApply(body -> (HttpResponse<T>) new FixtureResponse<>(request, headers, body))
				.toCompletableFuture();
		}
	}

	private record FixtureResponse<T>(HttpRequest request, HttpHeaders headers, T body) implements HttpResponse<T> {
		@Override public int statusCode() { return 200; }
		@Override public Optional<HttpResponse<T>> previousResponse() { return Optional.empty(); }
		@Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
		@Override public URI uri() { return request.uri(); }
		@Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
	}

}
