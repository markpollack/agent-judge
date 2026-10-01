/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.construction;

import io.github.markpollack.judge.jury.Jury;

/** Non-executing construction of a complete Jury. */
@FunctionalInterface
public interface ReadyJury {

	/**
	 * Builds the configured composition.
	 * @return ready Jury
	 */
	Jury build();

}
