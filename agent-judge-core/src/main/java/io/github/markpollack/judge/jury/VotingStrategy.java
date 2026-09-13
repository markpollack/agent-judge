/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury;

import io.github.markpollack.judge.description.StrategyDescription;
import io.github.markpollack.judge.result.Judgment;

import java.util.List;
import java.util.Map;

/**
 * Strategy for aggregating multiple judgments into a single verdict.
 *
 * <p>
 * VotingStrategy implementations define how to combine individual judgments from multiple
 * judges into a single aggregated judgment. Status-counting strategies aggregate
 * outcomes; numeric strategies aggregate each applicable judgment's
 * {@link Judgment#effectiveScore()} view.
 * </p>
 *
 * <p>
 * Example usage:
 * </p>
 * Executable examples are maintained in the Agent Judge Tutorial: https://github.com/markpollack/agent-judge-tutorial.
 *
 * @author Mark Pollack
 * @since 0.1.0
 * @see MajorityVotingStrategy
 * @see AverageVotingStrategy
 * @see WeightedAverageStrategy
 */
public interface VotingStrategy {

	/**
	 * Aggregate multiple judgments into a single judgment.
	 * @param judgments the list of individual judgments from judges
	 * @param weights optional weights for each judge (empty map for equal weights)
	 * @return aggregated judgment
	 */
	Judgment aggregate(List<Judgment> judgments, Map<String, Double> weights);

	/**
	 * Get the name of this voting strategy (for debugging and metadata).
	 * @return strategy name
	 */
	String getName();

	/**
	 * Describe how this strategy is configured, before it aggregates anything.
	 * <p>
	 * The default declares nothing: the description carries this strategy's name and
	 * implementation with {@code "parameters": {"declared": false}}. Override it to declare the
	 * parameters that decide the aggregate, such as an error policy or threshold, through
	 * {@link StrategyDescription#declared}. Every built-in strategy does.
	 * </p>
	 * @return the strategy's description
	 * @since 0.17.0
	 */
	default StrategyDescription describe() {
		return StrategyDescription.undeclared(this);
	}

}
