package io.github.markpollack.judge.conformance;

import java.time.Duration;

import io.github.markpollack.judge.completion.CompletionStatus;
import io.github.markpollack.judge.completion.CompletionEvidence;

import static org.assertj.core.api.Assertions.assertThat;

/** Shared executable contract for facts common to every evaluated-side adapter. */
public final class CompletionEvidenceConformance {

	private CompletionEvidenceConformance() {
	}

	/** Assert common successful-execution semantics. */
	public static void assertSuccessful(CompletionEvidence context, String goal, String output) {
		assertThat(context.request()).isEqualTo(goal);
		assertThat(context.status()).isEqualTo(CompletionStatus.SUCCESS);
		assertThat(context.response()).isEqualTo(output);
		assertThat(context.startedAt()).isNotNull();
		assertThat(context.elapsedTime()).isNotNull().isGreaterThanOrEqualTo(Duration.ZERO);
		assertThat(context.error()).isNull();
	}

	/** Assert common thrown-failure semantics. */
	public static void assertFailed(CompletionEvidence context, String goal, Throwable failure) {
		assertThat(context.request()).isEqualTo(goal);
		assertThat(context.status()).isEqualTo(CompletionStatus.FAILED);
		assertThat(context.response()).isNull();
		assertThat(context.startedAt()).isNotNull();
		assertThat(context.elapsedTime()).isNotNull().isGreaterThanOrEqualTo(Duration.ZERO);
		assertThat(context.error()).isSameAs(failure);
	}

}
