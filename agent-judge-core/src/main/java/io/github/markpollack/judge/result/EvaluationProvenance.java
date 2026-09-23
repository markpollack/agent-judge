/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.result;

import java.util.List;
import org.jspecify.annotations.Nullable;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Immutable evaluation identity and references to retained artifacts and declarations.
 *
 * @param instrumentId instrument identity
 * @param revision instrument revision
 * @param configurationDigest SHA-256 of retained exact configuration bytes
 * @param evidence input evidence references
 * @param response optional exact response reference
 * @param calibrationClaims provider declarations, empty when none retained
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EvaluationProvenance(String instrumentId, String revision, String configurationDigest,
		List<ArtifactRef> evidence, @Nullable ArtifactRef response, List<CalibrationClaim> calibrationClaims) {
	/** Validate and freeze this value. */
	public EvaluationProvenance {
		ValueRequirements.text(instrumentId, "instrumentId");
		ValueRequirements.text(revision, "revision");
		ValueRequirements.digest(configurationDigest);
		evidence = List.copyOf(evidence);
		calibrationClaims = List.copyOf(calibrationClaims);
	}
}
