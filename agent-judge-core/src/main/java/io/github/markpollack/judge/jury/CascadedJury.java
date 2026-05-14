/*
 * Copyright 2024 Spring AI Community
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.markpollack.judge.jury;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.context.JudgmentContext;
import io.github.markpollack.judge.result.Judgment;
import io.github.markpollack.judge.result.JudgmentStatus;

/**
 * A Jury that evaluates through cascading tiers with fail-fast and escalation semantics.
 * Each tier is itself a Jury (typically SimpleJury).
 *
 * <p>
 * Tier execution proceeds sequentially. Each tier's {@link TierPolicy} determines whether
 * to stop (reject/accept) or escalate to the next tier. The final tier always produces a
 * verdict.
 * </p>
 *
 * <p>
 * Example:
 * </p>
 * <pre>{@code
 * CascadedJury jury = CascadedJury.builder()
 *     .tier("deterministic", tier1Jury, TierPolicy.REJECT_ON_ANY_FAIL)
 *     .tier("structural", tier2Jury, TierPolicy.ACCEPT_ON_ALL_PASS)
 *     .tier("semantic", tier3Jury, TierPolicy.FINAL_TIER)
 *     .build();
 * Verdict verdict = jury.vote(context);
 * }</pre>
 *
 * @author Mark Pollack
 * @since 0.9.0
 * @see TierPolicy
 * @see TierConfig
 */
public class CascadedJury implements Jury {

	private static final Logger logger = LoggerFactory.getLogger(CascadedJury.class);

	private final List<TierConfig> tiers;

	private CascadedJury(List<TierConfig> tiers) {
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
		List<Verdict> executedTierVerdicts = new ArrayList<>();
		List<String> tiersExecuted = new ArrayList<>();

		for (int i = 0; i < tiers.size(); i++) {
			TierConfig tier = tiers.get(i);
			tiersExecuted.add(tier.name());

			Verdict tierVerdict;
			try {
				tierVerdict = tier.jury().vote(context);
			}
			catch (Exception ex) {
				logger.warn("Tier '{}' threw exception, escalating to next tier", tier.name(), ex);
				if (i == tiers.size() - 1) {
					// Final tier exception → ERROR verdict
					return buildErrorVerdict(tier.name(), ex, executedTierVerdicts, tiersExecuted);
				}
				continue;
			}

			executedTierVerdicts.add(tierVerdict);

			switch (tier.policy()) {
				case REJECT_ON_ANY_FAIL -> {
					if (hasAnyFail(tierVerdict)) {
						return buildCascadeVerdict(tierVerdict, executedTierVerdicts, tier.name(), tiersExecuted);
					}
					// No failures → escalate
				}
				case ACCEPT_ON_ALL_PASS -> {
					if (allPassed(tierVerdict)) {
						return buildCascadeVerdict(tierVerdict, executedTierVerdicts, tier.name(), tiersExecuted);
					}
					// Not all passed → escalate
				}
				case FINAL_TIER -> {
					return buildCascadeVerdict(tierVerdict, executedTierVerdicts, tier.name(), tiersExecuted);
				}
			}
		}

		// Should not reach here if last tier is FINAL_TIER, but defensive fallback
		Verdict lastVerdict = executedTierVerdicts.get(executedTierVerdicts.size() - 1);
		return buildCascadeVerdict(lastVerdict, executedTierVerdicts, tiers.get(tiers.size() - 1).name(),
				tiersExecuted);
	}

	private boolean hasAnyFail(Verdict tierVerdict) {
		return tierVerdict.individual().stream().anyMatch(j -> j.status() == JudgmentStatus.FAIL);
	}

	private boolean allPassed(Verdict tierVerdict) {
		return tierVerdict.individual().stream().allMatch(j -> j.status() == JudgmentStatus.PASS);
	}

	private Verdict buildCascadeVerdict(Verdict stoppingTierVerdict, List<Verdict> allTierVerdicts, String stoppedAt,
			List<String> tiersExecuted) {
		return Verdict.builder()
			.aggregated(stoppingTierVerdict.aggregated())
			.individual(stoppingTierVerdict.individual())
			.individualByName(stoppingTierVerdict.individualByName())
			.subVerdicts(allTierVerdicts)
			.build();
	}

	private Verdict buildErrorVerdict(String tierName, Exception ex, List<Verdict> allTierVerdicts,
			List<String> tiersExecuted) {
		Judgment errorJudgment = Judgment.error("Final tier '" + tierName + "' threw exception: " + ex.getMessage(),
				ex);
		return Verdict.builder().aggregated(errorJudgment).subVerdicts(allTierVerdicts).build();
	}

	/**
	 * Create a new builder for CascadedJury.
	 * @return builder instance
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Builder for CascadedJury.
	 */
	public static class Builder {

		private final List<TierConfig> tiers = new ArrayList<>();

		/**
		 * Add a tier to the cascade.
		 * @param name human-readable tier name (e.g., "deterministic")
		 * @param jury the jury for this tier
		 * @param policy how this tier's result maps to stop/escalate
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
