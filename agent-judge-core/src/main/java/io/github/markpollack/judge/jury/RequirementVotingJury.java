/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.jury;

import java.util.*;
import io.github.markpollack.judge.RequirementJudge;
import io.github.markpollack.judge.requirement.Requirement;
import io.github.markpollack.judge.description.*;

/**
 * Multiple opinions about the same supplied requirement, with an explicit voting
 * strategy.
 *
 * @param <S> native specification type
 * @param <E> evidence type
 */
public final class RequirementVotingJury<S, E> implements RequirementJury<S, E> {

	private final VotingStrategy strategy;

	private final List<RequirementJudge<S, E>> judges;

	RequirementVotingJury(VotingStrategy strategy, List<RequirementJudge<S, E>> judges) {
		this.strategy = Objects.requireNonNull(strategy, "strategy");
		this.judges = List.copyOf(judges);
		if (this.judges.isEmpty())
			throw new IllegalArgumentException("At least one opinion required");
	}

	/**
	 * Configured reduction.
	 * @return non-null voting strategy
	 */
	public VotingStrategy votingStrategy() {
		return strategy;
	}

	/**
	 * Ordered requirement-aware opinions.
	 * @return immutable roster
	 */
	public List<RequirementJudge<S, E>> judges() {
		return judges;
	}

	@Override
	public Verdict vote(Requirement<S> requirement, E evidence) {
		Objects.requireNonNull(requirement, "requirement");
		Objects.requireNonNull(evidence, "evidence");
		var builder = SimpleJury.<E>builder().votingStrategy(strategy).parallel(false);
		for (var judge : judges)
			builder.judge(value -> judge.judge(requirement, value));
		return builder.build().vote(evidence).forRequirement(requirement);
	}

	@Override
	public JuryDescription describe() {
		var opinions = new ArrayList<JudgeDescription>();
		for (var judge : judges)
			opinions.add(new JudgeDescription(null, null, null, null, null, ImplementationIdentity.of(judge.getClass()),
					null));
		return new OpaqueJuryDescription(ImplementationIdentity.of(getClass()), false, strategy.describe(), opinions);
	}

}
