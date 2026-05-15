package io.github.markpollack.judge.agentclient;

import java.nio.file.Path;
import java.time.Duration;

import io.github.markpollack.agents.client.AgentClientResponse;
import io.github.markpollack.agents.model.AgentGeneration;
import io.github.markpollack.agents.model.AgentGenerationMetadata;
import io.github.markpollack.agents.model.AgentResponse;
import io.github.markpollack.agents.model.AgentResponseMetadata;
import io.github.markpollack.judge.context.ExecutionStatus;
import io.github.markpollack.judge.context.JudgmentContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentClientJudgmentContextBuilderTest {

	private AgentClientResponse successResponse() {
		AgentGenerationMetadata genMeta = new AgentGenerationMetadata("SUCCESS", null);
		AgentGeneration gen = new AgentGeneration("Fixed the build error in Main.java", genMeta);
		AgentResponseMetadata meta = AgentResponseMetadata.builder()
			.model("claude-code")
			.duration(Duration.ofSeconds(45))
			.sessionId("sess-abc-123")
			.build();
		AgentResponse agentResponse = new AgentResponse(List.of(gen), meta);
		return new AgentClientResponse(agentResponse);
	}

	private AgentClientResponse failureResponse() {
		AgentGenerationMetadata genMeta = new AgentGenerationMetadata("ERROR", null);
		AgentGeneration gen = new AgentGeneration("", genMeta);
		AgentResponseMetadata meta = AgentResponseMetadata.builder()
			.model("codex")
			.duration(Duration.ofSeconds(120))
			.sessionId("sess-def-456")
			.build();
		AgentResponse agentResponse = new AgentResponse(List.of(gen), meta);
		return new AgentClientResponse(agentResponse);
	}

	@Test
	void shouldBuildContextFromSuccessfulResponse() {
		AgentClientResponse response = successResponse();
		Path workspace = Path.of("/tmp/project");

		JudgmentContext context = AgentClientJudgmentContextBuilder.from(response, "Fix the build", workspace);

		assertThat(context.goal()).isEqualTo("Fix the build");
		assertThat(context.workspace()).isEqualTo(workspace);
		assertThat(context.status()).isEqualTo(ExecutionStatus.SUCCESS);
		assertThat(context.agentOutput()).isPresent().hasValue("Fixed the build error in Main.java");
		assertThat(context.executionTime()).isEqualTo(Duration.ofSeconds(45));
		assertThat(context.metadata()).containsEntry(AgentClientMetadataKeys.MODEL, "claude-code");
		assertThat(context.metadata()).containsEntry(AgentClientMetadataKeys.SESSION_ID, "sess-abc-123");
		assertThat(context.metadata()).containsEntry(AgentClientMetadataKeys.FINISH_REASON, "SUCCESS");
	}

	@Test
	void shouldBuildContextFromFailedResponse() {
		AgentClientResponse response = failureResponse();

		JudgmentContext context = AgentClientJudgmentContextBuilder.from(response, "Impossible task",
				Path.of("/tmp/project"));

		assertThat(context.status()).isEqualTo(ExecutionStatus.FAILED);
		assertThat(context.metadata()).containsEntry(AgentClientMetadataKeys.MODEL, "codex");
		assertThat(context.metadata()).containsEntry(AgentClientMetadataKeys.FINISH_REASON, "ERROR");
	}

	@Test
	void shouldCaptureExceptionFromSupplier() {
		JudgmentContext context = AgentClientJudgmentContextBuilder.execute("Crash", Path.of("/tmp"), () -> {
			throw new RuntimeException("CLI process failed");
		});

		assertThat(context.status()).isEqualTo(ExecutionStatus.FAILED);
		assertThat(context.error()).isPresent()
			.hasValueSatisfying(e -> assertThat(e.getMessage()).isEqualTo("CLI process failed"));
		assertThat(context.executionTime()).isGreaterThanOrEqualTo(Duration.ZERO);
	}

	@Test
	void shouldHandleNullResponse() {
		JudgmentContext context = AgentClientJudgmentContextBuilder.from(null, "Test", Path.of("/tmp"));

		assertThat(context.status()).isEqualTo(ExecutionStatus.UNKNOWN);
		assertThat(context.agentOutput()).isEmpty();
	}

	@Test
	void shouldIncludeExtraMetadata() {
		AgentClientResponse response = successResponse();

		JudgmentContext context = AgentClientJudgmentContextBuilder.from(response, "Fix build", Path.of("/tmp"),
				java.util.Map.of("run.id", "exp-42", "dataset.row", 7));

		assertThat(context.metadata()).containsEntry("run.id", "exp-42");
		assertThat(context.metadata()).containsEntry("dataset.row", 7);
		assertThat(context.metadata()).containsEntry(AgentClientMetadataKeys.MODEL, "claude-code");
	}

}
