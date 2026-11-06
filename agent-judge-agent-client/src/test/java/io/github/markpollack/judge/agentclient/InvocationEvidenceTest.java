/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.agentclient;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.markpollack.agents.client.AgentClientResponse;
import io.github.markpollack.agents.model.AgentGeneration;
import io.github.markpollack.agents.model.AgentGenerationMetadata;
import io.github.markpollack.agents.model.AgentResponse;
import io.github.markpollack.agents.model.AgentResponseMetadata;
import io.github.markpollack.judge.ai.model.*;
import io.github.markpollack.judge.provenance.ArtifactRef;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class InvocationEvidenceTest {

    private AgentClientResponse response(String finish) {
        return new AgentClientResponse(new AgentResponse(
            List.of(new AgentGeneration("original answer", new AgentGenerationMetadata(finish, Map.of()))),
            new AgentResponseMetadata("original model", java.time.Duration.ofMillis(7), "session-original",
                Map.of("providerUsage", Map.of("tokens", 17)))));
    }

    @Test
    void recordingFailureRetainsProviderSuccessAnswerAndOriginalFacts() {
        var original = response("SUCCESS");
        var failure = new IllegalStateException("recording failed");
        var ref = ArtifactRef.ofBytes("original-decision", new byte[]{1}, null);
        var model = new AgentClientEvalModel(goal -> new AgentClientEvalModel.InvocationResult(original,
            Map.of("executionId", "execution-original", "decisionId", "decision-original",
                "recording", "FAILED", "termination", "CONFIRMED"), List.of(ref), failure), NativeCapture.json(100000));
        var result = model.generate(EvalModelRequest.user("unaltered goal"));
        assertThat(result.completed()).isFalse();
        assertThat(result.hasAnswer()).isTrue();
        assertThat(result.text()).isEqualTo("original answer");
        assertThat(result.failure()).isSameAs(failure);
        assertThat(result.artifacts()).contains(ref);
        assertThat(result.metadata().get("nativeResponseJson").toString()).contains("SUCCESS", "providerUsage", "17");
        assertThat(result.metadata().get("invocation").toString()).contains("execution-original", "decision-original", "FAILED");
        assertThat(result.usage()).isNull();
    }

    @Test
    void completedInvocationRetainsExactGoalAndNativeFailureStaysIncomplete() {
        var calls = new AtomicInteger();
        for (String finish : List.of("SUCCESS", "ERROR")) {
            var model = new AgentClientEvalModel(goal -> {
                assertThat(goal).isEqualTo("single unchanged goal"); calls.incrementAndGet();
                return new AgentClientEvalModel.InvocationResult(response(finish), Map.of("recording", "COMPLETE"), List.of(), null);
            }, NativeCapture.json(100000));
            var result = model.generate(EvalModelRequest.user("single unchanged goal"));
            assertThat(result.completed()).isEqualTo(finish.equals("SUCCESS"));
            assertThat(result.text()).isEqualTo("original answer");
        }
        assertThat(calls).hasValue(2);
    }

    @Test
    void missingResponseRetainsFailureAndDoesNotInventAnAnswer() {
        var failure = new IllegalStateException("provider failed before response");
        var model = new AgentClientEvalModel(goal -> new AgentClientEvalModel.InvocationResult(null,
            Map.of("executionId", "original"), List.of(), failure), NativeCapture.json(100000));
        var result = model.generate(EvalModelRequest.user("goal"));
        assertThat(result.completed()).isFalse(); assertThat(result.hasAnswer()).isFalse();
        assertThat(result.text()).isEmpty(); assertThat(result.failure()).isSameAs(failure);
        assertThat(result.metadata().get("invocation").toString()).contains("original");
    }

    @Test
    void requestValidationPrecedesInvocation() {
        var calls = new AtomicInteger();
        var model = new AgentClientEvalModel(goal -> {calls.incrementAndGet(); return null;}, NativeCapture.json(100000));
        var invalid = new EvalModelRequest(List.of(new EvalMessage(EvalMessageRole.USER, "goal")),
            new EvalModelOptions("changed model", null, null, null, null), Map.of());
        assertThatThrownBy(() -> model.generate(invalid)).isInstanceOf(IllegalArgumentException.class);
        assertThat(calls).hasValue(0);
        assertThatThrownBy(() -> model.generate(EvalModelRequest.user("goal"))).isInstanceOf(NullPointerException.class);
    }

    @Test
    void cancellationAndWrappedInterruptionPropagate() {
        var cancelled = new CancellationException("cancelled");
        var model = new AgentClientEvalModel(goal -> new AgentClientEvalModel.InvocationResult(response("SUCCESS"),
            Map.of(), List.of(), new CompletionException(cancelled)), NativeCapture.json(100000));
        assertThatThrownBy(() -> model.generate(EvalModelRequest.user("goal"))).isSameAs(cancelled);
        var interrupted = new AgentClientEvalModel(goal -> new AgentClientEvalModel.InvocationResult(null,
            Map.of(), List.of(), new CompletionException(new InterruptedException("interrupted"))), NativeCapture.json(100000));
        try {
            assertThatThrownBy(() -> interrupted.generate(EvalModelRequest.user("goal"))).isInstanceOf(CancellationException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {Thread.interrupted();}
    }

    @Test
    void thrownAsyncCancellationAndPreservationFailureAreNeverContained() {
        var cancelled = new CancellationException("cancelled");
        var model = new AgentClientEvalModel(goal -> {throw new CompletionException(cancelled);}, NativeCapture.json(100000));
        assertThatThrownBy(() -> model.execute(EvalModelRequest.user("goal"))).isSameAs(cancelled);
        var limit = new io.github.markpollack.judge.portable.PreservationLimitException("bounded", "original");
        var preserved = new AgentClientEvalModel(goal -> new AgentClientEvalModel.InvocationResult(null,
            Map.of(), List.of(), new CompletionException(limit)), NativeCapture.json(100000));
        assertThatThrownBy(() -> preserved.execute(EvalModelRequest.user("goal"))).isSameAs(limit);
    }

    @Test
    void captureFailureDoesNotDiscardLifecycleFactsOrOriginalAnswer() {
        var original = response("SUCCESS");
        var model = new AgentClientEvalModel(goal -> new AgentClientEvalModel.InvocationResult(original,
            Map.of("executionId", "original"), List.of(), null), nativeResponse -> {throw new IllegalStateException("capture failed");});
        var result = model.generate(EvalModelRequest.user("goal"));
        assertThat(result.completed()).isFalse(); assertThat(result.text()).isEqualTo("original answer");
        assertThat(result.metadata().get("invocation").toString()).contains("original");
    }
}
