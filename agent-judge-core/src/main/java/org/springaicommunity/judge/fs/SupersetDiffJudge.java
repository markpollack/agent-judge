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

package org.springaicommunity.judge.fs;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springaicommunity.judge.DeterministicJudge;
import org.springaicommunity.judge.context.JudgmentContext;
import org.springaicommunity.judge.result.Check;
import org.springaicommunity.judge.result.Judgment;
import org.springaicommunity.judge.result.JudgmentStatus;
import org.springaicommunity.judge.score.NumericalScore;

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
 * <pre>{@code
 * // Compare all files
 * SupersetDiffJudge judge = new SupersetDiffJudge();
 *
 * // Skip Maven wrapper files
 * SupersetDiffJudge judge = new SupersetDiffJudge(Set.of(".mvn/", "mvnw", "mvnw.cmd"));
 * }</pre>
 *
 * @author Mark Pollack
 * @since 0.9.1
 */
public class SupersetDiffJudge extends DeterministicJudge {

	static final String EXPECTED_DIR_KEY = "expectedDir";

	private final Set<String> excludes;

	public SupersetDiffJudge() {
		this(Set.of());
	}

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
			return Judgment.error("Failed to walk reference directory: " + ex.getMessage(), ex);
		}

		if (checks.isEmpty()) {
			return Judgment.abstain("No files in reference directory");
		}

		int passed = (int) checks.stream().filter(Check::passed).count();
		int total = checks.size();
		double score = (double) passed / total;
		boolean allMatch = passed == total;

		return Judgment.builder()
			.score(NumericalScore.normalized(score))
			.status(allMatch ? JudgmentStatus.PASS : JudgmentStatus.FAIL)
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
