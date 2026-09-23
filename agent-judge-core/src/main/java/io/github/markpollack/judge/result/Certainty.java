/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.result;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;

import java.util.Objects;
import org.jspecify.annotations.Nullable;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Metric-specific scalar support; no claim of cross-metric comparability or calibration.
 *
 * @param value finite support in [0,1]
 * @param metricId native or derived metric identity
 * @param origin reported or derived
 * @param target supported assessment component
 * @param derivationId versioned derivation identity, required only for DERIVED
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Certainty(@JsonProperty(required = true) @JsonSetter(nulls = Nulls.FAIL) double value, String metricId,
		SupportOrigin origin, AssessmentTarget target, @Nullable String derivationId) {
	/** Validate and freeze this value. */
	public Certainty {
		ValueRequirements.probability(value);
		ValueRequirements.text(metricId, "metricId");
		Objects.requireNonNull(origin, "origin");
		Objects.requireNonNull(target, "target");
		if (origin == SupportOrigin.DERIVED) {
			ValueRequirements.versioned(Objects.requireNonNull(derivationId, "derivationId"), "derivationId");
		}
		else if (derivationId != null) {
			throw new IllegalArgumentException("reported support has no derivationId");
		}
	}
}
