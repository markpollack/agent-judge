package io.github.markpollack.judge.springai;

/**
 * Public constants for Spring AI metadata keys stored in
 * {@code JudgmentContext.metadata()}.
 *
 * @author Mark Pollack
 * @since 0.10.0
 */
public final class SpringAiMetadataKeys {

	/** Unique identifier for the chat response. */
	public static final String RESPONSE_ID = "springai.responseId";

	/** Model name that handled the request. */
	public static final String MODEL = "springai.model";

	/** Finish reason string from the generation metadata (provider-specific). */
	public static final String FINISH_REASON = "springai.finishReason";

	/** Number of tokens in the user prompt. */
	public static final String USAGE_PROMPT_TOKENS = "springai.usage.promptTokens";

	/** Number of tokens in the AI response. */
	public static final String USAGE_COMPLETION_TOKENS = "springai.usage.completionTokens";

	/** Total tokens (prompt + completion). */
	public static final String USAGE_TOTAL_TOKENS = "springai.usage.totalTokens";

	/** Whether the response contains tool call requests. */
	public static final String HAS_TOOL_CALLS = "springai.hasToolCalls";

	/** List of tool calls requested by the model (best-effort, not full execution trace). */
	public static final String TOOL_CALLS = "springai.toolCalls";

	private SpringAiMetadataKeys() {
	}

}
