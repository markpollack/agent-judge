/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge;

import java.util.concurrent.CompletableFuture;

import io.github.markpollack.judge.context.JudgmentContext;
import io.github.markpollack.judge.result.Judgment;

/**
 * Asynchronous interface for judging agent execution results.
 *
 * <p>
 * This interface is completely separate from {@link Judge} following Spring's pattern of
 * separating sync and async interfaces. This allows for multiple async implementations
 * with varying sophistication levels.
 * </p>
 *
 * <p>
 * Async judges can be created by wrapping synchronous judges with an async adapter, or by
 * implementing this interface directly for truly asynchronous evaluation.
 * </p>
 *
 * <p>
 * <strong>Design Inspiration:</strong> Influenced by deepeval's async_mode pattern and
 * evals' parallel execution with ThreadPoolExecutor, but adapted to Spring's async
 * separation principle. Rather than default methods mixing sync/async (not Spring-like),
 * we provide completely separate interfaces for different execution models.
 * </p>
 *
 * <p>
 * Example usage:
 * </p>
 * Executable examples are maintained in the Agent Judge Tutorial: https://github.com/markpollack/agent-judge-tutorial.
 *
 * @author Mark Pollack
 * @since 0.1.0
 * @see Judge
 */
public interface AsyncJudge {

	/**
	 * Asynchronously evaluate an agent execution result.
	 * @param context the judgment context containing all information about the agent
	 * execution
	 * @return a CompletableFuture that will complete with the judgment
	 */
	CompletableFuture<Judgment> judgeAsync(JudgmentContext context);

}
