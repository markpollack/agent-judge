/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.judgment;
import io.github.markpollack.judge.portable.ValueRequirements;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;

/**
 * One native probability mass, preserved without renormalization.
 *
 * @param alternative declared alternative ID
 * @param probability finite probability in [0,1]
 */
public record ProbabilityMass(String alternative,
		@JsonProperty(required = true) @JsonSetter(nulls = Nulls.FAIL) double probability) {
	/** Validate and freeze this value. */
	public ProbabilityMass {
		ValueRequirements.text(alternative, "alternative");
		ValueRequirements.probability(probability);
	}
}
