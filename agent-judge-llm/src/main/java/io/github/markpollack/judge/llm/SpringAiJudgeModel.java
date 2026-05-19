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
			org.springframework.ai.chat.metadata.Usage springUsage = responseMeta.getUsage();
			if (springUsage != null) {
				usage = new Usage(springUsage.getPromptTokens(), springUsage.getCompletionTokens(),
						springUsage.getTotalTokens(), null);
			}
		}

		return new JudgeModelResponse(text, model, usage, metadata);
	}

}
