/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.construction;

import java.util.function.Supplier;

/**
 * Typed evidence construction. Choosing evidence consumes this stage.
 *
 * @param <E> evidence type
 */
public interface EvidenceStep<E> {

	/**
	 * Selects prepared evidence without executing.
	 * @param evidence real prepared value
	 * @return ready construction
	 */
	ReadyJudge evidence(E evidence);

	/**
	 * Selects acquisition at each execution boundary.
	 * @param evidence provider invoked once per execution
	 * @return ready construction
	 */
	ReadyJudge evidenceSupplier(Supplier<? extends E> evidence);

}
