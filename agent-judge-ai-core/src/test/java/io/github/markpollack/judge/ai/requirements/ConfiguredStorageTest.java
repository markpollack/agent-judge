/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.ai.requirements;
import io.github.markpollack.judge.verdict.InvocationRecords;
import io.github.markpollack.judge.verdict.SeatExecution;
import io.github.markpollack.judge.verdict.Verdict;

import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import io.github.markpollack.judge.ai.model.*;
import io.github.markpollack.judge.execution.*;
import io.github.markpollack.judge.judgment.*;
import io.github.markpollack.judge.jury.*;
import io.github.markpollack.judge.verdict.*;
import io.github.markpollack.judge.voting.*;
import io.github.markpollack.judge.provenance.*;
import io.github.markpollack.judge.requirement.*;
import io.github.markpollack.judge.serialization.*;
import io.github.markpollack.judge.evaluation.*;
import static org.assertj.core.api.Assertions.*;

/** Real current-codec/native-protocol tests; no live inference. */
class ConfiguredStorageTest {

	private final VerdictCodec codec = NativeRequirementCodecs.codec();

	private final Rfc2119Requirement a = Rfc2119Requirement.of("A", "rev7", "MUST", "retain facts", "audit", null);

	private final Rfc2119Requirement b = Rfc2119Requirement.of("B", "rev8", "SHOULD", "retain support", "audit", null);

	private static Invocation invocation(String id) {
		return new Invocation(id, "fixture:v1", true, "native", 3,
				Map.of("usage", Map.of("inputTokens", 11L, "outputTokens", 7L), "rawAnswer", "retained"),
				List.of(ArtifactRef.ofBytes("answer", new byte[] { 1, 2 }, null)));
	}

	private Verdict roster(String answer, AtomicInteger calls) {
		EvalModel runtime = request -> {
			calls.incrementAndGet();
			return new EvalModelResponse(answer, "model", Usage.builder().inputTokens(11).outputTokens(7).build(),
					Map.of("sessionId", "s7"));
		};
		return Rfc2119Jury.builder().runtime(runtime).requirements(List.of(a, b)).build().vote();
	}

	@Test
	void partialEnvelopeRoundTripsKeepEveryOriginalAndOneSharedOwner() {
		Map<String, Verdict.Conclusion> cases = Map.of("A: FAIL - Foo.java:1 violates", Verdict.Conclusion.FAIL,
				"A: PASS - Foo.java:1 holds", Verdict.Conclusion.INCONCLUSIVE,
				"A: FAIL - Foo.java:1 violates\nALIEN: PASS - unknown", Verdict.Conclusion.INCONCLUSIVE,
				"A: FAIL - first\nA: PASS - duplicate\nB: PASS - second", Verdict.Conclusion.INCONCLUSIVE,
				"unparseable", Verdict.Conclusion.INCONCLUSIVE);
		for (var entry : cases.entrySet()) {
			var calls = new AtomicInteger();
			var original = roster(entry.getKey(), calls);
			String json = codec.write(original);
			Verdict read = codec.read(json);
			assertThat(read).isEqualTo(original);
			assertThat(read.conclusion()).isEqualTo(entry.getValue());
			assertThat(calls).hasValue(1);
			assertThat(read.roster()).containsExactly(a, b);
			assertThat(read.roster().getFirst()).isInstanceOf(Rfc2119Requirement.class);
			assertThat(InvocationRecords.of(read)).hasSize(1);
			assertThat(json).doesNotContain("@class", "@type", "stackTrace");
			for (var attempt : read.compositeAttempts()) {
				var judgment = attempt.verdict().individual().getFirst();
				assertThat(judgment.requirement()).isInstanceOf(Rfc2119Requirement.class);
				assertThat(judgment.invocationIds()).containsExactly(read.invocations().getFirst().id());
				assertThat(judgment.invocations()).isEmpty();
			}
			assertThat(read.compositeAttempts().getLast().verdict().individual().getFirst().status())
				.isEqualTo(original.compositeAttempts().getLast().verdict().individual().getFirst().status());
		}
	}

