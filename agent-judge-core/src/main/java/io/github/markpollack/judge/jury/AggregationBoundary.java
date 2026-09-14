/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import io.github.markpollack.judge.result.Judgment;
import io.github.markpollack.judge.result.JudgmentReasonCode;
import io.github.markpollack.judge.result.JudgmentStatus;

/**
 * The single boundary every jury puts around its strategy.
 *
 * <p>
 * A strategy is caller code, even when the caller is this library. It can throw, return null, or
 * return an aggregate it was not entitled to produce, and before containment each of those took
 * the whole jury with it — inside a cascade, the enclosing tier too. A run in which nine judges
 * succeeded and one strategy threw reported nothing at all.
 * </p>
 *
 * <p>
 * So the call is wrapped once, here, and both juries use this rather than two copies that could
 * drift. A contained failure becomes an {@code ERROR aggregation_failed}: loud, countable,
 * excluded from the subject's denominator, and carrying no evidence block, because there is no
 * reduction to describe. Every judge's own result survives in {@link Verdict#individual()}.
 * {@link Error} is deliberately not caught — a {@code StackOverflowError} is not a judgment any
 * jury can report on.
 * </p>
 *
 * @author Mark Pollack
 * @since 0.17.0
 */
final class AggregationBoundary {

	/**
	 * The statuses a strategy's aggregate may carry.
	 * <p>
	 * An allow-list rather than a deny-list, so a status added later is refused by a jury that
	 * predates it instead of flowing through whichever branch happens to catch it. A contained
	 * error is loud and countable; a verdict whose meaning nobody has decided is not.
	 * </p>
	 */
	private static final Set<JudgmentStatus> ALLOWED_STATUSES = Set.of(JudgmentStatus.PASS, JudgmentStatus.FAIL,
			JudgmentStatus.ABSTAIN, JudgmentStatus.NOT_APPLICABLE, JudgmentStatus.ERROR);

	/**
	 * The reason codes a strategy's ERROR aggregate may carry.
	 * <p>
	 * A strategy reports two kinds of error: it propagated its inputs' errors, or it refused an
	 * exclusion it was configured to refuse. Every other instrument code belongs to something
	 * else — a judge, or the jury's own machinery — and a strategy emitting one would put a leaf
	 * cause, or a containment code, where a reader counts reductions.
	 * </p>
	 */
	private static final Set<JudgmentReasonCode> ALLOWED_ERROR_CODES = Set.of(JudgmentReasonCode.ERRORS_PROPAGATED,
			JudgmentReasonCode.NOT_APPLICABLE_REFUSED);

	private AggregationBoundary() {
	}

	/**
	 * Reduce within the boundary.
	 * @param strategy the configured strategy
	 * @param judgments the judgments to reduce
	 * @param weights the configured weights
	 * @param mayBeNotApplicable whether this jury is entitled to an excluded aggregate
	 * @param logger the calling jury's logger, so a contained failure is reported where it
	 * happened
	 * @return the strategy's aggregate, or the contained error that replaces it
	 */
	static Judgment aggregate(VotingStrategy strategy, List<Judgment> judgments, Map<String, Double> weights,
			boolean mayBeNotApplicable, Logger logger) {
		// Resolved before the call, so a strategy whose own getName() throws still has a name in
		// the diagnostic that reports it.
		String name = safeName(strategy);
		Judgment aggregate;
		try {
			aggregate = strategy.aggregate(judgments, weights);
		}
		catch (Exception ex) {
			String cause = ex.getMessage();
			return contained(logger, name,
					"threw " + ex.getClass().getName() + ((cause == null || cause.isBlank()) ? "" : ": " + cause));
		}
		String rejection = allowListRejection(aggregate, mayBeNotApplicable);
		return rejection == null ? aggregate : contained(logger, name, rejection);
	}

	/**
	 * Why this aggregate is not one the strategy was entitled to produce, or null.
	 * @param aggregate the strategy's result, possibly null
	 * @param mayBeNotApplicable whether the jury is entitled to an excluded aggregate
	 * @return the rejection, or null when the aggregate is allowed
	 */
	private static @Nullable String allowListRejection(@Nullable Judgment aggregate, boolean mayBeNotApplicable) {
		if (aggregate == null) {
			return "returned no aggregate";
		}
		if (!ALLOWED_STATUSES.contains(aggregate.status())) {
			return "returned an aggregate with status " + aggregate.status()
					+ ", which is not a status a strategy may produce";
		}
		if (aggregate.status() == JudgmentStatus.NOT_APPLICABLE && !mayBeNotApplicable) {
			return "returned a NOT_APPLICABLE aggregate, but this jury never declared that its aggregate "
					+ "may be excluded";
		}
		if (aggregate.status() == JudgmentStatus.ERROR && !ALLOWED_ERROR_CODES.contains(aggregate.reasonCode())) {
			return "returned an ERROR coded " + aggregate.reasonCode()
					+ ", which names a cause outside the reduction it performed";
		}
		return null;
	}

	private static Judgment contained(Logger logger, String strategyName, String what) {
		String reasoning = "Strategy '" + strategyName + "' " + what + "; no aggregate was produced";
		logger.warn("{}", reasoning);
		return Judgment.error(JudgmentReasonCode.AGGREGATION_FAILED, reasoning);
	}

	private static String safeName(VotingStrategy strategy) {
		String name;
		try {
			name = strategy.getName();
		}
		catch (Exception ex) {
			return strategy.getClass().getName();
		}
		return (name == null || name.isBlank()) ? strategy.getClass().getName() : name;
	}

	/**
	 * What produced a verdict's aggregate.
	 * <p>
	 * A machinery error means nothing determined an outcome; anything else — including a
	 * propagated error, which is the jury's own policy outcome — is the jury's own decision.
	 * </p>
	 * @param aggregated the aggregate
	 * @return the decision
	 */
	static Decision decisionFor(Judgment aggregated) {
		JudgmentReasonCode code = aggregated.reasonCode();
		boolean undecided = aggregated.status() == JudgmentStatus.ERROR && code != null
				&& code.originFamily() == JudgmentReasonCode.OriginFamily.MACHINERY;
		return undecided ? Decision.undecided() : Decision.own();
	}

}
