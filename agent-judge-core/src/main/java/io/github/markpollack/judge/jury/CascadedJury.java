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
import io.github.markpollack.judge.verdict.RoutingRule;
import io.github.markpollack.judge.verdict.Verdict;
import io.github.markpollack.judge.verdict.VerdictProvenance;
import io.github.markpollack.judge.verdict.VerdictProvenanceBasis;
import io.github.markpollack.judge.verdict.VerdictProvenanceKind;


import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import java.lang.System.Logger;


import io.github.markpollack.judge.description.CascadedJuryDescription;
import io.github.markpollack.judge.description.JuryDescription;
import io.github.markpollack.judge.description.TierDescription;
import io.github.markpollack.judge.judgment.Judgment;
import io.github.markpollack.judge.judgment.JudgmentReasonCode;
import io.github.markpollack.judge.judgment.JudgmentStatus;

/**
 * A jury that evaluates named tiers sequentially with explicit stop and escalation
 * semantics. Every entered tier in a returned result is represented by one complete
 * {@link CompositeAttempt}.
 *
 * @author Mark Pollack
 * @since 0.9.0
 * @see RoutingRule
 * @see TierConfig
 */
public class CascadedJury implements Jury {

	private static final Logger logger = System.getLogger(CascadedJury.class.getName());

	private final List<TierConfig> tiers;

	private CascadedJury(List<TierConfig> tiers) {
		Set<String> names = new HashSet<>();
		for (TierConfig tier : tiers) {
			if (!names.add(tier.name())) {
				throw new IllegalArgumentException("Duplicate cascade tier name: " + tier.name());
			}
		}
		this.tiers = List.copyOf(tiers);
	}

	/**
	 * Configured tiers in evaluation order.
	 * @return immutable tier list, with their routing rules
	 */
	public List<TierConfig> tiers() {
		return tiers;
	}

	/**
	 * Some tier's aggregate may be excluded.
	 * <p>
	 * A cascade has no strategy of its own — it adopts a tier's verdict — so its bound is
	 * the union of its tiers', computed recursively through whatever those tiers are made
	 * of. The one path that does not widen it is a stop on an individual rejection, whose
	 * root the cascade builds itself as a machinery error rather than an exclusion.
	 * </p>
	 * @return true when this jury's aggregate may be NOT_APPLICABLE
	 *
	 * @since 0.17.0
	 */
	@Override
	public boolean aggregateMayBeNotApplicable() {
		return describe().aggregateMayBeNotApplicable();
	}

	/**
	 * Describe this cascade's tiers in evaluation order, each with its policy and jury.
	 * <p>
	 * A cascade's verdict copies its aggregate and individual judgments from the tier
	 * that stopped it. Count per tier against {@code Verdict.compositeAttempts()}, never
	 * also the top-level aggregate; see {@link CascadedJuryDescription}.
	 * </p>
	 * @return a cascaded jury description
	 * @throws IllegalArgumentException if a tier cannot be described; the message names
	 * the tier
	 *
	 * @since 0.17.0
	 */
	@Override
	public JuryDescription describe() {
		List<TierDescription> described = new ArrayList<>(tiers.size());
		for (TierConfig tier : tiers) {
			try {
				described.add(new TierDescription(tier.name(), tier.routingRule(), tier.jury().describe()));
			}
			catch (IllegalArgumentException ex) {
				throw new IllegalArgumentException("tier '" + tier.name() + "': " + ex.getMessage(), ex);
			}
		}
		return new CascadedJuryDescription(described);
	}

	@Override
	public Verdict vote() {
		return CompositeExecutionScope.withinCompositeVote(() -> execute());
	}

