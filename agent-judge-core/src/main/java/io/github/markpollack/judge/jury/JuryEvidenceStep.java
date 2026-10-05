/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury;

import java.util.function.Supplier;

/**
 * Typed evidence configuration for a Jury.
 *
 * @param <E> evidence type
 */
public interface JuryEvidenceStep<E> {

	/**
	 * Selects prepared evidence.
	 * @param evidence real evidence
	 * @return ready construction
	 */
	ReadyJury evidence(E evidence);

	/**
	 * Configures fresh acquisition.
	 * @param evidence provider
	 * @return ready construction
	 */
	ReadyJury evidenceSupplier(Supplier<? extends E> evidence);

}
