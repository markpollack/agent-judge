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
 * <p>Enables judge backends that can use tools, inspect files, run commands, or perform
 * multi-step verification before returning a verdict. The agent receives the user message(s)
 * as its goal and returns its output as the judge response.
 *
 * <p>This is distinct from the evaluated-side bridge ({@link AgentClientJudgmentContextBuilder}
 * which converts agent output into JudgmentContext). This adapter uses an agent <em>as</em>
 * the judge backend.
 *
 * @author Mark Pollack
 * @since 0.10.0
 */
public final class AgentClientJudgeModel implements JudgeModel {

	private final AgentClient agentClient;

	public AgentClientJudgeModel(AgentClient agentClient) {
		this.agentClient = agentClient;
	}

	@Override
	public JudgeModelResponse generate(JudgeModelRequest request) {
		// Extract user message content as the goal
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

		metadata.put("successful", response.isSuccessful());

		AgentResponseMetadata meta = safeGetMetadata(response);
		if (meta != null) {
			model = meta.getModel();
			if (meta.getSessionId() != null) {
				metadata.put("sessionId", meta.getSessionId());
			}
		}

		return new JudgeModelResponse(text, model, null, metadata);
	}

	private static AgentResponseMetadata safeGetMetadata(AgentClientResponse response) {
		try {
			return response.getMetadata();
		}
		catch (Exception ex) {
			return null;
		}
	}

}
