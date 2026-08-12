/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.github.markpollack.judge.result.Judgment;
import io.github.markpollack.judge.result.JudgmentStatus;

/**
 * The set of judgments a strategy actually reduces over, after abstentions and the error
 * policy have been applied.
 *
 * <p>
 * Every voting strategy resolves its population the same way, so the rules live here once
 * rather than in five private copies — which is how they previously drifted apart.
 * </p>
 *
 * <p>
 * Exactly three mechanisms remove a judgment from the population:
 * </p>
 * <ul>
 * <li>{@link JudgmentStatus#ABSTAIN} — the judge does not apply, so it casts no vote;</li>
 * <li>{@code ERROR} under {@link ErrorPolicy#TREAT_AS_ABSTAIN} — converted to a non-vote;</li>
 * <li>{@code ERROR} under {@link ErrorPolicy#IGNORE} — removed entirely, by filtering.</li>
 * </ul>
 * <p>
 * {@link ErrorPolicy#TREAT_AS_FAIL} keeps the judgment <em>in</em> the population as a
 * FAIL, and {@link ErrorPolicy#PROPAGATE} short-circuits aggregation rather than removing
 * anything.
 * </p>
 *
 * @author Mark Pollack
 * @since 0.14.0
 */
record AggregationPopulation(List<Judgment> eligible, List<Integer> eligibleIndices, int inputCount,
		int explicitAbstainCount, int errorCount, ErrorPolicy errorPolicy, boolean propagateError) {

	/**
	 * Resolve the population for a set of submitted judgments.
	 * <p>
	 * {@code eligibleIndices} runs parallel to {@code eligible}, giving each surviving
	 * judgment's position in the submitted list. Weighted strategies need it because
	 * weights are keyed by original index, and filtering would otherwise misalign them.
	 * </p>
	 * @param judgments the submitted judgments; must be non-empty
	 * @param errorPolicy how errored judgments are handled
	 * @return the resolved population
	 */
	static AggregationPopulation resolve(List<Judgment> judgments, ErrorPolicy errorPolicy) {
		if (judgments == null || judgments.isEmpty()) {
			throw new IllegalArgumentException("Cannot aggregate empty judgment list");
		}

		int explicitAbstainCount = 0;
		int errorCount = 0;
		List<Judgment> eligible = new ArrayList<>();
		List<Integer> eligibleIndices = new ArrayList<>();

		for (int index = 0; index < judgments.size(); index++) {
			Judgment judgment = judgments.get(index);
			JudgmentStatus status = judgment.status();

			if (status == JudgmentStatus.ABSTAIN) {
				explicitAbstainCount++;
				continue;
			}

			if (status == JudgmentStatus.ERROR) {
				errorCount++;
				if (errorPolicy == ErrorPolicy.TREAT_AS_FAIL) {
					// Participates as a FAIL, preserving why it errored.
					eligible.add(Judgment.fail("Error treated as failure: " + judgment.reasoning()));
					eligibleIndices.add(index);
				}
				// TREAT_AS_ABSTAIN and IGNORE both become non-votes; the evidence, not the
				// population, is what tells them apart.
				continue;
			}

			eligible.add(judgment);
			eligibleIndices.add(index);
		}

		// PROPAGATE decides the aggregate after the complete population has been
		// inspected. Returning at the first error made evidence such as
		// explicitAbstainCount depend on input order.
		if (errorPolicy == ErrorPolicy.PROPAGATE && errorCount > 0) {
			return new AggregationPopulation(List.of(), List.of(), judgments.size(), explicitAbstainCount, errorCount,
					errorPolicy, true);
		}

		return new AggregationPopulation(List.copyOf(eligible), List.copyOf(eligibleIndices), judgments.size(),
				explicitAbstainCount, errorCount, errorPolicy, false);
	}

	boolean isEmpty() {
		return this.eligible.isEmpty();
	}

	/**
	 * Begin an evidence block populated with the universal keys.
	 * @param strategyToken the strategy's stable identifier
	 * @return an evidence builder for the strategy to extend
	 */
	AggregationEvidence.Builder evidence(String strategyToken) {
		return AggregationEvidence.builder()
			.put(AggregationEvidence.STRATEGY, strategyToken)
			.put(AggregationEvidence.ERROR_POLICY, this.errorPolicy.token())
			.put(AggregationEvidence.INPUT_COUNT, this.inputCount)
			.put(AggregationEvidence.ELIGIBLE_COUNT, this.eligible.size())
			.put(AggregationEvidence.EXPLICIT_ABSTAIN_COUNT, this.explicitAbstainCount)
			.put(AggregationEvidence.ERROR_COUNT, this.errorCount)
			.put(AggregationEvidence.IGNORED_ERROR_COUNT, countFor(ErrorPolicy.IGNORE))
			.put(AggregationEvidence.ERRORS_TREATED_AS_ABSTAIN_COUNT, countFor(ErrorPolicy.TREAT_AS_ABSTAIN))
			.put(AggregationEvidence.ERRORS_TREATED_AS_FAIL_COUNT, countFor(ErrorPolicy.TREAT_AS_FAIL));
	}

	private int countFor(ErrorPolicy policy) {
		return this.errorPolicy == policy ? this.errorCount : 0;
	}

	/**
	 * Build the ERROR aggregate for {@link ErrorPolicy#PROPAGATE}.
	 * @param strategyToken the strategy's stable identifier
	 * @return an error judgment carrying the evidence
	 */
	Judgment propagatedError(String strategyToken) {
		Judgment aggregate = Judgment.builder().error()
			.reasoning(String.format("%d of %d judgments errored and the error policy is propagate", this.errorCount,
					this.inputCount))
			.build();
		return AggregationEvidence.attach(aggregate, evidence(strategyToken).build());
	}

	/**
	 * Build the no-result aggregate for the case where nothing was eligible.
	 * @param strategyToken the strategy's stable identifier
	 * @param extraEvidence additional strategy-specific evidence, may be empty
	 * @return an abstaining judgment carrying the evidence
	 */
	Judgment noResult(String strategyToken, Map<String, Object> extraEvidence) {
		AggregationEvidence.Builder evidence = evidence(strategyToken);
		extraEvidence.forEach((key, value) -> {
			if (value instanceof Integer i) {
				evidence.put(key, (int) i);
			}
			else if (value instanceof Double d) {
				evidence.put(key, (double) d);
			}
			else {
				evidence.put(key, String.valueOf(value));
			}
		});

		Judgment aggregate = Judgment.builder().abstain().reasoning(noResultReasoning()).build();
		return AggregationEvidence.attach(aggregate, evidence.build());
	}

	private String noResultReasoning() {
		// IGNORE and TREAT_AS_ABSTAIN can both empty the population; say which one did,
		// so the two policies stay distinguishable in the reasoning as well as the counts.
		if (this.errorCount > 0 && this.errorPolicy == ErrorPolicy.IGNORE) {
			return String.format("No eligible judgments; %d error(s) ignored", this.errorCount);
		}
		if (this.errorCount > 0 && this.errorPolicy == ErrorPolicy.TREAT_AS_ABSTAIN) {
			return String.format("All %d judgment(s) abstained because of evaluation errors", this.errorCount);
		}
		if (this.explicitAbstainCount == this.inputCount) {
			return String.format("All %d judge(s) abstained", this.inputCount);
		}
		return String.format("No eligible judgments among %d submitted", this.inputCount);
	}

}
