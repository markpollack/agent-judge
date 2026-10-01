/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge;

import java.util.Objects;
import java.util.function.Supplier;
import io.github.markpollack.judge.judgment.Judgment;

/**
 * A configured deterministic string check. Construction is inert. Immutable and safe for
 * concurrent use when the evidence supplier is safe for concurrent use.
 */
public final class NonEmptyJudge implements Judge {

	private final Supplier<String> evidence;

	private NonEmptyJudge(Supplier<String> evidence) {
		this.evidence = evidence;
	}

	/**
	 * Begins typed evidence configuration.
	 * @return evidence stage
	 */
	public static io.github.markpollack.judge.construction.EvidenceStep<String> builder() {
		return new io.github.markpollack.judge.construction.EvidenceStep<>() {
			public io.github.markpollack.judge.construction.ReadyJudge evidence(String value) {
				Objects.requireNonNull(value, "evidence");
				return () -> new NonEmptyJudge(() -> value);
			}

			public io.github.markpollack.judge.construction.ReadyJudge evidenceSupplier(
					Supplier<? extends String> provider) {
				Objects.requireNonNull(provider, "evidence provider");
				return () -> new NonEmptyJudge(() -> Objects.requireNonNull(provider.get(), "acquired evidence"));
			}
		};
	}

	@Override
	public Judgment judge() {
		String value = Objects.requireNonNull(evidence.get(), "evidence");
		return value.isEmpty() ? Judgment.fail("The string is empty") : Judgment.pass("The string is nonempty");
	}

}
