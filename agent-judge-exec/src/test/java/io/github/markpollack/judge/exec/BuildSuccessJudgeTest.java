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

	/**
	 * Every public static factory reachable through {@code BuildSuccessJudge} must produce
	 * a build judge with the 10-minute build timeout. A factory inherited from another
	 * class would compile at {@code BuildSuccessJudge.builder(...)} and silently return a
	 * plain command judge with a 2-minute timeout.
	 */
	@Test
	void everyReachableFactoryBuildsABuildJudgeWithTheBuildTimeout() throws Exception {
		var factories = java.util.Arrays.stream(BuildSuccessJudge.class.getMethods())
			.filter(m -> java.lang.reflect.Modifier.isStatic(m.getModifiers()))
			.filter(m -> io.github.markpollack.judge.construction.EvidenceStep.class.isAssignableFrom(m.getReturnType()))
			.toList();
		assertThat(factories).extracting(java.lang.reflect.Method::getName)
			.containsExactlyInAnyOrder("builder", "maven", "gradle");
		for (var factory : factories) {
			Object[] args = java.util.Arrays.stream(factory.getParameterTypes()).map(type -> {
				if (type == String.class)
					return "true";
				if (type == String[].class)
					return new String[] { "--version" };
				if (type == int.class)
					return 0;
				if (type == java.time.Duration.class)
					return java.time.Duration.ofMinutes(10);
				if (type == java.util.function.Function.class)
					return (java.util.function.Function<Path, io.github.markpollack.sandbox.Sandbox>) io.github.markpollack.sandbox.LocalSandbox::new;
				throw new AssertionError("Unhandled parameter type " + type + " in " + factory);
			}).toArray();
			var step = (io.github.markpollack.judge.construction.EvidenceStep<?>) factory.invoke(null, args);
			@SuppressWarnings("unchecked")
			var judge = ((io.github.markpollack.judge.construction.EvidenceStep<Path>) step).evidence(tempDir).build();
			assertThat(judge).as(factory.toString()).isInstanceOf(BuildSuccessJudge.class);
			assertThat(((BuildSuccessJudge) judge).getTimeout()).as(factory.toString())
				.isEqualTo(java.time.Duration.ofMinutes(10));
		}
	}

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
