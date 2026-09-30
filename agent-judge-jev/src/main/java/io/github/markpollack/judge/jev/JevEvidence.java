/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jev;

import io.github.markpollack.judge.provenance.ArtifactRef;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Explicit selected UTF-8 evidence; the adapter never reads the workspace or agent
 * output.
 *
 * @param text selected evidence, without expected answers
 * @param bundle reference to exactly the UTF-8 bytes of text
 * @param manifest caller-reviewed recipe, bounds, exclusions and source lineage
 * @param requirementSha256 exact UTF-8 requirement digest to which the sufficiency
 * declaration applies
 * @param complete caller's declaration that evidence is sufficient for the binary
 * question
 */
public record JevEvidence(String text, ArtifactRef bundle, ArtifactRef manifest, String requirementSha256,
		boolean complete) {

	/** Validate the exact evidence binding. */
	public JevEvidence {
		Objects.requireNonNull(text);
		Objects.requireNonNull(bundle);
		Objects.requireNonNull(manifest);
		new ArtifactRef("requirement", requirementSha256, null);
		if (!ArtifactRef.ofBytes("evidence", text.getBytes(StandardCharsets.UTF_8), null)
			.sha256()
			.equals(bundle.sha256()))
			throw new IllegalArgumentException("Evidence digest mismatch");
		// Portable text validation also rejects unpaired UTF-16 surrogates.
		io.github.markpollack.judge.judgment.Judgment.pass("").toBuilder().metadata("text", text).build();
	}
}
