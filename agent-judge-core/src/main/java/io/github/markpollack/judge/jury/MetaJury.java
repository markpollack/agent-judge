/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury;

import io.github.markpollack.judge.verdict.CompositeAttempt;
import io.github.markpollack.judge.verdict.CompositeFailure;
import io.github.markpollack.judge.verdict.CompositeFailureCode;
import io.github.markpollack.judge.verdict.CompositeLimitExceededException;
import io.github.markpollack.judge.verdict.CompositeRelation;
import io.github.markpollack.judge.verdict.DispositionReason;
import io.github.markpollack.judge.voting.Participation;
import io.github.markpollack.judge.verdict.Seat;
import io.github.markpollack.judge.verdict.Verdict;
import io.github.markpollack.judge.verdict.VerdictProvenance;
import io.github.markpollack.judge.verdict.VerdictProvenanceKind;
import io.github.markpollack.judge.voting.ExclusionHandling;
import io.github.markpollack.judge.voting.VotingStrategy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import java.lang.System.Logger;

import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.description.JuryDescription;
import io.github.markpollack.judge.verdict.KeySource;
import io.github.markpollack.judge.description.MemberDescription;
import io.github.markpollack.judge.description.MetaJuryDescription;
import io.github.markpollack.judge.judgment.Judgment;
import io.github.markpollack.judge.judgment.JudgmentReasonCode;

/**
 * Named jury-of-juries implementation used by {@link Juries}. Exactly one declared usable
 * member retains its complete aggregate without invoking the meta-strategy. Failed
 * members remain stage failures; one survivor among multiple declared members is not
 * identity.
 */
class MetaJury implements VotingJury {

	private static final Logger logger = System.getLogger(MetaJury.class.getName());

	private final List<NamedJury> members;

	private final VotingStrategy metaStrategy;

