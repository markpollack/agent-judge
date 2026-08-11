package io.github.markpollack.judge.file;

import io.github.markpollack.judge.DeterministicJudge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.markpollack.judge.context.JudgmentContext;
import io.github.markpollack.judge.result.Check;
import io.github.markpollack.judge.result.Judgment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Judge that compares text files using whitespace-normalized string comparison.
 */
public class TextFileJudge extends DeterministicJudge {

	private static final Logger logger = LoggerFactory.getLogger(TextFileJudge.class);

	/** Create a whitespace-normalizing text-file judge. */
	public TextFileJudge() {
		super("TextFileJudge", "Compares text files using whitespace-normalized string comparison");
	}

	@Override
	public Judgment judge(JudgmentContext context) {
		String filePath = (String) context.metadata().get("filePath");
		Path expectedFile = (Path) context.metadata().get("expectedFile");
		Path actualFile = (Path) context.metadata().get("actualFile");

		try {
			String expected = Files.readString(expectedFile);

			if (!Files.exists(actualFile)) {
				return Judgment.fail("File missing: " + filePath);
			}

			String actual = Files.readString(actualFile);

			String normalizedExpected = expected.replaceAll("\\s+", " ").trim();
			String normalizedActual = actual.replaceAll("\\s+", " ").trim();

			if (normalizedExpected.equals(normalizedActual)) {
				return Judgment.builder().pass()
					.reasoning("Text file matches (whitespace-normalized)")
					.check(Check.pass(filePath))
					.build();
			}

			String diff = generateDiff(expected, actual);
			return Judgment.builder().fail()
				.reasoning("Text file differs: " + filePath)
				.check(Check.fail(filePath, diff))
				.build();

		}
		catch (IOException e) {
			logger.error("File comparison failed", e);
			return Judgment.error("Failed to read files: " + e.getMessage());
		}
	}

	private String generateDiff(String expected, String actual) {
		String[] expectedLines = expected.split("\n");
		String[] actualLines = actual.split("\n");

		StringBuilder diff = new StringBuilder();
		int maxLines = Math.max(expectedLines.length, actualLines.length);

		for (int i = 0; i < Math.min(maxLines, 15); i++) {
			String exp = i < expectedLines.length ? expectedLines[i] : "<missing>";
			String act = i < actualLines.length ? actualLines[i] : "<missing>";

			if (!exp.trim().equals(act.trim())) {
				diff.append(String.format("Line %d:%n  expected: %s%n  actual:   %s%n", i + 1, exp, act));
			}
		}

		if (maxLines > 15) {
			diff.append("... (truncated)\n");
		}

		return diff.toString();
	}

}
