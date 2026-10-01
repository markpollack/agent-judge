/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.llm;

import java.util.HashMap;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatResponse;

import io.github.markpollack.judge.ai.model.JudgeMessage;
import io.github.markpollack.judge.ai.model.JudgeMessageRole;
import io.github.markpollack.judge.ai.model.JudgeModel;
import io.github.markpollack.judge.ai.model.JudgeModelRequest;
import io.github.markpollack.judge.ai.model.JudgeModelResponse;
import io.github.markpollack.judge.ai.model.Usage;

/**
 * {@link JudgeModel} adapter that delegates to Spring AI {@link ChatClient}.
 *
 * <p>
 * This is the <strong>judging-side</strong> adapter — it uses Spring AI to invoke an LLM
 * as a judge backend. The evaluated-side bridge (converting Spring AI agent output into
 * CompletionEvidence) lives in {@code agent-judge-spring-ai}.
 *
 * @author Mark Pollack
 * @since 0.10.0
 */
public final class SpringAiJudgeModel implements JudgeModel {

	private final ChatClient chatClient;

	private final io.github.markpollack.judge.ai.model.NativeCapture<ChatResponse> capture;

	/**
	 * Create an adapter from an existing chat client.
	 * @param chatClient chat client
	 */
	public SpringAiJudgeModel(ChatClient chatClient) {
		this(chatClient, io.github.markpollack.judge.ai.model.NativeCapture.json(1048576));
	}

	/**
	 * Create an adapter from a chat client builder.
	 * @param chatClientBuilder chat client builder
	 */
	public SpringAiJudgeModel(ChatClient.Builder chatClientBuilder) {
		this(chatClientBuilder.build());
	}

	/**
	 * Configures portable or protected native response capture.
	 * @param client configured chat harness
	 * @param capture original native response capture
	 */
	public SpringAiJudgeModel(ChatClient client,
			io.github.markpollack.judge.ai.model.NativeCapture<ChatResponse> capture) {
		this.chatClient = java.util.Objects.requireNonNull(client);
		this.capture = java.util.Objects.requireNonNull(capture);
	}

	@Override
	public void validateRequest(JudgeModelRequest request) {
		java.util.Objects.requireNonNull(request);
		if (request.options().timeout() != null || request.options().responseFormat() != null)
			throw new IllegalArgumentException(
					"Spring AI timeout/responseFormat must be configured on the native harness");
	}

	@Override
	public JudgeModelResponse generate(JudgeModelRequest request) {
		validateRequest(request);
		java.util.List<org.springframework.ai.chat.messages.Message> messages = request.messages()
			.stream().<org.springframework.ai.chat.messages.Message>map(message -> switch (message.role()) {
				case SYSTEM -> new org.springframework.ai.chat.messages.SystemMessage(message.content());
				case USER -> new org.springframework.ai.chat.messages.UserMessage(message.content());
				case ASSISTANT -> new org.springframework.ai.chat.messages.AssistantMessage(message.content());
			})
			.toList();
		ChatClient.ChatClientRequestSpec spec = chatClient.prompt().messages(messages);
		var options = request.options();
		if (options.model() != null || options.temperature() != null || options.maxTokens() != null)
			spec = spec.options(org.springframework.ai.chat.prompt.ChatOptions.builder()
				.model(options.model())
				.temperature(options.temperature())
				.maxTokens(options.maxTokens()));

		ChatResponse chatResponse = spec.call().chatResponse();

		// Extract response text
		String text = "";
		if (chatResponse.getResult() != null && chatResponse.getResult().getOutput() != null) {
			text = chatResponse.getResult().getOutput().getText();
		}

		// Extract metadata
		String model = null;
		Usage usage = null;
		Map<String, Object> metadata = new HashMap<>();

		ChatResponseMetadata responseMeta = chatResponse.getMetadata();
		if (responseMeta != null) {
			model = responseMeta.getModel();
			if (responseMeta.getId() != null) {
				metadata.put("responseId", responseMeta.getId());
			}
			usage = tokenUsage(responseMeta.getUsage());
		}

		String finish = chatResponse.getResult() == null ? null
				: chatResponse.getResult().getMetadata().getFinishReason();
		if (finish != null)
			metadata.put("finishReason", finish);
		boolean completed = chatResponse.getResult() != null && (finish == null || finish.isBlank()
				|| java.util.Set.of("stop", "STOP", "SUCCESS", "COMPLETE").contains(finish));
		Throwable captureFailure = null;
		java.util.List<io.github.markpollack.judge.provenance.ArtifactRef> artifacts = java.util.List.of();
		try {
			var snapshot = capture.capture(chatResponse);
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
		return new JudgeModelResponse(text == null ? "" : text, model, usage, metadata, completed, artifacts,
				captureFailure);
	}

	/**
	 * Map the categories Spring AI actually reports, and no others.
	 *
	 * <p>
	 * Prompt, completion, and prompt-cache activity come straight across. Spring AI's
	 * {@code getTotalTokens()} does not, because its interface default computes prompt +
	 * completion when a provider supplied no total, so a caller cannot tell a reported
	 * total from a derived one — and a derived total is exactly what
	 * {@code reportedTotalTokens} must never hold. Reasoning tokens have no category on
	 * the Spring AI interface; a provider that reports them puts them in its native usage
	 * object, which is provider-specific and not read here.
	 * @param springUsage the Spring AI usage, or null when the response carried none
	 * @return the reported quantities, or null when the response carried no usage
	 */
	private static Usage tokenUsage(org.springframework.ai.chat.metadata.Usage springUsage) {
		if (springUsage == null) {
			return null;
		}
		Usage.Builder usage = Usage.builder();
		Integer promptTokens = springUsage.getPromptTokens();
		if (promptTokens != null) {
			usage.inputTokens(promptTokens);
		}
		Integer completionTokens = springUsage.getCompletionTokens();
		if (completionTokens != null) {
			usage.outputTokens(completionTokens);
		}
		Long cacheWriteTokens = springUsage.getCacheWriteInputTokens();
		if (cacheWriteTokens != null) {
			usage.cacheCreationTokens(cacheWriteTokens);
		}
		Long cacheReadTokens = springUsage.getCacheReadInputTokens();
		if (cacheReadTokens != null) {
			usage.cacheReadTokens(cacheReadTokens);
		}
		return usage.build();
	}

}
