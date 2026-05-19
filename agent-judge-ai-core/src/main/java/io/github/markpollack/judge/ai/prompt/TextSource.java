package io.github.markpollack.judge.ai.prompt;

/**
 * A source of prompt template text. Implementations load text from classpath
 * resources, files, or inline strings.
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
