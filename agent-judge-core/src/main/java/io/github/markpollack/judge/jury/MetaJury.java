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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.context.JudgmentContext;
import io.github.markpollack.judge.description.JuryDescription;
import io.github.markpollack.judge.description.KeySource;
import io.github.markpollack.judge.description.MemberDescription;
import io.github.markpollack.judge.description.MetaJuryDescription;
import io.github.markpollack.judge.result.Judgment;
import io.github.markpollack.judge.result.JudgmentReasonCode;

/** Package-private named jury-of-juries implementation used by {@link Juries}. */
class MetaJury implements Jury {

	private static final Logger logger = LoggerFactory.getLogger(MetaJury.class);

	private static final CompositeFailure EXECUTION_FAILURE =
			new CompositeFailure(CompositeFailureCode.JURY_EXECUTION_FAILED);

	private final List<NamedJury> members;

	private final VotingStrategy metaStrategy;

	/**
	 * A member whose aggregate may be excluded exists, and the strategy is configured to honour
	 * an exclusion.
	 * @return true when this jury's aggregate may be NOT_APPLICABLE
	 */
	@Override
	public boolean aggregateMayBeNotApplicable() {
		return metaStrategy.notApplicablePolicy() == NotApplicablePolicy.EXCLUDE
				&& members.stream().anyMatch(member -> member.jury().aggregateMayBeNotApplicable());
	}

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
		if (metaStrategy.notApplicablePolicy() == NotApplicablePolicy.REFUSE) {
			for (NamedJury member : this.members) {
				if (member.jury().aggregateMayBeNotApplicable()) {
					throw new IllegalArgumentException("member '" + member.name()
							+ "' declares that its aggregate may be NOT_APPLICABLE, but strategy '"
							+ metaStrategy.getName()
							+ "' refuses exclusions; configure NotApplicablePolicy.EXCLUDE or TREAT_AS_FAIL, "
							+ "or compose a member that does not exclude");
				}
			}
		}
	}

	@Override
	public List<Judge> getJudges() {
		return List.of();
	}

	@Override
	public VotingStrategy getVotingStrategy() {
		return metaStrategy;
	}

	/**
	 * Describe this meta-jury's strategy and its named members in execution order.
	 * <p>
	 * {@link #getJudges()} is empty for a meta-jury, so this description is the only view of
	 * its members before a vote.
	 * </p>
	 * @return a meta-jury description
	 * @throws IllegalArgumentException if a member cannot be described; the message names the
	 * member
	 */
	@Override
	public JuryDescription describe() {
		List<MemberDescription> described = new ArrayList<>(members.size());
		for (NamedJury member : members) {
			try {
				described.add(new MemberDescription(member.name(), member.jury().describe()));
			}
			catch (IllegalArgumentException ex) {
				throw new IllegalArgumentException("member '" + member.name() + "': " + ex.getMessage(), ex);
			}
		}
		return new MetaJuryDescription(metaStrategy.describe(), described, aggregateMayBeNotApplicable());
	}

	@Override
	public Verdict vote(JudgmentContext context) {
		return CompositeExecutionScope.withinCompositeVote(() -> execute(context));
	}

	private Verdict execute(JudgmentContext context) {
		List<CompositeAttempt> attempts = new ArrayList<>();
		List<Judgment> successful = new ArrayList<>();
		Map<String, Judgment> successfulByName = new LinkedHashMap<>();
		List<Seat> seats = new ArrayList<>();
		boolean anyStageFailed = false;

		for (int position = 0; position < members.size(); position++) {
			NamedJury member = members.get(position);
			Verdict verdict;
			try {
				verdict = CompositeExecutionScope.invokeChild(member.name(), () -> member.jury().vote(context));
			}
			catch (CompositeLimitExceededException ex) {
				throw ex;
			}
			catch (Exception ex) {
				logger.warn("Member '{}' did not produce a verdict ({}); recording a stage failure", member.name(),
						ex.getClass().getName(), ex);
				attempts.add(CompositeAttempt.executionFailed(member.name(), CompositeRelation.META_MEMBER, null,
						EXECUTION_FAILURE));
				anyStageFailed = true;
				continue;
			}

			DispositionReason reason = NotApplicableGuard.stageFailure(member.jury(), verdict);
			if (reason != null) {
				// The member's own verdict is kept exactly as it came back. The parent records
				// that it could not use it, which is a different fact from what the member said.
				attempts.add(CompositeAttempt.stageFailed(member.name(), CompositeRelation.META_MEMBER, null, reason,
						verdict));
				anyStageFailed = true;
				continue;
			}

			attempts.add(CompositeAttempt.used(member.name(), CompositeRelation.META_MEMBER, null, verdict));
			successful.add(verdict.aggregated());
			successfulByName.put(member.name(), verdict.aggregated());
			// Seats are the configured positions of the members that were used, so a gap in the
			// positions is itself the record that a member between them failed.
			seats.add(new Seat(position, member.name(), KeySource.DECLARED));
		}

		if (anyStageFailed) {
			// Successful members are kept: their work is evidence, and discarding it would make
			// a single broken member indistinguishable from a jury that ran nothing.
			Judgment aggregate = Judgment.error(JudgmentReasonCode.STAGE_FAILED,
					"One or more jury members did not produce a usable determination, so this jury reduced nothing.");
			return Verdict.builder()
				.aggregated(aggregate)
				.individual(successful)
				.individualByName(successfulByName)
				.seats(seats)
				.decision(Decision.undecided())
				.compositeAttempts(attempts)
				.build();
		}

		Judgment aggregate = aggregateWithinBoundary(successful);
		return Verdict.builder()
			.aggregated(aggregate)
			.individual(successful)
			.individualByName(successfulByName)
			.seats(seats)
			.decision(AggregationBoundary.decisionFor(aggregate))
			.compositeAttempts(attempts)
			.build();
	}

	/**
	 * Call the meta-strategy inside the same boundary a SimpleJury uses.
	 * @param successful the aggregates of the members that were used
	 * @return the strategy's aggregate, or the contained error that replaces it
	 */
	private Judgment aggregateWithinBoundary(List<Judgment> successful) {
		return AggregationBoundary.aggregate(metaStrategy, successful, Map.of(), aggregateMayBeNotApplicable(),
				logger);
	}

}
