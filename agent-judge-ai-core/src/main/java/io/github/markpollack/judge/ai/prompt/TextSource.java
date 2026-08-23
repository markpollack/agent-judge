package io.github.markpollack.judge.ai.prompt;

/**
 * A source of prompt template text. Implementations load text from classpath
 * resources, files, or inline strings.
 *
 * <p>
 * {@link #load()} may be called on any thread, including a jury's execution pool. An
 * implementation must therefore not depend on the calling thread's context classloader or
 * any other thread-local state. The {@link TextSources} factories satisfy this by
 * resolving their text eagerly, at construction, and replaying it.
 * </p>
 *
 * @author Mark Pollack
 * @since 0.10.0
 * @see TextSources
 */
@FunctionalInterface
public interface TextSource {

	/**
	 * Load the template text.
	 * @return the template text
	 */
	String load();

}
