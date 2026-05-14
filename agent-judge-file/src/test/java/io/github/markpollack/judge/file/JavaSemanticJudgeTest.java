package io.github.markpollack.judge.file;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import io.github.markpollack.judge.context.JudgmentContext;
import io.github.markpollack.judge.result.Judgment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link JavaSemanticJudge}.
 */
class JavaSemanticJudgeTest {

	@TempDir
	Path tempDir;

	private JavaSemanticJudge judge;

	private Path expectedFile;

	private Path actualFile;

	@BeforeEach
	void setUp() throws IOException {
		judge = new JavaSemanticJudge();
		expectedFile = tempDir.resolve("expected/Foo.java");
		actualFile = tempDir.resolve("actual/Foo.java");
		Files.createDirectories(expectedFile.getParent());
		Files.createDirectories(actualFile.getParent());
	}

	@Test
	void passesWhenFilesAreSemanticallySame() throws IOException {
		String expected = """
				package com.example;

				import java.util.List;
				import java.util.Map;

				public class Foo {
				    private String name;
				}
				""";

		String actual = """
				package com.example;

				import java.util.Map;
				import java.util.List;

				public class Foo {
				    private String name;
				}
				""";

		Files.writeString(expectedFile, expected);
		Files.writeString(actualFile, actual);

		JudgmentContext context = createContext("Foo.java");
		Judgment judgment = judge.judge(context);

		assertThat(judgment.pass()).isTrue();
		assertThat(judgment.reasoning()).contains("semantically matches");
	}

	@Test
	void failsWhenFieldMissing() throws IOException {
		String expected = """
				package com.example;

				public class Foo {
				    private String name;
				    private int age;
				}
				""";

		String actual = """
				package com.example;

				public class Foo {
				    private String name;
				}
				""";

		Files.writeString(expectedFile, expected);
		Files.writeString(actualFile, actual);

		JudgmentContext context = createContext("Foo.java");
		Judgment judgment = judge.judge(context);

		assertThat(judgment.pass()).isFalse();
		assertThat(judgment.reasoning()).contains("missing field");
	}

	@Test
	void failsWhenActualFileMissing() throws IOException {
		String expected = "public class Foo { }";
		Files.writeString(expectedFile, expected);
		// Don't create actual file

		JudgmentContext context = createContext("Foo.java");
		Judgment judgment = judge.judge(context);

		assertThat(judgment.pass()).isFalse();
		assertThat(judgment.reasoning()).contains("File missing");
	}

	@Test
	void passesWithDifferentFormatting() throws IOException {
		String expected = """
				public class Foo {
				    public void bar() {
				        int x = 1;
				        return;
				    }
				}
				""";

		String actual = """
				public class Foo {
				  public void bar() {
				    int x=1;
				    return;
				  }
				}
				""";

		Files.writeString(expectedFile, expected);
		Files.writeString(actualFile, actual);

		JudgmentContext context = createContext("Foo.java");
		Judgment judgment = judge.judge(context);

		assertThat(judgment.pass()).isTrue();
	}

	@Test
	void passesWithDifferentComments() throws IOException {
		String expected = """
				// Comment 1
				public class Foo {
				    /** Javadoc */
				    public void bar() { }
				}
				""";

		String actual = """
				/* Different comment */
				public class Foo {
				    // Different style
				    public void bar() { }
				}
				""";

		Files.writeString(expectedFile, expected);
		Files.writeString(actualFile, actual);

		JudgmentContext context = createContext("Foo.java");
		Judgment judgment = judge.judge(context);

		assertThat(judgment.pass()).isTrue();
	}

	@Test
	void failsWhenAnnotationMissing() throws IOException {
		String expected = """
				@Deprecated
				public class Foo { }
				""";

		String actual = """
				public class Foo { }
				""";

		Files.writeString(expectedFile, expected);
		Files.writeString(actualFile, actual);

		JudgmentContext context = createContext("Foo.java");
		Judgment judgment = judge.judge(context);

		assertThat(judgment.pass()).isFalse();
		assertThat(judgment.reasoning()).contains("annotation");
	}

	private JudgmentContext createContext(String filePath) {
		return JudgmentContext.builder()
			.goal("Compare " + filePath)
			.workspace(actualFile.getParent())
			.metadata(Map.of("filePath", filePath, "expectedFile", expectedFile, "actualFile", actualFile))
			.build();
	}

}
