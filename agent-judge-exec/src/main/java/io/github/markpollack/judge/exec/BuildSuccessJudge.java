/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.exec;

import java.nio.file.Path;
import io.github.markpollack.judge.judgment.Judgment;

import java.nio.file.Files;
import java.time.Duration;

/**
 * Judge that verifies build success by executing build commands.
 *
 * <p>
 * Extends {@link CommandJudge} with build-specific defaults and smart detection of build
 * tool wrappers (./mvnw, ./gradlew) with fallback to PATH-based tools (mvn, gradle).
 * </p>
 *
 * <p>
 * Example usage:
 * </p>
 *
 * Executable examples are maintained in the Agent Judge Tutorial:
 * https://github.com/markpollack/agent-judge-tutorial.
 *
 * <p>
 * The judge uses a 10-minute default timeout (longer than CommandJudge's 2 minutes) since
 * builds can take significant time.
 * </p>
 *
 * @author Mark Pollack
 * @since 0.1.0
 */
public class BuildSuccessJudge extends CommandJudge {

	private static final Duration BUILD_TIMEOUT = Duration.ofMinutes(10);

	/**
	 * Create a BuildSuccessJudge with a custom build command.
	 * @param buildCommand the build command to execute
	 * @param source fresh evidence acquisition provider, invoked once per direct
	 * execution
	 */
	public BuildSuccessJudge(java.util.function.Supplier<? extends Path> source, String buildCommand) {
		super(source, buildCommand, 0, BUILD_TIMEOUT);
	}

	/**
	 * Create a Maven build judge with auto-detection of mvnw wrapper.
	 * <p>
	 * Prefers ./mvnw if present in workspace, otherwise falls back to mvn on PATH.
	 * @param goals Maven goals to execute (e.g., "clean", "compile", "test")
	 * @return BuildSuccessJudge configured for Maven
	 */
	public static io.github.markpollack.judge.construction.EvidenceStep<Path> maven(String... goals) {
		return io.github.markpollack.judge.construction.EvidenceSteps
			.of(source -> new MavenBuildJudge(source, goals.clone()));
	}

	/**
	 * Create a Gradle build judge with auto-detection of gradlew wrapper.
	 * <p>
	 * Prefers ./gradlew if present in workspace, otherwise falls back to gradle on PATH.
	 * @param tasks Gradle tasks to execute (e.g., "build", "test")
	 * @return BuildSuccessJudge configured for Gradle
	 */
	public static io.github.markpollack.judge.construction.EvidenceStep<Path> gradle(String... tasks) {
		return io.github.markpollack.judge.construction.EvidenceSteps
			.of(source -> new GradleBuildJudge(source, tasks.clone()));
	}

	/**
	 * Maven-specific build judge that detects mvnw wrapper.
	 */
	private static class MavenBuildJudge extends BuildSuccessJudge {

		private final String[] goals;

		MavenBuildJudge(java.util.function.Supplier<? extends Path> source, String[] goals) {
			super(source, "mvn " + String.join(" ", goals)); // Temporary, will be
																// resolved in
			// judge()
			this.goals = goals.clone();
		}

		@Override
		protected Judgment evaluate(Path workspace) {
			// Detect wrapper in workspace
			String command = detectMavenCommand(workspace);
			// Create new judge with detected command
			BuildSuccessJudge actualJudge = new BuildSuccessJudge(() -> workspace, command);
			return actualJudge.judge();
		}

		private String detectMavenCommand(Path workspace) {
			Path mvnw = workspace.resolve("mvnw");
			if (Files.exists(mvnw) && Files.isExecutable(mvnw)) {
				return "./mvnw " + String.join(" ", goals);
			}
			return "mvn " + String.join(" ", goals);
		}

	}

	/**
	 * Gradle-specific build judge that detects gradlew wrapper.
	 */
	private static class GradleBuildJudge extends BuildSuccessJudge {

		private final String[] tasks;

		GradleBuildJudge(java.util.function.Supplier<? extends Path> source, String[] tasks) {
			super(source, "gradle " + String.join(" ", tasks)); // Temporary, will be
																// resolved in
			// judge()
			this.tasks = tasks.clone();
		}

		@Override
		protected Judgment evaluate(Path workspace) {
			// Detect wrapper in workspace
			String command = detectGradleCommand(workspace);
			// Create new judge with detected command
			BuildSuccessJudge actualJudge = new BuildSuccessJudge(() -> workspace, command);
			return actualJudge.judge();
		}

		private String detectGradleCommand(Path workspace) {
			Path gradlew = workspace.resolve("gradlew");
			if (Files.exists(gradlew) && Files.isExecutable(gradlew)) {
				return "./gradlew " + String.join(" ", tasks);
			}
			return "gradle " + String.join(" ", tasks);
		}

	}

	/**
	 * Configures a custom build command.
	 * @param command build command
	 * @return typed workspace stage
	 */
	public static io.github.markpollack.judge.construction.EvidenceStep<Path> builder(String command) {
		return io.github.markpollack.judge.construction.EvidenceSteps
			.of(source -> new BuildSuccessJudge(source, command));
	}

}
