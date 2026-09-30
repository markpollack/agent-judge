package io.github.markpollack.judge.file;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import io.github.markpollack.judge.judgment.Judgment;
import io.github.markpollack.judge.judgment.JudgmentStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FileComparisonJudgeTest {

	private final FileComparisonJudge judge = new FileComparisonJudge();

	@Test
	void matchingDirectoriesPass(@TempDir Path tempDir) throws IOException {
		Path expected = tempDir.resolve("expected");
		Path actual = tempDir.resolve("actual");
		Files.createDirectories(expected);
		Files.createDirectories(actual);

		String pom = """
				<project>
				    <modelVersion>4.0.0</modelVersion>
				    <groupId>com.example</groupId>
				    <artifactId>test</artifactId>
				    <version>1.0</version>
				</project>
				""";
		Files.writeString(expected.resolve("pom.xml"), pom);
		Files.writeString(actual.resolve("pom.xml"), pom);

		DirectoryComparison context = new DirectoryComparison(expected, actual);

		Judgment result = judge.judge(context);
		assertThat(result.pass()).isTrue();
	}

	@Test
	void mismatchedDirectoriesFail(@TempDir Path tempDir) throws IOException {
		Path expected = tempDir.resolve("expected");
		Path actual = tempDir.resolve("actual");
		Files.createDirectories(expected);
		Files.createDirectories(actual);

		Files.writeString(expected.resolve("pom.xml"), """
				<project>
				    <modelVersion>4.0.0</modelVersion>
				    <groupId>com.example</groupId>
				    <artifactId>test</artifactId>
				    <version>1.0</version>
				</project>
				""");
		Files.writeString(actual.resolve("pom.xml"), """
				<project>
				    <modelVersion>4.0.0</modelVersion>
				    <groupId>com.example</groupId>
				    <artifactId>test</artifactId>
				    <version>2.0</version>
				</project>
				""");

		DirectoryComparison context = new DirectoryComparison(expected, actual);

		Judgment result = judge.judge(context);
		assertThat(result.pass()).isFalse();
	}

	@Test
	void missingFilesFail(@TempDir Path tempDir) throws IOException {
		Path expected = tempDir.resolve("expected");
		Path actual = tempDir.resolve("actual");
		Files.createDirectories(expected);
		Files.createDirectories(actual);

		Files.writeString(expected.resolve("pom.xml"), "<project/>");
		// No pom.xml in actual

		DirectoryComparison context = new DirectoryComparison(expected, actual);

		Judgment result = judge.judge(context);
		assertThat(result.pass()).isFalse();
	}

	@Test
	void xmlFilesComparedSemantically(@TempDir Path tempDir) throws IOException {
		Path expected = tempDir.resolve("expected");
		Path actual = tempDir.resolve("actual");
		Files.createDirectories(expected.resolve(".mvn"));
		Files.createDirectories(actual.resolve(".mvn"));

		Files.writeString(expected.resolve(".mvn/extensions.xml"), """
				<?xml version="1.0" encoding="UTF-8"?>
				<extensions>
				    <extension>
				        <groupId>com.example</groupId>
				        <artifactId>ext</artifactId>
				        <version>1.0</version>
				    </extension>
				</extensions>
				""");
		Files.writeString(actual.resolve(".mvn/extensions.xml"), """
				<extensions>
				    <extension>
				        <groupId>com.example</groupId>
				        <artifactId>ext</artifactId>
				        <version>1.0</version>
				    </extension>
				</extensions>
				""");

		DirectoryComparison context = new DirectoryComparison(expected, actual);

		Judgment result = judge.judge(context);
		assertThat(result.pass()).isTrue();
	}

	@Test
	void textFilesComparedWithWhitespaceNormalization(@TempDir Path tempDir) throws IOException {
		Path expected = tempDir.resolve("expected");
		Path actual = tempDir.resolve("actual");
		Files.createDirectories(expected);
		Files.createDirectories(actual);

		Files.writeString(expected.resolve("README.txt"), "hello  world\n");
		Files.writeString(actual.resolve("README.txt"), "hello world\n");

		DirectoryComparison context = new DirectoryComparison(expected, actual);

		Judgment result = judge.judge(context);
		assertThat(result.pass()).isTrue();
	}

	@Test
	void unreadableFileOutranksKnownMismatchAndRetainsBoth(@TempDir Path tempDir) throws Exception {
		Path expected = tempDir.resolve("expected-broken");
		Path actual = tempDir.resolve("actual-broken");
		Files.createDirectories(expected);
		Files.createDirectories(actual);
		Files.writeString(expected.resolve("bad.xml"), "<root/>");
		Files.createDirectory(actual.resolve("bad.xml"));
		Files.writeString(expected.resolve("text.txt"), "expected");
		Files.writeString(actual.resolve("text.txt"), "different");
		var result = new FileComparisonJudge().judge(new DirectoryComparison(expected, actual));
		assertThat(result.status()).isEqualTo(JudgmentStatus.ERROR);
		assertThat(result.checks()).extracting(check -> check.judgment().status())
			.contains(JudgmentStatus.ERROR, JudgmentStatus.FAIL);
	}

	@Test
	void emptyDirectoryHasNoEstablishedComparison(@TempDir Path tempDir) throws Exception {
		Path expected = Files.createDirectories(tempDir.resolve("empty-expected"));
		Path actual = Files.createDirectories(tempDir.resolve("empty-actual"));
		assertThat(new FileComparisonJudge().judge(new DirectoryComparison(expected, actual)).status())
			.isEqualTo(JudgmentStatus.ABSTAIN);
	}

}
