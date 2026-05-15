package io.github.markpollack.judge.agentclient;

/**
 * Public constants for AgentClient metadata keys stored in {@code JudgmentContext.metadata()}.
 *
 * @author Mark Pollack
 * @since 0.10.0
 */
public final class AgentClientMetadataKeys {

	/** The model or CLI agent name used for execution. */
	public static final String MODEL = "agentclient.model";

	/** Session identifier for the agent execution. */
	public static final String SESSION_ID = "agentclient.sessionId";

	/** Finish reason from the agent generation metadata. */
	public static final String FINISH_REASON = "agentclient.finishReason";

	private AgentClientMetadataKeys() {
	}

}
