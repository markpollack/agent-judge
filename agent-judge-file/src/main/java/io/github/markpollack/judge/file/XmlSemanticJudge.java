package io.github.markpollack.judge.file;

import io.github.markpollack.judge.file.comparator.XmlSemanticComparator;
import io.github.markpollack.judge.file.comparator.XmlSemanticComparator.ComparisonResult;
import io.github.markpollack.judge.DeterministicJudge;
import io.github.markpollack.judge.context.JudgmentContext;
import io.github.markpollack.judge.result.Check;
import io.github.markpollack.judge.result.Judgment;
import io.github.markpollack.judge.result.JudgmentStatus;
import io.github.markpollack.judge.score.BooleanScore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Judge that compares non-POM XML files using DOM-based semantic comparison.
 */
public class XmlSemanticJudge extends DeterministicJudge {

	private final XmlSemanticComparator comparator = new XmlSemanticComparator();

	public XmlSemanticJudge() {
		super("XmlSemanticJudge", "Compares XML files using DOM-based semantic comparison");
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
					.reasoning("XML semantically matches")
					.check(Check.pass(filePath))
					.build();
			}

			String diff = String.join("\n", result.differences());
			return Judgment.builder()
				.score(new BooleanScore(false))
				.status(JudgmentStatus.FAIL)
				.reasoning("XML semantic differences: " + diff)
				.check(Check.fail(filePath, diff))
				.build();

		}
		catch (IOException e) {
			return Judgment.error("Failed to read files: " + e.getMessage(), e);
		}
	}

}
