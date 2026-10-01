/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.construction;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import io.github.markpollack.judge.Judge;

/** Typed, non-executing construction of a configured producer. */
public final class EvidenceSteps {

	private EvidenceSteps() {
	}

	/**
	 * Builds an evidence stage. No evidence is acquired here.
	 * @param <E> evidence type
	 * @param factory immutable producer configuration accepting an acquisition provider
	 * @return evidence configuration stage
	 */
	public static <E> EvidenceStep<E> of(Function<Supplier<? extends E>, ? extends Judge> factory) {
		Objects.requireNonNull(factory);
		return new EvidenceStep<>() {
			public ReadyJudge evidence(E evidence) {
				Objects.requireNonNull(evidence);
				return evidenceSupplier(() -> evidence);
			}

			public ReadyJudge evidenceSupplier(Supplier<? extends E> evidence) {
				Objects.requireNonNull(evidence);
				return () -> Objects.requireNonNull(factory.apply(evidence));
			}
		};
	}

}
