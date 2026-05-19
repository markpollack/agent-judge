package io.github.markpollack.judge.ai.model;

import java.math.BigDecimal;

/**
 * Token usage statistics from a judge model invocation.
 *
 * @param inputTokens tokens consumed by the prompt
 * @param outputTokens tokens generated in the response
 * @param totalTokens total tokens (input + output)
 * @param estimatedCost estimated cost of the invocation (nullable)
 * @author Mark Pollack
 * @since 0.10.0
 */
public record Usage(Integer inputTokens, Integer outputTokens, Integer totalTokens, BigDecimal estimatedCost) {

}
