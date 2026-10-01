/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.exec;

import io.github.markpollack.judge.DeterministicJudge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import io.github.markpollack.judge.judgment.Check;
import io.github.markpollack.judge.judgment.Judgment;
import io.github.markpollack.sandbox.ExecResult;
import io.github.markpollack.sandbox.ExecSpec;
import io.github.markpollack.sandbox.LocalSandbox;
import io.github.markpollack.sandbox.Sandbox;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Judge that executes a shell command and evaluates based on exit code.
 *
 * <p>
 * Executes a command in the workspace using a Sandbox and judges success based on the
 * exit code. This judge is useful for:
 * </p>
 * <ul>
 * <li>Running build commands (mvn compile, gradle build)</li>
 * <li>Running test suites (mvn test, npm test)</li>
 * <li>Running linters and code quality tools</li>
 * <li>Custom verification scripts</li>
 * </ul>
 *
 * <p>
 * Example usage:
 * </p>
 *
 * Executable examples are maintained in the Agent Judge Tutorial:
 * https://github.com/markpollack/agent-judge-tutorial.
 *
 * <p>
 * The judgment records the command, merged stdout/stderr, expected and actual exit codes,
 * and elapsed time under {@link Judgment#ELAPSED_MILLIS_KEY} in result metadata. Every
 * one of those is a portable value; {@link Judgment#elapsed()} gives the Java view of the
 * timing.
 * </p>
 *
 * @author Mark Pollack
 * @since 0.1.0
 * @see Sandbox
 * @see LocalSandbox
 */
public class CommandJudge extends DeterministicJudge<Path> {

	private static final Logger logger = LoggerFactory.getLogger(CommandJudge.class);

	private final String command;

	private final int expectedExitCode;

	private final Duration timeout;

	private final Function<Path, Sandbox> sandboxFactory;

	/**
	 * Create a CommandJudge with default settings (exit code 0, 2 minute timeout).
	 * @param command the shell command to execute
	 * @param source fresh evidence acquisition provider, invoked once per direct
	 * execution
	 */
	public CommandJudge(java.util.function.Supplier<? extends Path> source, String command) {
		this(source, command, 0, Duration.ofMinutes(2));
	}

	/**
	 * Create a CommandJudge with custom exit code and default timeout.
	 * @param command the shell command to execute
	 * @param expectedExitCode the expected exit code for success (typically 0)
	 * @param source fresh evidence acquisition provider, invoked once per direct
	 * execution
	 */
	public CommandJudge(java.util.function.Supplier<? extends Path> source, String command, int expectedExitCode) {
		this(source, command, expectedExitCode, Duration.ofMinutes(2));
	}

	/**
	 * Create a CommandJudge with custom exit code and timeout.
	 * @param command the shell command to execute
	 * @param expectedExitCode the expected exit code for success (typically 0)
	 * @param timeout maximum duration for command execution
	 * @param source fresh evidence acquisition provider, invoked once per direct
	 * execution
	 */
	public CommandJudge(java.util.function.Supplier<? extends Path> source, String command, int expectedExitCode,
			Duration timeout) {
		this(source, command, expectedExitCode, timeout, LocalSandbox::new);
	}

	/**
	 * Create a CommandJudge with custom Sandbox factory.
	 * @param command the shell command to execute
	 * @param expectedExitCode the expected exit code for success (typically 0)
	 * @param timeout maximum duration for command execution
	 * @param sandboxFactory factory function that creates a Sandbox for the given
	 * workspace path
	 * @param source fresh evidence acquisition provider, invoked once per direct
	 * execution
	 */
	public CommandJudge(java.util.function.Supplier<? extends Path> source, String command, int expectedExitCode,
			Duration timeout, Function<Path, Sandbox> sandboxFactory) {
		super(source, "CommandJudge",
				String.format("Executes command: %s (expects exit code %d)", command, expectedExitCode));
		this.command = command;
		this.expectedExitCode = expectedExitCode;
		this.timeout = timeout;
		this.sandboxFactory = sandboxFactory;
	}

	@Override
	protected Judgment evaluate(Path workspace) {
		try (Sandbox sandbox = sandboxFactory.apply(workspace)) {
			ExecSpec spec = ExecSpec.builder().shellCommand(command).timeout(timeout).build();

			ExecResult result = sandbox.exec(spec);

			boolean pass = result.exitCode() == expectedExitCode;

			Map<String, Object> metadata = new LinkedHashMap<>();
			metadata.put("command", command);
			metadata.put("exitCode", result.exitCode());
			metadata.put("expectedExitCode", expectedExitCode);
			metadata.put("output", result.mergedLog());
			// Result timing is a portable millisecond count under the conventional key,
			// not an ISO-8601 rendering of a Java Duration. Clamped because a clock
			// artifact should not turn a command that ran into a construction failure.
			metadata.put(Judgment.ELAPSED_MILLIS_KEY, Math.max(0L, result.duration().toMillis()));

			String reasoning = pass ? String.format("Command succeeded with exit code %d", result.exitCode()) : String
				.format("Command failed. Expected exit code %d but got %d", expectedExitCode, result.exitCode());

			// The command ran; a disallowed exit code is a completed negative finding.
			return (pass ? Judgment.builder().pass() : Judgment.builder().fail()).reasoning(reasoning)
				.check(pass ? Check.pass("command_execution", "Command executed successfully")
						: Check.fail("command_execution", "Command execution failed"))
				.metadata(metadata)
				.build();
		}
		catch (Exception ex) {
			// The command could not be run at all, so the judge reached no finding:
			// ERROR.
			logger.error("Command execution failed: {}", command, ex);
			return Judgment.builder()
				.error()
				.reasoning("Command execution failed: " + ex.getMessage())
				.check(new Check("command_execution", Judgment.error("Execution error: " + ex.getMessage())))
				.build();
		}
	}

	/**
	 * Get the command being executed.
	 * @return the command string
	 */
	public String getCommand() {
		return command;
	}

	/**
	 * Get the expected exit code.
	 * @return the expected exit code
	 */
	public int getExpectedExitCode() {
		return expectedExitCode;
	}

	/**
	 * Get the timeout duration.
	 * @return the timeout
	 */
	public Duration getTimeout() {
		return timeout;
	}

	/**
	 * Configures a producer without executing or acquiring evidence.
	 * @param command producer configuration
	 * @return typed evidence stage
	 */
	public static io.github.markpollack.judge.construction.EvidenceStep<Path> builder(String command) {
		return io.github.markpollack.judge.construction.EvidenceSteps.of(source -> new CommandJudge(source, command));
	}

	/**
	 * Configures a producer without executing or acquiring evidence.
	 * @param command producer configuration
	 * @param expectedExitCode producer configuration
	 * @return typed evidence stage
	 */
	public static io.github.markpollack.judge.construction.EvidenceStep<Path> builder(String command,
			int expectedExitCode) {
		return io.github.markpollack.judge.construction.EvidenceSteps
			.of(source -> new CommandJudge(source, command, expectedExitCode));
	}

	/**
	 * Configures a producer without executing or acquiring evidence.
	 * @param command producer configuration
	 * @param expectedExitCode producer configuration
	 * @param timeout producer configuration
	 * @return typed evidence stage
	 */
	public static io.github.markpollack.judge.construction.EvidenceStep<Path> builder(String command,
			int expectedExitCode, Duration timeout) {
		return io.github.markpollack.judge.construction.EvidenceSteps
			.of(source -> new CommandJudge(source, command, expectedExitCode, timeout));
	}

	/**
	 * Configures a producer without executing or acquiring evidence.
	 * @param command producer configuration
	 * @param expectedExitCode producer configuration
	 * @param timeout producer configuration
	 * @param sandboxFactory producer configuration
	 * @return typed evidence stage
	 */
	public static io.github.markpollack.judge.construction.EvidenceStep<Path> builder(String command,
			int expectedExitCode, Duration timeout, Function<Path, Sandbox> sandboxFactory) {
		return io.github.markpollack.judge.construction.EvidenceSteps
			.of(source -> new CommandJudge(source, command, expectedExitCode, timeout, sandboxFactory));
	}

}
