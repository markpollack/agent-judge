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

package org.springaicommunity.judge.coverage;

import java.util.List;

import org.springaicommunity.judge.DeterministicJudge;
import org.springaicommunity.judge.context.JudgmentContext;
import org.springaicommunity.judge.coverage.JaCoCoReportParser.CoverageMetrics;
import org.springaicommunity.judge.result.Check;
import org.springaicommunity.judge.result.Judgment;
import org.springaicommunity.judge.result.JudgmentStatus;
import org.springaicommunity.judge.score.NumericalScore;

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

	private final double maxImprovement;

	/**
	 * Create with default max improvement of 50 percentage points.
	 */
	public CoverageImprovementJudge() {
		this(DEFAULT_MAX_IMPROVEMENT);
	}

	/**
	 * Create with custom max improvement for normalization.
	 * @param maxImprovement the improvement value that maps to score 1.0
	 */
	public CoverageImprovementJudge(double maxImprovement) {
		super("CoverageImprovementJudge", "Measures test coverage improvement as a normalized score (0-1)");
		this.maxImprovement = maxImprovement;
	}

	@Override
	public Judgment judge(JudgmentContext context) {
		Object baselineObj = context.metadata().get("baselineCoverage");
		if (baselineObj == null) {
			return Judgment.abstain("No baselineCoverage in metadata");
		}

		if (!(baselineObj instanceof CoverageMetrics baseline)) {
			return Judgment.abstain("baselineCoverage is not CoverageMetrics: " + baselineObj.getClass().getName());
		}

		CoverageMetrics current = JaCoCoReportParser.parse(context.workspace());
		if (current.linesTotal() == 0 && current.summary().contains("not found")) {
			return Judgment.abstain("No JaCoCo report found in workspace");
		}

		double improvement = current.lineCoverage() - baseline.lineCoverage();
		double normalizedScore = Math.max(0.0, Math.min(1.0, improvement / maxImprovement));

		String reasoning = String.format(
				"Line coverage improved %.1f percentage points (%.1f%% → %.1f%%). "
						+ "Normalized score: %.3f (max improvement: %.1f pp)",
				improvement, baseline.lineCoverage(), current.lineCoverage(), normalizedScore, maxImprovement);

		Check improvementCheck = improvement > 0
				? Check.pass("coverage_improved",
						String.format("+%.1f pp (%.1f%% → %.1f%%)", improvement, baseline.lineCoverage(),
								current.lineCoverage()))
				: Check.fail("coverage_improved", String.format("%.1f pp (%.1f%% → %.1f%%)", improvement,
						baseline.lineCoverage(), current.lineCoverage()));

		return Judgment.builder()
			.score(NumericalScore.normalized(normalizedScore))
			.status(improvement > 0 ? JudgmentStatus.PASS : JudgmentStatus.FAIL)
			.reasoning(reasoning)
			.checks(List.of(improvementCheck))
			.metadata("baselineLineCoverage", baseline.lineCoverage())
			.metadata("currentLineCoverage", current.lineCoverage())
			.metadata("improvementPp", improvement)
			.metadata("normalizedScore", normalizedScore)
			.metadata("maxImprovement", maxImprovement)
			.build();
	}

	/**
	 * Get the max improvement used for normalization.
	 * @return the improvement value that maps to score 1.0
	 */
	public double getMaxImprovement() {
		return maxImprovement;
	}

}
