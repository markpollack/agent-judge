/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury;

import java.util.*;
import io.github.markpollack.judge.voting.StrategyDescription;
import io.github.markpollack.judge.description.*;
import io.github.markpollack.judge.portable.ImplementationIdentity;
import io.github.markpollack.judge.verdict.CompositeAttempt;
import io.github.markpollack.judge.verdict.Verdict;
import io.github.markpollack.judge.voting.VotingStrategy;

import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.description.JuryDescription;

/**
 * Configured composition or declared requirement audit returning a complete Verdict.
 *
 * <p>
 * A Jury is a separate abstraction from Judge that aggregates judgments from multiple
 * judges using a voting strategy. Unlike Judge which returns a Judgment, Jury returns a
 * Verdict containing both the judgment result and all individual judgments.
 * </p>
 *
 * <p>
 * Voting juries combine opinions using a {@link VotingStrategy}; cascades use routing
 * rules. The final verdict includes identity preservation via judge names and complete
 * named {@link CompositeAttempt} evidence for composite implementations.
 * </p>
 *
 * <p>
 * Example usage:
 * </p>
 * Executable examples are maintained in the Agent Judge Tutorial:
 * https://github.com/markpollack/agent-judge-tutorial.
 *
 * @author Mark Pollack
 * @since 0.1.0
 * @see SimpleJury
 * @see VotingStrategy
 * @see Verdict
 */
public interface Jury {

	/**
	 * Evaluate the configured composition, which may stop before every tier executes.
	 * @return verdict with judgment and individual judgments
	 */
	Verdict vote();

	/**
	 * Describe this jury's configured structure, available before any vote.
	 * <p>
	 * The default describes the jury as opaque: its implementation and any declared
	 * voting structure, without claiming to know how it seats, keys or weights them. The
	 * library's juries override it with a structural description. A jury that composes
	 * other juries should override it as well, so that its members are described by their
	 * own {@code describe()}.
	 * </p>
	 * @return the jury's description
	 * @throws IllegalArgumentException if a judge declares a configuration that is not
	 * portable; the message names where
	 *
	 * @since 0.17.0
	 */
	default JuryDescription describe() {

		Objects.requireNonNull(this, "this must not be null");
		VotingStrategy votingStrategy = this instanceof VotingJury voting ? voting.getVotingStrategy() : null;
		StrategyDescription strategy = votingStrategy == null ? null : votingStrategy.describe();
		List<? extends Judge> reported = Objects.requireNonNull(
				this instanceof VotingJury voting ? voting.getJudges() : List.of(), "getJudges() must not return null");
		List<JudgeDescription> judges = new ArrayList<>(reported.size());
		for (int index = 0; index < reported.size(); index++) {
			try {
				judges.add(io.github.markpollack.judge.description.JudgeDescription.of(reported.get(index)));
			}
			catch (IllegalArgumentException ex) {
				io.github.markpollack.judge.portable.PreservationLimitException.propagate(ex);
				throw new IllegalArgumentException("judges[" + index + "]: " + ex.getMessage(), ex);
			}
		}
		return new OpaqueJuryDescription(ImplementationIdentity.of(this.getClass()), strategy, judges);
	}

	/**
	 * Whether this jury's aggregate may be
	 * {@link io.github.markpollack.judge.judgment.JudgmentStatus#NOT_APPLICABLE}.
	 * <p>
	 * A conservative bound, declared before any vote. True means the jury is permitted to
	 * exclude the subject; false means a built-in parent that receives an excluded
	 * aggregate from it will treat that as a stage failure rather than honour it.
	 * </p>
	 * <p>
	 * The bound derives from {@link #describe()}. Opaque structures grant no permission.
	 * Custom implementations declare a typed description; they must keep it stable for
	 * the configured lifetime. Judge seats own their local exclusion permissions.
	 * </p>
	 * @return true when the aggregate may be not applicable; false by default
	 *
	 * @since 0.17.0
	 */
	default boolean aggregateMayBeNotApplicable() {
		return describe().aggregateMayBeNotApplicable();
	}

}
