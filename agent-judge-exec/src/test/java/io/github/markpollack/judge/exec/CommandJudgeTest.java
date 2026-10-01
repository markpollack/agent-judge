/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.exec;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import io.github.markpollack.judge.judgment.Judgment;
import io.github.markpollack.judge.judgment.JudgmentStatus;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class CommandJudgeTest {

	@TempDir
	Path tempDir;

	@Test
	void successfulCommandPassesJudgment() throws Exception {
		// Create a test file
		Path testFile = tempDir.resolve("test.txt");
		Files.writeString(testFile, "test content");

		// Command that should succeed (list files)
		io.github.markpollack.judge.construction.EvidenceStep<Path> judge = CommandJudge.builder("ls test.txt");
		Judgment judgment = judge.evidence(createContext()).build().judge();

		assertThat(judgment.pass()).isTrue();
		assertThat(judgment.reasoning()).contains("succeeded").contains("exit code 0");
		assertThat(judgment.checks()).hasSize(1);
		assertThat(judgment.checks().get(0).judgment().pass()).isTrue();
		assertThat(judgment.checks().get(0).id()).isEqualTo("command_execution");

		// Verify metadata
		assertThat(judgment.metadata()).containsEntry("command", "ls test.txt").containsEntry("exitCode", 0);
	}

	@Test
	void failingCommandFailsJudgment() {
		// Command that should fail (non-existent command)
		io.github.markpollack.judge.construction.EvidenceStep<Path> judge = CommandJudge
			.builder("nonexistentcommand123");
		Judgment judgment = judge.evidence(createContext()).build().judge();

		assertThat(judgment.status()).isEqualTo(JudgmentStatus.FAIL);
		assertThat(judgment.checks()).hasSize(1);
		assertThat(judgment.checks().get(0).judgment().status()).isEqualTo(JudgmentStatus.FAIL);
	}

	@Test
	void executionFailureProducesErrorRatherThanFail() {
		io.github.markpollack.judge.construction.EvidenceStep<Path> judge = CommandJudge.builder("echo never-runs", 0,
				Duration.ofSeconds(5), path -> {
					throw new IllegalStateException("sandbox unavailable");
				});

		Judgment judgment = judge.evidence(createContext()).build().judge();

		assertThat(judgment.status()).isEqualTo(JudgmentStatus.ERROR);
		assertThat(judgment.reasoning()).contains("sandbox unavailable");
		assertThat(judgment.checks()).singleElement().satisfies(check -> {
			assertThat(check.id()).isEqualTo("command_execution");
			assertThat(check.judgment().status()).isEqualTo(JudgmentStatus.ERROR);
			assertThat(check.judgment().finding()).isNull();
		});
	}

	@Test
	void customExitCodeJudgment() {
		// Command that exits with code 1 (grep with no match)
		io.github.markpollack.judge.construction.EvidenceStep<Path> judge = CommandJudge
			.builder("grep 'nonexistent' /dev/null", 1, Duration.ofSeconds(5));
		Judgment judgment = judge.evidence(createContext()).build().judge();

		assertThat(judgment.pass()).isTrue(); // Expects exit code 1
		assertThat(judgment.metadata()).containsEntry("expectedExitCode", 1).containsEntry("exitCode", 1);
	}

	@Test
	void commandOutputCapturedInMetadata() throws Exception {
		// Create test file with content
		Path testFile = tempDir.resolve("output.txt");
		Files.writeString(testFile, "Hello Judge");

		io.github.markpollack.judge.construction.EvidenceStep<Path> judge = CommandJudge.builder("cat output.txt");
		Judgment judgment = judge.evidence(createContext()).build().judge();

		assertThat(judgment.pass()).isTrue();
		assertThat(judgment.metadata()).containsKey("output");
		assertThat((String) judgment.metadata().get("output")).contains("Hello Judge");
	}

	@Test
	void metadataIncludesCommandDetails() {
		io.github.markpollack.judge.construction.EvidenceStep<Path> judge = CommandJudge.builder("echo 'test'", 0,
				Duration.ofSeconds(10));
		Judgment judgment = judge.evidence(createContext()).build().judge();

		assertThat(judgment.metadata()).containsEntry("command", "echo 'test'")
			.containsEntry("expectedExitCode", 0)
			.containsKey("exitCode")
			.containsKey("output")
			.containsKey("elapsedMillis");
		assertThat(judgment.metadata().get("elapsedMillis"))
			.as("timing is a portable integer, not an ISO-8601 rendering of a Duration")
			.isInstanceOf(Integer.class);
		assertThat(judgment.elapsed()).isNotNull().isGreaterThanOrEqualTo(Duration.ZERO);
	}

	private Path createContext() {
		return tempDir;
	}

}
