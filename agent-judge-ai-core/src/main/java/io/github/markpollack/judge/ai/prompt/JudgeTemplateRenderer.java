package io.github.markpollack.judge.ai.prompt;

import java.util.Map;

/**
 * Renders a template string with variables. Pluggable — the default implementation
 * uses simple {@code {{variable}}} substitution. Template engine adapters
 * (StringTemplate, Handlebars, JTE) go in optional modules.
 *
 * @author Mark Pollack
 * @since 0.10.0
 * @see SimpleJudgeTemplateRenderer
 */
@FunctionalInterface
public interface JudgeTemplateRenderer {

	/**
	 * Render a template with the given variables.
	 * @param template the template text
	 * @param variables the variable name-to-value map
	 * @return the rendered text
	 */
	String render(String template, Map<String, Object> variables);

}