	/**
	 * The single rule, applied to each tier in order.
	 *
	 * <p>
	 * Every tier is validated before routing. Refused non-final tiers continue; a refused
	 * final tier terminates with an inconclusive result retaining the original refusal.
	 *
	 * <p>
	 * There are only three things a tier can do. It can throw; it can return a verdict
	 * the cascade cannot use; or it can return one the cascade can. The interesting case
	 * is the middle one, and the rule there is deliberately asymmetric: a tier that did
	 * not finish may still have established a genuine rejection on the way, and one
	 * established violation is enough to reject. It is never enough to accept. A broken
	 * reduction cannot demonstrate that a subject is fine, so
	 * {@code STOP_ON_ALL_OPINIONS_PASS} never stops on a failed stage and simply
	 * escalates.
	 * </p>
	 * @return the cascade's verdict
	 */
	private Verdict execute() {
		List<CompositeAttempt> attempts = new ArrayList<>();
		for (TierConfig tier : tiers) {
			Verdict tierVerdict;
			try {
				tierVerdict = CompositeExecutionScope.invokeChild(tier.name(), () -> tier.jury().vote());
			}
			catch (CompositeLimitExceededException ex) {
				throw ex;
			}
			catch (Exception ex) {
				SimpleJury.preserveCancellation(ex);
				logger.log(System.Logger.Level.WARNING, "Tier {0} did not produce a verdict ({1}); continuing according to cascade policy",
						tier.name(), ex.getClass().getName(), ex);
				attempts.add(CompositeAttempt.executionFailed(tier.name(), CompositeRelation.CASCADE_TIER,
						tier.routingRule(), new CompositeFailure(CompositeFailureCode.JURY_EXECUTION_FAILED, ex)));
				if (tier.routingRule() == RoutingRule.FINAL_TIER) {
					return noTierDecided(attempts, "The final cascade tier failed to execute.");
				}
				continue;
			}

			// validate returned records while retaining invalid originals on attempts.
			try {
				tierVerdict.conclusion();
			}
			catch (IllegalArgumentException ex) {
				attempts.add(CompositeAttempt.stageFailed(tier.name(), CompositeRelation.CASCADE_TIER,
						tier.routingRule(), DispositionReason.INVALID_TIER_RESULT, tierVerdict));
				if (tier.routingRule() == RoutingRule.FINAL_TIER)
					return noTierDecided(attempts, "Invalid final tier");
				continue;
			}
			DispositionReason reason = NotApplicableGuard.stageFailure(tier.jury(), tierVerdict);
			if (reason == DispositionReason.CHILD_UNDECIDED)
				reason = null;
			if (reason != null) {
				// The tier ran but did not produce a determination this cascade may use.
				// Its
				// verdict is kept unchanged on the attempt; what changes is the parent's
				// record
				// of whether it could be used.
				attempts.add(CompositeAttempt.stageFailed(tier.name(), CompositeRelation.CASCADE_TIER,
						tier.routingRule(), reason, tierVerdict));
				if (tier.routingRule() == RoutingRule.FINAL_TIER) {
					return noTierDecided(attempts, "The final cascade tier did not produce a determination.");
				}
				continue;
			}

			attempts.add(CompositeAttempt.used(tier.name(), CompositeRelation.CASCADE_TIER, tier.routingRule(),
					tierVerdict));
			if (tier.routingRule() == RoutingRule.STOP_ON_ANY_OPINION_FAIL && hasAnyFail(tierVerdict)
					&& tierVerdict.provenance().kind() == VerdictProvenanceKind.UNDECIDED)
				return individualRejection(tier, tierVerdict, DispositionReason.CHILD_UNDECIDED, attempts);
			if (shouldStop(tier, tierVerdict)) {
				return tierOutcome(tier.name(), tierVerdict, attempts);
			}
		}
		// The builder requires a FINAL_TIER last, and every path through a final tier
		// returns,
		// so this is unreachable; it exists so a future policy cannot fall out of the
		// loop with
		// no verdict at all.
		return noTierDecided(attempts, "No cascade tier produced a determination.");
	}

	private boolean shouldStop(TierConfig tier, Verdict verdict) {
		return Verdict.routingStops(tier.routingRule(), verdict, true);
	}

	private boolean hasAnyFail(Verdict verdict) {
		return Verdict.routingStops(RoutingRule.STOP_ON_ANY_OPINION_FAIL, verdict, true);
	}

	private Verdict tierOutcome(String name, Verdict stoppingVerdict, List<CompositeAttempt> attempts) {
		return Verdict.builder()
			.judgment(stoppingVerdict.judgment())
			.individual(stoppingVerdict.individual())
			.individualByName(stoppingVerdict.individualByName())
			.weights(stoppingVerdict.weights())
			.seats(stoppingVerdict.seats())
			.declaredCardinality(stoppingVerdict.declaredCardinality())
			.provenance(VerdictProvenance.tier(name, VerdictProvenanceBasis.TIER_OUTCOME))
			.compositeAttempts(attempts)
			.build();
	}

