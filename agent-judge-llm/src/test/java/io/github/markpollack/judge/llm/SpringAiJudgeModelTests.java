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
		assertThat(response.usage().inputTokens()).isEqualTo(100L);
		assertThat(response.usage().outputTokens()).isEqualTo(50L);
		// Spring AI derives a total when the provider supplied none, so no total is
		// recorded: reportedTotalTokens holds only a total the source itself reported.
		assertThat(response.usage().reportedTotalTokens()).isNull();
		assertThat(response.metadata()).containsEntry("responseId", "resp-123");
	}

	@Test
	void generateMapsPromptCacheActivityWhenTheProviderReportsIt() {
		ChatClient chatClient = mock(ChatClient.class);
		ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
		ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);

		given(chatClient.prompt()).willReturn(requestSpec);
		given(requestSpec.user(any(String.class))).willReturn(requestSpec);
		given(requestSpec.call()).willReturn(callSpec);

		Generation generation = new Generation(
				new org.springframework.ai.chat.messages.AssistantMessage("relevant"));
		ChatResponseMetadata responseMeta = ChatResponseMetadata.builder()
			.model("claude-opus-5")
			.usage(new DefaultUsage(100, 50, 150, null, 40L, 60L))
			.build();
		given(callSpec.chatResponse()).willReturn(new ChatResponse(List.of(generation), responseMeta));

		JudgeModelResponse response = new SpringAiJudgeModel(chatClient)
			.generate(JudgeModelRequest.user("Is this relevant?"));

		assertThat(response.usage()).isNotNull();
		assertThat(response.usage().cacheReadTokens()).isEqualTo(40L);
		assertThat(response.usage().cacheCreationTokens()).isEqualTo(60L);
		// Spring AI has no reasoning category; a provider that reports one puts it in its
		// native usage object, which this adapter does not read.
		assertThat(response.usage().reasoningTokens()).isNull();
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