	/**
	 * A capable member exists and either one-member identity applies or the strategy
	 * excludes N/A. REFUSE remains a construction error for capable members.
	 * @return true when this jury's aggregate may be NOT_APPLICABLE
	 */
	@Override
	public boolean aggregateMayBeNotApplicable() {
		return describe().aggregateMayBeNotApplicable();
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
		if (metaStrategy.exclusionHandling() == ExclusionHandling.REFUSE) {
			for (NamedJury member : this.members) {
				if (member.jury().aggregateMayBeNotApplicable()) {
					throw new IllegalArgumentException("member '" + member.name()
							+ "' declares that its aggregate may be NOT_APPLICABLE, but strategy '"
							+ metaStrategy.getName()
							+ "' refuses exclusions; configure ExclusionHandling.EXCLUDE or TREAT_AS_FAIL, "
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
	 * {@link #getJudges()} is empty for a meta-jury, so this description is the only view
	 * of its members before a vote.
	 * </p>
	 * @return a meta-jury description
	 * @throws IllegalArgumentException if a member cannot be described; the message names
	 * the member
	 */
	@Override
	public JuryDescription describe() {
		List<MemberDescription> described = new ArrayList<>(members.size());
		for (NamedJury member : members) {
			try {
				described.add(new MemberDescription(member.name(), member.jury().describe()));
			}
			catch (IllegalArgumentException ex) {
				io.github.markpollack.judge.portable.PreservationLimitException.propagate(ex);
				throw new IllegalArgumentException("member '" + member.name() + "': " + ex.getMessage(), ex);
			}
		}
		return new MetaJuryDescription(metaStrategy.describe(), described);
	}

	@Override
	public Verdict vote() {
		return CompositeExecutionScope.withinCompositeVote(() -> execute());
	}

	private Verdict execute() {
		List<CompositeAttempt> attempts = new ArrayList<>();
		List<Judgment> successful = new ArrayList<>();
		Map<String, Judgment> successfulByName = new LinkedHashMap<>();
		List<Seat> seats = new ArrayList<>();
		boolean anyStageFailed = false;

		for (int position = 0; position < members.size(); position++) {
			NamedJury member = members.get(position);
			Verdict verdict;
			try {
				verdict = CompositeExecutionScope.invokeChild(member.name(), () -> member.jury().vote());
			}
			catch (CompositeLimitExceededException ex) {
				throw ex;
			}
			catch (Exception ex) {
				io.github.markpollack.judge.portable.PreservationLimitException.propagate(ex);
				SimpleJury.preserveCancellation(ex);
				logger.log(System.Logger.Level.WARNING,
						"Member {0} did not produce a verdict ({1}); recording a stage failure", member.name(),
						ex.getClass().getName(), ex);
				attempts.add(CompositeAttempt.executionFailed(member.name(), CompositeRelation.META_MEMBER, null,
						new CompositeFailure(CompositeFailureCode.JURY_EXECUTION_FAILED, ex)));
				anyStageFailed = true;
				continue;
			}

			// validate returned records while retaining invalid originals on attempts.
			try {
				verdict.conclusion();
			}
			catch (IllegalArgumentException ex) {
				io.github.markpollack.judge.portable.PreservationLimitException.propagate(ex);
				attempts.add(CompositeAttempt.stageFailed(member.name(), CompositeRelation.META_MEMBER, null,
						DispositionReason.INVALID_TIER_RESULT, verdict));
				anyStageFailed = true;
				continue;
			}
			DispositionReason reason = NotApplicableGuard.stageFailure(member.jury(), verdict);
			if (members.size() == 1 && reason == DispositionReason.CHILD_UNDECIDED)
				reason = null;
			if (reason != null) {
				// The member's own verdict is kept exactly as it came back. The parent
				// records
				// that it could not use it, which is a different fact from what the
				// member said.
				attempts.add(CompositeAttempt.stageFailed(member.name(), CompositeRelation.META_MEMBER, null, reason,
						verdict));
				anyStageFailed = true;
				continue;
			}

			attempts.add(CompositeAttempt.used(member.name(), CompositeRelation.META_MEMBER, null, verdict));
			successful.add(verdict.judgment());
			successfulByName.put(member.name(), verdict.judgment());
			// Seats are the configured positions of the members that were used, so a gap
			// in the
			// positions is itself the record that a member between them failed.
			seats.add(new Seat(position, member.name(), KeySource.DECLARED));
		}

		if (anyStageFailed) {
			// Successful members are kept: their work is evidence, and discarding it
			// would make
			// a single broken member indistinguishable from a jury that ran nothing.
			// Any exclusion this jury refused is named: a meta-jury has no later tier
			// whose
			// reasoning could explain the outcome instead, so R-E's enum-only allowance
			// does not
			// reach here and §7.3's free-text requirement stands.
			Judgment aggregate = Judgment.error(JudgmentReasonCode.STAGE_FAILED,
					"One or more jury members did not produce a usable determination, so this jury reduced nothing."
							+ NotApplicableGuard.refusedExclusionNote(attempts, "Member"));
			seats.replaceAll(seat -> seat.treated(Participation.NOT_REDUCED));
			return Verdict.advancedBuilder()
				.declaredCardinality(members.size())
				.judgment(aggregate)
				.individual(successful)
				.individualByName(successfulByName)
				.seats(seats)
				.provenance(VerdictProvenance.undecided())
				.compositeAttempts(attempts)
				.build();
		}

		boolean identity = members.size() == 1;
		var reduction = identity ? new AggregationBoundary.Reduction(successful.get(0), null) : AggregationBoundary
			.aggregate(metaStrategy, ballots(seats, successful), aggregateMayBeNotApplicable(), logger);
		Judgment aggregate = reduction.judgment();
		for (int i = 0; i < seats.size(); i++)
			seats.set(i, seats.get(i).treated(Participation.forJudgment(successful.get(i), aggregate, identity)));
		return Verdict.advancedBuilder()
			.reductionFailure(reduction.failure())
			.rule(reduction.rule())
			.declaredCardinality(members.size())
			.judgment(aggregate)
			.individual(successful)
			.individualByName(successfulByName)
			.seats(seats)
			.provenance(identity
					? (attempts.get(0).verdict().provenance().kind() == VerdictProvenanceKind.UNDECIDED
							? VerdictProvenance.undecided() : VerdictProvenance.own())
					: VerdictProvenance.decisionFor(aggregate))
			.compositeAttempts(attempts)
			.build();
	}

	/**
	 * Call the meta-strategy inside the same boundary a SimpleJury uses.
	 * @param successful the aggregates of the members that were used
	 * @return the strategy's aggregate, or the contained error that replaces it
	 */
	private static List<io.github.markpollack.judge.voting.Ballot> ballots(List<Seat> seats, List<Judgment> originals) {
		var result = new ArrayList<io.github.markpollack.judge.voting.Ballot>();
		for (int i = 0; i < seats.size(); i++)
			result.add(seats.get(i).ballot(originals.get(i)));
		return List.copyOf(result);
	}

}
