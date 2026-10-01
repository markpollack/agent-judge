/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.agentclient;

import java.util.HashMap;
import java.util.Map;

import io.github.markpollack.agents.client.AgentClient;
import io.github.markpollack.agents.client.AgentClientResponse;
import io.github.markpollack.agents.model.AgentResponseMetadata;

import io.github.markpollack.judge.ai.model.JudgeMessage;
import io.github.markpollack.judge.ai.model.JudgeMessageRole;
import io.github.markpollack.judge.ai.model.JudgeModel;
import io.github.markpollack.judge.ai.model.JudgeModelRequest;
import io.github.markpollack.judge.ai.model.JudgeModelResponse;

/**
 * {@link JudgeModel} adapter that delegates to {@link AgentClient} for agentic judges.
 *
 * <p>
 * Enables judge backends that can use tools, inspect files, run commands, or perform
 * multi-step verification before returning a verdict. The agent receives the user
 * message(s) as its goal and returns its output as the judge response.
 *
 * <p>
 * This is distinct from the evaluated-side bridge ({@link AgentClientEvidence} which
 * converts agent output into CompletionEvidence). This adapter uses an agent <em>as</em>
 * the judge backend.
 *
 * @author Mark Pollack
 * @since 0.10.0
 */
public final class AgentClientJudgeModel implements JudgeModel {

	private final AgentClient agentClient;

	private final io.github.markpollack.judge.ai.model.NativeCapture<AgentClientResponse> capture;

	/**
	 * Create a judging backend for the supplied AgentClient.
	 * @param agentClient client that executes the judging agent
	 */
	public AgentClientJudgeModel(AgentClient agentClient) {
		this(agentClient, io.github.markpollack.judge.ai.model.NativeCapture.json(1048576));
	}

	/**
	 * Configures protected native capture.
	 * @param client configured investigative harness
	 * @param capture portable or durable capture of its native result
	 */
	public AgentClientJudgeModel(AgentClient client,
			io.github.markpollack.judge.ai.model.NativeCapture<AgentClientResponse> capture) {
		this.agentClient = java.util.Objects.requireNonNull(client);
		this.capture = java.util.Objects.requireNonNull(capture);
	}

	@Override
	public void validateRequest(JudgeModelRequest request) {
		java.util.Objects.requireNonNull(request);
		if (request.messages().size() != 1 || request.messages().getFirst().role() != JudgeMessageRole.USER
				|| !request.options().equals(io.github.markpollack.judge.ai.model.JudgeModelOptions.defaults()))
			throw new IllegalArgumentException(
					"AgentClient judging supports exactly one USER goal and runtime-configured options");
	}

	@Override
	public JudgeModelResponse generate(JudgeModelRequest request) {
		validateRequest(request);
		// Extract the single supported user goal
		String goal = request.messages()
			.stream()
			.filter(m -> m.role() == JudgeMessageRole.USER)
			.map(JudgeMessage::content)
			.reduce((a, b) -> a + "\n" + b)
			.orElse("");

		AgentClientResponse response = agentClient.run(goal);

		String text = response.getResult() != null ? response.getResult() : "";
		String model = null;
		Map<String, Object> metadata = new HashMap<>();

		AgentResponseMetadata meta = safeGetMetadata(response);
		if (meta != null) {
			model = meta.getModel();
			if (meta.getSessionId() != null) {
				metadata.put("sessionId", meta.getSessionId());
			}
		}

		if (meta != null)
			metadata.put("nativeDuration",
					Map.of("value", meta.getDuration().toString(), "provenance", "native-default-or-reported"));
		var nativeResult = response.getAgentResponse();
		if (nativeResult != null && nativeResult.getResult() != null)
			metadata.put("finishReason", nativeResult.getResult().getMetadata().getFinishReason());
		boolean completed = nativeResult != null ? nativeResult.isSuccessful() : response.isSuccessful();
		Throwable captureFailure = null;
		java.util.List<io.github.markpollack.judge.provenance.ArtifactRef> artifacts = java.util.List.of();
		try {
			var snapshot = capture.capture(response);
			metadata.putAll(snapshot.facts());
			artifacts = snapshot.artifacts();
		}
		catch (java.util.concurrent.CancellationException cancelled) {
			throw cancelled;
		}
		catch (RuntimeException failure) {
			if (Thread.currentThread().isInterrupted())
				throw new java.util.concurrent.CancellationException("Native capture interrupted");
			captureFailure = failure;
			metadata.put("captureFailure",
					failure.getClass().getName() + ": " + java.util.Objects.toString(failure.getMessage(), ""));
			completed = false;
		}
		// Native provider quantities/cost/phases remain in the versioned SDK snapshot. No
		// unversioned guesses turn provider-specific keys into common usage or cost.
		return new JudgeModelResponse(text, model, null, metadata, completed, artifacts, captureFailure);
	}

	private static AgentResponseMetadata safeGetMetadata(AgentClientResponse response) {
		try {
			return response.getMetadata();
		}
		catch (java.util.concurrent.CancellationException cancelled) {
			throw cancelled;
		}
		catch (Exception ex) {
			if (Thread.currentThread().isInterrupted())
				throw new java.util.concurrent.CancellationException("Native metadata capture interrupted");
			return null;
		}
	}

}
