/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.file;

import io.github.markpollack.judge.file.comparator.JavaSemanticComparator;
import io.github.markpollack.judge.file.comparator.JavaSemanticComparator.ComparisonResult;
import io.github.markpollack.judge.DeterministicJudge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.markpollack.judge.judgment.Check;
import io.github.markpollack.judge.judgment.Judgment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Judge that compares Java source files using AST-based semantic comparison.
 * <p>
 * Tolerates differences in:
 * <ul>
 * <li>Whitespace and formatting</li>
 * <li>Import ordering</li>
 * <li>Comments</li>
 * </ul>
 */
public class JavaSemanticJudge extends DeterministicJudge<FileComparison> {

	private static final Logger logger = LoggerFactory.getLogger(JavaSemanticJudge.class);

	private final JavaSemanticComparator comparator = new JavaSemanticComparator();

	/**
	 * Create a Java source semantic-comparison judge.
	 * @param source fresh evidence acquisition provider, invoked once per direct
	 * execution
	 */
	public JavaSemanticJudge(java.util.function.Supplier<? extends FileComparison> source) {
		super(source, "JavaSemanticJudge", "Compares Java files using AST-based semantic comparison");
	}

	@Override
	protected Judgment evaluate(FileComparison evidence) {
		String filePath = evidence.relativePath();
		Path expectedFile = evidence.expectedFile();
		Path actualFile = evidence.actualFile();

		try {
			String expected = Files.readString(expectedFile);

			if (!Files.exists(actualFile)) {
				return Judgment.fail("File missing: " + filePath);
			}

			String actual = Files.readString(actualFile);
			ComparisonResult result = comparator.compare(expected, actual);

			if (result.equivalent()) {
				return Judgment.builder()
					.pass()
					.reasoning("Java semantically matches")
					.check(Check.pass(filePath))
					.build();
			}

			String diff = String.join("\n", result.differences());
			return Judgment.builder()
				.fail()
				.reasoning("Java semantic differences: " + diff)
				.check(Check.fail(filePath, diff))
				.build();

		}
		catch (IOException e) {
			logger.error("File comparison failed", e);
			return Judgment.error("Failed to read files: " + e.getMessage());
		}
	}

	/**
	 * Configures a producer without executing or acquiring evidence.
	 * @return typed evidence stage
	 */
	public static io.github.markpollack.judge.construction.EvidenceStep<FileComparison> builder() {
		return io.github.markpollack.judge.construction.EvidenceSteps.of(source -> new JavaSemanticJudge(source));
	}

}
