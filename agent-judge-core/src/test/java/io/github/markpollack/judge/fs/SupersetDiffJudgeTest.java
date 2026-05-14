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

package io.github.markpollack.judge.fs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import io.github.markpollack.judge.JudgeType;
import io.github.markpollack.judge.context.ExecutionStatus;
import io.github.markpollack.judge.context.JudgmentContext;
import io.github.markpollack.judge.result.Judgment;
import io.github.markpollack.judge.result.JudgmentStatus;
import io.github.markpollack.judge.score.NumericalScore;

import static org.assertj.core.api.Assertions.assertThat;

class SupersetDiffJudgeTest {

	@TempDir
	Path workspace;

	@TempDir
	Path referenceDir;

	private final SupersetDiffJudge judge = new SupersetDiffJudge();

	@Test
	void passesWhenWorkspaceMatchesReference() throws IOException {
		writeFile(referenceDir, "src/Main.java", "public class Main {}");
		writeFile(referenceDir, "pom.xml", "<project/>");
		writeFile(workspace, "src/Main.java", "public class Main {}");
		writeFile(workspace, "pom.xml", "<project/>");

		Judgment judgment = judge.judge(contextWithReference(referenceDir));

		assertThat(judgment.status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(judgment.checks()).hasSize(2);
		assertThat(judgment.checks()).allMatch(c -> c.passed());
		assertThat(judgment.reasoning()).contains("All 2 reference files matched");
		assertScore(judgment, 1.0);
	}

	@Test
	void passesWhenWorkspaceIsSupersetOfReference() throws IOException {
		writeFile(referenceDir, "src/Main.java", "public class Main {}");
		writeFile(workspace, "src/Main.java", "public class Main {}");
		writeFile(workspace, "src/Extra.java", "public class Extra {}");
		writeFile(workspace, "README.md", "# Hello");

		Judgment judgment = judge.judge(contextWithReference(referenceDir));

		assertThat(judgment.status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(judgment.checks()).hasSize(1);
		assertThat(judgment.reasoning()).contains("All 1 reference files matched");
	}

	@Test
	void failsWhenFileMissing() throws IOException {
		writeFile(referenceDir, "src/Main.java", "public class Main {}");
		writeFile(referenceDir, "pom.xml", "<project/>");
		writeFile(workspace, "pom.xml", "<project/>");

		Judgment judgment = judge.judge(contextWithReference(referenceDir));

		assertThat(judgment.status()).isEqualTo(JudgmentStatus.FAIL);
		assertThat(judgment.reasoning()).contains("1 of 2");
		assertScore(judgment, 0.5);

		assertThat(judgment.checks()).hasSize(2);
		assertThat(judgment.checks()).filteredOn(c -> !c.passed())
			.hasSize(1)
			.first()
			.satisfies(c -> assertThat(c.message()).contains("Missing file"));
	}

	@Test
	void failsWhenContentDiffers() throws IOException {
		writeFile(referenceDir, "src/Main.java", "public class Main {}");
		writeFile(workspace, "src/Main.java", "public class Main { int x; }");

		Judgment judgment = judge.judge(contextWithReference(referenceDir));

		assertThat(judgment.status()).isEqualTo(JudgmentStatus.FAIL);
		assertScore(judgment, 0.0);

		assertThat(judgment.checks()).hasSize(1);
		assertThat(judgment.checks().get(0).passed()).isFalse();
		assertThat(judgment.checks().get(0).message()).contains("Content differs");
	}

	@Test
	void partialMatchReportsCorrectScore() throws IOException {
		writeFile(referenceDir, "a.txt", "aaa");
		writeFile(referenceDir, "b.txt", "bbb");
		writeFile(referenceDir, "c.txt", "ccc");
		writeFile(workspace, "a.txt", "aaa");
		writeFile(workspace, "b.txt", "WRONG");
		// c.txt missing

		Judgment judgment = judge.judge(contextWithReference(referenceDir));

		assertThat(judgment.status()).isEqualTo(JudgmentStatus.FAIL);
		assertThat(judgment.reasoning()).contains("1 of 3");
		assertScore(judgment, 1.0 / 3.0);
	}

	@Test
	void excludesMatchingPaths() throws IOException {
		writeFile(referenceDir, "src/Main.java", "public class Main {}");
		writeFile(referenceDir, ".mvn/wrapper.properties", "distributionUrl=...");
		writeFile(referenceDir, "mvnw", "#!/bin/bash");
		writeFile(workspace, "src/Main.java", "public class Main {}");
		// .mvn/ and mvnw not in workspace — should be excluded

		SupersetDiffJudge judgeWithExcludes = new SupersetDiffJudge(Set.of(".mvn/", "mvnw"));

		Judgment judgment = judgeWithExcludes.judge(contextWithReference(referenceDir));

		assertThat(judgment.status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(judgment.checks()).hasSize(1);
	}

	@Test
	void abstainWhenNoExpectedDirInMetadata() {
		Judgment judgment = judge.judge(contextWithMetadata(Map.of()));

		assertThat(judgment.status()).isEqualTo(JudgmentStatus.ABSTAIN);
		assertThat(judgment.reasoning()).contains("No expectedDir");
	}

	@Test
	void abstainWhenReferenceDirDoesNotExist() {
		Path nonexistent = workspace.resolve("nonexistent-ref");

		Judgment judgment = judge.judge(contextWithReference(nonexistent));

		assertThat(judgment.status()).isEqualTo(JudgmentStatus.ABSTAIN);
		assertThat(judgment.reasoning()).contains("does not exist");
	}

	@Test
	void abstainWhenReferenceDirIsEmpty() throws IOException {
		// referenceDir exists but has no files
		Judgment judgment = judge.judge(contextWithReference(referenceDir));

		assertThat(judgment.status()).isEqualTo(JudgmentStatus.ABSTAIN);
		assertThat(judgment.reasoning()).contains("No files");
	}

	@Test
	void handlesNestedDirectories() throws IOException {
		writeFile(referenceDir, "src/main/java/com/example/App.java", "class App {}");
		writeFile(referenceDir, "src/main/resources/application.properties", "server.port=8080");
		writeFile(workspace, "src/main/java/com/example/App.java", "class App {}");
		writeFile(workspace, "src/main/resources/application.properties", "server.port=8080");

		Judgment judgment = judge.judge(contextWithReference(referenceDir));

		assertThat(judgment.status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(judgment.checks()).hasSize(2);
	}

	@Test
	void acceptsPathObjectInMetadata() throws IOException {
		writeFile(referenceDir, "a.txt", "content");
		writeFile(workspace, "a.txt", "content");

		JudgmentContext context = JudgmentContext.builder()
			.goal("Test")
			.workspace(workspace)
			.executionTime(Duration.ofSeconds(1))
			.startedAt(Instant.now())
			.status(ExecutionStatus.SUCCESS)
			.metadata(SupersetDiffJudge.EXPECTED_DIR_KEY, referenceDir)
			.build();

		Judgment judgment = judge.judge(context);

		assertThat(judgment.status()).isEqualTo(JudgmentStatus.PASS);
	}

	@Test
	void acceptsStringPathInMetadata() throws IOException {
		writeFile(referenceDir, "a.txt", "content");
		writeFile(workspace, "a.txt", "content");

		JudgmentContext context = JudgmentContext.builder()
			.goal("Test")
			.workspace(workspace)
			.executionTime(Duration.ofSeconds(1))
			.startedAt(Instant.now())
			.status(ExecutionStatus.SUCCESS)
			.metadata(SupersetDiffJudge.EXPECTED_DIR_KEY, referenceDir.toString())
			.build();

		Judgment judgment = judge.judge(context);

		assertThat(judgment.status()).isEqualTo(JudgmentStatus.PASS);
	}

	@Test
	void includesMetadataInResult() throws IOException {
		writeFile(referenceDir, "a.txt", "aaa");
		writeFile(referenceDir, "b.txt", "bbb");
		writeFile(workspace, "a.txt", "aaa");
		writeFile(workspace, "b.txt", "bbb");

		Judgment judgment = judge.judge(contextWithReference(referenceDir));

		assertThat(judgment.metadata()).containsKey("expectedDir");
		assertThat(judgment.metadata().get("matchedFiles")).isEqualTo(2);
		assertThat(judgment.metadata().get("totalFiles")).isEqualTo(2);
	}

	@Test
	void hasCorrectMetadata() {
		assertThat(judge.metadata().name()).isEqualTo("SupersetDiffJudge");
		assertThat(judge.metadata().description()).contains("superset");
		assertThat(judge.metadata().type()).isEqualTo(JudgeType.DETERMINISTIC);
	}

	// ==================== Helpers ====================

	private JudgmentContext contextWithReference(Path refDir) {
		return JudgmentContext.builder()
			.goal("Test superset")
			.workspace(workspace)
			.executionTime(Duration.ofSeconds(1))
			.startedAt(Instant.now())
			.status(ExecutionStatus.SUCCESS)
			.metadata(SupersetDiffJudge.EXPECTED_DIR_KEY, refDir.toString())
			.build();
	}

	private JudgmentContext contextWithMetadata(Map<String, Object> metadata) {
		return JudgmentContext.builder()
			.goal("Test superset")
			.workspace(workspace)
			.executionTime(Duration.ofSeconds(1))
			.startedAt(Instant.now())
			.status(ExecutionStatus.SUCCESS)
			.metadata(metadata)
			.build();
	}

	private static void writeFile(Path dir, String relativePath, String content) throws IOException {
		Path file = dir.resolve(relativePath);
		Files.createDirectories(file.getParent());
		Files.writeString(file, content);
	}

	private static void assertScore(Judgment judgment, double expected) {
		assertThat(judgment.score()).isInstanceOf(NumericalScore.class);
		assertThat(((NumericalScore) judgment.score()).normalized()).isCloseTo(expected,
				org.assertj.core.data.Offset.offset(0.001));
	}

}
