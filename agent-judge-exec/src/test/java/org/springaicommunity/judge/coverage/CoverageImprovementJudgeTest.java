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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springaicommunity.judge.context.ExecutionStatus;
import org.springaicommunity.judge.context.JudgmentContext;
import org.springaicommunity.judge.coverage.JaCoCoReportParser.CoverageMetrics;
import org.springaicommunity.judge.result.Judgment;
import org.springaicommunity.judge.result.JudgmentStatus;
import org.springaicommunity.judge.score.NumericalScore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link CoverageImprovementJudge}.
 *
 * @author Mark Pollack
 * @since 0.9.0
 */
class CoverageImprovementJudgeTest {

	@TempDir
	Path workspace;

	private final CoverageImprovementJudge judge = new CoverageImprovementJudge();

	@Test
	void significantImprovementReturnsHighScore() throws IOException {
		writeJacocoReport(90, 10); // 90% current
		CoverageMetrics baseline = new CoverageMetrics(60.0, 0, 0, 60, 100, 0, 0, 0, 0, "baseline");

		Judgment judgment = judge.judge(contextWithBaseline(baseline));

		assertThat(judgment.status()).isEqualTo(JudgmentStatus.PASS);
		NumericalScore score = (NumericalScore) judgment.score();
		// 30pp improvement / 50pp max = 0.6
		assertThat(score.normalized()).isCloseTo(0.6, org.assertj.core.data.Offset.offset(0.01));
	}

	@Test
	void noImprovementReturnsFail() throws IOException {
		writeJacocoReport(60, 40); // 60% — same as baseline
		CoverageMetrics baseline = new CoverageMetrics(60.0, 0, 0, 60, 100, 0, 0, 0, 0, "baseline");

		Judgment judgment = judge.judge(contextWithBaseline(baseline));

		assertThat(judgment.status()).isEqualTo(JudgmentStatus.FAIL);
		NumericalScore score = (NumericalScore) judgment.score();
		assertThat(score.normalized()).isCloseTo(0.0, org.assertj.core.data.Offset.offset(0.01));
	}

	@Test
	void regressionReturnsFailWithZeroScore() throws IOException {
		writeJacocoReport(50, 50); // 50% — below baseline
		CoverageMetrics baseline = new CoverageMetrics(60.0, 0, 0, 60, 100, 0, 0, 0, 0, "baseline");

		Judgment judgment = judge.judge(contextWithBaseline(baseline));

		assertThat(judgment.status()).isEqualTo(JudgmentStatus.FAIL);
		NumericalScore score = (NumericalScore) judgment.score();
		assertThat(score.normalized()).isCloseTo(0.0, org.assertj.core.data.Offset.offset(0.01));
	}

	@Test
	void maxImprovementReturnsPerfectScore() throws IOException {
		writeJacocoReport(100, 0); // 100% current
		CoverageMetrics baseline = new CoverageMetrics(50.0, 0, 0, 50, 100, 0, 0, 0, 0, "baseline");

		Judgment judgment = judge.judge(contextWithBaseline(baseline));

		assertThat(judgment.status()).isEqualTo(JudgmentStatus.PASS);
		NumericalScore score = (NumericalScore) judgment.score();
		// 50pp improvement / 50pp max = 1.0
		assertThat(score.normalized()).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.01));
	}

	@Test
	void noReportReturnsAbstain() {
		CoverageMetrics baseline = new CoverageMetrics(80.0, 0, 0, 80, 100, 0, 0, 0, 0, "baseline");

		Judgment judgment = judge.judge(contextWithBaseline(baseline));

		assertThat(judgment.status()).isEqualTo(JudgmentStatus.ABSTAIN);
		assertThat(judgment.reasoning()).contains("No JaCoCo report");
	}

	@Test
	void noBaselineReturnsAbstain() throws IOException {
		writeJacocoReport(80, 20);

		Judgment judgment = judge.judge(contextWithMetadata(Map.of()));

		assertThat(judgment.status()).isEqualTo(JudgmentStatus.ABSTAIN);
		assertThat(judgment.reasoning()).contains("No baselineCoverage");
	}

	@Test
	void customMaxImprovement() throws IOException {
		CoverageImprovementJudge custom = new CoverageImprovementJudge(20.0);
		writeJacocoReport(80, 20); // 80% current
		CoverageMetrics baseline = new CoverageMetrics(70.0, 0, 0, 70, 100, 0, 0, 0, 0, "baseline");

		Judgment judgment = custom.judge(contextWithBaseline(baseline));

		assertThat(judgment.status()).isEqualTo(JudgmentStatus.PASS);
		NumericalScore score = (NumericalScore) judgment.score();
		// 10pp improvement / 20pp max = 0.5
		assertThat(score.normalized()).isCloseTo(0.5, org.assertj.core.data.Offset.offset(0.01));
	}

	@Test
	void metadataContainsCoverageDetails() throws IOException {
		writeJacocoReport(85, 15); // 85%
		CoverageMetrics baseline = new CoverageMetrics(70.0, 0, 0, 70, 100, 0, 0, 0, 0, "baseline");

		Judgment judgment = judge.judge(contextWithBaseline(baseline));

		assertThat(judgment.metadata()).containsEntry("baselineLineCoverage", 70.0);
		assertThat(judgment.metadata().get("currentLineCoverage")).isNotNull();
		assertThat(judgment.metadata()).containsKey("improvementPp");
		assertThat(judgment.metadata()).containsKey("normalizedScore");
		assertThat(judgment.metadata()).containsEntry("maxImprovement", 50.0);
	}

	// ==================== Helpers ====================

	private JudgmentContext contextWithBaseline(CoverageMetrics baseline) {
		return contextWithMetadata(Map.of("baselineCoverage", baseline));
	}

	private JudgmentContext contextWithMetadata(Map<String, Object> metadata) {
		return JudgmentContext.builder()
			.goal("Improve test coverage")
			.workspace(workspace)
			.agentOutput("output")
			.status(ExecutionStatus.SUCCESS)
			.startedAt(Instant.now())
			.executionTime(Duration.ofSeconds(1))
			.metadata(metadata)
			.build();
	}

	private void writeJacocoReport(int linesCovered, int linesMissed) throws IOException {
		Path reportDir = workspace.resolve("target/site/jacoco");
		Files.createDirectories(reportDir);
		String xml = """
				<?xml version="1.0" encoding="UTF-8"?>
				<report name="test">
				  <counter type="LINE" covered="%d" missed="%d"/>
				  <counter type="BRANCH" covered="0" missed="0"/>
				  <counter type="METHOD" covered="0" missed="0"/>
				</report>
				""".formatted(linesCovered, linesMissed);
		Files.writeString(reportDir.resolve("jacoco.xml"), xml);
	}

}
