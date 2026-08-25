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
 * <p>
 * Every source produced here resolves its text <em>eagerly</em>, on the thread that calls
 * the factory method; the returned {@link TextSource} replays the captured text. A prompt
 * template is a small text file, so laziness buys nothing and costs correctness: rendering
 * happens on whatever thread a jury schedules the judge on. A parallel {@code SimpleJury}
 * renders on a {@code ForkJoinPool.commonPool()} worker whose context classloader is the
 * system classloader, not the application's, so a classpath template that resolved
 * perfectly well from application code could fail to load there and drop that judge out of
 * the vote while the jury still returned a verdict.
 * </p>
 *
 * <p>
 * Eager resolution also puts the failure where the caller can act on it. A missing
 * resource or an unreadable file throws at construction, with a stack trace pointing at
 * the configuration that named it, rather than inside a judge on a pool thread.
 * </p>
 *
 * @author Mark Pollack
 * @since 0.10.0
 */
public final class TextSources {

	private TextSources() {
	}

	/**
	 * Load template text from the classpath, immediately.
	 * <p>
	 * The resource is read before this method returns. Resolution tries this class's own
	 * classloader first, so it never depends on the calling thread's context classloader,
	 * then the context classloader and the system classloader — those cover container and
	 * child-first arrangements where the library is loaded by a parent that cannot see
	 * application resources.
	 * </p>
	 * @param path classpath resource path (e.g., "judges/relevance.md")
	 * @return a text source replaying the loaded text
	 * @throws IllegalArgumentException if no candidate classloader can see the resource
	 * @throws UncheckedIOException if the resource is found but cannot be read
	 */
	public static TextSource classpath(String path) {
		String content = readClasspath(path);
		return () -> content;
	}

	/**
	 * Load template text from a file, immediately.
	 * <p>
	 * The file is read before this method returns, so the template text is frozen at
	 * construction. That is the reproducible choice: a template that can be edited
	 * mid-run would let two judgments in the same run be made against different prompts.
	 * </p>
	 * @param path the file path
	 * @return a text source replaying the loaded text
	 * @throws UncheckedIOException if the file cannot be read
	 */
	public static TextSource file(Path path) {
		String content = readFile(path);
		return () -> content;
	}

	/**
	 * Wrap an inline string as a text source.
	 * @param content the template text
	 * @return a text source that returns the string directly
	 */
	public static TextSource string(String content) {
		return () -> content;
	}

	private static String readClasspath(String path) {
		for (ClassLoader loader : candidateLoaders()) {
			if (loader == null) {
				continue;
			}
			try (InputStream is = loader.getResourceAsStream(path)) {
				if (is != null) {
					return new String(is.readAllBytes(), StandardCharsets.UTF_8);
				}
			}
			catch (IOException ex) {
				throw new UncheckedIOException("Failed to load classpath resource: " + path, ex);
			}
		}
		throw new IllegalArgumentException("Classpath resource not found: " + path);
	}

	private static ClassLoader[] candidateLoaders() {
		return new ClassLoader[] { TextSources.class.getClassLoader(), Thread.currentThread().getContextClassLoader(),
				ClassLoader.getSystemClassLoader() };
	}

	private static String readFile(Path path) {
		try {
			return Files.readString(path, StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Failed to load file: " + path, ex);
		}
	}

}