	@Test
	void justifiedAndRejectedExclusionsPreserveOriginalAndTreatment() {
		for (String applicability : Arrays.asList(null, "feature exists")) {
			var requirement = Rfc2119Requirement.of("C", "rev7", "MUST", "retain", "audit", applicability);
			Verdict result = Rfc2119Jury.builder()
				.runtime((EvalModel) request -> new EvalModelResponse("C: NOT_APPLICABLE - feature absent", null,
						null, Map.of()))
				.requirements(List.of(requirement))
				.build()
				.vote();
			Verdict read = codec.read(codec.write(result));
			assertThat(read).isEqualTo(result);
			var child = read.compositeAttempts().getFirst().verdict();
			assertThat(child.individual().getFirst().status()).isEqualTo(JudgmentStatus.NOT_APPLICABLE);
			if (applicability == null) {
				assertThat(child.seats().getFirst().execution()).isEqualTo(SeatExecution.RETURNED_REJECTED);
				assertThat(child.seats().getFirst().rejection().status()).isEqualTo(JudgmentStatus.ERROR);
				assertThat(read.conclusion()).isEqualTo(Verdict.Conclusion.INCONCLUSIVE);
			}
			else {
				assertThat(child.seats().getFirst().rejection()).isNull();
				assertThat(read.conclusion()).isEqualTo(Verdict.Conclusion.NOT_APPLICABLE);
			}
		}
	}

	@Test
	void standaloneNativeTypesAndOriginalFailureSurviveWithoutInventingExecution() {
		var failure = new IllegalStateException("decode failed");
		var nativeFact = new Invocation("i1", "typed:v1", false, null, 4, Map.of("usage", Map.of("inputTokens", 3L)),
				List.of(), failure);
		EvalRuntime<RequirementRequest<Rfc2119Specification, String>, Judgment> runtime = request -> new NativeExecution<>(
				Judgment.error("bad native answer"), nativeFact);
		var original = Rfc2119Judge.builder().runtime(runtime).requirement(a).evidence("actual").build().judge();
		assertThat(original.requirement()).isSameAs(a);
		assertThat(original.invocations().getFirst().cause()).isSameAs(failure);
		var read = codec.read(codec.write(Verdict.single("rfc", original)));
		assertThat(read.individual().getFirst()).isEqualTo(original);
		assertThat(read.individual().getFirst().requirement()).isInstanceOf(Rfc2119Requirement.class);
		assertThat(read.individual().getFirst().invocations().getFirst().cause()).isNull();
		assertThatThrownBy(() -> new VerdictCodec().write(Verdict.single("rfc", original)))
			.hasMessageContaining("specification");
		var ears = new EarsRequirement("UC1-AC1", "rev9", "When requested, the system shall retain facts.",
				new EarsSpecification("facts", "When requested, the system shall retain facts.", null), a.source());
		var earsRead = codec.read(codec.write(Verdict.single("ears", Judgment.pass("satisfied").forRequirement(ears))));
		assertThat(earsRead.individual().getFirst().requirement()).isEqualTo(ears).isInstanceOf(EarsRequirement.class);
		var rule = codec.read(codec.write(Verdict.single("rule", Judgment.pass("coded rule"))));
		assertThat(rule.individual().getFirst().requirement()).isNull();
		assertThat(InvocationRecords.of(rule)).isEmpty();
	}

