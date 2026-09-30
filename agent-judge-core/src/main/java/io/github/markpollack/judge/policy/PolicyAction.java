/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.policy;

/** What the application should do with a verdict; never a replacement conclusion. */
public enum PolicyAction {

	/** Trust the conclusion, including a rejection or inconclusive conclusion. */
	RELY,
	/** Withhold reliance. */
	ABSTAIN,
	/** Request further action by the caller; does not restart composition. */
	ESCALATE

}
