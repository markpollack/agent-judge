/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.exec;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import io.github.markpollack.judge.judgment.Judgment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class BuildSuccessJudgeTest {

	@TempDir
	Path tempDir;

	@Test
	void customBuildCommandExecutes() {
		io.github.markpollack.judge.construction.EvidenceStep<Path> judge = BuildSuccessJudge
			.builder("echo 'Building...'");
		Judgment judgment = judge.evidence(createContext()).build().judge();

		assertThat(judgment.pass()).isTrue();
		assertThat(judgment.reasoning()).contains("succeeded");
	}

	@Test
	void mavenDetectsWrapperWhenPresent() throws IOException {
		// Create mvnw wrapper
		Path mvnw = tempDir.resolve("mvnw");
		Files.writeString(mvnw, "#!/bin/bash\necho 'Maven wrapper'\nexit 0");
		makeExecutable(mvnw);

		io.github.markpollack.judge.construction.EvidenceStep<Path> judge = BuildSuccessJudge.maven("--version");
		Judgment judgment = judge.evidence(createContext()).build().judge();

		assertThat(judgment.pass()).isTrue();
		// Verify it used wrapper (check output contains wrapper script output)
		String output = (String) judgment.metadata().get("output");
		assertThat(output).contains("Maven wrapper");
	}

	@Test
	void mavenFallsBackToMvnWhenWrapperMissing() {
		// No mvnw in tempDir
		io.github.markpollack.judge.construction.EvidenceStep<Path> judge = BuildSuccessJudge.maven("--version");
		Judgment judgment = judge.evidence(createContext()).build().judge();

		// This will use system 'mvn' which may or may not exist
		// We just verify the judge executes (pass/fail depends on system)
		assertThat(judgment).isNotNull();
		assertThat(judgment.metadata()).containsKey("command");
	}

	@Test
	void gradleDetectsWrapperWhenPresent() throws IOException {
		// Create gradlew wrapper
		Path gradlew = tempDir.resolve("gradlew");
		Files.writeString(gradlew, "#!/bin/bash\necho 'Gradle wrapper'\nexit 0");
		makeExecutable(gradlew);

		io.github.markpollack.judge.construction.EvidenceStep<Path> judge = BuildSuccessJudge.gradle("--version");
		Judgment judgment = judge.evidence(createContext()).build().judge();

		assertThat(judgment.pass()).isTrue();
		String output = (String) judgment.metadata().get("output");
		assertThat(output).contains("Gradle wrapper");
	}

	@Test
	void gradleFallsBackToGradleWhenWrapperMissing() {
		// No gradlew in tempDir
		io.github.markpollack.judge.construction.EvidenceStep<Path> judge = BuildSuccessJudge.gradle("--version");
		Judgment judgment = judge.evidence(createContext()).build().judge();

		// This will use system 'gradle' which may or may not exist
		assertThat(judgment).isNotNull();
		assertThat(judgment.metadata()).containsKey("command");
	}

	@Test
	void mavenSupportsMultipleGoals() throws IOException {
		Path mvnw = tempDir.resolve("mvnw");
		Files.writeString(mvnw, "#!/bin/bash\necho \"Goals: $@\"\nexit 0");
		makeExecutable(mvnw);

		io.github.markpollack.judge.construction.EvidenceStep<Path> judge = BuildSuccessJudge.maven("clean", "compile",
				"test");
		Judgment judgment = judge.evidence(createContext()).build().judge();

		assertThat(judgment.pass()).isTrue();
		String output = (String) judgment.metadata().get("output");
		assertThat(output).contains("clean compile test");
	}

	@Test
	void gradleSupportsMultipleTasks() throws IOException {
		Path gradlew = tempDir.resolve("gradlew");
		Files.writeString(gradlew, "#!/bin/bash\necho \"Tasks: $@\"\nexit 0");
		makeExecutable(gradlew);

		io.github.markpollack.judge.construction.EvidenceStep<Path> judge = BuildSuccessJudge.gradle("clean", "build",
				"test");
		Judgment judgment = judge.evidence(createContext()).build().judge();

		assertThat(judgment.pass()).isTrue();
		String output = (String) judgment.metadata().get("output");
		assertThat(output).contains("clean build test");
	}

	@Test
	void buildFailureDetected() throws IOException {
		// Create failing build script
		Path mvnw = tempDir.resolve("mvnw");
		Files.writeString(mvnw, "#!/bin/bash\necho 'BUILD FAILURE'\nexit 1");
		makeExecutable(mvnw);

		io.github.markpollack.judge.construction.EvidenceStep<Path> judge = BuildSuccessJudge.maven("compile");
		Judgment judgment = judge.evidence(createContext()).build().judge();

		assertThat(judgment.pass()).isFalse();
		assertThat(judgment.reasoning()).contains("failed");
	}

	private Path createContext() {
		return tempDir;
	}

	private void makeExecutable(Path file) throws IOException {
		if (System.getProperty("os.name").toLowerCase().contains("win")) {
			// Windows - file is executable by default
			return;
		}
		// Unix-like - set executable permission
		Set<PosixFilePermission> perms = Files.getPosixFilePermissions(file);
		perms.add(PosixFilePermission.OWNER_EXECUTE);
		perms.add(PosixFilePermission.GROUP_EXECUTE);
		perms.add(PosixFilePermission.OTHERS_EXECUTE);
		Files.setPosixFilePermissions(file, perms);
	}

}
