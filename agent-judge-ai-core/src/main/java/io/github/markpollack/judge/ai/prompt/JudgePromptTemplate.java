package io.github.markpollack.judge.ai.prompt;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.markpollack.judge.context.JudgmentContext;

/**
 * A judge prompt template loaded from an external source.
 *
 * <p>Delegates rendering to a pluggable {@link JudgeTemplateRenderer}.
 * {@code requiredVariables} is an optional invocation contract declared by the
 * template author — the implementation does not infer required variables from
 * prompt syntax.
 *
 * @author Mark Pollack
 * @since 0.10.0
 * @see JudgeTemplateRenderer
 * @see SimpleJudgeTemplateRenderer
 */
public final class JudgePromptTemplate {

	private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{([^}]+)}}");

	private final String name;

	private final TextSource source;

	private final JudgeTemplateRenderer renderer;

	private final Set<String> requiredVariables;

	private final MissingVariablePolicy missingVariablePolicy;

	/**
	 * Policy for unresolved placeholders after rendering.
	 */
	public enum MissingVariablePolicy {

		/** Throw if any placeholders remain after rendering. */
		STRICT,
		/** Replace remaining placeholders with empty string. */
		EMPTY_STRING,
		/** Leave unresolved placeholders in the output. */
		LEAVE_PLACEHOLDER

	}

	private JudgePromptTemplate(String name, TextSource source, JudgeTemplateRenderer renderer,
			Set<String> requiredVariables, MissingVariablePolicy missingVariablePolicy) {
		this.name = name;
		this.source = source;
		this.renderer = renderer;
		this.requiredVariables = Set.copyOf(requiredVariables);
		this.missingVariablePolicy = missingVariablePolicy;
	}

	/**
	 * Create a template from a classpath resource with default settings.
	 * @param path classpath resource path
	 * @return a new template
	 */
	public static JudgePromptTemplate fromClasspath(String path) {
		return new JudgePromptTemplate(pathToName(path), TextSources.classpath(path),
				SimpleJudgeTemplateRenderer.INSTANCE, Set.of(), MissingVariablePolicy.STRICT);
	}

	/**
	 * Create a template from a file with default settings.
	 * @param path the file path
	 * @return a new template
	 */
	public static JudgePromptTemplate fromFile(Path path) {
		return new JudgePromptTemplate(path.getFileName().toString(), TextSources.file(path),
				SimpleJudgeTemplateRenderer.INSTANCE, Set.of(), MissingVariablePolicy.STRICT);
	}

	/**
	 * Create a template from an inline string with default settings.
	 * @param name the template name
	 * @param template the template text
	 * @return a new template
	 */
	public static JudgePromptTemplate fromString(String name, String template) {
		return new JudgePromptTemplate(name, TextSources.string(template), SimpleJudgeTemplateRenderer.INSTANCE,
				Set.of(), MissingVariablePolicy.STRICT);
	}

	/**
	 * Render this template with variables extracted from a judgment context.
	 * @param context the judgment context
	 * @return the rendered prompt text
	 */
	public String render(JudgmentContext context) {
		Map<String, Object> variables = JudgmentVariables.from(context);

		// Validate declared required variables before rendering
		for (String required : requiredVariables) {
			if (!variables.containsKey(required) || variables.get(required) == null) {
				throw new IllegalStateException(
						"Required variable '" + required + "' not present in JudgmentContext for template '" + name
								+ "'");
			}
		}

		String rendered = renderer.render(source.load(), variables);

		return applyMissingVariablePolicy(rendered);
	}

	public String name() {
		return name;
	}

	public Set<String> requiredVariables() {
		return requiredVariables;
	}

	public MissingVariablePolicy missingVariablePolicy() {
		return missingVariablePolicy;
	}

	private String applyMissingVariablePolicy(String rendered) {
		return switch (missingVariablePolicy) {
			case STRICT -> {
				Matcher m = PLACEHOLDER_PATTERN.matcher(rendered);
				if (m.find()) {
					throw new IllegalStateException(
							"Unresolved placeholder '{{" + m.group(1) + "}}' in template '" + name + "'");
				}
				yield rendered;
			}
			case EMPTY_STRING -> PLACEHOLDER_PATTERN.matcher(rendered).replaceAll("");
			case LEAVE_PLACEHOLDER -> rendered;
		};
	}

	private static String pathToName(String path) {
		int lastSlash = path.lastIndexOf('/');
		return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private String name;

		private TextSource source;

		private JudgeTemplateRenderer renderer = SimpleJudgeTemplateRenderer.INSTANCE;

		private Set<String> requiredVariables = new LinkedHashSet<>();

		private MissingVariablePolicy missingVariablePolicy = MissingVariablePolicy.STRICT;

		public Builder name(String name) {
			this.name = name;
			return this;
		}

		public Builder source(TextSource source) {
			this.source = source;
			return this;
		}

		public Builder renderer(JudgeTemplateRenderer renderer) {
			this.renderer = renderer;
			return this;
		}

		public Builder requiredVariables(String... variables) {
			for (String v : variables) {
				this.requiredVariables.add(v);
			}
			return this;
		}

		public Builder missingVariablePolicy(MissingVariablePolicy policy) {
			this.missingVariablePolicy = policy;
			return this;
		}

		public JudgePromptTemplate build() {
			if (name == null) {
				throw new IllegalStateException("Template name is required");
			}
			if (source == null) {
				throw new IllegalStateException("Template source is required");
			}
			return new JudgePromptTemplate(name, source, renderer, requiredVariables, missingVariablePolicy);
		}

	}

}
