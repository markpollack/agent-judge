/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.assertj;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.judgment.Judgment;
import io.github.markpollack.judge.policy.PolicyAction;
import io.github.markpollack.judge.policy.PolicyDecision;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A stage is a value the caller holds, so its once-only execution must not depend on a
 * lock the caller can take. Caller code that synchronizes on the stage must not block
 * evaluation or policy selection.
 */
class StageLockTest {

	@Test
	void callerHoldingTheStageMonitorDoesNotBlockEvaluationOrPolicySelection() throws Exception {
		var stage = Assertions.assertThat((Judge) () -> Judgment.pass("yes"));
		var held = new CountDownLatch(1);
		var release = new CountDownLatch(1);
		try (var threads = Executors.newFixedThreadPool(2)) {
			threads.submit(() -> {
				synchronized (stage) {
					held.countDown();
					release.await();
				}
				return null;
			});
			assertThat(held.await(5, TimeUnit.SECONDS)).isTrue();
			try {
				var branch = threads
					.submit(() -> stage.withPolicy(verdict -> new PolicyDecision(PolicyAction.RELY, "caller")))
					.get(5, TimeUnit.SECONDS);
				assertThat(threads.submit(branch::evaluate).get(5, TimeUnit.SECONDS).verdict().conclusion())
					.hasToString("PASS");
				assertThat(threads.submit(stage::evaluate).get(5, TimeUnit.SECONDS)).isNotNull();
			}
			finally {
				release.countDown();
			}
		}
	}

}
