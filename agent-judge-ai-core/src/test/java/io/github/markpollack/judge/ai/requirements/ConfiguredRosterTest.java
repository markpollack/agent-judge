/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.ai.requirements;
import io.github.markpollack.judge.verdict.Verdict;

import java.util.*;
import java.nio.file.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import io.github.markpollack.judge.ai.model.*;
import io.github.markpollack.judge.jury.*;
import io.github.markpollack.judge.verdict.*;
import io.github.markpollack.judge.voting.*;
import io.github.markpollack.judge.judgment.*;
import io.github.markpollack.judge.requirement.*;
import io.github.markpollack.judge.description.*;
import io.github.markpollack.judge.evaluation.*;
import io.github.markpollack.judge.policy.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** Executes actual public roster producers with deterministic native answers. */
public class ConfiguredRosterTest {

	@Test
	void rosterContract() {
		exercise();
	}

	public static void main(String[] args) {
		exercise();
		System.out.println("Configured RFC/EARS roster and cancellation contracts: PASS");
	}

	static Rfc2119Requirement rfc(String id, String applies) {
		return new Rfc2119Requirement(id, "7", "Preserve audit facts",
				new Rfc2119Specification("MUST", "preserve facts", "auditability", applies),
				Requirement.text(id, "7", "source").source());
	}

	static void exercise() {
		var a = rfc("A", null);
		var b = rfc("B", null);
		var requirements = List.of(a, b);
		for (var input : Map
			.of("A: FAIL - Foo.java:1 violates", Verdict.Conclusion.FAIL, "A: PASS - Foo.java:1 holds",
					Verdict.Conclusion.INCONCLUSIVE, "A: FAIL - Foo.java:1 violates\nALIEN: PASS - Bar.java:2",
					Verdict.Conclusion.INCONCLUSIVE,
					"A: FAIL - Foo.java:1 violates\nA: PASS - Foo.java:2\nB: PASS - Bar.java:2",
					Verdict.Conclusion.INCONCLUSIVE, "unparseable response", Verdict.Conclusion.INCONCLUSIVE)
			.entrySet()) {
			AtomicInteger calls = new AtomicInteger();
			JudgeModel runtime = request -> {
				calls.incrementAndGet();
				assertThat(request.messages().getFirst().content()).contains("A", "B");
				return new JudgeModelResponse(input.getKey(), "native-model",
						Usage.builder().inputTokens(11).outputTokens(7).build(), Map.of("sessionId", "run-7"), true);
			};
			Jury ready = Rfc2119Jury.builder().runtime(runtime).requirements(requirements).build();
			assertThat(calls).hasValue(0);
			assertThat(ready.describe().routingOpinionBound()).isEqualTo(OpinionBound.KNOWN_NONE);
			Verdict result = ready.vote();
			assertThat(calls).hasValue(1);
			assertThat(result.conclusion()).isEqualTo(input.getValue());
			assertThat(result.roster()).containsExactly(a, b);
			assertThat(result.individual()).isEmpty();
			assertThat(result.requirement()).isNull();
			assertThat(result.compositeAttempts()).hasSize(2);
			assertThat(result.invocations()).hasSize(1);
			assertThat(result.invocations().getFirst().nativeFacts()).containsEntry("sessionId", "run-7");
			assertThat(result.compositeAttempts().getFirst().verdict().individual().getFirst().requirement())
				.isSameAs(a);
			assertThat(result.compositeAttempts().getFirst().verdict().individual().getFirst().invocationIds())
				.containsExactly(result.invocations().getFirst().id());
			assertThat(result.compositeAttempts().getFirst().verdict().individual().getFirst().invocations()).isEmpty();
			Evaluations.apply(result, complete -> {
				assertThat(complete).isSameAs(result);
				assertThat(complete.compositeAttempts()).hasSize(2);
				return new PolicyDecision(PolicyAction.ABSTAIN, "inspect incomplete evidence");
			});
			assertThat(calls).hasValue(1);
		}
		var conditional = rfc("C", "the feature exists");
		Verdict excluded = Rfc2119Jury.builder()
			.runtime((JudgeModel) request -> new JudgeModelResponse("C: NOT_APPLICABLE - feature absent", null, null,
					Map.of()))
			.requirements(List.of(conditional))
			.build()
			.vote();
		assertThat(excluded.conclusion()).isEqualTo(Verdict.Conclusion.NOT_APPLICABLE);
		Verdict refused = Rfc2119Jury.builder()
			.runtime((JudgeModel) request -> new JudgeModelResponse("A: NOT_APPLICABLE - feature absent", null, null,
					Map.of()))
			.requirements(List.of(a))
			.build()
			.vote();
		assertThat(refused.conclusion()).isEqualTo(Verdict.Conclusion.INCONCLUSIVE);
		assertThat(refused.compositeAttempts().getFirst().verdict().individual().getFirst().status())
			.isEqualTo(JudgmentStatus.NOT_APPLICABLE);
		assertThat(refused.compositeAttempts().getFirst().verdict().seats().getFirst().rejection()).isNotNull();
		JudgeModel cancelled = request -> {
			throw new CancellationException("caller cancelled");
		};
		assertThatThrownBy(() -> Rfc2119Judge.builder().runtime(cancelled).requirement(a).build().judge())
			.isInstanceOf(CancellationException.class);
		assertThatThrownBy(() -> Rfc2119Jury.builder().runtime(cancelled).requirements(requirements).build().vote())
			.isInstanceOf(CancellationException.class);
		var e = new EarsRequirement("UC1-AC1", "7", "When requested, the system shall preserve facts.",
				new EarsSpecification("retain", "When requested, the system shall preserve facts.", null), a.source());
		assertThatThrownBy(() -> EarsJudge.builder().runtime(cancelled).requirement(e).build().judge())
			.isInstanceOf(CancellationException.class);
		assertThatThrownBy(() -> EarsJury.builder().runtime(cancelled).requirements(List.of(e)).build().vote())
			.isInstanceOf(CancellationException.class);
	}

