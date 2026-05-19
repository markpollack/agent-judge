package io.github.markpollack.judge.ai.prompt;

import java.util.Map;

/**
 * Default renderer using {@code {{variable}}} placeholder substitution.
 * Supports flat variables and dotted metadata references
 * (e.g., {@code {{metadata.key}}}).
 *
 * <p>No loops, conditionals, or expression language. For more capable
 * rendering, plug in a template engine adapter via {@link JudgeTemplateRenderer}.
 *
 * @author Mark Pollack
 * @since 0.10.0
 */
public final class SimpleJudgeTemplateRenderer implements JudgeTemplateRenderer {

	public static final SimpleJudgeTemplateRenderer INSTANCE = new SimpleJudgeTemplateRenderer();

	@Override
	public String render(String template, Map<String, Object> variables) {
		String result = template;
		for (var entry : variables.entrySet()) {
			String placeholder = "{{" + entry.getKey() + "}}";
			String value = entry.getValue() != null ? entry.getValue().toString() : "";
			result = result.replace(placeholder, value);
		}
		return result;
	}

}
