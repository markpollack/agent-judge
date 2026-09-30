package io.github.markpollack.judge.file;

import io.github.markpollack.judge.DeterministicJudge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.markpollack.judge.judgment.Check;
import io.github.markpollack.judge.judgment.Judgment;
import io.github.markpollack.judge.judgment.JudgmentStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Composite judge that walks the expected (after/) directory and dispatches each file to
 * the appropriate sub-judge based on file type.
 */
public class FileComparisonJudge extends DeterministicJudge<DirectoryComparison> {

	private static final Logger logger = LoggerFactory.getLogger(FileComparisonJudge.class);

	private final MavenSemanticJudge mavenJudge = new MavenSemanticJudge();

	private final XmlSemanticJudge xmlJudge = new XmlSemanticJudge();

	private final JavaSemanticJudge javaJudge = new JavaSemanticJudge();

	private final TextFileJudge textJudge = new TextFileJudge();

	/** Create a composite semantic file-comparison judge. */
	public FileComparisonJudge() {
		super("FileComparisonJudge", "Compares all files in expected directory against actual directory");
	}

	@Override
	public Judgment judge(DirectoryComparison evidence) {
		Path expectedDir = evidence.expectedDirectory();
		Path actualDir = evidence.actualDirectory();

		try {
			List<Check> checks = new ArrayList<>();
			List<String> failures = new ArrayList<>();
			boolean instrumentFailure = false;
			boolean unresolved = false;

			try (Stream<Path> paths = Files.walk(expectedDir)) {
				for (Path expectedPath : paths.filter(Files::isRegularFile).toList()) {
					Path relativePath = expectedDir.relativize(expectedPath);
					String filePath = relativePath.toString();

					// Skip build artifacts
					if (filePath.startsWith("target/") || filePath.startsWith("target\\")) {
						continue;
					}

					Path actualPath = actualDir.resolve(relativePath);

					FileComparison fileEvidence = new FileComparison(filePath, expectedPath, actualPath);

					Judgment fileJudgment = dispatch(filePath, fileEvidence);

					if (fileJudgment.checks().isEmpty()) {
						checks.add(new Check(filePath, fileJudgment));
					}
					else {
						checks.addAll(fileJudgment.checks());
					}
					instrumentFailure |= fileJudgment.status() == JudgmentStatus.ERROR;
					unresolved |= fileJudgment.status() == JudgmentStatus.ABSTAIN;
					if (!fileJudgment.pass()) {
						failures.add(filePath + ": " + fileJudgment.reasoning());
					}
				}
			}

			if (instrumentFailure) {
				return Judgment.error("File comparison could not complete: " + String.join("; ", failures))
					.toBuilder()
					.checks(checks)
					.build();
			}
			if (unresolved) {
				return Judgment.abstain("File comparison is unresolved: " + String.join("; ", failures))
					.toBuilder()
					.checks(checks)
					.build();
			}
			if (checks.isEmpty())
				return Judgment.abstain("No files to compare");
			if (failures.isEmpty()) {
				return Judgment.builder()
					.pass()
					.reasoning("All " + checks.size() + " files match")
					.checks(checks)
					.build();
			}

			return Judgment.builder()
				.fail()
				.reasoning(failures.size() + " file(s) differ: " + String.join("; ", failures))
				.checks(checks)
				.build();

		}
		catch (IOException e) {
			logger.error("File comparison failed", e);
			return Judgment.error("Failed to walk expected directory: " + e.getMessage());
		}
	}

	private Judgment dispatch(String filePath, FileComparison evidence) {
		if (filePath.endsWith("pom.xml")) {
			return mavenJudge.judge(evidence);
		}
		if (filePath.endsWith(".xml")) {
			return xmlJudge.judge(evidence);
		}
		if (filePath.endsWith(".java")) {
			return javaJudge.judge(evidence);
		}
		return textJudge.judge(evidence);
	}

}
