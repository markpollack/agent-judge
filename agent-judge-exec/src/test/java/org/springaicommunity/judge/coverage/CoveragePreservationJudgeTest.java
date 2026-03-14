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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link CoveragePreservationJudge}.
 *
 * @author Mark Pollack
 * @since 0.9.0
 */
class CoveragePreservationJudgeTest {

	@TempDir
	Path workspace;

	private final CoveragePreservationJudge judge = new CoveragePreservationJudge();

	@Test
	void withinThresholdReturnsPass() throws IOException {
		writeJacocoReport(80, 20); // 80% coverage
		CoverageMetrics baseline = new CoverageMetrics(82.0, 0, 0, 82, 100, 0, 0, 0, 0, "baseline");

		Judgment judgment = judge.judge(contextWithBaseline(baseline));

		assertThat(judgment.status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(judgment.reasoning()).contains("within threshold");
	}

	@Test
	void exceedsThresholdReturnsFail() throws IOException {
		writeJacocoReport(70, 30); // 70% coverage
		CoverageMetrics baseline = new CoverageMetrics(82.0, 0, 0, 82, 100, 0, 0, 0, 0, "baseline");

		Judgment judgment = judge.judge(contextWithBaseline(baseline));

		assertThat(judgment.status()).isEqualTo(JudgmentStatus.FAIL);
		assertThat(judgment.reasoning()).contains("exceeds threshold");
	}

	@Test
	void exactThresholdReturnsPass() throws IOException {
		writeJacocoReport(75, 25); // 75% — exactly 5pp drop from 80%
		CoverageMetrics baseline = new CoverageMetrics(80.0, 0, 0, 80, 100, 0, 0, 0, 0, "baseline");

		Judgment judgment = judge.judge(contextWithBaseline(baseline));

		assertThat(judgment.status()).isEqualTo(JudgmentStatus.PASS);
	}

	@Test
	void noReportReturnsFail() {
		CoverageMetrics baseline = new CoverageMetrics(80.0, 0, 0, 80, 100, 0, 0, 0, 0, "baseline");

		Judgment judgment = judge.judge(contextWithBaseline(baseline));

		assertThat(judgment.status()).isEqualTo(JudgmentStatus.FAIL);
		assertThat(judgment.reasoning()).contains("No JaCoCo report");
	}

	@Test
	void stringBaselineIsAccepted() throws IOException {
		writeJacocoReport(80, 20); // 80% coverage
		JudgmentContext context = contextWithMetadata(Map.of("baselineCoverage", "82.0"));

		Judgment judgment = judge.judge(context);

		assertThat(judgment.status()).isEqualTo(JudgmentStatus.PASS);
	}

	@Test
	void noBaselineReturnsAbstain() throws IOException {
		writeJacocoReport(80, 20);

		Judgment judgment = judge.judge(contextWithMetadata(Map.of()));

		assertThat(judgment.status()).isEqualTo(JudgmentStatus.ABSTAIN);
		assertThat(judgment.reasoning()).contains("No baselineCoverage");
	}

	@Test
	void customThreshold() throws IOException {
		CoveragePreservationJudge strict = new CoveragePreservationJudge(2.0);
		writeJacocoReport(77, 23); // 77% — 3pp drop from 80%
		CoverageMetrics baseline = new CoverageMetrics(80.0, 0, 0, 80, 100, 0, 0, 0, 0, "baseline");

		Judgment judgment = strict.judge(contextWithBaseline(baseline));

		assertThat(judgment.status()).isEqualTo(JudgmentStatus.FAIL);
		assertThat(judgment.reasoning()).contains("2.0");
	}

	@Test
	void coverageImprovedReturnsPass() throws IOException {
		writeJacocoReport(90, 10); // 90% — improved from baseline
		CoverageMetrics baseline = new CoverageMetrics(80.0, 0, 0, 80, 100, 0, 0, 0, 0, "baseline");

		Judgment judgment = judge.judge(contextWithBaseline(baseline));

		assertThat(judgment.status()).isEqualTo(JudgmentStatus.PASS);
	}

	@Test
	void metadataContainsCoverageDetails() throws IOException {
		writeJacocoReport(78, 22); // 78%
		CoverageMetrics baseline = new CoverageMetrics(80.0, 0, 0, 80, 100, 0, 0, 0, 0, "baseline");

		Judgment judgment = judge.judge(contextWithBaseline(baseline));

		assertThat(judgment.metadata()).containsEntry("baselineLineCoverage", 80.0);
		assertThat(judgment.metadata().get("currentLineCoverage")).isNotNull();
		assertThat(judgment.metadata()).containsKey("coverageDrop");
		assertThat(judgment.metadata()).containsEntry("threshold", 5.0);
	}

	// ==================== Helpers ====================

	private JudgmentContext contextWithBaseline(CoverageMetrics baseline) {
		return contextWithMetadata(Map.of("baselineCoverage", baseline));
	}

	private JudgmentContext contextWithMetadata(Map<String, Object> metadata) {
		return JudgmentContext.builder()
			.goal("Test migration")
			.workspace(workspace)
			.agentOutput("output")
			.status(ExecutionStatus.SUCCESS)
			.startedAt(Instant.now())
			.executionTime(Duration.ofSeconds(1))
			.metadata(metadata)
			.build();
	}

	/**
	 * Write a minimal JaCoCo XML report to the workspace.
	 */
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
