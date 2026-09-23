/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.result;

/** Application decision about using the producer assessment. */
public enum AcceptanceAction {

	/** Use the producer disposition, including a negative assessment. */
	USE_ASSESSMENT,

	/** Withhold the assessment. */
	ABSTAIN,

	/** Withhold the assessment and retain escalation intent. */
	ESCALATE

}