	@Test
	void rfcSingleCancellationPropagates() {
		JudgeModel runtime = request -> {
			throw new CancellationException("stop");
		};
		assertThatThrownBy(() -> Rfc2119Judge.builder().runtime(runtime).requirement(rfc("A", null)).build().judge())
			.isInstanceOf(CancellationException.class);
	}

	@Test
	void rfcRosterCancellationPropagates() {
		JudgeModel runtime = request -> {
			throw new CancellationException("stop");
		};
		assertThatThrownBy(
				() -> Rfc2119Jury.builder().runtime(runtime).requirements(List.of(rfc("A", null))).build().vote())
			.isInstanceOf(CancellationException.class);
	}

	@Test
	void earsSingleCancellationPropagates() {
		JudgeModel runtime = request -> {
			throw new CancellationException("stop");
		};
		var requirement = EarsRequirement.of("UC1-AC1", "rev1", "retain",
				"When requested, the system shall retain facts.", null);
		assertThatThrownBy(() -> EarsJudge.builder().runtime(runtime).requirement(requirement).build().judge())
			.isInstanceOf(CancellationException.class);
	}

	@Test
	void earsRosterCancellationPropagates() {
		JudgeModel runtime = request -> {
			throw new CancellationException("stop");
		};
		var requirement = EarsRequirement.of("UC1-AC1", "rev1", "retain",
				"When requested, the system shall retain facts.", null);
		assertThatThrownBy(() -> EarsJury.builder().runtime(runtime).requirements(List.of(requirement)).build().vote())
			.isInstanceOf(CancellationException.class);
	}

	@Test
	void oversizedRosterIsRejectedBeforeNativeExecution() {
		var calls = new AtomicInteger();
		JudgeModel runtime = request -> {
			calls.incrementAndGet();
			return new JudgeModelResponse("unused", null, null, Map.of());
		};
		var roster = java.util.stream.IntStream.range(0, 257).mapToObj(i -> rfc("R" + i, null)).toList();
		assertThatThrownBy(() -> Rfc2119Jury.builder().runtime(runtime).requirements(roster))
			.isInstanceOf(IllegalArgumentException.class);
		assertThat(calls).hasValue(0);
	}

	@Test
	void pinnedTutorialCountsAndOneInvocation() throws Exception {
		Path base = Path.of(getClass().getResource("/configured-tutorial/criteria.md").toURI()).getParent();
		var ears = EarsRequirement.from(base.resolve("criteria.md"), "tutorial:27756e9");
		assertThat(ears).hasSize(52);
		var slice = ears.stream()
			.filter(e -> Set.of("UC6-AC7", "UC6-AC8", "UC6-AC9", "UC6-AC10", "UC6-AC11", "UC6-AC12").contains(e.id()))
			.toList();
		assertThat(slice).hasSize(6);
		var rfc = Rfc2119Requirement.from(base.resolve("rules.md"), "tutorial:27756e9");
		assertThat(rfc).hasSize(13);
		for (var scenario : Map.of("ears-six.txt", slice, "ears-all.txt", ears).entrySet()) {
			var calls = new AtomicInteger();
			String answer = Files.readString(base.resolve(scenario.getKey()));
			var jury = EarsJury.builder().runtime((JudgeModel) request -> {
				calls.incrementAndGet();
				return new JudgeModelResponse(answer, "recorded", null, Map.of());
			}).requirements(scenario.getValue()).build();
			Verdict result = jury.vote();
			assertThat(calls).hasValue(1);
			assertThat(result.roster()).hasSize(scenario.getValue().size());
			assertThat(result.compositeAttempts()).hasSameSizeAs(result.roster());
			assertThat(result.invocations()).hasSize(1);
			result.conclusion();
		}
		var calls = new AtomicInteger();
		String answer = Files.readString(base.resolve("rfc-all.txt"));
		var result = Rfc2119Jury.builder().runtime((JudgeModel) request -> {
			calls.incrementAndGet();
			return new JudgeModelResponse(answer, "recorded", null, Map.of());
		}).requirements(rfc).build().vote();
		assertThat(calls).hasValue(1);
		assertThat(result.roster()).hasSize(13);
		assertThat(result.invocations()).hasSize(1);
		result.conclusion();
	}

}
