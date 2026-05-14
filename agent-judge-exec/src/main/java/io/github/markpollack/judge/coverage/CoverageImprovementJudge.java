/*
 * Copyright 2024 Spring AI Community
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.markpollack.judge.coverage;

import java.util.List;

import io.github.markpollack.judge.DeterministicJudge;
import io.github.markpollack.judge.context.JudgmentContext;
import io.github.markpollack.judge.coverage.JaCoCoReportParser.CoverageMetrics;
import io.github.markpollack.judge.result.Check;
import io.github.markpollack.judge.result.Judgment;
import io.github.markpollack.judge.result.JudgmentStatus;
import io.github.markpollack.judge.score.NumericalScore;

/**
 * Judge that measures test coverage improvement as a numerical score.
 *
 * <p>
 * Compares the final JaCoCo coverage against a baseline from
 * {@code metadata("baselineCoverage")} and produces a normalized score representing the
 * coverage delta. The score is normalized to 0-1 where 0 means no improvement and 1 means
 * the maximum expected improvement was achieved.
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
public class CoverageImprovementJudge extends DeterministicJudge {

	private static final double DEFAULT_MAX_IMPROVEMENT = 50.0;

	private static final double DEFAULT_MINIMUM_LINE_COVERAGE = 0.0;

	private final double maxImprovement;

	private final double minimumLineCoverage;

	/**
	 * Create with default max improvement of 50 percentage points and no minimum coverage
	 * requirement.
	 */
	public CoverageImprovementJudge() {
		this(DEFAULT_MAX_IMPROVEMENT, DEFAULT_MINIMUM_LINE_COVERAGE);
	}

	/**
	 * Create with custom max improvement for normalization and no minimum coverage
	 * requirement.
	 * @param maxImprovement the improvement value that maps to score 1.0
	 */
	public CoverageImprovementJudge(double maxImprovement) {
		this(maxImprovement, DEFAULT_MINIMUM_LINE_COVERAGE);
	}

	/**
	 * Create with custom max improvement and minimum line coverage threshold.
	 * @param maxImprovement the improvement value that maps to score 1.0
	 * @param minimumLineCoverage minimum required line coverage percentage (0–100); fails
	 * if current coverage is below this value regardless of improvement
	 */
	public CoverageImprovementJudge(double maxImprovement, double minimumLineCoverage) {
		super("CoverageImprovementJudge", "Measures test coverage improvement as a normalized score (0-1)");
		this.maxImprovement = maxImprovement;
		this.minimumLineCoverage = minimumLineCoverage;
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
			return Judgment.fail("No JaCoCo report found in workspace — coverage cannot be verified");
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

		return Judgment.builder()
			.score(NumericalScore.normalized(normalizedScore))
			.status(pass ? JudgmentStatus.PASS : JudgmentStatus.FAIL)
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

}
