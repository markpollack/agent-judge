package org.springaicommunity.judge.file;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springaicommunity.judge.context.JudgmentContext;
import org.springaicommunity.judge.result.Judgment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

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

		JudgmentContext context = JudgmentContext.builder()
			.goal("test")
			.workspace(actual)
			.metadata(Map.of("expectedDir", expected))
			.build();

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

		JudgmentContext context = JudgmentContext.builder()
			.goal("test")
			.workspace(actual)
			.metadata(Map.of("expectedDir", expected))
			.build();

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

		JudgmentContext context = JudgmentContext.builder()
			.goal("test")
			.workspace(actual)
			.metadata(Map.of("expectedDir", expected))
			.build();

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

		JudgmentContext context = JudgmentContext.builder()
			.goal("test")
			.workspace(actual)
			.metadata(Map.of("expectedDir", expected))
			.build();

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

		JudgmentContext context = JudgmentContext.builder()
			.goal("test")
			.workspace(actual)
			.metadata(Map.of("expectedDir", expected))
			.build();

		Judgment result = judge.judge(context);
		assertThat(result.pass()).isTrue();
	}

}
