/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.fs;

import io.github.markpollack.judge.DeterministicJudge;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.markpollack.judge.context.JudgmentContext;
import io.github.markpollack.judge.result.Check;
import io.github.markpollack.judge.result.Judgment;

/**
 * Judge that verifies file content matches expected criteria.
 *
 * <p>
 * Supports three matching modes:
 * </p>
 * <ul>
 * <li>EXACT - Content must match exactly</li>
 * <li>CONTAINS - Content must contain the expected string</li>
 * <li>REGEX - Content must match the regular expression pattern</li>
 * </ul>
 *
 * <p>
 * Example usage:
 * </p>
 * Executable examples are maintained in the Agent Judge Tutorial: https://github.com/markpollack/agent-judge-tutorial.
 *
 * @author Mark Pollack
 * @since 0.1.0
 */
public class FileContentJudge extends DeterministicJudge {

	private static final Logger logger = LoggerFactory.getLogger(FileContentJudge.class);

	private final String filePath;

	private final String expectedContent;

	private final MatchMode matchMode;

	/**
	 * Create a content judge with an explicit match mode.
	 * @param filePath path relative to the judgment workspace
	 * @param expectedContent expected text or regular expression
	 * @param matchMode comparison mode
	 */
	public FileContentJudge(String filePath, String expectedContent, MatchMode matchMode) {
		super("FileContentJudge",
				String.format("Verifies file content at %s (%s match)", filePath, matchMode.name().toLowerCase()));
		this.filePath = filePath;
		this.expectedContent = expectedContent;
		this.matchMode = matchMode;
	}

	/**
	 * Create an exact-content judge.
	 * @param filePath path relative to the judgment workspace
	 * @param expectedContent expected file content
	 */
	public FileContentJudge(String filePath, String expectedContent) {
		this(filePath, expectedContent, MatchMode.EXACT);
	}

	@Override
	public Judgment judge(JudgmentContext context) {
		Path workspace = context.workspace().toAbsolutePath().normalize();
		Path targetFile = workspace.resolve(filePath).toAbsolutePath().normalize();

		// Same containment rule as FileExistsJudge, and the same reason. Path.resolve
		// returns its argument unchanged when that argument is absolute, so an absolute or
		// traversing filePath silently leaves the workspace and this judge would then
		// compare content the subject never produced. A path that escapes is a
		// misconfigured judge, not a failing subject, so it is ERROR rather than FAIL.
		if (!targetFile.startsWith(workspace)) {
			return Judgment.error(String.format(
					"Path escapes the workspace and cannot be judged against it: %s resolves outside %s", filePath,
					workspace));
		}

		// A missing file is a completed evaluation with a negative result: FAIL.
		if (!Files.exists(targetFile)) {
			return Judgment.builder().fail()
				.reasoning(String.format("File not found at %s", filePath))
				.check(Check.fail("file_exists", "File not found: " + filePath))
				.build();
		}

		// An unreadable file means the judge could not evaluate at all: ERROR, not FAIL.
		String actualContent;
		try {
			actualContent = Files.readString(targetFile);
		}
		catch (Exception ex) {
			logger.error("Failed to read file {}", targetFile, ex);
			return Judgment.builder().error()
				.reasoning(String.format("Failed to read file: %s", ex.getMessage()))
				.check(Check.pass("file_exists", "File exists"))
				.check(Check.fail("file_readable", "Failed to read: " + ex.getMessage()))
				.build();
		}

		// Match content based on mode
		boolean matches = switch (matchMode) {
			case EXACT -> actualContent.equals(expectedContent);
			case CONTAINS -> actualContent.contains(expectedContent);
			case REGEX -> Pattern.compile(expectedContent).matcher(actualContent).find();
		};

		return (matches ? Judgment.builder().pass() : Judgment.builder().fail())
			.reasoning(matches ? String.format("Content %s matches in %s", matchMode.name().toLowerCase(), filePath)
					: String.format("Content does not %s match in %s", matchMode.name().toLowerCase(), filePath))
			.check(Check.pass("file_exists", "File found"))
			.check(Check.pass("file_readable", "File readable"))
			.check(matches ? Check.pass("content_match", String.format("%s match successful", matchMode))
					: Check.fail("content_match", String.format("%s match failed", matchMode)))
			.build();
	}

	/**
	 * Content matching mode.
	 */
	public enum MatchMode {

		/**
		 * Content must match exactly.
		 */
		EXACT,

		/**
		 * Content must contain the expected string.
		 */
		CONTAINS,

		/**
		 * Content must match the regex pattern.
		 */
		REGEX

	}

}
