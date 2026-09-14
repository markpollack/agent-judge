/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury;

import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.context.JudgmentContext;
import io.github.markpollack.judge.description.JuryDescription;

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

	/**
	 * Describe this jury's configured structure, available before any vote.
	 * <p>
	 * The default describes the jury as {@linkplain JuryDescription#opaque(Jury) opaque}: its
	 * implementation, its strategy and its flattened judges, without claiming to know how it
	 * seats, keys or weights them. The library's juries override it with a structural
	 * description. A jury that composes other juries should override it as well, so that its
	 * members are described by their own {@code describe()}.
	 * </p>
	 * @return the jury's description
	 * @throws IllegalArgumentException if a judge declares a configuration that is not
	 * portable; the message names where
	 * @since 0.17.0
	 */
	default JuryDescription describe() {
		return JuryDescription.opaque(this);
	}

	/**
	 * Whether this jury's aggregate may be
	 * {@link io.github.markpollack.judge.result.JudgmentStatus#NOT_APPLICABLE}.
	 * <p>
	 * A conservative bound, declared before any vote. True means the jury is permitted to
	 * exclude the subject; false means a built-in parent that receives an excluded aggregate
	 * from it will treat that as a stage failure rather than honour it.
	 * </p>
	 * <p>
	 * The default is {@code false}, which is the safe direction and the honest one: a jury this
	 * library cannot inspect has made no pre-spend guarantee about its own denominator, so it is
	 * checked at runtime wherever a built-in parent receives its output. Override it only if the
	 * jury really can return an excluded aggregate — and then it must, because otherwise its
	 * legitimate exclusions will be contained as errors.
	 * </p>
	 * @return true when the aggregate may be not applicable; false by default
	 * @since 0.17.0
	 */
	default boolean aggregateMayBeNotApplicable() {
		return false;
	}

}
