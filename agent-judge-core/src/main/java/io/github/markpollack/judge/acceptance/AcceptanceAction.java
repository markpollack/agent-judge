/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.acceptance;

/** Whether the application may rely on the Judgment as rendered. */
public enum AcceptanceAction {

	/** Use the producer disposition, including a negative Judgment. */
	RELY,

	/** Withhold reliance. */
	ABSTAIN,

	/** Withhold reliance and retain escalation intent. */
	ESCALATE

}
