package io.github.markpollack.judge.ai.prompt;

import java.nio.file.Path;
import java.util.Map;

import io.github.markpollack.judge.context.ExecutionStatus;
import io.github.markpollack.judge.context.JudgmentContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JudgmentVariablesTests {

	@Test
	void extractsStandardVariables() {
		JudgmentContext context = JudgmentContext.builder()
			.goal("fix the bug")
			.agentOutput("Bug fixed.")
			.workspace(Path.of("/tmp/workspace"))
			.status(ExecutionStatus.SUCCESS)
			.build();

		Map<String, Object> vars = JudgmentVariables.from(context);

		assertThat(vars.get("goal")).isEqualTo("fix the bug");
		assertThat(vars.get("output")).isEqualTo("Bug fixed.");
		assertThat(vars.get("workspace")).isEqualTo("/tmp/workspace");
		assertThat(vars.get("status")).isEqualTo("SUCCESS");
	}

	@Test
	void extractsMetadataWithDottedKeys() {
		JudgmentContext context = JudgmentContext.builder()
			.goal("test")
			.status(ExecutionStatus.SUCCESS)
			.metadata("reference", "expected answer")
			.metadata("rag.context", "some context")
			.build();

		Map<String, Object> vars = JudgmentVariables.from(context);

		assertThat(vars.get("metadata.reference")).isEqualTo("expected answer");
		assertThat(vars.get("metadata.rag.context")).isEqualTo("some context");
	}

	@Test
	void handlesNullsGracefully() {
		JudgmentContext context = JudgmentContext.builder().status(ExecutionStatus.UNKNOWN).build();

		Map<String, Object> vars = JudgmentVariables.from(context);

		assertThat(vars.get("goal")).isEqualTo("");
		assertThat(vars.get("output")).isEqualTo("");
		assertThat(vars.get("workspace")).isEqualTo("");
	}

}
