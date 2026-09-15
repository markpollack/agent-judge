/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.context.JudgmentContext;
import io.github.markpollack.judge.description.CascadedJuryDescription;
import io.github.markpollack.judge.description.JuryDescription;
import io.github.markpollack.judge.description.TierDescription;
import io.github.markpollack.judge.result.Judgment;
import io.github.markpollack.judge.result.JudgmentReasonCode;
import io.github.markpollack.judge.result.JudgmentStatus;

/**
 * A jury that evaluates named tiers sequentially with explicit stop and escalation
 * semantics. Every entered tier in a returned result is represented by one complete
 * {@link CompositeAttempt}.
 *
 * @author Mark Pollack
 * @since 0.9.0
 * @see TierPolicy
 * @see TierConfig
 */
public class CascadedJury implements Jury {

	private static final Logger logger = LoggerFactory.getLogger(CascadedJury.class);

	private static final CompositeFailure EXECUTION_FAILURE =
			new CompositeFailure(CompositeFailureCode.JURY_EXECUTION_FAILED);

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

	@Override
	public List<Judge> getJudges() {
		return tiers.stream().flatMap(tier -> tier.jury().getJudges().stream()).toList();
	}

	@Override
	public VotingStrategy getVotingStrategy() {
		return null;
	}

	/**
	 * Some tier's aggregate may be excluded.
	 * <p>
	 * A cascade has no strategy of its own — it adopts a tier's verdict — so its bound is the
	 * union of its tiers', computed recursively through whatever those tiers are made of. The
	 * one path that does not widen it is a stop on an individual rejection, whose root the
	 * cascade builds itself as a machinery error rather than an exclusion.
	 * </p>
	 * @return true when this jury's aggregate may be NOT_APPLICABLE
	 * @since 0.17.0
	 */
	@Override
	public boolean aggregateMayBeNotApplicable() {
		return tiers.stream().anyMatch(tier -> tier.jury().aggregateMayBeNotApplicable());
	}

	/**
	 * Describe this cascade's tiers in evaluation order, each with its policy and jury.
	 * <p>
	 * A cascade's verdict copies its aggregate and individual judgments from the tier that
	 * stopped it. Count per tier against {@code Verdict.compositeAttempts()}, never also the
	 * top-level aggregate; see {@link CascadedJuryDescription}.
	 * </p>
	 * @return a cascaded jury description
	 * @throws IllegalArgumentException if a tier cannot be described; the message names the
	 * tier
	 * @since 0.17.0
	 */
	@Override
	public JuryDescription describe() {
		List<TierDescription> described = new ArrayList<>(tiers.size());
		for (TierConfig tier : tiers) {
			try {
				described.add(new TierDescription(tier.name(), tier.policy(), tier.jury().describe()));
			}
			catch (IllegalArgumentException ex) {
				throw new IllegalArgumentException("tier '" + tier.name() + "': " + ex.getMessage(), ex);
			}
		}
		return new CascadedJuryDescription(described);
	}

	@Override
	public Verdict vote(JudgmentContext context) {
		return CompositeExecutionScope.withinCompositeVote(() -> execute(context));
	}

	/**
	 * The single rule, applied to each tier in order.
	 *
	 * <p>
	 * There are only three things a tier can do. It can throw; it can return a verdict the
	 * cascade cannot use; or it can return one the cascade can. The interesting case is the
	 * middle one, and the rule there is deliberately asymmetric: a tier that did not finish may
	 * still have established a genuine rejection on the way, and one established violation is
	 * enough to reject. It is never enough to accept. A broken reduction cannot demonstrate that
	 * a subject is fine, so {@code ACCEPT_ON_ALL_PASS} never stops on a failed stage and simply
	 * escalates.
	 * </p>
	 *
	 * @param context the judgment context
	 * @return the cascade's verdict
	 */
	private Verdict execute(JudgmentContext context) {
		List<CompositeAttempt> attempts = new ArrayList<>();
		for (TierConfig tier : tiers) {
			Verdict tierVerdict;
			try {
				tierVerdict = CompositeExecutionScope.invokeChild(tier.name(), () -> tier.jury().vote(context));
			}
			catch (CompositeLimitExceededException ex) {
				throw ex;
			}
			catch (Exception ex) {
				logger.warn("Tier '{}' did not produce a verdict ({}); continuing according to cascade policy",
						tier.name(), ex.getClass().getName(), ex);
				attempts.add(CompositeAttempt.executionFailed(tier.name(), CompositeRelation.CASCADE_TIER,
						tier.policy(), EXECUTION_FAILURE));
				if (tier.policy() == TierPolicy.FINAL_TIER) {
					return noTierDecided(attempts, "The final cascade tier failed to execute.");
				}
				continue;
			}

			DispositionReason reason = NotApplicableGuard.stageFailure(tier.jury(), tierVerdict);
			if (reason != null) {
				// The tier ran but did not produce a determination this cascade may use. Its
				// verdict is kept unchanged on the attempt; what changes is the parent's record
				// of whether it could be used.
				attempts.add(CompositeAttempt.stageFailed(tier.name(), CompositeRelation.CASCADE_TIER, tier.policy(),
						reason, tierVerdict));
				if (tier.policy() == TierPolicy.FINAL_TIER) {
					return noTierDecided(attempts, "The final cascade tier did not produce a determination.");
				}
				if (tier.policy() == TierPolicy.REJECT_ON_ANY_FAIL && hasAnyFail(tierVerdict)) {
					return individualRejection(tier, tierVerdict, reason, attempts);
				}
				continue;
			}

			attempts.add(CompositeAttempt.used(tier.name(), CompositeRelation.CASCADE_TIER, tier.policy(),
					tierVerdict));
			if (shouldStop(tier, tierVerdict)) {
				return tierOutcome(tier.name(), tierVerdict, attempts);
			}
		}
		// The builder requires a FINAL_TIER last, and every path through a final tier returns,
		// so this is unreachable; it exists so a future policy cannot fall out of the loop with
		// no verdict at all.
		return noTierDecided(attempts, "No cascade tier produced a determination.");
	}

