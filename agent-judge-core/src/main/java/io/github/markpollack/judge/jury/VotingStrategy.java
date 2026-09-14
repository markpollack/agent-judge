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
 * <h2>Writing your own</h2>
 * <p>
 * A custom strategy is not only an arithmetic choice; it is also the place a result format is
 * either kept honest or quietly broken. Three obligations, none of them optional:
 * </p>
 * <ul>
 * <li><b>Apply not-applicable accounting.</b> An exclusion is not a vote and not an
 * abstention. Resolve the population the way the built-ins do rather than filtering by hand,
 * or a criterion that left the denominator becomes indistinguishable from one that was
 * decided.</li>
 * <li><b>Write the universal evidence keys.</b> {@link AggregationEvidence} is how a reader
 * derives a rate from a stored result. A strategy that reduces without publishing what it
 * reduced over produces a number nobody can check.</li>
 * <li><b>Never return {@link io.github.markpollack.judge.result.JudgmentStatus#NOT_APPLICABLE}
 * unless the jury is capable of it.</b> Excluding a criterion the jury never declared it could
 * exclude is the one move that silently shrinks a denominator, so a jury contains an
 * unauthorized exclusion as an {@code ERROR} rather than honouring it.</li>
 * </ul>
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

	/**
	 * How this strategy treats a {@link io.github.markpollack.judge.result.JudgmentStatus#NOT_APPLICABLE}
	 * input.
	 * <p>
	 * The default is {@link NotApplicablePolicy#REFUSE}: a strategy that says nothing has not
	 * decided that its denominator may shrink, and honouring an exclusion it was never
	 * configured for would make that decision on the author's behalf.
	 * </p>
	 * <p>
	 * This exists so composition can be validated <em>before</em> anything runs. A jury whose
	 * strategy refuses exclusions cannot seat a judge that declares it may exclude; the
	 * contradiction is a construction error rather than a surprise at vote time, which is the
	 * difference between a build that fails and a spend that produces an unusable result. It is
	 * a declaration, not the enforcement: the reduction applies the policy itself, and the
	 * jury contains an aggregate it was not entitled to produce.
	 * </p>
	 * @return the declared not-applicable policy; never null
	 * @since 0.17.0
	 */
	default NotApplicablePolicy notApplicablePolicy() {
		return NotApplicablePolicy.REFUSE;
	}

}
