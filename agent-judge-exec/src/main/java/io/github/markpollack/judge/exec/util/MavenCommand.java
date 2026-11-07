/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.exec.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import io.github.markpollack.sandbox.ExecResult;
import io.github.markpollack.sandbox.ExecSpec;
import io.github.markpollack.sandbox.Sandbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Maven executable for a workspace: its wrapper when present and executable,
 * otherwise {@code mvn} from the PATH.
 */
final class MavenCommand {

	private static final Logger logger = LoggerFactory.getLogger(MavenCommand.class);

	private MavenCommand() {
	}

	/**
	 * Detect Maven wrapper or fallback to mvn.
	 * @param workspace the Maven project root
	 * @return Maven command to use
	 */
	static String detect(Path workspace) {
		Path mvnw = workspace.resolve("mvnw");
		if (Files.exists(mvnw) && Files.isExecutable(mvnw)) {
			logger.debug("Found Maven wrapper: {}", mvnw);
			return "./mvnw";
		}
		logger.debug("Maven wrapper not found, using 'mvn' from PATH");
		return "mvn";
	}

	/**
	 * Run the workspace's Maven with these goals.
	 * @param sandbox where to run
	 * @param workspace the Maven project root
	 * @param timeoutMinutes execution limit
	 * @param goals Maven goals and arguments
	 * @return the execution result
	 */
	static ExecResult execute(Sandbox sandbox, Path workspace, long timeoutMinutes, String... goals) {
		String mavenCmd = detect(workspace);
		logger.debug("Maven command: {} {}", mavenCmd, String.join(" ", goals));
		List<String> command = new ArrayList<>();
		command.add(mavenCmd);
		command.addAll(List.of(goals));
		return sandbox.exec(ExecSpec.builder().command(command).timeout(Duration.ofMinutes(timeoutMinutes)).build());
	}

}
