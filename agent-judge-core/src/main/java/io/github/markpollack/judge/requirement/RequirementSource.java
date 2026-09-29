/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.requirement;

import java.util.Objects;
import org.jspecify.annotations.Nullable;
import io.github.markpollack.judge.result.ArtifactRef;

/**
 * Source snapshot and its document-local requirement identity.
 * @param artifact exact source snapshot, including its content digest
 * @param nativeId document-local identity, or null when the source has none
 */
public record RequirementSource(ArtifactRef artifact, @Nullable String nativeId) {
    /** Validate the source reference and any declared local identity. */
    public RequirementSource {
        Objects.requireNonNull(artifact, "artifact");
        if (nativeId != null) Requirement.requireText(nativeId);
    }
}
