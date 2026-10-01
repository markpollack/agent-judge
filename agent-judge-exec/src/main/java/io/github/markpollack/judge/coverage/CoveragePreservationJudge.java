/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.coverage;

import io.github.markpollack.judge.DeterministicJudge;
import io.github.markpollack.judge.coverage.JaCoCoReportParser.CoverageMetrics;
import io.github.markpollack.judge.judgment.Check;
import io.github.markpollack.judge.judgment.Judgment;

/**
 * Judge that verifies test coverage has not dropped beyond a threshold compared to a
 * baseline.
 *
 * <p>
 * Parses the JaCoCo XML report from the workspace and compares line coverage against a
 * typed baseline. The default threshold is 5 percentage points (from FreshBrew research —
 * structural anti-gaming threshold).
 * </p>
 *
 * @author Mark Pollack
 * @since 0.9.0
 */
public class CoveragePreservationJudge extends DeterministicJudge<CoverageComparison> {

	private static final double DEFAULT_THRESHOLD = 5.0;

	private final double threshold;

	/**
	 * Create with default threshold of 5 percentage points.
	 * @param source fresh evidence acquisition provider, invoked once per direct
	 * execution
	 */
	public CoveragePreservationJudge(java.util.function.Supplier<? extends CoverageComparison> source) {
		this(source, DEFAULT_THRESHOLD);
	}

	/**
	 * Create with custom threshold.
	 * @param threshold maximum allowed coverage drop in percentage points
	 * @param source fresh evidence acquisition provider, invoked once per direct
	 * execution
	 */
	public CoveragePreservationJudge(java.util.function.Supplier<? extends CoverageComparison> source,
			double threshold) {
		super(source, "CoveragePreservationJudge",
				"Verifies test coverage drop within " + threshold + " percentage points of baseline");
		this.threshold = threshold;
	}

	@Override
	protected Judgment evaluate(CoverageComparison evidence) {
		double baselineLineCoverage = evidence.baselineLineCoverage();

		CoverageMetrics current = JaCoCoReportParser.parse(evidence.workspace());
		if (current.linesTotal() == 0 && current.summary().contains("not found")) {
			// The required input to this evaluation is missing, so the judge could not
			// complete. ERROR lets the jury's ErrorHandling decide whether to propagate,
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

		return (pass ? Judgment.builder().pass() : Judgment.builder().fail()).reasoning(reasoning)
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

	/**
	 * Configures a producer without executing or acquiring evidence.
	 * @return typed evidence stage
	 */
	public static io.github.markpollack.judge.construction.EvidenceStep<CoverageComparison> builder() {
		return io.github.markpollack.judge.construction.EvidenceSteps
			.of(source -> new CoveragePreservationJudge(source));
	}

	/**
	 * Configures a producer without executing or acquiring evidence.
	 * @param threshold producer configuration
	 * @return typed evidence stage
	 */
	public static io.github.markpollack.judge.construction.EvidenceStep<CoverageComparison> builder(double threshold) {
		return io.github.markpollack.judge.construction.EvidenceSteps
			.of(source -> new CoveragePreservationJudge(source, threshold));
	}

}
