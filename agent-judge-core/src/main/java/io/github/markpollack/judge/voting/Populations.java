/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.voting;

import java.util.List;
import java.util.Map;

import io.github.markpollack.judge.judgment.Judgment;
import io.github.markpollack.judge.judgment.JudgmentStatus;
import org.jspecify.annotations.Nullable;

/**
 * The steps every built-in strategy takes before and after its own reduction.
 */
final class Populations {

	private Populations() {
	}

	/**
	 * Resolve the eligible population of these ballots under the strategy's policies.
	 * @param ballots the seats' ballots
	 * @param errorPolicy how errors count
	 * @param notApplicablePolicy how exclusions count
	 * @return the resolved population
	 */
	static AggregationPopulation of(List<Ballot> ballots, ErrorHandling errorPolicy,
			ExclusionHandling notApplicablePolicy) {
		return AggregationPopulation.resolve(Ballots.judgments(ballots), errorPolicy, notApplicablePolicy);
	}

	/**
	 * Refuse a missing policy, error policy first.
	 * @param errorPolicy how errors count
	 * @param notApplicablePolicy how exclusions count
	 */
	static void requirePolicies(ErrorHandling errorPolicy, ExclusionHandling notApplicablePolicy) {
		if (errorPolicy == null) {
			throw new IllegalArgumentException("errorPolicy must not be null");
		}
		if (notApplicablePolicy == null) {
			throw new IllegalArgumentException("notApplicablePolicy must not be null");
		}
	}

	/**
	 * The aggregate when the strategy has nothing of its own to decide: the policy exit,
	 * or the no-result abstention over an empty population. An empty conjunction is
	 * vacuously true, so an empty population is never PASS.
	 * @param population the resolved population
	 * @param strategy the strategy token
	 * @return that aggregate, or null when the strategy decides
	 */
	static @Nullable Judgment exit(AggregationPopulation population, String strategy) {
		if (population.hasPolicyExit()) {
			return population.policyExitAggregate(strategy);
		}
		if (population.isEmpty()) {
			return population.noResult(strategy, Map.of());
		}
		return null;
	}

	/**
	 * How many eligible judgments have this status.
	 * @param population the resolved population
	 * @param status PASS or FAIL
	 * @return the count
	 */
	static int count(AggregationPopulation population, JudgmentStatus status) {
		return (int) population.eligible().stream().filter(j -> j.status() == status).count();
	}

	/**
	 * Attach the population's evidence with its pass and fail counts.
	 * @param population the resolved population
	 * @param strategy the strategy token
	 * @param aggregate the strategy's decision
	 * @return the aggregate with its evidence
	 */
	static Judgment withCounts(AggregationPopulation population, String strategy, Judgment aggregate) {
		return AggregationEvidence.attach(aggregate,
				population.evidence(strategy)
					.put(AggregationEvidence.PASS_COUNT, count(population, JudgmentStatus.PASS))
					.put(AggregationEvidence.FAIL_COUNT, count(population, JudgmentStatus.FAIL))
					.build());
	}

	/**
	 * A score compared with a pass threshold, with the threshold in the evidence.
	 * @param population the resolved population
	 * @param strategy the strategy token
	 * @param measure what the score is, such as "Average"
	 * @param score the reduced score
	 * @param threshold the pass threshold
	 * @return the aggregate
	 */
	static Judgment thresholded(AggregationPopulation population, String strategy, String measure, double score,
			double threshold) {
		boolean passed = score >= threshold;
		Judgment aggregate = (passed ? Judgment.builder().pass() : Judgment.builder().fail()).score(score)
			.reasoning(String.format("%s score: %.2f across %d applicable judge(s) (threshold: %.2f, result: %s)",
					measure, score, population.eligible().size(), threshold, passed ? "pass" : "fail"))
			.build();
		return AggregationEvidence.attach(aggregate,
				population.evidence(strategy).put(AggregationEvidence.THRESHOLD, threshold).build());
	}

}
