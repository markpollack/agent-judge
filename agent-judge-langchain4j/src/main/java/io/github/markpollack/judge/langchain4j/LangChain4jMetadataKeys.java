package io.github.markpollack.judge.langchain4j;

/** Public constants for LangChain4j facts stored in {@code JudgmentContext.metadata()}. */
public final class LangChain4jMetadataKeys {

	/** Identifier of the final model response. */
	public static final String RESPONSE_ID = "langchain4j.responseId";

	/** Model name reported by the final model response. */
	public static final String MODEL = "langchain4j.model";

	/** Finish reason of the final model response. */
	public static final String FINISH_REASON = "langchain4j.finishReason";

	/** Aggregate input token count. */
	public static final String USAGE_INPUT_TOKENS = "langchain4j.usage.inputTokens";

	/** Aggregate output token count. */
	public static final String USAGE_OUTPUT_TOKENS = "langchain4j.usage.outputTokens";

	/** Native aggregate token-usage object retained for in-process compatibility. */
	public static final String TOKEN_USAGE = "langchain4j.tokenUsage";

	/** Native tool-execution list. */
	public static final String TOOL_EXECUTIONS = "langchain4j.toolExecutions";

	/** Native RAG source list. */
	public static final String SOURCES = "langchain4j.sources";

	private LangChain4jMetadataKeys() {
	}

}
