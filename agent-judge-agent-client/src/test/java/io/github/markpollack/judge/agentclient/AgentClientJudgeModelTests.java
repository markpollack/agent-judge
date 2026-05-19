package io.github.markpollack.judge.agentclient;

import io.github.markpollack.agents.client.AgentClient;
import io.github.markpollack.agents.client.AgentClientResponse;
import io.github.markpollack.agents.model.AgentResponse;
import io.github.markpollack.agents.model.AgentResponseMetadata;
import io.github.markpollack.judge.ai.model.JudgeModelRequest;
import io.github.markpollack.judge.ai.model.JudgeModelResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class AgentClientJudgeModelTests {

	@Test
	void generateDelegatesToAgentClient() {
		AgentClient agentClient = mock(AgentClient.class);
		AgentClientResponse clientResponse = mock(AgentClientResponse.class);

		given(agentClient.run(any(String.class))).willReturn(clientResponse);
		given(clientResponse.getResult()).willReturn("The code is correct.");
		given(clientResponse.isSuccessful()).willReturn(true);
		given(clientResponse.getMetadata())
			.willReturn(new AgentResponseMetadata("claude-sonnet-4-20250514", null, "session-abc", null));

		AgentClientJudgeModel model = new AgentClientJudgeModel(agentClient);
		JudgeModelResponse response = model.generate(JudgeModelRequest.user("Review this code for correctness"));

		assertThat(response.text()).isEqualTo("The code is correct.");
		assertThat(response.model()).isEqualTo("claude-sonnet-4-20250514");
		assertThat(response.metadata()).containsEntry("successful", true);
		assertThat(response.metadata()).containsEntry("sessionId", "session-abc");
	}

	@Test
	void handlesNullMetadataGracefully() {
		AgentClient agentClient = mock(AgentClient.class);
		AgentClientResponse clientResponse = mock(AgentClientResponse.class);

		given(agentClient.run(any(String.class))).willReturn(clientResponse);
		given(clientResponse.getResult()).willReturn("output");
		given(clientResponse.isSuccessful()).willReturn(false);
		given(clientResponse.getMetadata()).willThrow(new RuntimeException("no metadata"));

		AgentClientJudgeModel model = new AgentClientJudgeModel(agentClient);
		JudgeModelResponse response = model.generate(JudgeModelRequest.user("test"));

		assertThat(response.text()).isEqualTo("output");
		assertThat(response.model()).isNull();
		assertThat(response.metadata()).containsEntry("successful", false);
	}

	@Test
	void handlesNullResult() {
		AgentClient agentClient = mock(AgentClient.class);
		AgentClientResponse clientResponse = mock(AgentClientResponse.class);

		given(agentClient.run(any(String.class))).willReturn(clientResponse);
		given(clientResponse.getResult()).willReturn(null);
		given(clientResponse.isSuccessful()).willReturn(false);
		given(clientResponse.getMetadata()).willThrow(new RuntimeException("no metadata"));

		AgentClientJudgeModel model = new AgentClientJudgeModel(agentClient);
		JudgeModelResponse response = model.generate(JudgeModelRequest.user("test"));

		assertThat(response.text()).isEmpty();
	}

}
