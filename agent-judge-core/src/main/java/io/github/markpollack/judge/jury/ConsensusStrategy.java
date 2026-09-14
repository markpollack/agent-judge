/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury;

import java.util.List;
import java.util.Map;

import io.github.markpollack.judge.description.StrategyDescription;
import io.github.markpollack.judge.result.Judgment;
import io.github.markpollack.judge.result.JudgmentStatus;

/**
 * Consensus voting strategy: every applicable judge must reach the same verdict.
 *
 * <p>
 * A status-counting strategy. It reads {@link Judgment#status()}, the outcome of record.
 * </p>
 *
 * <p>
 * {@link JudgmentStatus#ABSTAIN} means the judge reached no decision, so it casts no vote and
 * is excluded from the population; consensus is computed over the judges that did decide. A
 * judge that could not settle its question must not be able to break unanimity by failing to
 * answer it.
 * </p>
 *
 * <p>
 * {@link JudgmentStatus#NOT_APPLICABLE} is a different claim — the question should not have
 * been asked here — and is governed by {@link NotApplicablePolicy}, which defaults to refusing
 * it. An exclusion is not silently absorbed into an abstention, because the two produce
 * different denominators.
 * </p>
 *
 * <table border="1">
 * <caption>Outcomes</caption>
 * <tr><th>Inputs</th><th>Result</th></tr>
 * <tr><td>all PASS</td><td>PASS</td></tr>
 * <tr><td>all FAIL</td><td>FAIL</td></tr>
 * <tr><td>PASS + ABSTAIN</td><td>PASS</td></tr>
 * <tr><td>FAIL + ABSTAIN</td><td>FAIL</td></tr>
 * <tr><td>PASS + FAIL</td><td>ABSTAIN — the applicable judges disagree</td></tr>
 * <tr><td>all ABSTAIN</td><td>ABSTAIN</td></tr>
 * <tr><td>any ERROR</td><td>per {@link ErrorPolicy}, default PROPAGATE</td></tr>
 * </table>
 *
 * <p>
 * Disagreement yields {@code ABSTAIN}: every applicable judge completed, but they reached
 * no collective finding, so consensus has nothing to report. This is an aggregation
 * conclusion, not a gate decision — whether a split panel is rejected or escalated belongs
 * to an explicit {@link TierPolicy} or downstream gate, which reads individual judgments.
 * A consumer that must fail closed on disagreement checks for {@code ABSTAIN} explicitly.
 * </p>
 *
 * <p>
 * Disagreement and unanimous failure therefore differ in status; disagreement and a
 * no-applicable-judge abstention share {@code ABSTAIN} and are told apart by the
 * reasoning and the {@link AggregationEvidence#PASS_COUNT}/{@link
 * AggregationEvidence#FAIL_COUNT} vote evidence, which a no-result aggregate does not
 * emit.
 * </p>
 *
 * <p>
 * Note this is independent of {@link CascadedJury} escalation, which inspects a tier's
 * <em>individual</em> judgments rather than its aggregate. A tier using
 * {@link TierPolicy#ACCEPT_ON_ALL_PASS} still escalates when any judge abstains.
 * </p>
 *
 * <p>
 * Example usage:
 * </p>
 * Executable examples are maintained in the Agent Judge Tutorial: https://github.com/markpollack/agent-judge-tutorial.
 *
 * @author Mark Pollack
 * @since 0.1.0
 */
public class ConsensusStrategy implements VotingStrategy {

	private final ErrorPolicy errorPolicy;

	private final NotApplicablePolicy notApplicablePolicy;

	/**
	 * Create a consensus strategy with the default error policy.
	 */
	public ConsensusStrategy() {
		this(ErrorPolicy.PROPAGATE);
	}

	/**
	 * Create a consensus strategy with a custom error policy.
	 * @param errorPolicy policy for handling errors
	 * @throws IllegalArgumentException if {@code errorPolicy} is null
	 */
	public ConsensusStrategy(ErrorPolicy errorPolicy) {
		this(errorPolicy, NotApplicablePolicy.REFUSE);
	}

	/**
	 * Create a consensus strategy with custom error and not-applicable policies.
	 * @param errorPolicy policy for handling errors
	 * @param notApplicablePolicy policy for handling excluded judgments
	 * @throws IllegalArgumentException if either policy is null
	 * @since 0.17.0
	 */
	public ConsensusStrategy(ErrorPolicy errorPolicy, NotApplicablePolicy notApplicablePolicy) {
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
	public Judgment aggregate(List<Judgment> judgments, Map<String, Double> weights) {
		AggregationPopulation population = AggregationPopulation.resolve(judgments, this.errorPolicy,
				this.notApplicablePolicy);

		if (population.hasPolicyExit()) {
			return population.policyExitAggregate(getName());
		}
		if (population.isEmpty()) {
			return population.noResult(getName(), Map.of());
		}

		int eligibleCount = population.eligible().size();
		int passCount = (int) population.eligible().stream().filter(j -> j.status() == JudgmentStatus.PASS).count();
		int failCount = (int) population.eligible().stream().filter(j -> j.status() == JudgmentStatus.FAIL).count();

		JudgmentStatus status;
		String reasoning;
		if (passCount == eligibleCount) {
			status = JudgmentStatus.PASS;
			reasoning = String.format("Unanimous consensus: all %d applicable judge(s) passed", eligibleCount);
		}
		else if (failCount == eligibleCount) {
			status = JudgmentStatus.FAIL;
			reasoning = String.format("Unanimous consensus: all %d applicable judge(s) failed", eligibleCount);
		}
		else {
			// The applicable judges disagree, so there is no collective finding to report.
			// Rejecting or escalating that split is a gate decision, not this one.
			status = JudgmentStatus.ABSTAIN;
			reasoning = String.format("No consensus: %d passed, %d failed among %d applicable judge(s)", passCount,
					failCount, eligibleCount);
		}

		Judgment aggregate = switch (status) {
			case PASS -> Judgment.builder().pass().reasoning(reasoning).build();
			case FAIL -> Judgment.builder().fail().reasoning(reasoning).build();
			case ABSTAIN -> Judgment.builder().abstain().reasoning(reasoning).build();
			case NOT_APPLICABLE, ERROR ->
				throw new IllegalStateException("Consensus produced an unexpected status: " + status);
		};
		return AggregationEvidence.attach(aggregate, population.evidence(getName())
				.put(AggregationEvidence.PASS_COUNT, passCount)
				.put(AggregationEvidence.FAIL_COUNT, failCount)
				.build());
	}

	@Override
	public String getName() {
		return "consensus";
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
	public NotApplicablePolicy notApplicablePolicy() {
		return this.notApplicablePolicy;
	}


}
