/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.serialization;
import io.github.markpollack.judge.verdict.InvocationRecords;
import io.github.markpollack.judge.verdict.Verdict;
import io.github.markpollack.judge.voting.AllMustPassStrategy;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import io.github.markpollack.judge.evaluation.*;
import io.github.markpollack.judge.jury.*;
import io.github.markpollack.judge.verdict.*;
import io.github.markpollack.judge.voting.*;
import io.github.markpollack.judge.judgment.*;
import io.github.markpollack.judge.policy.*;
import io.github.markpollack.judge.provenance.*;
import static org.assertj.core.api.Assertions.*;

class ConfiguredStorageBoundaryTest {

	@Test
	void bothBaselineValidV4HistoriesAreExplicitlyRefused() throws Exception {
		for (String name : List.of("v4-one-member-refusal.json", "v4-fallback-selection.json")) {
			try (var resource = getClass().getResourceAsStream("/conformance/v4-history/" + name)) {
				assertThat(resource).isNotNull();
				String historical = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
				assertThat(historical).contains("\"schemaVersion\":4");
				assertThatThrownBy(() -> new VerdictCodec().read(historical)).hasMessageContaining("expected 5")
					.hasMessageContaining("7387aab1bf9d3bd56e4d9a932f2f40e2978d676d");
			}
		}
	}

	@Test
	void unresolvedOwnershipCannotReachPolicyOrEvaluationOrStorage() {
		var calls = new AtomicInteger();
		var incomplete = Verdict.single("native", Judgment.pass("yes").withInvocationIds(List.of("missing")));
		assertThatThrownBy(() -> Evaluations.apply(incomplete, v -> {
			calls.incrementAndGet();
			return new PolicyDecision(PolicyAction.RELY, "unused");
		})).hasMessageContaining("Unresolved native invocation");
		assertThatThrownBy(() -> Evaluations.of(incomplete)).hasMessageContaining("Unresolved native invocation");
		assertThatThrownBy(() -> new VerdictCodec().write(incomplete))
			.hasMessageContaining("Unresolved native invocation");
		assertThat(calls).hasValue(0);
	}

	@Test
	void repeatedOwnersResolveOnceAndConflictingFactsAreRefused() {
		var fact = new Invocation("shared", "fixture:v1", true, null, 1, Map.of("inputTokens", 2L), List.of());
		var answer = Judgment.pass("yes").withInvocation(fact);
		var jury = SimpleJury.builder()
			.judge(() -> answer)
			.judge(() -> answer)
			.votingStrategy(new AllMustPassStrategy())
			.build();
		var result = jury.vote();
		assertThat(InvocationRecords.of(result)).containsExactly(fact);
		assertThat(new VerdictCodec().read(new VerdictCodec().write(result))).isEqualTo(result);
		var conflicting = new Invocation("shared", "fixture:v1", true, null, 2, Map.of("inputTokens", 2L), List.of());
		var conflict = SimpleJury.builder()
			.judge(() -> answer)
			.judge(() -> Judgment.pass("other").withInvocation(conflicting))
			.votingStrategy(new AllMustPassStrategy())
			.build()
			.vote();
		assertThatThrownBy(() -> Evaluations.of(conflict))
			.hasMessageContaining("Conflicting native invocation identity");
	}

}
