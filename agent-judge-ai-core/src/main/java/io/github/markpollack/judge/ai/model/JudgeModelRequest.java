package io.github.markpollack.judge.ai.model;

import java.util.List;
import java.util.Map;

/**
 * A request to a judge model backend.
 *
 * <p>Contains messages (role-based conversation), model options, and arbitrary metadata.
 * Use the {@link #user(String)} factory for the common single-prompt case.
 *
 * @param messages the conversation messages
 * @param options model invocation options
 * @param metadata arbitrary metadata passed through to the model adapter
 * @author Mark Pollack
 * @since 0.10.0
 */
public record JudgeModelRequest(List<JudgeMessage> messages, JudgeModelOptions options,
		Map<String, Object> metadata) {

	public JudgeModelRequest {
		messages = List.copyOf(messages);
		metadata = Map.copyOf(metadata);
	}

	/**
	 * Create a request with a single user message and default options.
	 * @param prompt the user prompt text
	 * @return a new request
	 */
	public static JudgeModelRequest user(String prompt) {
		return new JudgeModelRequest(List.of(new JudgeMessage(JudgeMessageRole.USER, prompt)),
				JudgeModelOptions.defaults(), Map.of());
	}

}
