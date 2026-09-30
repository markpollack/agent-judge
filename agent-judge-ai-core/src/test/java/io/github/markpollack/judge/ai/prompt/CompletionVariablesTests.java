package io.github.markpollack.judge.ai.prompt;

import java.util.Map;

import io.github.markpollack.judge.completion.CompletionStatus;
import io.github.markpollack.judge.completion.CompletionEvidence;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CompletionVariablesTests {

	@Test
	void extractsStandardVariables() {
		CompletionEvidence context = CompletionEvidence.builder()
			.request("fix the bug")
			.response("Bug fixed.")
			.status(CompletionStatus.SUCCESS)
			.build();

		Map<String, Object> vars = CompletionVariables.from(context);

		assertThat(vars.get("goal")).isEqualTo("fix the bug");
		assertThat(vars.get("output")).isEqualTo("Bug fixed.");
		assertThat(vars).doesNotContainKey("workspace");
		assertThat(vars.get("status")).isEqualTo("SUCCESS");
	}

	@Test
	void extractsMetadataWithDottedKeys() {
		CompletionEvidence context = CompletionEvidence.builder()
			.request("test")
			.status(CompletionStatus.SUCCESS)
			.metadata("reference", "expected answer")
			.metadata("rag.context", "some context")
			.build();

		Map<String, Object> vars = CompletionVariables.from(context);

		assertThat(vars.get("metadata.reference")).isEqualTo("expected answer");
		assertThat(vars.get("metadata.rag.context")).isEqualTo("some context");
	}

	@Test
	void handlesNullsGracefully() {
		CompletionEvidence context = CompletionEvidence.builder().status(CompletionStatus.UNKNOWN).build();

		Map<String, Object> vars = CompletionVariables.from(context);

		assertThat(vars.get("goal")).isEqualTo("");
		assertThat(vars.get("output")).isEqualTo("");
		assertThat(vars).doesNotContainKey("workspace");
	}

}
