/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.fs;

import io.github.markpollack.judge.DeterministicJudge;

import java.nio.file.Files;
import java.nio.file.Path;

import io.github.markpollack.judge.context.JudgmentContext;
import io.github.markpollack.judge.result.Check;
import io.github.markpollack.judge.result.Judgment;

/**
 * Judge that verifies file existence in the workspace.
 *
 * <p>
 * This is a simple deterministic judge that checks if a file exists at the specified path
 * relative to the workspace directory.
 * </p>
 *
 * <p>
 * Example usage:
 * </p>
 * Executable examples are maintained in the Agent Judge Tutorial: https://github.com/markpollack/agent-judge-tutorial.
 *
 * @author Mark Pollack
 * @since 0.1.0
 */
public class FileExistsJudge extends DeterministicJudge {

	private final String filePath;

	/**
	 * Create a file-existence judge.
	 * @param filePath path relative to the judgment workspace
	 */
	public FileExistsJudge(String filePath) {
		super("FileExistsJudge", "Verifies that file exists at path: " + filePath);
		this.filePath = filePath;
	}

	@Override
	public Judgment judge(JudgmentContext context) {
		Path targetFile = context.workspace().resolve(filePath);
		boolean exists = Files.exists(targetFile);

		return (exists ? Judgment.builder().pass() : Judgment.builder().fail())
			.reasoning(exists ? String.format("File exists at %s", filePath)
					: String.format("File not found at %s", filePath))
			.check(exists ? Check.pass("file_exists", "File found at " + filePath)
					: Check.fail("file_exists", "File not found at " + filePath))
			.build();
	}

}
