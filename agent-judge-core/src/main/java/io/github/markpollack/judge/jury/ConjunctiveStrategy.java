/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury;

import java.util.List;
import java.util.Map;

import io.github.markpollack.judge.description.StrategyDescription;
import io.github.markpollack.judge.result.Judgment;

/**
 * Conjunctive voting strategy: every applicable judge must clear the bar.
 *
 * <p>
 * A numeric strategy, and the <em>non-compensatory</em> counterpart to
 * {@link AverageVotingStrategy}. It reduces over {@link Judgment#effectiveScore()} with
 * {@code min} rather than a mean, so a single low assessment decides the aggregate and
 * cannot be offset by strong siblings.
 * </p>
 *
 * <h2>Compensatory and non-compensatory scoring</h2>
 * <p>
 * The distinction is the established one from educational measurement. A
 * <em>compensatory</em> rule lets a strong criterion pay for a weak one; a
 * <em>conjunctive</em> (non-compensatory) rule requires each criterion to meet the
 * standard on its own. Compensatory scoring is the common default, which is exactly why
 * this strategy is worth naming: averaging is what you get when nobody chooses.
 * </p>
 * <p>
 * The failure the mean permits, stated concretely — seven criteria scored out of three,
 * one of them missed entirely:
 * </p>
 * <pre>
 * {3,3,3,3,3,3,0}  mean = 18/21 = 0.857  passes any mean bar below 0.857
 *                  min  =  0/3  = 0.000  fails every bar above zero
 * </pre>
 * <p>
 * If a rubric states a per-criterion acceptability line — "raise a concern for any score
 * below 2" — then a mean contradicts the rubric that produced its inputs. Use this
 * strategy where that line exists, and {@code AverageVotingStrategy} where the criteria
 * genuinely trade off against one another. A hybrid — conjunctive over critical criteria,
 * compensatory over the rest — is expressed by composing two juries rather than by
 * configuring one strategy.
 * </p>
 *
 * <h2>The threshold is required</h2>
 * <p>
 * There is no no-argument constructor and no default bar. A conjunctive rule exists to
 * enforce an acceptability line, so the line is the caller's to state, the same way
 * {@link Judgment#scored(double)} refuses to produce an outcome until the caller supplies
 * a threshold. Deriving that number from the rubric's own level semantics — rather than
 * from the distribution of scores it must judge — is the caller's responsibility.
 * </p>
 *
 * <h2>Population and evidence</h2>
 * <p>
 * Abstentions leave the population entirely, because "no assessment" is not the assessment
 * zero; errors are governed by {@link ErrorPolicy} (default {@code PROPAGATE}) and exclusions
 * by {@link NotApplicablePolicy} (default {@code REFUSE}). If nothing is eligible the result
 * is {@code ABSTAIN} rather than a manufactured failing score — an empty conjunction is
 * vacuously true, and reporting that as {@code PASS} is precisely the "passed over nothing"
 * defect this strategy is meant not to have.
 * </p>
 * <p>
 * Beyond the universal keys, the evidence records
 * {@link AggregationEvidence#THRESHOLD} and
 * {@link AggregationEvidence#BINDING_ELIGIBLE_INDEX} — the position, among the submitted
 * judgments, of the judgment that produced the minimum. The binding judgment is the
 * diagnosis: it names which contributor held the aggregate down, which a mean cannot
 * report.
 * </p>
 *
 * @author Mark Pollack
 * @since 0.16.0
 * @see AverageVotingStrategy
 * @see AggregationEvidence#BINDING_ELIGIBLE_INDEX
 */
public class ConjunctiveStrategy implements VotingStrategy {

	private final double threshold;

	private final ErrorPolicy errorPolicy;

	private final NotApplicablePolicy notApplicablePolicy;

	/**
	 * Create a conjunctive strategy with the default error policy.
	 * @param threshold the normalized bar every applicable judgment must reach, in
	 * {@code [0.0, 1.0]}
	 * @throws IllegalArgumentException if the threshold is not a finite value in
	 * {@code [0.0, 1.0]}
	 */
	public ConjunctiveStrategy(double threshold) {
		this(threshold, ErrorPolicy.PROPAGATE);
	}

