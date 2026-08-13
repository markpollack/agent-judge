/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury;

import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.context.JudgmentContext;

import java.util.List;

/**
 * Jury of multiple judges that vote on agent execution.
 *
 * <p>
 * A Jury is a separate abstraction from Judge that aggregates judgments from multiple
 * judges using a voting strategy. Unlike Judge which returns a Judgment, Jury returns a
 * Verdict containing both the aggregated result and all individual judgments.
 * </p>
 *
 * <p>
 * The jury executes all its constituent judges (potentially in parallel) and aggregates
 * their judgments using a {@link VotingStrategy}. The final verdict includes identity
	 * preservation via judge names and complete named {@link CompositeAttempt} evidence for
	 * composite implementations.
 * </p>
 *
 * <p>
 * Example usage:
 * </p>
 * Executable examples are maintained in the Agent Judge Tutorial: https://github.com/markpollack/agent-judge-tutorial.
 *
 * @author Mark Pollack
 * @since 0.1.0
 * @see SimpleJury
 * @see VotingStrategy
 * @see Verdict
 */
public interface Jury {

	/**
	 * Get the list of judges in this jury.
	 * @return list of judges
	 */
	List<Judge> getJudges();

	/**
	 * Get the voting strategy used to aggregate judgments.
	 * @return voting strategy
	 */
	VotingStrategy getVotingStrategy();

	/**
	 * Execute all judges and aggregate their judgments into a verdict.
	 * @param context the judgment context
	 * @return verdict with aggregated and individual judgments
	 */
	Verdict vote(JudgmentContext context);

}
