/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.assertj;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.evaluation.*;
import io.github.markpollack.judge.judgment.Judgment;
import io.github.markpollack.judge.policy.*;
import static org.assertj.core.api.Assertions.*;

class AttributedPolicyCacheTest {

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void concurrentTerminalsReuseOneAttributedSuccessOrFailureAndOneOriginalVerdict(boolean failed) {
		var executions = new AtomicInteger();
		var policies = new AtomicInteger();
		var cause = new IllegalStateException("policy failed");
		var attribution = new PolicyAttribution("caller", "1", Map.of("configurationBytes", "retained"));
		var seen = new java.util.concurrent.atomic.AtomicReference<io.github.markpollack.judge.verdict.Verdict>();
		var stage = Assertions.assertThat((Judge) () -> {
			executions.incrementAndGet();
			return Judgment.pass("original");
		}).withPolicy(v -> {
			seen.set(v);
			policies.incrementAndGet();
			if (failed)
				throw cause;
			return new PolicyDecision(PolicyAction.RELY, "decided");
		}, attribution);
		var futures = java.util.stream.IntStream.range(0, 8)
			.mapToObj(i -> CompletableFuture.supplyAsync(stage::evaluate))
			.toList();
		var first = futures.getFirst().join();
		for (var future : futures)
			assertThat(future.join()).isSameAs(first);
		assertThat(first.verdict()).isSameAs(seen.get());
		assertThat(executions).hasValue(1);
		assertThat(policies).hasValue(1);
		if (failed) {
			assertThat(((PolicyResult.Failed) first.policyResult()).cause()).isSameAs(cause);
			assertThat(((PolicyResult.Failed) first.policyResult()).attribution()).isSameAs(attribution);
		}
		else
			assertThat(((PolicyResult.Decided) first.policyResult()).attribution()).isSameAs(attribution);
	}

}
