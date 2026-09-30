/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.provenance;

import java.util.List;

/**
 * Source-backed provider declaration, not empirical certification of calibration.
 *
 * @param id stable versioned claim identity
 * @param issuer declaring party
 * @param scope declared applicability population or scope
 * @param statement retained claim text
 * @param signalIds applicable native signal identities
 * @param sources exact-byte sources supporting the declaration
 */
public record CalibrationClaim(String id, String issuer, String scope, String statement, List<String> signalIds,
		List<ArtifactRef> sources) {
	/** Validate and freeze this value. */
	public CalibrationClaim {
		ValueRequirements.versioned(id, "claim id");
		ValueRequirements.text(issuer, "issuer");
		ValueRequirements.text(scope, "scope");
		ValueRequirements.text(statement, "statement");
		signalIds = ValueRequirements.domain(signalIds, "signalIds");
		sources = List.copyOf(sources);
		ValueRequirements.domain(sources.stream().map(ArtifactRef::id).toList(), "source IDs");
	}
}
