/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.llm;

import java.util.*;
import java.util.concurrent.CancellationException;
import io.github.markpollack.judge.ai.model.*;
import io.github.markpollack.judge.ai.requirements.*;
import io.github.markpollack.judge.judgment.*;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.metadata.*;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.ChatOptions;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Native SDK fixtures exercise the actual configured RFC2119 boundary. No inference. */
class ConfiguredNativeProtocolTest {

	record Harness(ChatClient client, ChatClient.ChatClientRequestSpec request, ChatClient.CallResponseSpec call) {
	}

	Harness harness(String text) {
		var client = mock(ChatClient.class);
		var request = mock(ChatClient.ChatClientRequestSpec.class);
		var call = mock(ChatClient.CallResponseSpec.class);
		when(client.prompt()).thenReturn(request);
		when(request.messages(org.mockito.ArgumentMatchers.<Message>anyList())).thenReturn(request);
		when(request.options(any(ChatOptions.Builder.class))).thenReturn(request);
		when(request.call()).thenReturn(call);
		var response = new ChatResponse(
				List.of(new Generation(new AssistantMessage(text),
						ChatGenerationMetadata.builder().finishReason("stop").build())),
				ChatResponseMetadata.builder()
					.model("fixture-model")
					.id("response-7")
					.usage(new DefaultUsage(19, 7, 26, null, 3L, 5L))
					.build());
		when(call.chatResponse()).thenReturn(response);
		return new Harness(client, request, call);
	}

	@Test
	void configuredNativeJudgingPreservesSdkAnswerUsageAndActualInput() {
		var h = harness("R: PASS - Foo.java:7 retained");
		var runtime = new SpringAiEvalModel(h.client());
		var actual = Rfc2119Requirement.of("R", "7", "MUST", "retain native evidence", "audit", null);
		var ready = Rfc2119Judge.builder().runtime(runtime).requirement(actual).evidence("observed").build();
		verifyNoInteractions(h.request(), h.call());
		Judgment result = ready.judge();
		assertThat(result.status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(result.requirement()).isSameAs(actual);
		assertThat(result.invocations()).hasSize(1);
		var original = result.invocations().getFirst();
		assertThat(original.completed()).isTrue();
		assertThat(original.model()).isEqualTo("fixture-model");
		assertThat(original.nativeFacts()).containsEntry("text", "R: PASS - Foo.java:7 retained")
			.containsEntry("responseId", "response-7");
		assertThat(original.nativeFacts().get("nativeResponseJson").toString()).contains("Foo.java:7", "19",
				"response-7", "stop");
		assertThat(((Map<?, ?>) original.nativeFacts().get("usage")).containsKey("cacheCreationTokens")).isTrue();
		verify(h.call(), times(1)).chatResponse();
		var messages = org.mockito.ArgumentCaptor.forClass(List.class);
		verify(h.request()).messages(messages.capture());
		assertThat(((Message) messages.getValue().getFirst()).getText()).contains("R", "revision: 7",
				actual.source().artifact().sha256());
	}

	@Test
	void supportedRolesAndOptionsReachNativeHarness() {
		var h = harness("answer");
		var runtime = new SpringAiEvalModel(h.client());
		var request = new EvalModelRequest(List.of(new EvalMessage(EvalMessageRole.SYSTEM, "system"),
				new EvalMessage(EvalMessageRole.USER, "user"), new EvalMessage(EvalMessageRole.ASSISTANT, "prior")),
				new EvalModelOptions("selected", 0.2, 123, null, null), Map.of());
		var result = runtime.execute(request);
		assertThat(result.invocation().nativeFacts()).containsEntry("requestMetadata", request.metadata());
		var messages = org.mockito.ArgumentCaptor.forClass(List.class);
		verify(h.request()).messages(messages.capture());
		assertThat(messages.getValue()).extracting(item -> ((Message) item).getMessageType())
			.containsExactly(MessageType.SYSTEM, MessageType.USER, MessageType.ASSISTANT);
		var options = org.mockito.ArgumentCaptor.forClass(ChatOptions.Builder.class);
		verify(h.request()).options(options.capture());
		var nativeOptions = options.getValue().build();
		assertThat(nativeOptions.getModel()).isEqualTo("selected");
		assertThat(nativeOptions.getTemperature()).isEqualTo(0.2);
		assertThat(nativeOptions.getMaxTokens()).isEqualTo(123);
	}

	@Test
	void unsupportedOptionsAndMetadataAreRefusedBeforeNativeCalls() {
		var client = mock(ChatClient.class);
		var runtime = new SpringAiEvalModel(client);
		assertThatThrownBy(
				() -> runtime.execute(new EvalModelRequest(List.of(new EvalMessage(EvalMessageRole.USER, "request")),
						EvalModelOptions.defaults(), Map.of("correlation", "local"))))
			.isInstanceOf(IllegalArgumentException.class);
		for (var options : List.of(new EvalModelOptions(null, null, null, java.time.Duration.ofSeconds(1), null),
				new EvalModelOptions(null, null, null, null, "json"))) {
			assertThatThrownBy(() -> runtime.execute(
					new EvalModelRequest(List.of(new EvalMessage(EvalMessageRole.USER, "request")), options, Map.of())))
				.isInstanceOf(IllegalArgumentException.class);
		}
		verifyNoInteractions(client);
	}

	@Test
	void preservationLimitEscapesNativeCaptureAndConfiguredJudgingWithOriginalSdkObject() {
		var h = harness("R: PASS - Foo.java:7 retained");
		var captured = new java.util.concurrent.atomic.AtomicReference<ChatResponse>();
		var runtime = new SpringAiEvalModel(h.client(), response -> {
			captured.set(response);
			return NativeCapture.<ChatResponse>json(1).capture(response);
		});
		var actual = Rfc2119Requirement.of("R", "7", "MUST", "retain evidence", "audit", null);
		assertThatThrownBy(() -> Rfc2119Judge.builder().runtime(runtime).requirement(actual).build().judge())
			.isInstanceOfSatisfying(io.github.markpollack.judge.portable.PreservationLimitException.class,
					limit -> assertThat(limit.original()).isSameAs(captured.get()));
		assertThat(captured.get().getResult().getOutput().getText()).isEqualTo("R: PASS - Foo.java:7 retained");
		verify(h.call(), times(1)).chatResponse();
	}

	@Test
	void cancellationEscapesNativeCaptureAndGeneration() {
		var h = harness("R: PASS - evidence");
		var cancelled = new CancellationException("caller cancelled");
		var runtime = new SpringAiEvalModel(h.client(), response -> {
			throw cancelled;
		});
		assertThatThrownBy(() -> runtime.execute(EvalModelRequest.user("test"))).isSameAs(cancelled);
		when(h.call().chatResponse()).thenThrow(cancelled);
		assertThatThrownBy(() -> new SpringAiEvalModel(h.client()).execute(EvalModelRequest.user("test")))
			.isSameAs(cancelled);
	}

}
