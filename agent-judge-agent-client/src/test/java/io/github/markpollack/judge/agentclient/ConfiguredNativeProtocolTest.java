/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.agentclient;

import java.util.*;
import java.util.concurrent.CancellationException;
import io.github.markpollack.agents.client.*;
import io.github.markpollack.agents.model.*;
import io.github.markpollack.judge.ai.model.*;
import io.github.markpollack.judge.ai.requirements.*;
import io.github.markpollack.judge.judgment.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Real SDK response values, with the external agent call stubbed locally. */
class ConfiguredNativeProtocolTest {

	AgentClientResponse response(String finish, String text) {
		return new AgentClientResponse(new AgentResponse(
				List.of(new AgentGeneration(text,
						new AgentGenerationMetadata(finish,
								Map.of("toolCalls", List.of(Map.of("name", "read", "path", "Foo.java")))))),
				new AgentResponseMetadata("fixture-model", java.time.Duration.ofMillis(27), "session-7",
						Map.of("providerSchema", "fixture:v1", "providerUsage",
								Map.of("reasoning", 9, "costUsd", 0.012), "phases", List.of("read", "verify")))),
				Map.of("workspace", "fixture"));
	}

	@Test
	void configuredRfcRetainsNativeProviderFactsWithoutGuessingCommonUsage() {
		var client = mock(AgentClient.class);
		when(client.run(anyString())).thenReturn(response("COMPLETE", "R: FAIL - Foo.java:7 violation"));
		var actual = Rfc2119Requirement.of("R", "7", "MUST", "retain evidence", "audit", null);
		var ready = Rfc2119Judge.builder().runtime(new AgentClientJudgeModel(client)).requirement(actual).build();
		verifyNoInteractions(client);
		Judgment result = ready.judge();
		assertThat(result.status()).isEqualTo(JudgmentStatus.FAIL);
		assertThat(result.requirement()).isSameAs(actual);
		var invocation = result.invocations().getFirst();
		assertThat(invocation.completed()).isTrue();
		assertThat(invocation.nativeFacts()).containsEntry("sessionId", "session-7")
			.containsEntry("finishReason", "COMPLETE");
		assertThat(invocation.nativeFacts().get("nativeResponseJson").toString()).contains("providerUsage", "0.012",
				"phases", "workspace", "Foo.java");
		assertThat(invocation.nativeFacts()).doesNotContainKey("usage");
		verify(client, times(1)).run(anyString());
	}

	@Test
	void unsupportedRolesAndOptionsHaveZeroNativeCalls() {
		var client = mock(AgentClient.class);
		var runtime = new AgentClientJudgeModel(client);
		var requests = new ArrayList<JudgeModelRequest>();
		requests.add(new JudgeModelRequest(List.of(new JudgeMessage(JudgeMessageRole.SYSTEM, "system")),
				JudgeModelOptions.defaults(), Map.of()));
		requests.add(new JudgeModelRequest(
				List.of(new JudgeMessage(JudgeMessageRole.USER, "one"), new JudgeMessage(JudgeMessageRole.USER, "two")),
				JudgeModelOptions.defaults(), Map.of()));
		for (var options : List.of(new JudgeModelOptions("other", null, null, null, null),
				new JudgeModelOptions(null, 0.1, null, null, null), new JudgeModelOptions(null, null, 1, null, null),
				new JudgeModelOptions(null, null, null, java.time.Duration.ofSeconds(1), null),
				new JudgeModelOptions(null, null, null, null, "json")))
			requests.add(new JudgeModelRequest(List.of(new JudgeMessage(JudgeMessageRole.USER, "request")), options,
					Map.of()));
		for (var request : requests)
			assertThatThrownBy(() -> runtime.execute(request)).isInstanceOf(IllegalArgumentException.class);
		verifyNoInteractions(client);
	}

	@Test
	void nativeFailureAndCaptureFailureKeepOriginalEvidence() {
		var client = mock(AgentClient.class);
		when(client.run(anyString())).thenReturn(response("ERROR", "native failure details"));
		var runtime = new AgentClientJudgeModel(client);
		var failure = runtime.execute(JudgeModelRequest.user("test"));
		assertThat(failure.invocation().completed()).isFalse();
		assertThat(failure.invocation().nativeFacts().get("nativeResponseJson").toString())
			.contains("native failure details", "providerUsage");
		var bounded = new AgentClientJudgeModel(client, NativeCapture.json(1)).execute(JudgeModelRequest.user("test"));
		assertThat(bounded.invocation().nativeFacts()).containsEntry("text", "native failure details")
			.containsKey("captureFailure");
	}

	@Test
	void cancellationIsNotTranslatedToAFailedJudgment() {
		var client = mock(AgentClient.class);
		var cancellation = new CancellationException("caller cancelled");
		when(client.run(anyString())).thenThrow(cancellation);
		assertThatThrownBy(() -> new AgentClientJudgeModel(client).execute(JudgeModelRequest.user("test")))
			.isSameAs(cancellation);
	}

}