	/**
	 * The cascade stopped on a rejection a failed tier had already established.
	 * <p>
	 * No FAIL and no score is manufactured. The rejection is carried by the provenance,
	 * and the root aggregate stays an instrument failure, so a reader counts one
	 * machinery failure and classifies the item as non-pass — rather than reading an
	 * error as though the subject had been assessed and found wanting.
	 * </p>
	 * @param tier the tier that failed
	 * @param tierVerdict the verdict it returned
	 * @param reason why the cascade could not use it
	 * @param attempts the attempts so far
	 * @return the cascade's verdict
	 */
	private Verdict individualRejection(TierConfig tier, Verdict tierVerdict, DispositionReason reason,
			List<CompositeAttempt> attempts) {
		Judgment root = reason == DispositionReason.CHILD_UNDECIDED ? tierVerdict.judgment()
				: Judgment.error(JudgmentReasonCode.STAGE_FAILED, "Tier '" + tier.name()
						+ "' returned NOT_APPLICABLE without declaring that its aggregate may be excluded, so its "
						+ "reduction is a stage failure; the cascade stopped because a genuine individual FAIL in "
						+ "that tier established the rejection.");
		return Verdict.builder()
			.judgment(root)
			.individual(tierVerdict.individual())
			.individualByName(tierVerdict.individualByName())
			.weights(tierVerdict.weights())
			.seats(tierVerdict.seats())
			.declaredCardinality(tierVerdict.declaredCardinality())
			.provenance(VerdictProvenance.tier(tier.name(), VerdictProvenanceBasis.INDIVIDUAL_REJECTION))
			.compositeAttempts(attempts)
			.build();
	}

	/**
	 * Nothing decided: an empty root, complete attempts, and a machinery error.
	 * <p>
	 * Any exclusion this cascade refused along the way is named here, because no later
	 * tier's reasoning survives to explain the outcome and the disposition enum alone
	 * leaves a reader unable to tell a refused exclusion from a tier that simply broke.
	 * R-E's enum-only allowance is scoped to the case where a later selected tier
	 * supplies the root, which never reaches this method.
	 * </p>
	 * @param attempts the attempts recorded so far
	 * @param reasoning why no tier decided
	 * @return the undecided verdict
	 */
	private Verdict noTierDecided(List<CompositeAttempt> attempts, String reasoning) {
		return Verdict.builder()
			.judgment(Judgment.error(JudgmentReasonCode.NO_TIER_DECIDED,
					reasoning + NotApplicableGuard.refusedExclusionNote(attempts, "Tier")))
			.provenance(VerdictProvenance.undecided())
			.compositeAttempts(attempts)
			.build();
	}

	/**
	 * Create a new builder for CascadedJury.
	 * @return builder instance
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * {@code Builder} for {@link CascadedJury}.
	 *
	 */
	public static class Builder {

		private final List<TierConfig> tiers = new ArrayList<>();

		/** Create an empty cascade builder. */
		public Builder() {
		}

		/**
		 * Add a named tier to the cascade.
		 * @param name stable unique sibling identity
		 * @param jury jury for this tier
		 * @param policy how this tier maps to stop or escalation
		 * @return this builder
		 */
		public Builder tier(String name, Jury jury, RoutingRule policy) {
			tiers.add(new TierConfig(name, jury, policy));
			return this;
		}

		/**
		 * Build the CascadedJury instance.
		 * @return configured CascadedJury
		 */
		public CascadedJury build() {
			if (tiers.isEmpty()) {
				throw new IllegalStateException("CascadedJury requires at least one tier");
			}
			TierConfig lastTier = tiers.get(tiers.size() - 1);
			if (lastTier.routingRule() != RoutingRule.FINAL_TIER) {
				throw new IllegalStateException("Last tier must use FINAL_TIER policy, but '" + lastTier.name()
						+ "' uses " + lastTier.routingRule());
			}
			for (TierConfig tier : tiers) {
				if ((tier.routingRule() == RoutingRule.STOP_ON_ANY_OPINION_FAIL
						|| tier.routingRule() == RoutingRule.STOP_ON_ALL_OPINIONS_PASS)
						&& tier.jury()
							.describe()
							.routingOpinionBound() == io.github.markpollack.judge.description.OpinionBound.KNOWN_NONE)
					throw new IllegalArgumentException(
							"Tier '" + tier.name() + "' has no root opinions; use a conclusion routing rule");
			}

			return new CascadedJury(tiers);
		}

	}

}
