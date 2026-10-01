/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.assertj;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.jury.*;
import io.github.markpollack.judge.judgment.Judgment;
import io.github.markpollack.judge.evaluation.*;
import io.github.markpollack.judge.policy.*;
import static org.assertj.core.api.Assertions.*;

class AssertionLifecycleTest {

	@Test
	void concurrentTerminalsShareOriginalCompleteResultAndPolicy() throws Exception {
		var producerCalls = new AtomicInteger();
		var policyCalls = new AtomicInteger();
		Jury jury = () -> {
			producerCalls.incrementAndGet();
			return Verdict.single("ready", Judgment.pass("yes"));
		};
		var stage = Assertions.assertThat(jury).withPolicy(verdict -> {
			policyCalls.incrementAndGet();
			return new PolicyDecision(PolicyAction.RELY, "original");
		});
		assertThat(producerCalls).hasValue(0);
		try (var threads = Executors.newFixedThreadPool(2)) {
			var first = threads.submit(stage::evaluate);
			var second = threads.submit(stage::evaluate);
			assertThat(first.get(5, TimeUnit.SECONDS)).isSameAs(second.get(5, TimeUnit.SECONDS));
		}
		stage.isPassed();
		Assertions.assertThat(stage.evaluate()).isPassed();
		Assertions.assertThat(stage.evaluate().verdict()).isPassed();
		assertThat(producerCalls).hasValue(1);
		assertThat(policyCalls).hasValue(1);
		assertThatThrownBy(() -> stage.withPolicy(verdict -> new PolicyDecision(PolicyAction.RELY, "later")))
			.isInstanceOf(IllegalStateException.class);
		jury.vote();
		assertThat(producerCalls).hasValue(2);
	}

	@Test
	void failedPolicyIsRetainedOnceWithExactOriginalCause() {
		var calls = new AtomicInteger();
		var failure = new IllegalStateException("policy unavailable");
		var stage = Assertions.assertThat((Judge) () -> Judgment.pass("yes")).withPolicy(verdict -> {
			calls.incrementAndGet();
			throw failure;
		});
		var result = stage.evaluate();
		assertThat(result.policyResult()).isInstanceOf(PolicyResult.Failed.class);
		assertThat(((PolicyResult.Failed) result.policyResult()).cause()).isSameAs(failure);
		assertThat(stage.evaluate()).isSameAs(result);
		assertThatThrownBy(stage::isPassed).isInstanceOf(AssertionError.class);
		assertThat(calls).hasValue(1);
	}

	@Test
	void cancellationAndThrownJuryAreCachedWithoutPolicy() {
		for (RuntimeException failure : new RuntimeException[] { new CancellationException("cancelled"),
				new IllegalArgumentException("failed") }) {
			var calls = new AtomicInteger();
			var policyCalls = new AtomicInteger();
			Jury jury = () -> {
				calls.incrementAndGet();
				throw failure;
			};
			var stage = Assertions.assertThat(jury).withPolicy(verdict -> {
				policyCalls.incrementAndGet();
				return new PolicyDecision(PolicyAction.RELY, "unused");
			});
			assertThatThrownBy(stage::evaluate).isSameAs(failure);
			assertThatThrownBy(stage::evaluate).isSameAs(failure);
			assertThat(calls).hasValue(1);
			assertThat(policyCalls).hasValue(0);
		}
	}

}
