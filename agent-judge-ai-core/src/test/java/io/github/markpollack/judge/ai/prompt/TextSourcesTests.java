package io.github.markpollack.judge.ai.prompt;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link TextSources}, whose sources resolve at construction rather than at
 * render time.
 */
class TextSourcesTests {

	@Test
	void classpathResourceIsReadBeforeTheFactoryReturns() {
		TextSource source = TextSources.classpath("judges/test-relevance.md");

		// Nothing is left to resolve, so load() is a replay and cannot fail on the thread
		// that happens to call it.
		assertThat(source.load()).isEqualTo(source.load()).contains("{{goal}}");
	}

	@Test
	void missingClasspathResourceThrowsAtConstruction() {
		assertThatThrownBy(() -> TextSources.classpath("nonexistent/template.md"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("not found");
	}

	@Test
	void fileTextIsFrozenAtConstruction(@TempDir Path dir) throws Exception {
		Path template = dir.resolve("relevance.md");
		Files.writeString(template, "original {{goal}}");

		TextSource source = TextSources.file(template);
		Files.writeString(template, "edited mid-run {{goal}}");

		// Two judgments in one run must be made against the same prompt.
		assertThat(source.load()).isEqualTo("original {{goal}}");
	}

	@Test
	void missingFileThrowsAtConstruction(@TempDir Path dir) {
		assertThatThrownBy(() -> TextSources.file(dir.resolve("absent.md"))).isInstanceOf(UncheckedIOException.class)
			.hasMessageContaining("Failed to load file");
	}

	@Test
	void stringSourceReturnsItsContent() {
		assertThat(TextSources.string("literal").load()).isEqualTo("literal");
	}

}
