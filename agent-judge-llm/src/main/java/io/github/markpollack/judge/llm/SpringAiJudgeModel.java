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
 * <p>This is the <strong>judging-side</strong> adapter — it uses Spring AI to invoke
 * an LLM as a judge backend. The evaluated-side bridge (converting Spring AI agent
 * output into JudgmentContext) lives in {@code agent-judge-spring-ai}.
 *
 * @author Mark Pollack
 * @since 0.10.0
 */
public final class SpringAiJudgeModel implements JudgeModel {

	private final ChatClient chatClient;

	public SpringAiJudgeModel(ChatClient chatClient) {
		this.chatClient = chatClient;
	}

	public SpringAiJudgeModel(ChatClient.Builder chatClientBuilder) {
		this.chatClient = chatClientBuilder.build();
	}

	@Override
	public JudgeModelResponse generate(JudgeModelRequest request) {
		ChatClient.ChatClientRequestSpec spec = chatClient.prompt();

		// Map messages by role
		for (JudgeMessage message : request.messages()) {
			if (message.role() == JudgeMessageRole.SYSTEM) {
				spec = spec.system(message.content());
			}
			else if (message.role() == JudgeMessageRole.USER) {
				spec = spec.user(message.content());
			}
		}

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

		return new JudgeModelResponse(text, model, usage, metadata);
	}

	/**
	 * Map the categories Spring AI actually reports, and no others.
	 *
	 * <p>Prompt, completion, and prompt-cache activity come straight across. Spring AI's
	 * {@code getTotalTokens()} does not, because its interface default computes
	 * prompt + completion when a provider supplied no total, so a caller cannot tell a
	 * reported total from a derived one — and a derived total is exactly what
	 * {@code reportedTotalTokens} must never hold. Reasoning tokens have no category on the
	 * Spring AI interface; a provider that reports them puts them in its native usage
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
