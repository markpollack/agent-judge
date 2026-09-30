/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.rag;

import java.util.Objects;

/**
 * The required subjects of a retrieval-grounded answer evaluation. Empty text expresses
 * missing evidence; judges retain ABSTAIN rather than inventing an answer or context.
 *
 * @param question original question
 * @param retrievedContext material the answer should be grounded in
 * @param answer proposed answer
 */
public record RagEvidence(String question, String retrievedContext, String answer) {
	/** Require explicit fields; empty values represent unavailable evidence. */
	/** Validate the required evidence values. */
	public RagEvidence {
		Objects.requireNonNull(question, "question");
		Objects.requireNonNull(retrievedContext, "retrievedContext");
		Objects.requireNonNull(answer, "answer");
	}
}