	/**
	 * Create a conjunctive strategy with a custom error policy.
	 * @param threshold the normalized bar every applicable judgment must reach, in
	 * {@code [0.0, 1.0]}
	 * @param errorPolicy policy for handling errors
	 * @throws IllegalArgumentException if the threshold is not a finite value in
	 * {@code [0.0, 1.0]}, or if {@code errorPolicy} is null
	 */
	public ConjunctiveStrategy(double threshold, ErrorPolicy errorPolicy) {
		this(threshold, errorPolicy, NotApplicablePolicy.REFUSE);
	}

	/**
	 * Create a conjunctive strategy with custom error and not-applicable policies.
	 * @param threshold the normalized bar every applicable judgment must reach, in
	 * {@code [0.0, 1.0]}
	 * @param errorPolicy policy for handling errors
	 * @param notApplicablePolicy policy for handling excluded judgments
	 * @throws IllegalArgumentException if the threshold is not a finite value in
	 * {@code [0.0, 1.0]}, or if either policy is null
	 * @since 0.17.0
	 */
	public ConjunctiveStrategy(double threshold, ErrorPolicy errorPolicy, NotApplicablePolicy notApplicablePolicy) {
		if (notApplicablePolicy == null) {
			throw new IllegalArgumentException("notApplicablePolicy must not be null");
		}
		this.notApplicablePolicy = notApplicablePolicy;
		if (!Double.isFinite(threshold)) {
			throw new IllegalArgumentException("threshold must be finite, but was " + threshold);
		}
		if (threshold < 0.0 || threshold > 1.0) {
			throw new IllegalArgumentException("threshold must be between 0.0 and 1.0, but was " + threshold);
		}
		if (errorPolicy == null) {
			throw new IllegalArgumentException("errorPolicy must not be null");
		}
		this.threshold = threshold;
		this.errorPolicy = errorPolicy;
	}

	/**
	 * The bar every applicable judgment must reach.
	 * @return the normalized threshold
	 */
	public double getThreshold() {
		return this.threshold;
	}

	@Override
	public Judgment aggregate(List<Judgment> judgments, Map<String, Double> weights) {
		AggregationPopulation population = AggregationPopulation.resolve(judgments, this.errorPolicy,
				this.notApplicablePolicy);

		if (population.hasPolicyExit()) {
			return population.policyExitAggregate(getName());
		}
		if (population.isEmpty()) {
			// An empty conjunction is vacuously true. Reporting that as PASS is the
			// "a check that passed on nothing" defect, so it stays a no-result ABSTAIN.
			return population.noResult(getName(), Map.of());
		}

		// Every eligible judgment is PASS or FAIL, so effectiveScore is always present.
		List<Judgment> eligible = population.eligible();
		int bindingPosition = 0;
		double minimum = eligible.get(0).effectiveScore().orElseThrow();
		for (int i = 1; i < eligible.size(); i++) {
			double score = eligible.get(i).effectiveScore().orElseThrow();
			if (score < minimum) {
				minimum = score;
				bindingPosition = i;
			}
		}
		// Report the binding judgment's position among the SUBMITTED judgments, not among
		// the survivors: the caller indexes its judges by the former.
		int bindingIndex = population.eligibleIndices().get(bindingPosition);

		boolean passed = minimum >= this.threshold;
		Judgment aggregate = (passed ? Judgment.builder().pass() : Judgment.builder().fail())
			.score(minimum)
			.reasoning(String.format(
					"Minimum score: %.2f across %d applicable judge(s) (threshold: %.2f, result: %s); "
							+ "binding judgment at index %d",
					minimum, eligible.size(), this.threshold, passed ? "pass" : "fail", bindingIndex))
			.build();

		AggregationEvidence.Builder evidence = population.evidence(getName())
			.put(AggregationEvidence.THRESHOLD, this.threshold)
			.put(AggregationEvidence.BINDING_ELIGIBLE_INDEX, bindingIndex);
		return AggregationEvidence.attach(aggregate, evidence.build());
	}

	@Override
	public String getName() {
		return "conjunctive";
	}

	/**
	 * Declares the error policy and the threshold.
	 * @return the declared description
	 * @since 0.17.0
	 */
	@Override
	public StrategyDescription describe() {
		return StrategyDescription.declared(this, this.errorPolicy, this.notApplicablePolicy, this.threshold,
				Map.of());
	}

	/** {@inheritDoc} */
	@Override
	public NotApplicablePolicy notApplicablePolicy() {
		return this.notApplicablePolicy;
	}


}
