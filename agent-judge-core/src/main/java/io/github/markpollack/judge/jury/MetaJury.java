/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.context.JudgmentContext;
import io.github.markpollack.judge.result.Judgment;

/** Package-private named jury-of-juries implementation used by {@link Juries}. */
class MetaJury implements Jury {

	private static final CompositeFailure EXECUTION_FAILURE =
			new CompositeFailure(CompositeFailureCode.JURY_EXECUTION_FAILED);

	private final List<NamedJury> members;

	private final VotingStrategy metaStrategy;

	MetaJury(List<NamedJury> members, VotingStrategy metaStrategy) {
		if (members == null || members.isEmpty()) {
			throw new IllegalArgumentException("At least one named jury is required");
		}
		if (metaStrategy == null) {
			throw new IllegalArgumentException("Meta voting strategy is required");
		}
		Set<String> names = new HashSet<>();
		for (NamedJury member : members) {
			if (member == null) {
				throw new IllegalArgumentException("Named jury must not be null");
			}
			if (!names.add(member.name())) {
				throw new IllegalArgumentException("Duplicate meta-jury member name: " + member.name());
			}
		}
		this.members = List.copyOf(members);
		this.metaStrategy = metaStrategy;
	}

	@Override
	public List<Judge> getJudges() {
		return List.of();
	}

	@Override
	public VotingStrategy getVotingStrategy() {
		return metaStrategy;
	}

	@Override
	public Verdict vote(JudgmentContext context) {
		return CompositeExecutionScope.withinCompositeVote(() -> execute(context));
	}

	private Verdict execute(JudgmentContext context) {
		List<CompositeAttempt> attempts = new ArrayList<>();
		List<Judgment> successful = new ArrayList<>();
		Map<String, Judgment> successfulByName = new LinkedHashMap<>();
		boolean anyFailure = false;

		for (NamedJury member : members) {
			try {
				Verdict verdict = CompositeExecutionScope.invokeChild(() -> member.jury().vote(context));
				attempts.add(new CompositeAttempt(member.name(), CompositeRelation.META_MEMBER, null, verdict, null));
				successful.add(verdict.aggregated());
				successfulByName.put(member.name(), verdict.aggregated());
			}
			catch (CompositeLimitExceededException ex) {
				throw ex;
			}
			catch (Exception ex) {
				anyFailure = true;
				attempts.add(new CompositeAttempt(member.name(), CompositeRelation.META_MEMBER, null, null,
						EXECUTION_FAILURE));
			}
		}

		Judgment aggregate = anyFailure ? Judgment.error("One or more jury members failed to execute.")
				: metaStrategy.aggregate(successful, Map.of());
		return Verdict.builder()
			.aggregated(aggregate)
			.individual(successful)
			.individualByName(successfulByName)
			.compositeAttempts(attempts)
			.build();
	}

}
