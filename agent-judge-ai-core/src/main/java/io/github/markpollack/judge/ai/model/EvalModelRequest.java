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
public record EvalModelRequest(List<EvalMessage> messages, EvalModelOptions options,
		Map<String, Object> metadata) {

	/** Copy request collections into immutable values. */
	public EvalModelRequest {
		messages = List.copyOf(messages);
		metadata = io.github.markpollack.judge.portable.PortableValues.copy(metadata,"request.metadata");
		java.util.Objects.requireNonNull(options);
		if(messages.isEmpty()) throw new IllegalArgumentException("At least one message required");
	}

	/**
	 * Create a request with a single user message and default options.
	 * @param prompt the user prompt text
	 * @return a new request
	 */
	public static EvalModelRequest user(String prompt) {
		return new EvalModelRequest(List.of(new EvalMessage(EvalMessageRole.USER, prompt)),
				EvalModelOptions.defaults(), Map.of());
	}

}
