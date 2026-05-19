package io.github.markpollack.judge.ai.prompt;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SimpleJudgeTemplateRendererTests {

	private final SimpleJudgeTemplateRenderer renderer = SimpleJudgeTemplateRenderer.INSTANCE;

	@Test
	void substitutesPlaceholders() {
		String result = renderer.render("Hello {{name}}, your score is {{score}}.",
				Map.of("name", "Alice", "score", 95));

		assertThat(result).isEqualTo("Hello Alice, your score is 95.");
	}

	@Test
	void nullValuesReplaceWithEmpty() {
		Map<String, Object> vars = new java.util.HashMap<>();
		vars.put("name", null);
		String result = renderer.render("Hello {{name}}.", vars);

		assertThat(result).isEqualTo("Hello .");
	}

	@Test
	void unresolvedPlaceholdersLeftInPlace() {
		String result = renderer.render("{{known}} and {{unknown}}", Map.of("known", "value"));

		assertThat(result).isEqualTo("value and {{unknown}}");
	}

	@Test
	void emptyVariablesMap() {
		String result = renderer.render("No variables here.", Map.of());

		assertThat(result).isEqualTo("No variables here.");
	}

	@Test
	void dottedVariableNames() {
		String result = renderer.render("Ref: {{metadata.ref}}", Map.of("metadata.ref", "expected"));

		assertThat(result).isEqualTo("Ref: expected");
	}

}
