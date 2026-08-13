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
import io.github.markpollack.judge.result.Judgment;
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

	@Override
	public Verdict vote(JudgmentContext context) {
		return CompositeExecutionScope.withinCompositeVote(() -> execute(context));
	}

	private Verdict execute(JudgmentContext context) {
		List<CompositeAttempt> attempts = new ArrayList<>();
		Verdict lastSuccessful = null;
		for (TierConfig tier : tiers) {
			Verdict tierVerdict;
			try {
				tierVerdict = CompositeExecutionScope.invokeChild(() -> tier.jury().vote(context));
			}
			catch (CompositeLimitExceededException ex) {
				throw ex;
			}
			catch (Exception ex) {
				logger.warn("Tier '{}' failed to execute; continuing according to cascade policy", tier.name());
				attempts.add(new CompositeAttempt(tier.name(), CompositeRelation.CASCADE_TIER, tier.policy(), null,
						EXECUTION_FAILURE));
				if (tier.policy() == TierPolicy.FINAL_TIER) {
					return errorVerdict("The final cascade tier failed to execute.", attempts);
				}
				continue;
			}

			attempts.add(new CompositeAttempt(tier.name(), CompositeRelation.CASCADE_TIER, tier.policy(), tierVerdict,
					null));
			lastSuccessful = tierVerdict;
			if (shouldStop(tier, tierVerdict)) {
				return successfulVerdict(tierVerdict, attempts);
			}
		}

		if (lastSuccessful == null) {
			return errorVerdict("No cascade tier returned a verdict.", attempts);
		}
		return successfulVerdict(lastSuccessful, attempts);
	}

	private boolean shouldStop(TierConfig tier, Verdict verdict) {
		return switch (tier.policy()) {
			case REJECT_ON_ANY_FAIL -> hasAnyFail(verdict);
			case ACCEPT_ON_ALL_PASS -> allPassed(verdict);
			case FINAL_TIER -> true;
		};
	}

	private boolean hasAnyFail(Verdict verdict) {
		return verdict.individual().stream().anyMatch(judgment -> judgment.status() == JudgmentStatus.FAIL);
	}

	private boolean allPassed(Verdict verdict) {
		return verdict.individual().stream().allMatch(judgment -> judgment.status() == JudgmentStatus.PASS);
	}

	private Verdict successfulVerdict(Verdict stoppingVerdict, List<CompositeAttempt> attempts) {
		return Verdict.builder()
			.aggregated(stoppingVerdict.aggregated())
			.individual(stoppingVerdict.individual())
			.individualByName(stoppingVerdict.individualByName())
			.weights(stoppingVerdict.weights())
			.compositeAttempts(attempts)
			.build();
	}

	private Verdict errorVerdict(String reasoning, List<CompositeAttempt> attempts) {
		Judgment error = Judgment.error(reasoning);
		return Verdict.builder().aggregated(error).compositeAttempts(attempts).build();
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
