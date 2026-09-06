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
		Path workspace = context.workspace().toAbsolutePath().normalize();
		Path targetFile = workspace.resolve(filePath).toAbsolutePath().normalize();

		// A path that leaves the workspace is a misconfigured judge, not a failing
		// subject. ERROR rather than FAIL, because FAIL would blame the subject for the
		// author's mistake, and at a reject-on-any-fail tier that rejects the run.
		// resolve() returns its argument unchanged when that argument is absolute, so an
		// absolute filePath silently escapes and the judge can pass on a file the subject
		// never created.
		if (!targetFile.startsWith(workspace)) {
			return Judgment.error(String.format(
					"Path escapes the workspace and cannot be judged against it: %s resolves outside %s", filePath,
					workspace));
		}

		// isRegularFile, not exists: a directory is not a file, and the difference is not
		// pedantic here. An expected-path entry of "src/main/java" names a directory Maven
		// creates in every scaffold, so exists() made that check pass before the subject
		// did anything. A judge that cannot fail is not a judge.
		boolean isFile = Files.isRegularFile(targetFile);
		boolean isDirectory = Files.isDirectory(targetFile);

		String reason;
		if (isFile) {
			reason = String.format("File exists at %s", filePath);
		}
		else if (isDirectory) {
			reason = String.format("Expected a file at %s, found a directory", filePath);
		}
		else {
			reason = String.format("File not found at %s", filePath);
		}

		return (isFile ? Judgment.builder().pass() : Judgment.builder().fail())
			.reasoning(reason)
			.check(isFile ? Check.pass("file_exists", "File found at " + filePath)
					: Check.fail("file_exists", reason))
			.build();
	}

}
