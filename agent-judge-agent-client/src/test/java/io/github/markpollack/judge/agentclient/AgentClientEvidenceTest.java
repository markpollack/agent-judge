package io.github.markpollack.judge.agentclient;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

import io.github.markpollack.agents.client.AgentClientResponse;
import io.github.markpollack.agents.model.AgentGeneration;
import io.github.markpollack.agents.model.AgentGenerationMetadata;
import io.github.markpollack.agents.model.AgentResponse;
import io.github.markpollack.agents.model.AgentResponseMetadata;
import io.github.markpollack.judge.completion.CompletionStatus;
import io.github.markpollack.judge.conformance.CompletionEvidenceConformance;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentClientEvidenceTest {

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
	void shouldCaptureSuccessfulResponse() {
		AgentClientResponse response = successResponse();
		Path workspace = Path.of("/tmp/project");

		AgentExecutionEvidence context = AgentClientEvidence.from(response, "Fix the build", workspace);

		assertThat(context.completion().request()).isEqualTo("Fix the build");
		assertThat(context.workspace()).isEqualTo(workspace);
		assertThat(context.completion().status()).isEqualTo(CompletionStatus.SUCCESS);
		assertThat(context.completion().response()).isEqualTo("Fixed the build error in Main.java");
		assertThat(context.completion().elapsedTime()).isEqualTo(Duration.ofSeconds(45));
		assertThat(context.completion().startedAt()).isNull();
		assertThat(context.completion().metadata()).containsEntry(AgentClientMetadataKeys.MODEL, "claude-code");
		assertThat(context.completion().metadata()).containsEntry(AgentClientMetadataKeys.SESSION_ID, "sess-abc-123");
		assertThat(context.completion().metadata()).containsEntry(AgentClientMetadataKeys.FINISH_REASON, "SUCCESS");
		CompletionEvidenceConformance.assertSuccessful(context.completion(), "Fix the build",
				"Fixed the build error in Main.java");
	}

	@Test
	void shouldPreserveTimeoutAndCancellationStatus() {
		AgentExecutionEvidence timeout = AgentClientEvidence.from(responseWithFinishReason("TIMEOUT"), "Wait",
				Path.of("/tmp/project"));
		AgentExecutionEvidence cancelled = AgentClientEvidence.from(responseWithFinishReason("CANCELLED"), "Stop",
				Path.of("/tmp/project"));

		assertThat(timeout.completion().status()).isEqualTo(CompletionStatus.TIMEOUT);
		assertThat(cancelled.completion().status()).isEqualTo(CompletionStatus.CANCELLED);
	}

	@Test
	void shouldCaptureFailedResponse() {
		AgentClientResponse response = failureResponse();

		AgentExecutionEvidence context = AgentClientEvidence.from(response, "Impossible task", Path.of("/tmp/project"));

		assertThat(context.completion().status()).isEqualTo(CompletionStatus.FAILED);
		assertThat(context.completion().metadata()).containsEntry(AgentClientMetadataKeys.MODEL, "codex");
		assertThat(context.completion().metadata()).containsEntry(AgentClientMetadataKeys.FINISH_REASON, "ERROR");
	}

	@Test
	void shouldCaptureExceptionFromSupplier() {
		RuntimeException failure = new RuntimeException("CLI process failed");
		AgentExecutionEvidence context = AgentClientEvidence.execute("Crash", Path.of("/tmp"), () -> {
			throw failure;
		});

		assertThat(context.completion().status()).isEqualTo(CompletionStatus.FAILED);
		assertThat(context.completion().error()).isNotNull()
			.satisfies(e -> assertThat(e.getMessage()).isEqualTo("CLI process failed"));
		assertThat(context.completion().elapsedTime()).isGreaterThanOrEqualTo(Duration.ZERO);
		CompletionEvidenceConformance.assertFailed(context.completion(), "Crash", failure);
	}

	private AgentClientResponse responseWithFinishReason(String finishReason) {
		AgentGeneration generation = new AgentGeneration("", new AgentGenerationMetadata(finishReason, null));
		return new AgentClientResponse(new AgentResponse(List.of(generation), AgentResponseMetadata.builder().build()));
	}

	@Test
	void shouldHandleNullResponse() {
		AgentExecutionEvidence context = AgentClientEvidence.from(null, "Test", Path.of("/tmp"));

		assertThat(context.completion().status()).isEqualTo(CompletionStatus.UNKNOWN);
		assertThat(context.completion().startedAt()).isNull();
		assertThat(context.completion().elapsedTime()).isNull();
		assertThat(context.completion().response()).isNull();
	}

	@Test
	void shouldIncludeExtraMetadata() {
		AgentClientResponse response = successResponse();

		AgentExecutionEvidence context = AgentClientEvidence.from(response, "Fix build", Path.of("/tmp"),
				java.util.Map.of("run.id", "exp-42", "dataset.row", 7));

		assertThat(context.completion().metadata()).containsEntry("run.id", "exp-42");
		assertThat(context.completion().metadata()).containsEntry("dataset.row", 7);
		assertThat(context.completion().metadata()).containsEntry(AgentClientMetadataKeys.MODEL, "claude-code");
	}

	@Test
	void precomputedResponseDoesNotInventTiming() {
		var completion = AgentClientEvidence.from(responseWithoutMetadata(), "Test", Path.of("/tmp")).completion();
		assertThat(completion.startedAt()).isNull();
		assertThat(completion.elapsedTime()).isNull();
	}

	@Test
	void executionMeasuresMissingTimingAndPreservesReportedZeroDuration() {
		Instant before = Instant.now();
		var measured = AgentClientEvidence.execute("Test", Path.of("/tmp"), () -> responseWithoutMetadata())
			.completion();
		Instant after = Instant.now();
		assertThat(measured.startedAt()).isBetween(before, after);
		assertThat(measured.elapsedTime()).isGreaterThanOrEqualTo(Duration.ZERO);
		var zeroResponse = new AgentClientResponse(
				new AgentResponse(List.of(new AgentGeneration("done", new AgentGenerationMetadata("SUCCESS", null))),
						AgentResponseMetadata.builder().duration(Duration.ZERO).build()));
		var reported = AgentClientEvidence.execute("Test", Path.of("/tmp"), () -> zeroResponse).completion();
		assertThat(reported.elapsedTime()).isEqualTo(Duration.ZERO);
	}

	private AgentClientResponse responseWithoutMetadata() {
		var response = mock(AgentClientResponse.class);
		when(response.getResult()).thenReturn("done");
		when(response.isSuccessful()).thenReturn(true);
		return response;
	}

}
