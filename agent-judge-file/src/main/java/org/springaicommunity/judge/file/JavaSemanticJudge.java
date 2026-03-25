package org.springaicommunity.judge.file;

import org.springaicommunity.judge.file.comparator.JavaSemanticComparator;
import org.springaicommunity.judge.file.comparator.JavaSemanticComparator.ComparisonResult;
import org.springaicommunity.judge.DeterministicJudge;
import org.springaicommunity.judge.context.JudgmentContext;
import org.springaicommunity.judge.result.Check;
import org.springaicommunity.judge.result.Judgment;
import org.springaicommunity.judge.result.JudgmentStatus;
import org.springaicommunity.judge.score.BooleanScore;

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
				return Judgment.builder()
					.score(new BooleanScore(true))
					.status(JudgmentStatus.PASS)
					.reasoning("Java semantically matches")
					.check(Check.pass(filePath))
					.build();
			}

			String diff = String.join("\n", result.differences());
			return Judgment.builder()
				.score(new BooleanScore(false))
				.status(JudgmentStatus.FAIL)
				.reasoning("Java semantic differences: " + diff)
				.check(Check.fail(filePath, diff))
				.build();

		}
		catch (IOException e) {
			return Judgment.error("Failed to read files: " + e.getMessage(), e);
		}
	}

}
