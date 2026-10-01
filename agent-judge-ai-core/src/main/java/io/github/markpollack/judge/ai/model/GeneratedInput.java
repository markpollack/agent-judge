/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.ai.model;

/** Input modes supported by a configured generated-answer harness. */
public enum GeneratedInput {

	/** The harness accepts the caller's actual prepared evidence. */
	PREPARED_EVIDENCE,

	/** The harness can acquire evidence and judge through its configured native tools. */
	INTEGRATED_INVESTIGATION

}
