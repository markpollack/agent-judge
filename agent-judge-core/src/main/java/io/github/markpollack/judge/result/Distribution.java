/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.result;

import java.util.List;
import java.util.Objects;

/**
 * Complete predictive distribution on a declared assessment domain.
 *
 * @param target assessment component
 * @param domainId declared domain identity
 * @param masses unique complete masses, retained exactly as supplied
 */
public record Distribution(AssessmentTarget target, String domainId, List<ProbabilityMass> masses) {
	/** Validate and freeze this value. */
	public Distribution {
		Objects.requireNonNull(target, "target");
		ValueRequirements.text(domainId, "domainId");
		masses = List.copyOf(masses);
		ValueRequirements.domain(masses.stream().map(ProbabilityMass::alternative).toList(), "mass keys");
		double sum = masses.stream().mapToDouble(ProbabilityMass::probability).sum();
		if (Math.abs(sum - 1) > 1e-6) {
			throw new IllegalArgumentException("probability masses must sum to one within 1e-6");
		}
	}
}
