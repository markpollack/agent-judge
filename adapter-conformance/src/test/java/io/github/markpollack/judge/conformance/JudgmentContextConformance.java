package io.github.markpollack.judge.conformance;

import java.time.Duration;

import io.github.markpollack.judge.context.ExecutionStatus;
import io.github.markpollack.judge.context.JudgmentContext;

import static org.assertj.core.api.Assertions.assertThat;

/** Shared executable contract for facts common to every evaluated-side adapter. */
public final class JudgmentContextConformance {

	private JudgmentContextConformance() {
	}

	/** Assert common successful-execution semantics. */
	public static void assertSuccessful(JudgmentContext context, String goal, String output) {
		assertThat(context.goal()).isEqualTo(goal);
		assertThat(context.status()).isEqualTo(ExecutionStatus.SUCCESS);
		assertThat(context.agentOutput()).contains(output);
		assertThat(context.startedAt()).isNotNull();
		assertThat(context.executionTime()).isNotNull().isGreaterThanOrEqualTo(Duration.ZERO);
		assertThat(context.error()).isEmpty();
	}

	/** Assert common thrown-failure semantics. */
	public static void assertFailed(JudgmentContext context, String goal, Throwable failure) {
		assertThat(context.goal()).isEqualTo(goal);
		assertThat(context.status()).isEqualTo(ExecutionStatus.FAILED);
		assertThat(context.agentOutput()).isEmpty();
		assertThat(context.startedAt()).isNotNull();
		assertThat(context.executionTime()).isNotNull().isGreaterThanOrEqualTo(Duration.ZERO);
		assertThat(context.error()).contains(failure);
	}

}
