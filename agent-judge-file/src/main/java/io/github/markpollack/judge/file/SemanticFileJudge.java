/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.file;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import io.github.markpollack.judge.DeterministicJudge;
import io.github.markpollack.judge.judgment.Check;
import io.github.markpollack.judge.judgment.Judgment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The comparison every semantic file judge performs: read the expected file, fail when
 * the actual file is missing, and pass or fail on the format's own equivalence.
 */
abstract class SemanticFileJudge extends DeterministicJudge<FileComparison> {

	private static final Logger logger = LoggerFactory.getLogger(SemanticFileJudge.class);

	/**
	 * What a format's comparator concluded.
	 *
	 * @param equivalent whether the files are semantically equal
	 * @param differences what differs, when they are not
	 */
	record Comparison(boolean equivalent, List<String> differences) {
	}

	private final String label;

	SemanticFileJudge(java.util.function.Supplier<? extends FileComparison> source, String name, String description,
			String label) {
		super(source, name, description);
		this.label = label;
	}

	/**
	 * Compare the two contents with the format's comparator.
	 * @param expected expected content
	 * @param actual actual content
	 * @return the comparison
	 */
	abstract Comparison compare(String expected, String actual);

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
			Comparison result = compare(expected, Files.readString(actualFile));
			if (result.equivalent()) {
				return Judgment.builder()
					.pass()
					.reasoning(label + " semantically matches")
					.check(Check.pass(filePath))
					.build();
			}
			String diff = String.join("\n", result.differences());
			return Judgment.builder()
				.fail()
				.reasoning(label + " semantic differences: " + diff)
				.check(Check.fail(filePath, diff))
				.build();
		}
		catch (IOException e) {
			logger.error("File comparison failed", e);
			return Judgment.error("Failed to read files: " + e.getMessage());
		}
	}

}
