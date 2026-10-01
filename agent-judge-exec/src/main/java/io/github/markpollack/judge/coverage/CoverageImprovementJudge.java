/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.coverage;

import java.util.List;

import io.github.markpollack.judge.DeterministicJudge;
import io.github.markpollack.judge.coverage.JaCoCoReportParser.CoverageMetrics;
import io.github.markpollack.judge.judgment.Check;
import io.github.markpollack.judge.judgment.Judgment;

/**
 * Judge that measures test coverage improvement as a numerical score.
 *
 * <p>
 * Compares the final JaCoCo coverage against a typed baseline and produces a normalized
 * score representing the coverage delta. The score is normalized to 0-1 where 0 means no
 * improvement and 1 means the maximum expected improvement was achieved.
 * </p>
 *
 * <p>
 * Unlike {@link CoveragePreservationJudge} which is a boolean gate (pass/fail), this
 * judge produces a continuous score suitable for cross-variant comparison in growth
 * stories.
 * </p>
 *
 * @author Mark Pollack
 * @since 0.9.0
 */
public class CoverageImprovementJudge extends DeterministicJudge<CoverageComparison> {

	private static final double DEFAULT_MAX_IMPROVEMENT = 50.0;

	private static final double DEFAULT_MINIMUM_LINE_COVERAGE = 0.0;

	private final double maxImprovement;

	private final double minimumLineCoverage;

	/**
	 * Create with default max improvement of 50 percentage points and no minimum coverage
	 * requirement.
	 * @param source fresh evidence acquisition provider, invoked once per direct
	 * execution
	 */
	public CoverageImprovementJudge(java.util.function.Supplier<? extends CoverageComparison> source) {
		this(source, DEFAULT_MAX_IMPROVEMENT, DEFAULT_MINIMUM_LINE_COVERAGE);
	}

	/**
	 * Create with custom max improvement for normalization and no minimum coverage
	 * requirement.
	 * @param maxImprovement the improvement value that maps to score 1.0
	 * @param source fresh evidence acquisition provider, invoked once per direct
	 * execution
	 */
	public CoverageImprovementJudge(java.util.function.Supplier<? extends CoverageComparison> source,
			double maxImprovement) {
		this(source, maxImprovement, DEFAULT_MINIMUM_LINE_COVERAGE);
	}

	/**
	 * Create with custom max improvement and minimum line coverage threshold.
	 * @param maxImprovement the improvement value that maps to score 1.0
	 * @param minimumLineCoverage minimum required line coverage percentage (0–100); fails
	 * if current coverage is below this value regardless of improvement
	 * @param source fresh evidence acquisition provider, invoked once per direct
	 * execution
	 */
	public CoverageImprovementJudge(java.util.function.Supplier<? extends CoverageComparison> source,
			double maxImprovement, double minimumLineCoverage) {
		super(source, "CoverageImprovementJudge", "Measures test coverage improvement as a normalized score (0-1)");
		this.maxImprovement = maxImprovement;
		this.minimumLineCoverage = minimumLineCoverage;
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

		double improvement = current.lineCoverage() - baselineLineCoverage;
		double normalizedScore = Math.max(0.0, Math.min(1.0, improvement / maxImprovement));

		boolean meetsMinimum = minimumLineCoverage <= 0.0 || current.lineCoverage() >= minimumLineCoverage;
		boolean pass = improvement > 0 && meetsMinimum;

		String reasoning;
		if (!meetsMinimum) {
			reasoning = String.format(
					"Line coverage %.1f%% is below minimum threshold of %.1f%% (improvement: %.1f pp). "
							+ "Normalized score: %.3f",
					current.lineCoverage(), minimumLineCoverage, improvement, normalizedScore);
		}
		else {
			reasoning = String.format(
					"Line coverage improved %.1f percentage points (%.1f%% → %.1f%%). "
							+ "Normalized score: %.3f (max improvement: %.1f pp)",
					improvement, baselineLineCoverage, current.lineCoverage(), normalizedScore, maxImprovement);
		}

		List<Check> checks = new java.util.ArrayList<>();
		checks.add(improvement > 0
				? Check.pass("coverage_improved",
						String.format("+%.1f pp (%.1f%% → %.1f%%)", improvement, baselineLineCoverage,
								current.lineCoverage()))
				: Check.fail("coverage_improved", String.format("%.1f pp (%.1f%% → %.1f%%)", improvement,
						baselineLineCoverage, current.lineCoverage())));
		if (minimumLineCoverage > 0.0) {
			checks.add(meetsMinimum
					? Check.pass("minimum_coverage_met",
							String.format("%.1f%% >= %.1f%% minimum", current.lineCoverage(), minimumLineCoverage))
					: Check.fail("minimum_coverage_met",
							String.format("%.1f%% < %.1f%% minimum", current.lineCoverage(), minimumLineCoverage)));
		}

		return (pass ? Judgment.builder().pass() : Judgment.builder().fail()).score(normalizedScore)
			.reasoning(reasoning)
			.checks(checks)
			.metadata("baselineLineCoverage", baselineLineCoverage)
			.metadata("currentLineCoverage", current.lineCoverage())
			.metadata("improvementPp", improvement)
			.metadata("normalizedScore", normalizedScore)
			.metadata("maxImprovement", maxImprovement)
			.metadata("minimumLineCoverage", minimumLineCoverage)
			.build();
	}

	/**
	 * Get the max improvement used for normalization.
	 * @return the improvement value that maps to score 1.0
	 */
	public double getMaxImprovement() {
		return maxImprovement;
	}

	/**
	 * Get the minimum line coverage threshold.
	 * @return minimum required line coverage percentage (0 means no minimum)
	 */
	public double getMinimumLineCoverage() {
		return minimumLineCoverage;
	}

	/**
	 * Configures a producer without executing or acquiring evidence.
	 * @return typed evidence stage
	 */
	public static io.github.markpollack.judge.construction.EvidenceStep<CoverageComparison> builder() {
		return io.github.markpollack.judge.construction.EvidenceSteps
			.of(source -> new CoverageImprovementJudge(source));
	}

	/**
	 * Configures a producer without executing or acquiring evidence.
	 * @param maxImprovement producer configuration
	 * @return typed evidence stage
	 */
	public static io.github.markpollack.judge.construction.EvidenceStep<CoverageComparison> builder(
			double maxImprovement) {
		return io.github.markpollack.judge.construction.EvidenceSteps
			.of(source -> new CoverageImprovementJudge(source, maxImprovement));
	}

	/**
	 * Configures a producer without executing or acquiring evidence.
	 * @param maxImprovement producer configuration
	 * @param minimumLineCoverage producer configuration
	 * @return typed evidence stage
	 */
	public static io.github.markpollack.judge.construction.EvidenceStep<CoverageComparison> builder(
			double maxImprovement, double minimumLineCoverage) {
		return io.github.markpollack.judge.construction.EvidenceSteps
			.of(source -> new CoverageImprovementJudge(source, maxImprovement, minimumLineCoverage));
	}

}
