/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.coverage;

import io.github.markpollack.judge.DeterministicJudge;
import io.github.markpollack.judge.context.JudgmentContext;
import io.github.markpollack.judge.coverage.JaCoCoReportParser.CoverageMetrics;
import io.github.markpollack.judge.result.Check;
import io.github.markpollack.judge.result.Judgment;

/**
 * Judge that verifies test coverage has not dropped beyond a threshold compared to a
 * baseline.
 *
 * <p>
 * Parses the JaCoCo XML report from the workspace and compares line coverage against a
 * baseline from {@code metadata("baselineCoverage")}. The default threshold is 5
 * percentage points (from FreshBrew research — structural anti-gaming threshold).
 * </p>
 *
 * @author Mark Pollack
 * @since 0.9.0
 */
public class CoveragePreservationJudge extends DeterministicJudge {

	private static final double DEFAULT_THRESHOLD = 5.0;

	private final double threshold;

	/**
	 * Create with default threshold of 5 percentage points.
	 */
	public CoveragePreservationJudge() {
		this(DEFAULT_THRESHOLD);
	}

	/**
	 * Create with custom threshold.
	 * @param threshold maximum allowed coverage drop in percentage points
	 */
	public CoveragePreservationJudge(double threshold) {
		super("CoveragePreservationJudge",
				"Verifies test coverage drop within " + threshold + " percentage points of baseline");
		this.threshold = threshold;
	}

	@Override
	public Judgment judge(JudgmentContext context) {
		Object baselineObj = context.metadata().get("baselineCoverage");
		if (baselineObj == null) {
			return Judgment.abstain("No baselineCoverage in metadata");
		}

		double baselineLineCoverage;
		if (baselineObj instanceof CoverageMetrics baseline) {
			baselineLineCoverage = baseline.lineCoverage();
		}
		else {
			try {
				baselineLineCoverage = Double.parseDouble(baselineObj.toString());
			}
			catch (NumberFormatException e) {
				return Judgment.abstain("Invalid baselineCoverage value: " + baselineObj);
			}
		}

		CoverageMetrics current = JaCoCoReportParser.parse(context.workspace());
		if (current.linesTotal() == 0 && current.summary().contains("not found")) {
			// The required input to this evaluation is missing, so the judge could not
			// complete. ERROR lets the jury's ErrorPolicy decide whether to propagate,
			// convert, or ignore the infrastructure failure.
			return Judgment.error("No JaCoCo report found in workspace — coverage evaluation could not complete");
		}

		double drop = baselineLineCoverage - current.lineCoverage();
		boolean pass = drop <= threshold;

		String reasoning = pass
				? String.format("Line coverage drop %.1f%% (%.1f%% → %.1f%%) within threshold of %.1f%%", drop,
						baselineLineCoverage, current.lineCoverage(), threshold)
				: String.format("Line coverage drop %.1f%% (%.1f%% → %.1f%%) exceeds threshold of %.1f%%", drop,
						baselineLineCoverage, current.lineCoverage(), threshold);

		Check coverageCheck = pass
				? Check.pass("line_coverage_preserved",
						String.format("Drop %.1f%% <= %.1f%% threshold", drop, threshold))
				: Check.fail("line_coverage_preserved",
						String.format("Drop %.1f%% > %.1f%% threshold", drop, threshold));

		return (pass ? Judgment.builder().pass() : Judgment.builder().fail())
			.reasoning(reasoning)
			.checks(java.util.List.of(coverageCheck))
			.metadata("baselineLineCoverage", baselineLineCoverage)
			.metadata("currentLineCoverage", current.lineCoverage())
			.metadata("coverageDrop", drop)
			.metadata("threshold", threshold)
			.build();
	}

	/**
	 * Get the threshold.
	 * @return maximum allowed coverage drop in percentage points
	 */
	public double getThreshold() {
		return threshold;
	}

}