	private boolean shouldStop(TierConfig tier, Verdict verdict) {
		return switch (tier.policy()) {
			case REJECT_ON_ANY_FAIL -> hasAnyFail(verdict);
			case ACCEPT_ON_ALL_PASS -> allPassed(verdict);
			case FINAL_TIER -> true;
		};
	}

	/**
	 * Whether the tier established a genuine individual rejection.
	 * <p>
	 * A FAIL in a tier's {@code individual} is either a leaf judge's own finding or a member
	 * jury's completed reduction, including one a configured {@code TREAT_AS_FAIL} produced.
	 * Both are real. What is never here is a machinery error: an error is not a FAIL, and the
	 * library's own failure never becomes rejection evidence.
	 * </p>
	 * @param verdict the tier's verdict
	 * @return true when at least one individual judgment failed
	 */
	private boolean hasAnyFail(Verdict verdict) {
		return verdict.individual().stream().anyMatch(judgment -> judgment.status() == JudgmentStatus.FAIL);
	}

	private boolean allPassed(Verdict verdict) {
		return verdict.individual().stream().allMatch(judgment -> judgment.status() == JudgmentStatus.PASS);
	}

	/** The cascade adopted a tier's own determination. */
	private Verdict tierOutcome(String name, Verdict stoppingVerdict, List<CompositeAttempt> attempts) {
		return Verdict.builder()
			.aggregated(stoppingVerdict.aggregated())
			.individual(stoppingVerdict.individual())
			.individualByName(stoppingVerdict.individualByName())
			.weights(stoppingVerdict.weights())
			.seats(stoppingVerdict.seats())
			.decision(Decision.tier(name, DecisionBasis.TIER_OUTCOME))
			.compositeAttempts(attempts)
			.build();
	}

	/**
	 * The cascade stopped on a rejection a failed tier had already established.
	 * <p>
	 * No FAIL and no score is manufactured. The rejection is carried by the decision, and the
	 * root aggregate stays an instrument failure, so a reader counts one machinery failure and
	 * classifies the item as non-pass — rather than reading an error as though the subject had
	 * been assessed and found wanting.
	 * </p>
	 * @param tier the tier that failed
	 * @param tierVerdict the verdict it returned
	 * @param reason why the cascade could not use it
	 * @param attempts the attempts so far
	 * @return the cascade's verdict
	 */
	private Verdict individualRejection(TierConfig tier, Verdict tierVerdict, DispositionReason reason,
			List<CompositeAttempt> attempts) {
		Judgment root = reason == DispositionReason.CHILD_UNDECIDED ? tierVerdict.aggregated()
				: Judgment.error(JudgmentReasonCode.STAGE_FAILED, "Tier '" + tier.name()
						+ "' returned NOT_APPLICABLE without declaring that its aggregate may be excluded, so its "
						+ "reduction is a stage failure; the cascade stopped because a genuine individual FAIL in "
						+ "that tier established the rejection.");
		return Verdict.builder()
			.aggregated(root)
			.individual(tierVerdict.individual())
			.individualByName(tierVerdict.individualByName())
			.weights(tierVerdict.weights())
			.seats(tierVerdict.seats())
			.decision(Decision.tier(tier.name(), DecisionBasis.INDIVIDUAL_REJECTION))
			.compositeAttempts(attempts)
			.build();
	}

	/** Nothing decided: an empty root, complete attempts, and a machinery error. */
	private Verdict noTierDecided(List<CompositeAttempt> attempts, String reasoning) {
		return Verdict.builder()
			.aggregated(Judgment.error(JudgmentReasonCode.NO_TIER_DECIDED, reasoning))
			.decision(Decision.undecided())
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

	/** Builder for {@link CascadedJury}. */
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
		public Builder tier(String name, Jury jury, TierPolicy policy) {
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
			if (lastTier.policy() != TierPolicy.FINAL_TIER) {
				throw new IllegalStateException("Last tier must use FINAL_TIER policy, but '" + lastTier.name()
						+ "' uses " + lastTier.policy());
			}
			return new CascadedJury(tiers);
		}

	}

}
