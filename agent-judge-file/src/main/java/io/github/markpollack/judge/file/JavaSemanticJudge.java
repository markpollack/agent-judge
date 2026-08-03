package io.github.markpollack.judge.file;

import io.github.markpollack.judge.file.comparator.JavaSemanticComparator;
import io.github.markpollack.judge.file.comparator.JavaSemanticComparator.ComparisonResult;
import io.github.markpollack.judge.DeterministicJudge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.markpollack.judge.context.JudgmentContext;
import io.github.markpollack.judge.result.Check;
import io.github.markpollack.judge.result.Judgment;
import io.github.markpollack.judge.result.JudgmentStatus;

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
public class JavaSemanticJudge extends DeterministicJudge {

	private static final Logger logger = LoggerFactory.getLogger(JavaSemanticJudge.class);

	private final JavaSemanticComparator comparator = new JavaSemanticComparator();

	public JavaSemanticJudge() {
		super("JavaSemanticJudge", "Compares Java files using AST-based semantic comparison");
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
			ComparisonResult result = comparator.compare(expected, actual);

			if (result.equivalent()) {
				return Judgment.verdict(true)
					.because("Java semantically matches")
					.withCheck(Check.pass(filePath))
					.build();
			}

			String diff = String.join("\n", result.differences());
			return Judgment.verdict(false)
				.because("Java semantic differences: " + diff)
				.withCheck(Check.fail(filePath, diff))
				.build();

		}
		catch (IOException e) {
			logger.error("File comparison failed", e);
			return Judgment.error("Failed to read files: " + e.getMessage());
		}
	}

}
