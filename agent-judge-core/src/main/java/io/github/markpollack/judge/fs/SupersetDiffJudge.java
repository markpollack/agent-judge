/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.fs;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.markpollack.judge.DeterministicJudge;
import io.github.markpollack.judge.context.JudgmentContext;
import io.github.markpollack.judge.result.Check;
import io.github.markpollack.judge.result.Judgment;

/**
 * Judge that verifies the workspace is a file-level superset of a reference directory.
 *
 * <p>
 * For every regular file in the reference directory, this judge checks that:
 * </p>
 * <ol>
 * <li>A file at the same relative path exists in the workspace</li>
 * <li>The file content is byte-identical</li>
 * </ol>
 *
 * <p>
 * Extra files in the workspace that are not in the reference are allowed — this is
 * superset semantics, not exact match.
 * </p>
 *
 * <p>
 * The reference directory path is read from {@link JudgmentContext#metadata()} under the
 * key {@value #EXPECTED_DIR_KEY}. If the key is missing, the judge abstains.
 * </p>
 *
 * <p>
 * File paths matching any of the configured exclude patterns are skipped during
 * comparison.
 * </p>
 *
 * <p>
 * Example usage:
 * </p>
 *
 * Executable examples are maintained in the Agent Judge Tutorial: https://github.com/markpollack/agent-judge-tutorial.
 *
 * @author Mark Pollack
 * @since 0.9.1
 */
public class SupersetDiffJudge extends DeterministicJudge {

	private static final Logger logger = LoggerFactory.getLogger(SupersetDiffJudge.class);

	static final String EXPECTED_DIR_KEY = "expectedDir";

	private final Set<String> excludes;

	/** Create a judge with no excluded paths. */
	public SupersetDiffJudge() {
		this(Set.of());
	}

	/**
	 * Create a judge with excluded relative paths.
	 * @param excludes paths excluded from comparison
	 */
	public SupersetDiffJudge(Set<String> excludes) {
		super("SupersetDiffJudge", "Verifies output is a superset of the reference project");
		this.excludes = Set.copyOf(excludes);
	}

	@Override
	public Judgment judge(JudgmentContext context) {
		Object expectedDirObj = context.metadata().get(EXPECTED_DIR_KEY);
		if (expectedDirObj == null) {
			return Judgment.abstain("No " + EXPECTED_DIR_KEY + " in metadata");
		}

		Path expectedDir = toPath(expectedDirObj);
		if (!Files.isDirectory(expectedDir)) {
			return Judgment.abstain("Reference directory does not exist: " + expectedDir);
		}

		Path workspace = context.workspace();
		List<Check> checks = new ArrayList<>();

		try {
			Files.walkFileTree(expectedDir, new SimpleFileVisitor<>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					String relativePath = expectedDir.relativize(file).toString();
					if (isExcluded(relativePath)) {
						return FileVisitResult.CONTINUE;
					}

					Path outputFile = workspace.resolve(relativePath);
					if (!Files.exists(outputFile)) {
						checks.add(Check.fail(relativePath, "Missing file: " + relativePath));
					}
					else {
						long mismatch = Files.mismatch(file, outputFile);
						if (mismatch == -1L) {
							checks.add(Check.pass(relativePath));
						}
						else {
							checks.add(Check.fail(relativePath,
									"Content differs at byte " + mismatch + ": " + relativePath));
						}
					}
					return FileVisitResult.CONTINUE;
				}
			});
		}
		catch (IOException ex) {
			logger.error("Failed to walk reference directory {}", expectedDir, ex);
			return Judgment.error("Failed to walk reference directory: " + ex.getMessage());
		}

		if (checks.isEmpty()) {
			return Judgment.abstain("No files in reference directory");
		}

		int passed = (int) checks.stream().filter(Check::passed).count();
		int total = checks.size();
		double score = (double) passed / total;
		boolean allMatch = passed == total;

		return (allMatch ? Judgment.builder().pass() : Judgment.builder().fail())
			.score(score)
			.reasoning(allMatch ? String.format("All %d reference files matched", total)
					: String.format("%d of %d reference files matched", passed, total))
			.checks(checks)
			.metadata(EXPECTED_DIR_KEY, expectedDir.toString())
			.metadata("matchedFiles", passed)
			.metadata("totalFiles", total)
			.build();
	}

	private boolean isExcluded(String relativePath) {
		for (String exclude : excludes) {
			if (relativePath.equals(exclude) || relativePath.startsWith(exclude)) {
				return true;
			}
		}
		return false;
	}

	private static Path toPath(Object value) {
		if (value instanceof Path p) {
			return p;
		}
		return Path.of(value.toString());
	}

}
