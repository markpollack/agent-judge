/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.llm;

import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.metadata.*;
import org.springframework.ai.chat.model.*;
import io.github.markpollack.judge.ai.model.*;
import io.github.markpollack.judge.ai.requirements.*;
import io.github.markpollack.judge.judgment.*;
import io.github.markpollack.judge.jury.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SpringUsagePreservationTest {

	private ChatClient client(ChatResponseMetadata metadata) {
		var client = mock(ChatClient.class);
		var request = mock(ChatClient.ChatClientRequestSpec.class);
		var call = mock(ChatClient.CallResponseSpec.class);
		when(client.prompt()).thenReturn(request);
		when(request.messages(org.mockito.ArgumentMatchers.<Message>anyList())).thenReturn(request);
		when(request.call()).thenReturn(call);
		var response = new ChatResponse(List.of(new Generation(new AssistantMessage("A: PASS - original answer"),
				ChatGenerationMetadata.builder().finishReason("stop").build())), metadata);
		when(call.chatResponse()).thenReturn(response);
		return client;
	}

	@Test
	void malformedUsageKeepsOriginalSdkAnswerAndSeparateMappingFailure() {
		var client = client(ChatResponseMetadata.builder()
			.model("original-model")
			.id("original-id")
			.usage(new DefaultUsage(-1, 7))
			.build());
		var runtime = new SpringAiJudgeModel(client);
		var nativeResult = runtime.execute(JudgeModelRequest.user("local fixture"));
		var response = nativeResult.answer();
		assertThat(response.text()).isEqualTo("A: PASS - original answer");
		assertThat(response.model()).isEqualTo("original-model");
		assertThat(response.usage()).isNull();
		assertThat(response.completed()).isFalse();
		assertThat(response.failure()).isInstanceOf(IllegalArgumentException.class);
		assertThat(response.metadata()).containsEntry("responseId", "original-id")
			.containsEntry("finishReason", "stop")
			.containsKey("mappingFailure")
			.doesNotContainKeys("failureType", "captureFailure");
		assertThat(response.metadata().get("nativeResponseJson").toString()).contains("original answer",
				"original-model", "original-id", "-1", "7");
		var requirement = Rfc2119Requirement.of("A", "1", "MUST", "retain", "audit", null);
		var judgment = Rfc2119Judge.builder().runtime(runtime).requirement(requirement).build().judge();
		assertThat(judgment.status()).isEqualTo(JudgmentStatus.ERROR);
		assertThat(judgment.invocations().getFirst().nativeFacts()).containsEntry("text", response.text())
			.containsKey("mappingFailure")
			.doesNotContainKey("usage");
		var codec = NativeRequirementCodecs.codec();
		var retained = codec.read(codec.write(Verdict.single("A", judgment)));
		assertThat(retained.individual().getFirst()).isEqualTo(judgment);
		verify(client, times(2)).prompt();
	}

	@Test
	void defaultEmptyUsageStaysAbsentInCommonFacts() {
		var client = client(ChatResponseMetadata.builder().model("original-model").id("original-id").build());
		var runtime = new SpringAiJudgeModel(client);
		var result = runtime.execute(JudgeModelRequest.user("local fixture"));
		assertThat(result.answer().usage()).isNull();
		assertThat(result.invocation().nativeFacts()).doesNotContainKey("usage");
		assertThat(result.answer().completed()).isTrue();
		assertThat(result.invocation().nativeFacts()).containsKey("nativeResponseJson");
		var original = Verdict.single("observed",
				Judgment.pass("Local retention fixture").withInvocation(result.invocation()));
		var codec = NativeRequirementCodecs.codec();
		var read = codec.read(codec.write(original));
		assertThat(read).isEqualTo(original);
		assertThat(InvocationRecords.of(read).getFirst().nativeFacts()).doesNotContainKey("usage");
		verify(client, times(1)).prompt();
	}

	@Test
	void explicitlyReportedZeroUsageRemainsPresent() {
		var result = new SpringAiJudgeModel(
				client(ChatResponseMetadata.builder().usage(new DefaultUsage(0, 0)).build()))
			.execute(JudgeModelRequest.user("local fixture"));
		assertThat(result.answer().usage().inputTokens()).isZero();
		assertThat(result.answer().usage().outputTokens()).isZero();
		assertThat(result.answer().usage().reportedTotalTokens()).isNull();
	}

	@Test
	void protectedCaptureRunsBeforeMalformedUsageMappingAndRetainsArtifacts() {
		var client = client(ChatResponseMetadata.builder()
			.model("original-model")
			.id("original-id")
			.usage(new DefaultUsage(-1, 7))
			.build());
		var captured = new AtomicReference<ChatResponse>();
		var artifact = io.github.markpollack.judge.provenance.ArtifactRef.ofBytes("protected-sdk-answer",
				new byte[] { 1, 2 }, null);
		var runtime = new SpringAiJudgeModel(client, response -> {
			captured.set(response);
			return new NativeSnapshot(Map.of("protected", true), List.of(artifact));
		});
		var result = runtime.execute(JudgeModelRequest.user("local fixture"));
		assertThat(captured.get().getMetadata().getUsage().getPromptTokens()).isEqualTo(-1);
		assertThat(captured.get().getResult().getOutput().getText()).isEqualTo(result.answer().text());
		assertThat(result.invocation().artifacts()).containsExactly(artifact);
		assertThat(result.invocation().nativeFacts()).containsEntry("protected", true).containsKey("mappingFailure");
	}

	@Test
	void captureAndMappingFailuresRemainDistinct() {
		var captureFailure = new IllegalStateException("capture failed");
		var runtime = new SpringAiJudgeModel(
				client(ChatResponseMetadata.builder().usage(new DefaultUsage(-1, 7)).build()), response -> {
					throw captureFailure;
				});
		var result = runtime.execute(JudgeModelRequest.user("local fixture"));
		assertThat(result.answer().text()).isEqualTo("A: PASS - original answer");
		assertThat(result.invocation().nativeFacts()).containsKeys("captureFailure", "mappingFailure");
		assertThat(result.invocation().cause()).isSameAs(captureFailure);
	}

	@Test
	void cancellationEscapesCaptureBeforeMalformedUsageMapping() {
		var cancelled = new CancellationException("caller cancelled");
		var runtime = new SpringAiJudgeModel(
				client(ChatResponseMetadata.builder().usage(new DefaultUsage(-1, 7)).build()), response -> {
					throw cancelled;
				});
		assertThatThrownBy(() -> runtime.execute(JudgeModelRequest.user("local fixture"))).isSameAs(cancelled);
	}

}
