/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.voting;

import java.util.List;
import java.util.Map;

import io.github.markpollack.judge.judgment.Judgment;
import io.github.markpollack.judge.judgment.JudgmentStatus;

/**
 * Eligible-opinion unanimity: PASS only when every eligible opinion passes; FAIL when any
 * eligible opinion fails. Exclusions, abstentions and errors follow the declared
 * policies; no eligible input is an abstention or an all-excluded result. This voting
 * rule does not assert complete AllOf Requirement coverage. The stable wire token is
 * {@code allMustPass}.
 */
public class AllEligiblePassStrategy implements VotingStrategy {

	private final ErrorHandling errorPolicy;

	private final ExclusionHandling notApplicablePolicy;

	/**
	 * Create a gate strategy with the default error policy.
	 */
	public AllEligiblePassStrategy() {
		this(ErrorHandling.PROPAGATE);
	}

	/**
	 * Create a gate strategy with a custom error policy.
	 * @param errorPolicy policy for handling errors
	 * @throws IllegalArgumentException if {@code errorPolicy} is null
	 */
	public AllEligiblePassStrategy(ErrorHandling errorPolicy) {
		this(errorPolicy, ExclusionHandling.REFUSE);
	}

	/**
	 * Create a gate strategy with custom error and not-applicable policies.
	 * @param errorPolicy policy for handling errors
	 * @param notApplicablePolicy policy for handling excluded judgments
	 * @throws IllegalArgumentException if either policy is null
	 * @since 0.17.0
	 */
	public AllEligiblePassStrategy(ErrorHandling errorPolicy, ExclusionHandling notApplicablePolicy) {
		if (errorPolicy == null) {
			throw new IllegalArgumentException("errorPolicy must not be null");
		}
		if (notApplicablePolicy == null) {
			throw new IllegalArgumentException("notApplicablePolicy must not be null");
		}
		this.errorPolicy = errorPolicy;
		this.notApplicablePolicy = notApplicablePolicy;
	}

	@Override
	public Judgment aggregate(List<Ballot> ballots) {
		List<Judgment> judgments = Ballots.judgments(ballots);
		AggregationPopulation population = AggregationPopulation.resolve(judgments, this.errorPolicy,
				this.notApplicablePolicy);

		if (population.hasPolicyExit()) {
			return population.policyExitAggregate(getName());
		}
		if (population.isEmpty()) {
			// Never PASS. An empty conjunction is vacuously true, and a gate that passes
			// because it lost its requirements is the failure this class exists to
			// prevent.
			return population.noResult(getName(), Map.of());
		}

		int passCount = 0;
		int failCount = 0;
		for (Judgment judgment : population.eligible()) {
			if (judgment.status() == JudgmentStatus.PASS) {
				passCount++;
			}
			else {
				failCount++;
			}
		}

		boolean passed = failCount == 0;
		int eligibleCount = population.eligible().size();
		Judgment aggregate = (passed ? Judgment.builder().pass() : Judgment.builder().fail())
			.reasoning(passed ? String.format("All %d applicable requirement(s) passed", eligibleCount)
					: String.format("%d of %d applicable requirement(s) failed", failCount, eligibleCount))
			.build();

		AggregationEvidence.Builder evidence = population.evidence(getName())
			.put(AggregationEvidence.PASS_COUNT, passCount)
			.put(AggregationEvidence.FAIL_COUNT, failCount);
		return AggregationEvidence.attach(aggregate, evidence.build());
	}

	@Override
	public String getName() {
		return "allMustPass";
	}

	/**
	 * Declares both policies. This strategy has no threshold.
	 * @return the declared description
	 * @since 0.17.0
	 */
	@Override
	public StrategyDescription describe() {
		return StrategyDescription.declared(this, this.errorPolicy, this.notApplicablePolicy, null, Map.of());
	}

	/** {@inheritDoc} */
	@Override
	public ExclusionHandling exclusionHandling() {
		return this.notApplicablePolicy;
	}

}
