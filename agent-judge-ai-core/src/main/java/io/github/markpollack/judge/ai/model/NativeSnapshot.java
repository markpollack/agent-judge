/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.ai.model;

import java.util.List;
import java.util.Map;
import io.github.markpollack.judge.provenance.ArtifactRef;

/**
 * Portable capture of a native response, before domain decoding.
 *
 * @param facts exact native data represented as portable values or bounded JSON
 * @param artifacts caller-owned durable artifact references
 */
public record NativeSnapshot(Map<String, Object> facts, List<ArtifactRef> artifacts) {
	/** Freezes the observed native facts and protected references. */
	public NativeSnapshot {
		facts = Map.copyOf(facts);
		artifacts = List.copyOf(artifacts);
	}
}
