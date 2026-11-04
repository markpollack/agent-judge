/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.agentclient;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.github.markpollack.agents.client.AgentClient;
import io.github.markpollack.agents.client.AgentClientResponse;
import io.github.markpollack.judge.provenance.ArtifactRef;

import io.github.markpollack.judge.ai.model.EvalMessage;
import io.github.markpollack.judge.ai.model.EvalMessageRole;
import io.github.markpollack.judge.ai.model.EvalModel;
import io.github.markpollack.judge.ai.model.EvalModelRequest;
import io.github.markpollack.judge.ai.model.EvalModelResponse;

/**
 * {@link EvalModel} adapter that delegates to {@link AgentClient} for agentic judges.
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
public final class AgentClientEvalModel implements EvalModel {

	private final Invocation invocation;

	/**
	 * A configured invocation using the consumer's existing execution lifecycle.
	 * Implementations retain the original response and await their required termination,
	 * recording delivery and archive qualification before returning a successful result.
	 * Cancellation and interruption must propagate; ordinary execution or recording
	 * failures belong in {@link InvocationResult#failure()} alongside any original response.
	 */
	@FunctionalInterface
	public interface Invocation {

		/**
		 * Executes the validated single user goal.
		 * @param goal unmodified user goal
		 * @return original response and its execution evidence
		 */
		InvocationResult invoke(String goal);

	}

	/**
	 * Original invocation outcome, independent of provider success. Facts must be portable;
	 * they are retained under the response metadata key {@code invocation}. A non-null
	 * failure prevents evaluation completion without rewriting the provider response.
	 * @param response original response, or null if none was produced
	 * @param facts original execution, decision, termination and recording facts
	 * @param artifacts retained original execution evidence
	 * @param failure invocation or required recording/qualification failure, or null
	 */
	public record InvocationResult(AgentClientResponse response, Map<String, Object> facts,
			List<ArtifactRef> artifacts, Throwable failure) {

		/**
		 * Creates an immutable description of an invocation.
		 */
		public InvocationResult {
			facts = io.github.markpollack.judge.portable.PortableValues.copy(facts, "invocation.facts");
			artifacts = List.copyOf(artifacts);
		}

	}

	private final io.github.markpollack.judge.ai.model.NativeCapture<AgentClientResponse> capture;

	/**
	 * Create a judging backend for the supplied AgentClient.
	 * @param agentClient client that executes the judging agent
	 */
	public AgentClientEvalModel(AgentClient agentClient) {
		this(agentClient, io.github.markpollack.judge.ai.model.NativeCapture.json(1048576));
	}

	/**
	 * Configures protected native capture.
	 * @param client configured investigative harness
	 * @param capture portable or durable capture of its native result
	 */
	public AgentClientEvalModel(AgentClient client,
			io.github.markpollack.judge.ai.model.NativeCapture<AgentClientResponse> capture) {
		java.util.Objects.requireNonNull(client);
		this.invocation = goal -> new InvocationResult(client.run(goal), Map.of(), List.of(), null);
		this.capture = java.util.Objects.requireNonNull(capture);
	}

	/**
	 * Configures an existing execution lifecycle and protected native response capture.
	 * The invocation must await and validate its required recording/termination boundary;
	 * this adapter does not own a second execution engine or qualify Journal archives.
	 * The supplied facts and artifacts survive an invocation failure, as does any original
	 * response. Legacy Client constructors retain their original provider-only semantics.
	 * @param invocation configured lifecycle that returns original outcome and evidence
	 * @param capture portable or durable capture of the original native response
	 */
	public AgentClientEvalModel(Invocation invocation,
			io.github.markpollack.judge.ai.model.NativeCapture<AgentClientResponse> capture) {
		this.invocation = java.util.Objects.requireNonNull(invocation);
		this.capture = java.util.Objects.requireNonNull(capture);
	}

	@Override
	public void validateRequest(EvalModelRequest request) {
		java.util.Objects.requireNonNull(request);
		if (!request.metadata().isEmpty())
			throw new IllegalArgumentException("This configured adapter does not transmit request metadata");
		if (request.messages().size() != 1 || request.messages().getFirst().role() != EvalMessageRole.USER
				|| !request.options().equals(io.github.markpollack.judge.ai.model.EvalModelOptions.defaults()))
			throw new IllegalArgumentException(
					"AgentClient judging supports exactly one USER goal and runtime-configured options");
	}

	@Override
	public EvalModelResponse generate(EvalModelRequest request) {
		validateRequest(request);
		// Extract the single supported user goal
		String goal = request.messages()
			.stream()
			.filter(m -> m.role() == EvalMessageRole.USER)
			.map(EvalMessage::content)
			.reduce((a, b) -> a + "\n" + b)
			.orElse("");

		InvocationResult result = invoke(goal);
		propagateControlFailure(result.failure());
		AgentClientResponse response = result.response();
		Map<String, Object> metadata = new HashMap<>();
		if (!result.facts().isEmpty())
			metadata.put("invocation", result.facts());
		var artifacts = new ArrayList<ArtifactRef>(result.artifacts());
		Throwable captureFailure = result.failure();
		if (captureFailure != null)
			metadata.put("invocationFailure", captureFailure.getClass().getName() + ": "
					+ java.util.Objects.toString(captureFailure.getMessage(), ""));
		if (response == null)
			return new EvalModelResponse("", null, null, metadata, false, artifacts,
					captureFailure != null ? captureFailure : new IllegalStateException("AgentClient returned no response"),
					EvalModelResponse.AnswerState.NO_ANSWER);
		try {
			var snapshot = capture.capture(response);
			metadata.putAll(snapshot.facts());
			artifacts.addAll(snapshot.artifacts());
		}
		catch (java.util.concurrent.CancellationException cancelled) {
			throw cancelled;
		}
		catch (RuntimeException failure) {
			io.github.markpollack.judge.portable.PreservationLimitException.propagate(failure);
			if (Thread.currentThread().isInterrupted())
				throw new java.util.concurrent.CancellationException("Native capture interrupted");
			if (captureFailure == null)
				captureFailure = failure;
			metadata.put("captureFailure",
					failure.getClass().getName() + ": " + java.util.Objects.toString(failure.getMessage(), ""));
		}
		String text = "";
		String model = null;
		boolean completed = false;
		try {
			text = java.util.Objects.toString(response.getResult(), "");
			var meta = response.getMetadata();
			if (meta != null) {
				model = meta.getModel();
				if (meta.getSessionId() != null)
					metadata.put("sessionId", meta.getSessionId());
				if (meta.getDuration() != null)
					metadata.put("nativeDuration",
							Map.of("value", meta.getDuration().toString(), "provenance", "native-default-or-reported"));
			}
			var nativeResult = response.getAgentResponse();
			if (nativeResult != null && nativeResult.getResult() != null
					&& nativeResult.getResult().getMetadata().getFinishReason() != null)
				metadata.put("finishReason", nativeResult.getResult().getMetadata().getFinishReason());
			completed = captureFailure == null
					&& (nativeResult != null ? nativeResult.isSuccessful() : response.isSuccessful());
		}
		catch (java.util.concurrent.CancellationException cancelled) {
			throw cancelled;
		}
		catch (RuntimeException failure) {
			io.github.markpollack.judge.portable.PreservationLimitException.propagate(failure);
			if (Thread.currentThread().isInterrupted())
				throw new java.util.concurrent.CancellationException("Native response mapping interrupted");
			if (captureFailure == null)
				captureFailure = failure;
			metadata.put("mappingFailure",
					failure.getClass().getName() + ": " + java.util.Objects.toString(failure.getMessage(), ""));
		}

		if (!result.facts().isEmpty())
			metadata.put("invocation", result.facts());
		// Native provider quantities/cost/phases remain in the versioned SDK snapshot. No
		// unversioned guesses turn provider-specific keys into common usage or cost.
		return new EvalModelResponse(text, model, null, metadata, completed, artifacts, captureFailure);
	}

	private InvocationResult invoke(String goal) {
		try {
			return java.util.Objects.requireNonNull(invocation.invoke(goal), "Invocation returned no outcome");
		}
		catch (RuntimeException failure) {
			propagateControlFailure(failure);
			throw failure;
		}
	}

	private static void propagateControlFailure(Throwable failure) {
		io.github.markpollack.judge.portable.PreservationLimitException.propagate(failure);
		var seen = new java.util.IdentityHashMap<Throwable, Boolean>();
		for (Throwable current = failure; current != null && seen.put(current, Boolean.TRUE) == null;
				current = current.getCause()) {
			if (current instanceof Error error)
				throw error;
			if (current instanceof java.util.concurrent.CancellationException cancelled)
				throw cancelled;
			if (current instanceof InterruptedException) {
				Thread.currentThread().interrupt();
				var cancelled = new java.util.concurrent.CancellationException("Invocation interrupted");
				cancelled.initCause(current);
				throw cancelled;
			}
		}
		if (Thread.currentThread().isInterrupted())
			throw new java.util.concurrent.CancellationException("Invocation interrupted");
	}

}
