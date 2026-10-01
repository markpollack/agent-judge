/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.file;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import io.github.markpollack.judge.JudgeType;
import io.github.markpollack.judge.judgment.Judgment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FileExistsJudgeTest {

	@TempDir
	Path tempDir;

	@Test
	void passesWhenFileExists() throws IOException {
		// Create a test file
		Path testFile = tempDir.resolve("test.txt");
		Files.writeString(testFile, "test content");

		io.github.markpollack.judge.construction.EvidenceStep<Path> judge = FileExistsJudge.builder("test.txt");

		Path context = tempDir;

		Judgment judgment = judge.evidence(context).build().judge();

		assertThat(judgment.pass()).isTrue();
		assertThat(judgment.score()).isNull();
		assertThat(judgment.effectiveScore()).hasValue(1.0);
		assertThat(judgment.reasoning()).contains("File exists");
		assertThat(judgment.checks()).hasSize(1);
		assertThat(judgment.checks().get(0).judgment().pass()).isTrue();
	}

	@Test
	void failsWhenFileDoesNotExist() {
		io.github.markpollack.judge.construction.EvidenceStep<Path> judge = FileExistsJudge.builder("nonexistent.txt");

		Path context = tempDir;

		Judgment judgment = judge.evidence(context).build().judge();

		assertThat(judgment.pass()).isFalse();
		assertThat(judgment.effectiveScore()).hasValue(0.0);
		assertThat(judgment.reasoning()).contains("File not found");
		assertThat(judgment.checks()).hasSize(1);
		assertThat(judgment.checks().get(0).judgment().pass()).isFalse();
	}

	@Test
	void hasCorrectMetadata() {
		io.github.markpollack.judge.construction.EvidenceStep<Path> judge = FileExistsJudge.builder("test.txt");

		assertThat(((io.github.markpollack.judge.JudgeWithMetadata) judge.evidence(tempDir).build()).metadata().name())
			.isEqualTo("FileExistsJudge");
		assertThat(((io.github.markpollack.judge.JudgeWithMetadata) judge.evidence(tempDir).build()).metadata()
			.description()).contains("test.txt");
		assertThat(((io.github.markpollack.judge.JudgeWithMetadata) judge.evidence(tempDir).build()).metadata().type())
			.isEqualTo(JudgeType.DETERMINISTIC);
	}

}
