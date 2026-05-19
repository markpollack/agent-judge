package io.github.markpollack.judge.ai.prompt;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Factory methods for common {@link TextSource} implementations.
 *
 * @author Mark Pollack
 * @since 0.10.0
 */
public final class TextSources {

	private TextSources() {
	}

	/**
	 * Load template text from the classpath.
	 * @param path classpath resource path (e.g., "judges/relevance.md")
	 * @return a text source that loads from the classpath
	 */
	public static TextSource classpath(String path) {
		return () -> {
			try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(path)) {
				if (is == null) {
					throw new IllegalArgumentException("Classpath resource not found: " + path);
				}
				return new String(is.readAllBytes(), StandardCharsets.UTF_8);
			}
			catch (IOException ex) {
				throw new UncheckedIOException("Failed to load classpath resource: " + path, ex);
			}
		};
	}

	/**
	 * Load template text from a file.
	 * @param path the file path
	 * @return a text source that loads from the file
	 */
	public static TextSource file(Path path) {
		return () -> {
			try {
				return Files.readString(path, StandardCharsets.UTF_8);
			}
			catch (IOException ex) {
				throw new UncheckedIOException("Failed to load file: " + path, ex);
			}
		};
	}

	/**
	 * Wrap an inline string as a text source.
	 * @param content the template text
	 * @return a text source that returns the string directly
	 */
	public static TextSource string(String content) {
		return () -> content;
	}

}