	@Test
	void exactEvidenceSnapshotsAndPerItemExecutionsRetainLowerFacts() {
		var calls = new AtomicInteger();
		var acquisitions = new AtomicInteger();
		var evidence = new ArrayList<String>();
		var lower = invocation("shared-lower");
		EvalRuntime<RequirementRequest<Rfc2119Specification, String>, Judgment> runtime = request -> {
			evidence.add(request.evidence());
			return new NativeExecution<>(Judgment.pass("satisfied").withInvocation(lower),
					invocation("call-" + calls.incrementAndGet()));
		};
		var mutable = new LinkedHashMap<>(Map.of("A", "a-evidence", "B", "b-evidence"));
		var stage = Rfc2119Jury.builder().runtime(runtime).requirements(List.of(a, b));
		var ready = stage.evidenceByRequirement(mutable).build();
		mutable.put("A", "changed");
		assertThat(calls).hasValue(0);
		var result = ready.vote();
		assertThat(evidence).containsExactly("a-evidence", "b-evidence");
		assertThat(InvocationRecords.of(result)).hasSize(3);
		assertThat(codec.read(codec.write(result))).isEqualTo(result);
		evidence.clear();
		stage.evidenceSupplier(() -> {
			acquisitions.incrementAndGet();
			return "common";
		}).build().vote();
		assertThat(acquisitions).hasValue(1);
		assertThat(evidence).containsExactly("common", "common");
		assertThatThrownBy(() -> stage.evidenceByRequirement(Map.of("A", "missing B")))
			.hasMessageContaining("cover exactly");
	}

	@Test
	void failedSiblingPreservesOriginalCauseAndCancellationPropagates() {
		var failure = new IllegalArgumentException("native failed");
		EvalRuntime<RequirementRequest<Rfc2119Specification, String>, Judgment> runtime = request -> {
			if (request.requirement().id().equals("B"))
				throw failure;
			return new NativeExecution<>(Judgment.fail("bound violation"), invocation("i1"));
		};
		var result = Rfc2119Jury.builder()
			.runtime(runtime)
			.requirements(List.of(a, b))
			.evidence("actual")
			.build()
			.vote();
		assertThat(result.conclusion()).isEqualTo(Verdict.Conclusion.FAIL);
		assertThat(result.compositeAttempts().getLast().failure().cause()).isSameAs(failure);
		assertThat(codec.read(codec.write(result)).conclusion()).isEqualTo(Verdict.Conclusion.FAIL);
		EvalRuntime<RequirementRequest<Rfc2119Specification, String>, Judgment> cancelled = request -> {
			throw new CancellationException("stop");
		};
		assertThatThrownBy(() -> Rfc2119Jury.builder()
			.runtime(cancelled)
			.requirements(List.of(a, b))
			.evidence("actual")
			.build()
			.vote()).isInstanceOf(CancellationException.class);
	}

	@Test
	void currentCodecRefusesMissingOrContradictoryOwnershipAndHistoricalVersions() throws Exception {
		var original = roster("A: FAIL - violated", new AtomicInteger());
		var mapper = new ObjectMapper().registerModule(io.github.markpollack.judge.serialization.ResultJson.module());
		var tree = (ObjectNode) mapper.readTree(codec.write(original));
		for (int version : List.of(2, 3, 4)) {
			var historical = tree.deepCopy();
			historical.put("schemaVersion", version);
			assertThatThrownBy(() -> codec.read(historical.toString()))
				.hasMessageContaining("archival reading with baseline")
				.hasMessageContaining("expected 5");
		}
		for (String key : List.of("roster", "invocations")) {
			var incomplete = tree.deepCopy();
			incomplete.remove(key);
			assertThatThrownBy(() -> codec.read(incomplete.toString())).isInstanceOf(IllegalArgumentException.class);
		}
		var missingOwner = tree.deepCopy();
		missingOwner.putArray("invocations");
		assertThatThrownBy(() -> codec.read(missingOwner.toString())).hasMessageContaining("invocation");
		var wrongOrder = tree.deepCopy();
		var roster = wrongOrder.withArray("roster");
		var first = roster.get(0);
		roster.set(0, roster.get(1));
		roster.set(1, first);
		assertThatThrownBy(() -> codec.read(wrongOrder.toString())).isInstanceOf(IllegalArgumentException.class);
		var contradictory = tree.deepCopy();
		contradictory.put("declaredCardinality", 3);
		assertThatThrownBy(() -> codec.read(contradictory.toString())).isInstanceOf(IllegalArgumentException.class);
	}

}
