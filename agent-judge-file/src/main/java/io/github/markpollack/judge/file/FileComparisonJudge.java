package io.github.markpollack.judge.file;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Composite judge that walks the expected (after/) directory and dispatches each file to
 * the appropriate sub-judge based on file type.
 */
public class FileComparisonJudge extends DeterministicJudge {

	private static final Logger logger = LoggerFactory.getLogger(FileComparisonJudge.class);

	private final MavenSemanticJudge mavenJudge = new MavenSemanticJudge();

	private final XmlSemanticJudge xmlJudge = new XmlSemanticJudge();

	private final JavaSemanticJudge javaJudge = new JavaSemanticJudge();

	private final TextFileJudge textJudge = new TextFileJudge();

	public FileComparisonJudge() {
		super("FileComparisonJudge", "Compares all files in expected directory against actual directory");
	}

	@Override
	public Judgment judge(JudgmentContext context) {
		Path expectedDir = (Path) context.metadata().get("expectedDir");
		Path actualDir = context.workspace();

		try {
			List<Check> checks = new ArrayList<>();
			List<String> failures = new ArrayList<>();

			try (Stream<Path> paths = Files.walk(expectedDir)) {
				for (Path expectedPath : paths.filter(Files::isRegularFile).toList()) {
					Path relativePath = expectedDir.relativize(expectedPath);
					String filePath = relativePath.toString();

					// Skip build artifacts
					if (filePath.startsWith("target/") || filePath.startsWith("target\\")) {
						continue;
					}

					Path actualPath = actualDir.resolve(relativePath);

					JudgmentContext fileContext = JudgmentContext.builder()
						.goal("Compare " + filePath)
						.workspace(actualDir)
						.metadata(Map.of("filePath", filePath, "expectedFile", expectedPath, "actualFile", actualPath))
						.build();

					Judgment fileJudgment = dispatch(filePath, fileContext);

					checks.addAll(fileJudgment.checks());
					if (!fileJudgment.pass()) {
						failures.add(filePath + ": " + fileJudgment.reasoning());
					}
				}
			}

			if (failures.isEmpty()) {
				return Judgment.verdict(true)
					.because("All " + checks.size() + " files match")
					.withChecks(checks)
					.build();
			}

			return Judgment.verdict(false)
				.because(failures.size() + " file(s) differ: " + String.join("; ", failures))
				.withChecks(checks)
				.build();

		}
		catch (IOException e) {
			logger.error("File comparison failed", e);
			return Judgment.error("Failed to walk expected directory: " + e.getMessage());
		}
	}

	private Judgment dispatch(String filePath, JudgmentContext context) {
		if (filePath.endsWith("pom.xml")) {
			return mavenJudge.judge(context);
		}
		if (filePath.endsWith(".xml")) {
			return xmlJudge.judge(context);
		}
		if (filePath.endsWith(".java")) {
			return javaJudge.judge(context);
		}
		return textJudge.judge(context);
	}

}
