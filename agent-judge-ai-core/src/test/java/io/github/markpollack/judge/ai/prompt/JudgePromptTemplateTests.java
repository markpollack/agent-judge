package io.github.markpollack.judge.ai.prompt;

import io.github.markpollack.judge.context.ExecutionStatus;
import io.github.markpollack.judge.context.JudgmentContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JudgePromptTemplateTests {

	@Test
	void renderSubstitutesVariables() {
		var template = JudgePromptTemplate.fromString("test", "Goal: {{goal}}\nOutput: {{output}}");

		JudgmentContext context = JudgmentContext.builder()
			.goal("summarize")
			.agentOutput("A summary.")
			.status(ExecutionStatus.SUCCESS)
			.build();

		String rendered = template.render(context);

		assertThat(rendered).isEqualTo("Goal: summarize\nOutput: A summary.");
	}

	@Test
	void renderMetadataVariables() {
		var template = JudgePromptTemplate.fromString("test", "Ref: {{metadata.reference}}");

		JudgmentContext context = JudgmentContext.builder()
			.goal("test")
			.status(ExecutionStatus.SUCCESS)
			.metadata("reference", "expected answer")
			.build();

		String rendered = template.render(context);

		assertThat(rendered).isEqualTo("Ref: expected answer");
	}

	@Test
	void strictPolicyRejectsUnresolvedPlaceholders() {
		var template = JudgePromptTemplate.fromString("test", "{{goal}} and {{unknown}}");

		JudgmentContext context = JudgmentContext.builder()
			.goal("test")
			.status(ExecutionStatus.SUCCESS)
			.build();

		assertThatThrownBy(() -> template.render(context)).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("unknown");
	}

	@Test
	void emptyStringPolicyReplacesUnresolved() {
		var template = JudgePromptTemplate.builder()
			.name("test")
			.source(TextSources.string("{{goal}} and {{unknown}}"))
			.missingVariablePolicy(JudgePromptTemplate.MissingVariablePolicy.EMPTY_STRING)
			.build();

		JudgmentContext context = JudgmentContext.builder()
			.goal("test")
			.status(ExecutionStatus.SUCCESS)
			.build();

		assertThat(template.render(context)).isEqualTo("test and ");
	}

	@Test
	void leavePlaceholderPolicy() {
		var template = JudgePromptTemplate.builder()
			.name("test")
			.source(TextSources.string("{{goal}} and {{unknown}}"))
			.missingVariablePolicy(JudgePromptTemplate.MissingVariablePolicy.LEAVE_PLACEHOLDER)
			.build();

		JudgmentContext context = JudgmentContext.builder()
			.goal("test")
			.status(ExecutionStatus.SUCCESS)
			.build();

		assertThat(template.render(context)).isEqualTo("test and {{unknown}}");
	}

	@Test
	void requiredVariablesEnforced() {
		var template = JudgePromptTemplate.builder()
			.name("test")
			.source(TextSources.string("{{goal}} {{output}}"))
			.requiredVariables("goal", "metadata.reference")
			.build();

		JudgmentContext context = JudgmentContext.builder()
			.goal("test")
			.agentOutput("output")
			.status(ExecutionStatus.SUCCESS)
			.build();

		assertThatThrownBy(() -> template.render(context)).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("metadata.reference");
	}

	@Test
	void classpathResourceLoads() {
		var template = JudgePromptTemplate.fromClasspath("judges/test-relevance.md");

		JudgmentContext context = JudgmentContext.builder()
			.goal("test goal")
			.agentOutput("test output")
			.status(ExecutionStatus.SUCCESS)
			.build();

		String rendered = template.render(context);

		assertThat(rendered).contains("test goal");
		assertThat(rendered).contains("test output");
		assertThat(rendered).doesNotContain("{{");
	}

	@Test
	void missingClasspathResourceThrows() {
		var template = JudgePromptTemplate.fromClasspath("nonexistent/template.md");

		JudgmentContext context = JudgmentContext.builder()
			.goal("test")
			.status(ExecutionStatus.SUCCESS)
			.build();

		assertThatThrownBy(() -> template.render(context)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("not found");
	}

	@Test
	void builderValidation() {
		assertThatThrownBy(() -> JudgePromptTemplate.builder().build()).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("name");

		assertThatThrownBy(() -> JudgePromptTemplate.builder().name("test").build())
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("source");
	}

}
