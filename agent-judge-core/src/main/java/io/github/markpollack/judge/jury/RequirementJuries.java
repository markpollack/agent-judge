/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.jury;

import java.util.*;
import io.github.markpollack.judge.RequirementJudge;

/** Factories for multiple opinions on the same actual requirement. */
public final class RequirementJuries {

	private RequirementJuries() {
	}

	/**
	 * Compose opinions with the declared strategy. The supplied invocation requirement
	 * goes to every judge.
	 * @param <S> specification type
	 * @param <E> evidence type
	 * @param strategy voting rule
	 * @param judges ordered opinions on the same constituent
	 * @return requirement-aware jury preserving every opinion
	 */
	public static <S, E> RequirementVotingJury<S, E> voting(VotingStrategy strategy,
			List<RequirementJudge<S, E>> judges) {
		return new RequirementVotingJury<>(strategy, judges);
	}

}
