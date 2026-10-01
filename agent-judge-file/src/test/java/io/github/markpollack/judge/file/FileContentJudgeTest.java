/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.file;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import io.github.markpollack.judge.file.FileContentJudge.MatchMode;
import io.github.markpollack.judge.judgment.Judgment;
import io.github.markpollack.judge.judgment.JudgmentStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FileContentJudgeTest {

	@TempDir
	Path tempDir;

	@Test
	void exactMatchPassesWhenContentMatches() throws IOException {
		Path testFile = tempDir.resolve("test.txt");
		Files.writeString(testFile, "Hello World");

		io.github.markpollack.judge.construction.EvidenceStep<Path> judge = FileContentJudge.builder("test.txt",
				"Hello World", MatchMode.EXACT);
		Judgment judgment = judge.evidence(createContext()).build().judge();

		assertThat(judgment.pass()).isTrue();
		assertThat(judgment.reasoning()).contains("exact").contains("matches");
		assertThat(judgment.checks()).hasSize(3);
		assertThat(judgment.checks()).allMatch(check -> check.judgment().pass());
	}

	@Test
	void exactMatchFailsWhenContentDiffers() throws IOException {
		Path testFile = tempDir.resolve("test.txt");
		Files.writeString(testFile, "Hello World");

		io.github.markpollack.judge.construction.EvidenceStep<Path> judge = FileContentJudge.builder("test.txt",
				"Goodbye World", MatchMode.EXACT);
		Judgment judgment = judge.evidence(createContext()).build().judge();

		assertThat(judgment.status()).isEqualTo(JudgmentStatus.FAIL);
		assertThat(judgment.checks()).hasSize(3);
		assertThat(judgment.checks().get(2).judgment().pass()).isFalse();
		assertThat(judgment.checks().get(2).id()).isEqualTo("content_match");
	}

	@Test
	void readFailureProducesErrorRatherThanFail() throws IOException {
		Files.createDirectory(tempDir.resolve("directory.txt"));
		io.github.markpollack.judge.construction.EvidenceStep<Path> judge = FileContentJudge.builder("directory.txt",
				"content", MatchMode.EXACT);

		Judgment judgment = judge.evidence(createContext()).build().judge();

		assertThat(judgment.status()).isEqualTo(JudgmentStatus.ERROR);
		assertThat(judgment.reasoning()).contains("Failed to read file");
		assertThat(judgment.checks()).extracting("id").containsExactly("file_exists", "file_readable");
		assertThat(judgment.checks().get(0).judgment().pass()).isTrue();
		assertThat(judgment.checks().get(1).judgment().pass()).isFalse();
	}

	@Test
	void containsMatchPassesWhenContentContainsString() throws IOException {
		Path testFile = tempDir.resolve("log.txt");
		Files.writeString(testFile, "Build completed successfully at 10:30 AM");

		io.github.markpollack.judge.construction.EvidenceStep<Path> judge = FileContentJudge.builder("log.txt",
				"successfully", MatchMode.CONTAINS);
		Judgment judgment = judge.evidence(createContext()).build().judge();

		assertThat(judgment.pass()).isTrue();
		assertThat(judgment.reasoning()).contains("contains").contains("matches");
	}

	@Test
	void containsMatchFailsWhenContentMissing() throws IOException {
		Path testFile = tempDir.resolve("log.txt");
		Files.writeString(testFile, "Build failed");

		io.github.markpollack.judge.construction.EvidenceStep<Path> judge = FileContentJudge.builder("log.txt",
				"successfully", MatchMode.CONTAINS);
		Judgment judgment = judge.evidence(createContext()).build().judge();

		assertThat(judgment.pass()).isFalse();
	}

	@Test
	void regexMatchPassesWhenPatternMatches() throws IOException {
		Path testFile = tempDir.resolve("data.json");
		Files.writeString(testFile, "{\"status\": \"success\", \"count\": 42}");

		io.github.markpollack.judge.construction.EvidenceStep<Path> judge = FileContentJudge.builder("data.json",
				"\\{.*\"status\".*\\}", MatchMode.REGEX);
		Judgment judgment = judge.evidence(createContext()).build().judge();

		assertThat(judgment.pass()).isTrue();
		assertThat(judgment.reasoning()).contains("regex").contains("matches");
	}

	@Test
	void regexMatchFailsWhenPatternDoesNotMatch() throws IOException {
		Path testFile = tempDir.resolve("data.txt");
		Files.writeString(testFile, "plain text");

		io.github.markpollack.judge.construction.EvidenceStep<Path> judge = FileContentJudge.builder("data.txt",
				"^\\d+$", MatchMode.REGEX);
		Judgment judgment = judge.evidence(createContext()).build().judge();

		assertThat(judgment.pass()).isFalse();
	}

	@Test
	void failsWhenFileDoesNotExist() {
		io.github.markpollack.judge.construction.EvidenceStep<Path> judge = FileContentJudge.builder("missing.txt",
				"content", MatchMode.EXACT);
		Judgment judgment = judge.evidence(createContext()).build().judge();

		assertThat(judgment.pass()).isFalse();
		assertThat(judgment.reasoning()).contains("not found");
		assertThat(judgment.checks()).hasSize(1);
		assertThat(judgment.checks().get(0).id()).isEqualTo("file_exists");
		assertThat(judgment.checks().get(0).judgment().pass()).isFalse();
	}

	@Test
	void defaultsToExactMatch() throws IOException {
		Path testFile = tempDir.resolve("test.txt");
		Files.writeString(testFile, "exact");

		io.github.markpollack.judge.construction.EvidenceStep<Path> judge = FileContentJudge.builder("test.txt",
				"exact");
		Judgment judgment = judge.evidence(createContext()).build().judge();

		assertThat(judgment.pass()).isTrue();
	}

	private Path createContext() {
		return tempDir;
	}

}
