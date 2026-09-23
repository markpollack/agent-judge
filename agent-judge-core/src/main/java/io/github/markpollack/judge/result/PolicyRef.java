/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.result;

/**
 * Immutable application policy identity.
 *
 * @param id policy identity
 * @param revision policy revision
 * @param configurationDigest SHA-256 of retained exact policy configuration bytes
 */
public record PolicyRef(String id, String revision, String configurationDigest) {
	/** Validate and freeze this value. */
	public PolicyRef {
		ValueRequirements.text(id, "id");
		ValueRequirements.text(revision, "revision");
		ValueRequirements.digest(configurationDigest);
	}
}
