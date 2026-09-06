/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury;

import java.util.List;
import java.util.Map;

import io.github.markpollack.judge.result.Judgment;
import io.github.markpollack.judge.result.JudgmentStatus;

/**
 * Gate strategy: every applicable judge must return {@code PASS}, and a mixed jury is a
 * rejection.
 *
 * <p>
 * A status-counting strategy. It is the conjunction over <em>outcomes</em>, and it is the
 * companion to {@link ConjunctiveStrategy}, which is the conjunction over <em>scores</em>.
 * Reach for this one when the judges express Boolean requirements — a definition of done —
 * and for {@code ConjunctiveStrategy} when they measure a rubric.
 * </p>
 *
 * <h2>Why this is not {@link ConsensusStrategy}</h2>
 * <p>
 * The two agree everywhere except the case that matters to a gate:
 * </p>
 * <table border="1">
 * <caption>Applicable judgments to aggregate</caption>
 * <tr><th>Input</th><th>{@code ConsensusStrategy}</th><th>This strategy</th></tr>
 * <tr><td>all PASS</td><td>PASS</td><td>PASS</td></tr>
 * <tr><td>all FAIL</td><td>FAIL</td><td>FAIL</td></tr>
 * <tr><td><b>mixed PASS and FAIL</b></td><td><b>ABSTAIN</b> — the judges disagree</td><td><b>FAIL</b> — a requirement was not met</td></tr>
 * </table>
 * <p>
 * Consensus reports a collective fact and deliberately leaves rejection to a gate; its own
 * implementation says so. This is that gate. A definition of done has no notion of its
 * requirements "disagreeing": one of them was not satisfied, so the work is not done.
 * </p>
 *
 * <h2>Why this is not a threshold over {@code effectiveScore()}</h2>
 * <p>
 * A numeric strategy applied to Boolean judgments reaches them through
 * {@link Judgment#effectiveScore()}, which derives {@code 1.0} for {@code PASS} and
 * {@code 0.0} for {@code FAIL}. That works arithmetically and is the wrong expression: it
 * routes a status through a numeric channel and reintroduces the duplicate-representation
 * this library's result type exists to prevent. A judge that stored no score should not be
 * aggregated as though it had one.
 * </p>
 *
 * <h2>The empty case is load-bearing</h2>
 * <p>
 * With nothing eligible the result is {@code ABSTAIN}, never {@code PASS}. A hand-written
 * gate is usually {@code judgments.stream().allMatch(...)}, and {@code allMatch} over an
 * empty stream is {@code true} — so a definition of done that lost its requirements reports
 * that everything is done. That is the defect this strategy exists not to have, and the
 * reason the emptiness check is not left to the caller.
 * </p>
 *
 * <p>
 * Abstentions leave the population, because a judge that does not apply is not a
 * requirement this subject has to meet. Errors are governed by {@link ErrorPolicy} (default
 * {@code PROPAGATE}), so a requirement that could not be evaluated does not quietly pass.
 * </p>
 *
 * @author Mark Pollack
 * @since 0.16.0
 * @see ConjunctiveStrategy
 * @see ConsensusStrategy
 */
public class AllMustPassStrategy implements VotingStrategy {

	private final ErrorPolicy errorPolicy;

	/**
	 * Create a gate strategy with the default error policy.
	 */
	public AllMustPassStrategy() {
		this(ErrorPolicy.PROPAGATE);
	}

	/**
	 * Create a gate strategy with a custom error policy.
	 * @param errorPolicy policy for handling errors
	 */
	public AllMustPassStrategy(ErrorPolicy errorPolicy) {
		this.errorPolicy = errorPolicy;
	}

	@Override
	public Judgment aggregate(List<Judgment> judgments, Map<String, Double> weights) {
		AggregationPopulation population = AggregationPopulation.resolve(judgments, this.errorPolicy);

		if (population.propagateError()) {
			return population.propagatedError(getName());
		}
		if (population.isEmpty()) {
			// Never PASS. An empty conjunction is vacuously true, and a gate that passes
			// because it lost its requirements is the failure this class exists to prevent.
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
			.reasoning(passed
					? String.format("All %d applicable requirement(s) passed", eligibleCount)
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

}
