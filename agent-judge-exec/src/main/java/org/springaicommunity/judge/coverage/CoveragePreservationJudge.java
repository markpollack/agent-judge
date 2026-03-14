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

import org.springaicommunity.judge.DeterministicJudge;
import org.springaicommunity.judge.context.JudgmentContext;
import org.springaicommunity.judge.coverage.JaCoCoReportParser.CoverageMetrics;
import org.springaicommunity.judge.result.Check;
import org.springaicommunity.judge.result.Judgment;
import org.springaicommunity.judge.result.JudgmentStatus;
import org.springaicommunity.judge.score.BooleanScore;

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
			return Judgment.fail("No JaCoCo report found in workspace — coverage cannot be verified");
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

		return Judgment.builder()
			.score(new BooleanScore(pass))
			.status(pass ? JudgmentStatus.PASS : JudgmentStatus.FAIL)
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
