/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.exec;

import java.nio.file.Path;
import io.github.markpollack.judge.DeterministicJudge;
import io.github.markpollack.judge.judgment.Judgment;

import java.nio.file.Files;
import java.time.Duration;

/**
 * Judge that verifies build success by executing build commands.
 *
 * <p>
 * Runs a build command through {@link CommandJudge}'s execution with build-specific
 * defaults (expected exit code 0, 10-minute timeout) and detects build tool wrappers
 * ({@code ./mvnw}, {@code ./gradlew}), falling back to PATH-based tools ({@code mvn},
 * {@code gradle}).
 * </p>
 *
 * <p>
 * It is composed of, not derived from, {@code CommandJudge}, so the only factories
 * reachable through this class are its own: {@link #builder(String)},
 * {@link #maven(String...)} and {@link #gradle(String...)}. Use {@code CommandJudge}
 * directly for another exit code or timeout.
 * </p>
 *
 * Executable examples are maintained in the Agent Judge Tutorial:
 * https://github.com/markpollack/agent-judge-tutorial.
 *
 * @author Mark Pollack
 * @since 0.1.0
 */
public class BuildSuccessJudge extends DeterministicJudge<Path> {

	private static final Duration BUILD_TIMEOUT = Duration.ofMinutes(10);

	private final String command;

	/**
	 * Create a BuildSuccessJudge with a custom build command.
	 * @param buildCommand the build command to execute
	 * @param source fresh evidence acquisition provider, invoked once per direct
	 * execution
	 */
	public BuildSuccessJudge(java.util.function.Supplier<? extends Path> source, String buildCommand) {
		super(source, "CommandJudge", String.format("Executes command: %s (expects exit code %d)", buildCommand, 0));
		this.command = buildCommand;
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

	/**
	 * Create a Maven build judge with auto-detection of mvnw wrapper.
	 * <p>
	 * Prefers ./mvnw if present in workspace, otherwise falls back to mvn on PATH.
	 * @param goals Maven goals to execute (e.g., "clean", "compile", "test")
	 * @return BuildSuccessJudge configured for Maven
	 */
	public static io.github.markpollack.judge.construction.EvidenceStep<Path> maven(String... goals) {
		String[] copy = goals.clone();
		return io.github.markpollack.judge.construction.EvidenceSteps
			.of(source -> new WrapperBuildJudge(source, "mvnw", "mvn", copy));
	}

	/**
	 * Create a Gradle build judge with auto-detection of gradlew wrapper.
	 * <p>
	 * Prefers ./gradlew if present in workspace, otherwise falls back to gradle on PATH.
	 * @param tasks Gradle tasks to execute (e.g., "build", "test")
	 * @return BuildSuccessJudge configured for Gradle
	 */
	public static io.github.markpollack.judge.construction.EvidenceStep<Path> gradle(String... tasks) {
		String[] copy = tasks.clone();
		return io.github.markpollack.judge.construction.EvidenceSteps
			.of(source -> new WrapperBuildJudge(source, "gradlew", "gradle", copy));
	}

	@Override
	protected Judgment evaluate(Path workspace) {
		return run(workspace, command);
	}

	private static Judgment run(Path workspace, String command) {
		return new CommandJudge(() -> workspace, command, 0, BUILD_TIMEOUT).evaluate(workspace);
	}

	/**
	 * Get the build command, as configured; a wrapper-detecting judge resolves its actual
	 * command per workspace.
	 * @return the command string
	 */
	public String getCommand() {
		return command;
	}

	/**
	 * Get the expected exit code.
	 * @return always 0
	 */
	public int getExpectedExitCode() {
		return 0;
	}

	/**
	 * Get the timeout duration.
	 * @return the 10-minute build timeout
	 */
	public Duration getTimeout() {
		return BUILD_TIMEOUT;
	}

	/**
	 * Build judge that prefers the workspace's executable wrapper over the tool on PATH.
	 */
	private static final class WrapperBuildJudge extends BuildSuccessJudge {

		private final String wrapper;

		private final String tool;

		private final String[] arguments;

		WrapperBuildJudge(java.util.function.Supplier<? extends Path> source, String wrapper, String tool,
				String[] arguments) {
			super(source, tool + " " + String.join(" ", arguments));
			this.wrapper = wrapper;
			this.tool = tool;
			this.arguments = arguments.clone();
		}

		@Override
		protected Judgment evaluate(Path workspace) {
			Path script = workspace.resolve(wrapper);
			String executable = Files.exists(script) && Files.isExecutable(script) ? "./" + wrapper : tool;
			return run(workspace, executable + " " + String.join(" ", arguments));
		}

	}

}
