/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.ai.model;

/**
 * Framework-agnostic interface for invoking an AI model or agent backend for judge
 * evaluation.
 *
 * <p>
 * Implementations bridge to specific AI runtimes:
 * <ul>
 * <li>{@code SpringAiEvalModel} — wraps Spring AI ChatClient</li>
 * <li>{@code AgentClientEvalModel} — wraps AgentClient for agentic judges</li>
 * <li>Lambda — any {@code EvalModelRequest → EvalModelResponse} function</li>
 * </ul>
 *
 * @author Mark Pollack
 * @since 0.10.0
 */
@FunctionalInterface
public interface EvalModel
		extends io.github.markpollack.judge.execution.EvalRuntime<EvalModelRequest, EvalModelResponse> {

	/**
	 * Generate a response from the model.
	 * @param request the model request containing messages and options
	 * @return the model response
	 */
	EvalModelResponse generate(EvalModelRequest request);

	/**
	 * Declares the input protocols of this configured harness. The general protocol
	 * accepts both forms; a harness with narrower capabilities must restrict this
	 * declaration. Capability comes from configuration, never provider brand.
	 * @return supported input forms
	 */
	default java.util.Set<GeneratedInput> supportedInputs() {
		return java.util.Set.of(GeneratedInput.PREPARED_EVIDENCE, GeneratedInput.INTEGRATED_INVESTIGATION);
	}

	/**
	 * Restricts the configured harness without invoking or changing its native protocol.
	 * The application is responsible for configuring investigation tools when declaring
	 * that mode. This wrapper preserves the delegate's original native execution facts.
	 * @param inputs supported input forms, at least one
	 * @return immutable capability declaration around this harness
	 */
	default EvalModel withInputs(GeneratedInput... inputs) {
		java.util.Set<GeneratedInput> modes = java.util.Set.copyOf(java.util.List.of(inputs));
		if (modes.isEmpty() || !supportedInputs().containsAll(modes))
			throw new IllegalArgumentException("Input modes must be a nonempty subset of the configured harness");
		EvalModel delegate = this;
		return new EvalModel() {
			@Override
			public java.util.Set<GeneratedInput> supportedInputs() {
				return modes;
			}

			@Override
			public EvalModelResponse generate(EvalModelRequest request) {
				return delegate.generate(request);
			}

			@Override
			public void validateRequest(EvalModelRequest request) {
				delegate.validateRequest(request);
			}

			@Override
			public io.github.markpollack.judge.execution.NativeExecution<EvalModelResponse> execute(
					EvalModelRequest request) {
				return delegate.execute(request);
			}
		};
	}

	/**
	 * Refuses a known unsupported mode during construction, before acquisition or calls.
	 * @param input selected input form
	 */
	default void requireInput(GeneratedInput input) {
		if (!supportedInputs().contains(java.util.Objects.requireNonNull(input)))
			throw new IllegalArgumentException("Configured harness does not support " + input);
	}

	/**
	 * Convenience for simple single-prompt invocation.
	 * @param prompt the user prompt text
	 * @return the response text
	 */
	default String generateText(String prompt) {
		return generate(EvalModelRequest.user(prompt)).text();
	}

	/**
	 * Executes and retains the native response before classification.
	 * @param request exact supported request
	 * @return original answer and portable invocation observations
	 */
	@Override
	default io.github.markpollack.judge.execution.NativeExecution<EvalModelResponse> execute(
			EvalModelRequest request) {
		validateRequest(request);
		if (Thread.currentThread().isInterrupted())
			throw new java.util.concurrent.CancellationException("Native execution interrupted before invocation");
		long start = System.nanoTime();
		EvalModelResponse answer;
		try {
			answer = java.util.Objects.requireNonNull(generate(request), "Native runtime returned null");
		}
		catch (java.util.concurrent.CancellationException ex) {
			throw ex;
		}
		catch (RuntimeException ex) {
			if (Thread.currentThread().isInterrupted())
				throw new java.util.concurrent.CancellationException("Native execution interrupted");
			answer = EvalModelResponse.noAnswer(ex);
		}
		if(Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException("Native execution interrupted after return");
		java.util.Map<String, Object> facts = new java.util.LinkedHashMap<>(answer.metadata());
		if(answer.hasAnswer()) facts.put("text", answer.text());
		facts.put("answerState",answer.answerState().name());
		facts.put("options",request.options().toPortable());
		facts.put("messages",
				request.messages()
					.stream()
					.map(m -> java.util.Map.of("role", m.role().name(), "content", m.content()))
					.toList());
		facts.put("requestMetadata", request.metadata());
		if (answer.usage() != null)
			facts.put("usage", answer.usage().toPortableMap());
		var invocation = new io.github.markpollack.judge.provenance.Invocation(java.util.UUID.randomUUID().toString(),
				"generated-answer:v2", answer.completed(), answer.model(),
				java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start), facts,
				answer.artifacts(), answer.failure());
		return new io.github.markpollack.judge.execution.NativeExecution<>(answer, invocation);
	}

	/**
	 * Checks capabilities before any native invocation. Adapters must honor or reject
	 * declared roles/options; failures here are configuration errors.
	 * @param request declared request
	 */
	default void validateRequest(EvalModelRequest request) {
		java.util.Objects.requireNonNull(request);
	}

}
