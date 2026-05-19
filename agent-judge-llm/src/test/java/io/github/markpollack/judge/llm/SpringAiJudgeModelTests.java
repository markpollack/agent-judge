package io.github.markpollack.judge.llm;

import java.util.List;

import io.github.markpollack.judge.ai.model.JudgeModelRequest;
import io.github.markpollack.judge.ai.model.JudgeModelResponse;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class SpringAiJudgeModelTests {

	@Test
	void generateExtractsTextAndMetadata() {
		ChatClient chatClient = mock(ChatClient.class);
		ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
		ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);

		given(chatClient.prompt()).willReturn(requestSpec);
		given(requestSpec.user(any(String.class))).willReturn(requestSpec);
		given(requestSpec.call()).willReturn(callSpec);

		org.springframework.ai.chat.messages.AssistantMessage assistantMessage = new org.springframework.ai.chat.messages.AssistantMessage(
				"relevant");
		Generation generation = new Generation(assistantMessage);
		ChatResponseMetadata responseMeta = ChatResponseMetadata.builder()
			.model("gpt-4o")
			.id("resp-123")
			.usage(new DefaultUsage(100, 50))
			.build();
		ChatResponse chatResponse = new ChatResponse(List.of(generation), responseMeta);
		given(callSpec.chatResponse()).willReturn(chatResponse);

		SpringAiJudgeModel model = new SpringAiJudgeModel(chatClient);
		JudgeModelResponse response = model.generate(JudgeModelRequest.user("Is this relevant?"));

		assertThat(response.text()).isEqualTo("relevant");
		assertThat(response.model()).isEqualTo("gpt-4o");
		assertThat(response.usage()).isNotNull();
		assertThat(response.usage().inputTokens()).isEqualTo(100);
		assertThat(response.usage().outputTokens()).isEqualTo(50);
		assertThat(response.metadata()).containsEntry("responseId", "resp-123");
	}

	@Test
	void generateTextConvenience() {
		ChatClient chatClient = mock(ChatClient.class);
		ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
		ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);

		given(chatClient.prompt()).willReturn(requestSpec);
		given(requestSpec.user(any(String.class))).willReturn(requestSpec);
		given(requestSpec.call()).willReturn(callSpec);

		org.springframework.ai.chat.messages.AssistantMessage msg = new org.springframework.ai.chat.messages.AssistantMessage(
				"yes");
		ChatResponse chatResponse = new ChatResponse(List.of(new Generation(msg)));
		given(callSpec.chatResponse()).willReturn(chatResponse);

		SpringAiJudgeModel model = new SpringAiJudgeModel(chatClient);

		assertThat(model.generateText("hello")).isEqualTo("yes");
	}

}
