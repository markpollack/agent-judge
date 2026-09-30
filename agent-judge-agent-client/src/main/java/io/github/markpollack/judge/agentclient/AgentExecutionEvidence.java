/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.agentclient;

import java.nio.file.Path;
import java.util.Objects;
import io.github.markpollack.judge.completion.CompletionEvidence;

/**
 * Observations of a CLI agent run in a filesystem workspace.
 *
 * @param workspace directory in which the agent ran
 * @param completion captured request, response and runtime outcome
 */
public record AgentExecutionEvidence(Path workspace, CompletionEvidence completion) {
	/** Validate the required evidence values. */
	public AgentExecutionEvidence {
		Objects.requireNonNull(workspace, "workspace");
		Objects.requireNonNull(completion, "completion");
	}
}
