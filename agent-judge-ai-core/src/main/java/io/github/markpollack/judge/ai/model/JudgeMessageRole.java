package io.github.markpollack.judge.ai.model;

/**
 * Roles for messages in a judge model request.
 *
 * @author Mark Pollack
 * @since 0.10.0
 */
public enum JudgeMessageRole {

	/** System instruction. */
	SYSTEM,

	/** User request. */
	USER,

	/** Assistant response. */
	ASSISTANT

}
