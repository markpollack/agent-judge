/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.coverage;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Coverage report workspace and the prior line coverage percentage to compare against.
 *
 * @param workspace workspace containing the current JaCoCo report
 * @param baselineLineCoverage prior line coverage, from zero to one hundred
 */
public record CoverageComparison(Path workspace, double baselineLineCoverage) {
	/** Validate the required evidence values. */
	public CoverageComparison {
		Objects.requireNonNull(workspace, "workspace");
		if (!Double.isFinite(baselineLineCoverage) || baselineLineCoverage < 0 || baselineLineCoverage > 100) {
			throw new IllegalArgumentException("baselineLineCoverage must be finite and between 0 and 100");
		}
	}
}
