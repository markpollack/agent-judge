package io.github.markpollack.judge.ai.model;

/**
 * A message in a judge model request.
 *
 * @param role the message role (SYSTEM, USER, ASSISTANT)
 * @param content the message content
 * @author Mark Pollack
 * @since 0.10.0
 */
public record JudgeMessage(JudgeMessageRole role, String content) {

}
