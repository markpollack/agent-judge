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

package io.github.markpollack.judge.exec;

import io.github.markpollack.judge.DeterministicJudge;
import io.github.markpollack.judge.context.JudgmentContext;
import io.github.markpollack.judge.result.Check;
import io.github.markpollack.judge.result.Judgment;
import io.github.markpollack.judge.result.JudgmentStatus;
import io.github.markpollack.judge.score.BooleanScore;
import io.github.markpollack.sandbox.ExecResult;
import io.github.markpollack.sandbox.ExecSpec;
import io.github.markpollack.sandbox.LocalSandbox;
import io.github.markpollack.sandbox.Sandbox;

import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
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
 * <pre>{@code
 * // Simple command with default timeout (2 minutes) and expected exit code (0)
 * CommandJudge mvnCompile = new CommandJudge("mvn compile");
 *
 * // Custom exit code and timeout
 * CommandJudge customCommand = new CommandJudge("my-script.sh", 0, Duration.ofMinutes(5));
 *
 * // Check for non-zero exit (command expected to fail)
 * CommandJudge shouldFail = new CommandJudge("grep 'ERROR' build.log", 1);
 *
 * // With custom Sandbox factory (for Docker execution or testing)
 * CommandJudge withDocker = new CommandJudge("mvn test", 0, Duration.ofMinutes(5),
 *     path -> DockerSandbox.builder().workDir(path).build());
 * }</pre>
 *
 * <p>
 * The judgment includes stdout, stderr, and exit code in metadata for detailed analysis.
 * </p>
 *
 * @author Mark Pollack
 * @since 0.1.0
 * @see Sandbox
 * @see LocalSandbox
 */
public class CommandJudge extends DeterministicJudge {

	private final String command;

	private final int expectedExitCode;

	private final Duration timeout;

	private final Function<Path, Sandbox> sandboxFactory;

	/**
	 * Create a CommandJudge with default settings (exit code 0, 2 minute timeout).
	 * @param command the shell command to execute
	 */
	public CommandJudge(String command) {
		this(command, 0, Duration.ofMinutes(2));
	}

	/**
	 * Create a CommandJudge with custom exit code and default timeout.
	 * @param command the shell command to execute
	 * @param expectedExitCode the expected exit code for success (typically 0)
	 */
	public CommandJudge(String command, int expectedExitCode) {
		this(command, expectedExitCode, Duration.ofMinutes(2));
	}

	/**
	 * Create a CommandJudge with custom exit code and timeout.
	 * @param command the shell command to execute
	 * @param expectedExitCode the expected exit code for success (typically 0)
	 * @param timeout maximum duration for command execution
	 */
	public CommandJudge(String command, int expectedExitCode, Duration timeout) {
		this(command, expectedExitCode, timeout, LocalSandbox::new);
	}

	/**
	 * Create a CommandJudge with custom Sandbox factory.
	 * @param command the shell command to execute
	 * @param expectedExitCode the expected exit code for success (typically 0)
	 * @param timeout maximum duration for command execution
	 * @param sandboxFactory factory function that creates a Sandbox for the given
	 * workspace path
	 */
	public CommandJudge(String command, int expectedExitCode, Duration timeout,
			Function<Path, Sandbox> sandboxFactory) {
		super("CommandJudge", String.format("Executes command: %s (expects exit code %d)", command, expectedExitCode));
		this.command = command;
		this.expectedExitCode = expectedExitCode;
		this.timeout = timeout;
		this.sandboxFactory = sandboxFactory;
	}

	@Override
	public Judgment judge(JudgmentContext context) {
		try (Sandbox sandbox = sandboxFactory.apply(context.workspace())) {
			ExecSpec spec = ExecSpec.builder().shellCommand(command).timeout(timeout).build();

			ExecResult result = sandbox.exec(spec);

			boolean pass = result.exitCode() == expectedExitCode;

			Map<String, Object> metadata = new HashMap<>();
			metadata.put("command", command);
			metadata.put("exitCode", result.exitCode());
			metadata.put("expectedExitCode", expectedExitCode);
			metadata.put("output", result.mergedLog());
			metadata.put("duration", result.duration().toString());

			String reasoning = pass ? String.format("Command succeeded with exit code %d", result.exitCode()) : String
				.format("Command failed. Expected exit code %d but got %d", expectedExitCode, result.exitCode());

			return Judgment.builder()
				.score(new BooleanScore(pass))
				.status(pass ? JudgmentStatus.PASS : JudgmentStatus.FAIL)
				.reasoning(reasoning)
				.check(pass ? Check.pass("command_execution", "Command executed successfully")
						: Check.fail("command_execution", "Command execution failed"))
				.metadata(metadata)
				.build();
		}
		catch (Exception e) {
			return Judgment.builder()
				.score(new BooleanScore(false))
				.status(JudgmentStatus.FAIL)
				.reasoning("Command execution failed: " + e.getMessage())
				.check(Check.fail("command_execution", "Execution error: " + e.getMessage()))
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

}
