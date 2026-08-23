package io.github.markpollack.judge.ai.prompt;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

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
	void missingClasspathResourceThrowsAtConstruction() {
		// Eager resolution moves this failure to the caller's thread and the caller's
		// stack, where the configuration that named the resource is still visible.
		assertThatThrownBy(() -> JudgePromptTemplate.fromClasspath("nonexistent/template.md"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("not found");
	}

	// ==================== Template loading is thread-context independent ====================
	//
	// Regression guards for the defect where a classpath template resolved lazily through
	// the thread context classloader. A parallel SimpleJury renders on a common-pool
	// worker whose TCCL is the system classloader, so under exec:java or any container
	// the resource was not found, the judge threw, and it dropped out of the vote while
	// the jury still returned a verdict. Both tests failed before the fix.

	@Test
	void classpathTemplateRendersOnAThreadWhoseContextClassLoaderCannotSeeIt() throws Exception {
		var template = JudgePromptTemplate.fromClasspath("judges/test-relevance.md");

		String rendered = withoutApplicationContextClassLoader(() -> template.render(renderContext()));

		assertThat(rendered).contains("test goal").contains("test output").doesNotContain("{{");
	}

	@Test
	void classpathTemplateResolvesWithoutTheContextClassLoaderAtAll() throws Exception {
		// Constructed as well as rendered with a blinded TCCL: resolution must not consult
		// it, rather than merely consulting it at a luckier moment.
		String rendered = withoutApplicationContextClassLoader(
				() -> JudgePromptTemplate.fromClasspath("judges/test-relevance.md").render(renderContext()));

		assertThat(rendered).contains("test goal").contains("test output");
	}

	private static JudgmentContext renderContext() {
		return JudgmentContext.builder()
			.goal("test goal")
			.agentOutput("test output")
			.status(ExecutionStatus.SUCCESS)
			.build();
	}

	/**
	 * Run {@code action} on a thread whose context classloader is an empty loader with no
	 * parent, standing in for the common-pool worker that exposed this defect.
	 */
	private static <T> T withoutApplicationContextClassLoader(Callable<T> action) throws Exception {
		try (URLClassLoader blind = new URLClassLoader(new URL[0], null)) {
			FutureTask<T> task = new FutureTask<>(() -> {
				// Guard against the test going vacuous if this loader ever gains sight of
				// the resource.
				assertThat(Thread.currentThread().getContextClassLoader().getResourceAsStream(
						"judges/test-relevance.md")).isNull();
				return action.call();
			});
			Thread thread = new Thread(task, "blind-context-classloader");
			thread.setContextClassLoader(blind);
			thread.start();
			thread.join();
			try {
				return task.get();
			}
			catch (ExecutionException ex) {
				if (ex.getCause() instanceof Exception cause) {
					throw cause;
				}
				throw ex;
			}
		}
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
