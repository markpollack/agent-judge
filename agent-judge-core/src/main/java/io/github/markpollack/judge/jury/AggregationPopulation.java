/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import io.github.markpollack.judge.result.Judgment;
import io.github.markpollack.judge.result.JudgmentReasonCode;
import io.github.markpollack.judge.result.JudgmentStatus;

/**
 * The set of judgments a strategy actually reduces over, after exclusions, abstentions and the
 * two policies have been applied.
 *
 * <p>
 * Every voting strategy resolves its population the same way, so the rules live here once
 * rather than in seven private copies — which is how they previously drifted apart.
 * </p>
 *
 * <h2>One complete scan</h2>
 * <p>
 * The submitted judgments are counted <em>as submitted</em>, in one pass, before any policy is
 * applied. That ordering is the whole point: a count taken after filtering can only describe
 * the survivors, and the question a reader asks of a rubric — how much of it applied, how much
 * errored — is a question about what arrived.
 * </p>
 * <ol>
 * <li><b>Count the originals.</b> {@code inputCount}, {@code explicitAbstainCount},
 * {@code notApplicableCount}, {@code errorCount}, and {@code errorCodeCounts} flattened through
 * propagating wrappers.</li>
 * <li><b>Policy exits</b>, in precedence, each building an ERROR aggregate and reducing
 * nothing. Every treatment counter is then zero, because no treatment was performed.</li>
 * <li><b>Contributions</b> at the original indices, with counters recording the treatment
 * actually performed.</li>
 * <li><b>Reduce.</b> A failing contribution is an input to the reduction, not a forced
 * aggregate FAIL, and the original judgment never receives a score.</li>
 * <li><b>No eligible contributions:</b> an all-excluded population under
 * {@link NotApplicablePolicy#EXCLUDE} is {@code NOT_APPLICABLE}; anything else is
 * {@code ABSTAIN}.</li>
 * </ol>
 *
 * <h2>Origin decides what an error may become</h2>
 * <p>
 * An error that came from a configured judge is the error policy's business. An error that
 * came from the library's own machinery is not: converting a broken reduction into a FAIL
 * would charge the library's failure to the subject, and a rejection that nobody can
 * distinguish from a real one is worse than no rejection at all. So a machinery-origin error
 * is never a failing contribution, <em>even under</em> {@link ErrorPolicy#TREAT_AS_FAIL}; that
 * combination propagates instead.
 * </p>
 *
 * @author Mark Pollack
 * @since 0.14.0
 */
record AggregationPopulation(List<Judgment> eligible, List<Integer> eligibleIndices, int inputCount,
		int explicitAbstainCount, int notApplicableCount, int errorCount,
		Map<JudgmentReasonCode, Integer> errorCodeCounts, int ignoredErrorCount, int errorsTreatedAsAbstainCount,
		int errorsTreatedAsFailCount, int notApplicableTreatedAsFailCount, ErrorPolicy errorPolicy,
		NotApplicablePolicy notApplicablePolicy, @Nullable PolicyExit policyExit) {

	/**
	 * A policy that decided the aggregate before anything was reduced.
	 *
	 * @param code the instrument code the aggregate carries
	 * @param reasoning the aggregate's explanation
	 */
	record PolicyExit(JudgmentReasonCode code, String reasoning) {
	}

	/**
	 * Resolve the population for a set of submitted judgments.
	 * <p>
	 * {@code eligibleIndices} runs parallel to {@code eligible}, giving each surviving
	 * contribution's position in the submitted list. Weighted strategies need it because
	 * weights are keyed by original index, and filtering would otherwise misalign them.
	 * </p>
	 * @param judgments the submitted judgments; must be non-empty
	 * @param errorPolicy how errored judgments are handled
	 * @param notApplicablePolicy how excluded judgments are handled
	 * @return the resolved population
	 */
	static AggregationPopulation resolve(List<Judgment> judgments, ErrorPolicy errorPolicy,
			NotApplicablePolicy notApplicablePolicy) {
		if (judgments == null || judgments.isEmpty()) {
			throw new IllegalArgumentException("Cannot aggregate empty judgment list");
		}
		Objects.requireNonNull(errorPolicy, "errorPolicy must not be null");
		Objects.requireNonNull(notApplicablePolicy, "notApplicablePolicy must not be null");

		// ---- 1. Count the originals ----
		int explicitAbstainCount = 0;
		int notApplicableCount = 0;
		int errorCount = 0;
		int machineryOriginErrorCount = 0;
		Map<JudgmentReasonCode, Integer> errorCodeCounts = new EnumMap<>(JudgmentReasonCode.class);

		for (Judgment judgment : judgments) {
			JudgmentStatus status = judgment.status();
			if (status == JudgmentStatus.PASS || status == JudgmentStatus.FAIL) {
				continue;
			}
			if (status == JudgmentStatus.ABSTAIN) {
				explicitAbstainCount++;
			}
			else if (status == JudgmentStatus.NOT_APPLICABLE) {
				notApplicableCount++;
			}
			else if (status == JudgmentStatus.ERROR) {
				errorCount++;
				if (hasMachineryOrigin(judgment)) {
					machineryOriginErrorCount++;
				}
				flattenOrigin(judgment, errorCodeCounts);
			}
			else {
				// Unreachable today. It exists so that adding a status is a loud failure the
				// containment boundary records, rather than a judgment silently treated as a
				// vote by whichever branch happened to catch it.
				throw new IllegalStateException("Unrecognized judgment status: " + status);
			}
		}

		// ---- 2. Policy exits, in precedence ----
		PolicyExit exit = policyExitFor(judgments.size(), notApplicableCount, errorCount, machineryOriginErrorCount,
				errorPolicy, notApplicablePolicy);
		if (exit != null) {
			return new AggregationPopulation(List.of(), List.of(), judgments.size(), explicitAbstainCount,
					notApplicableCount, errorCount, freeze(errorCodeCounts), 0, 0, 0, 0, errorPolicy,
					notApplicablePolicy, exit);
		}

		// ---- 3. Contributions, at the original indices ----
		List<Judgment> eligible = new ArrayList<>();
		List<Integer> eligibleIndices = new ArrayList<>();
		int ignoredErrorCount = 0;
		int errorsTreatedAsAbstainCount = 0;
		int errorsTreatedAsFailCount = 0;
		int notApplicableTreatedAsFailCount = 0;

		for (int index = 0; index < judgments.size(); index++) {
			Judgment judgment = judgments.get(index);
			switch (judgment.status()) {
				case PASS, FAIL -> {
					eligible.add(judgment);
					eligibleIndices.add(index);
				}
				case ABSTAIN -> {
					// A judge that reached no decision casts no vote.
				}
				case NOT_APPLICABLE -> {
					if (notApplicablePolicy == NotApplicablePolicy.TREAT_AS_FAIL) {
						// The contribution fails; the original judgment is untouched and still
						// carries no score.
						eligible.add(Judgment.fail("Not applicable treated as failure: " + judgment.reasoning()));
						eligibleIndices.add(index);
						notApplicableTreatedAsFailCount++;
					}
				}
				case ERROR -> {
					switch (errorPolicy) {
						case TREAT_AS_FAIL -> {
							// Step 2 already exited if any error was machinery-origin, so every
							// error reaching here is a judge's own and the policy governs it.
							eligible.add(Judgment.fail("Error treated as failure: " + judgment.reasoning()));
							eligibleIndices.add(index);
							errorsTreatedAsFailCount++;
						}
						case TREAT_AS_ABSTAIN -> errorsTreatedAsAbstainCount++;
						case IGNORE -> ignoredErrorCount++;
						case PROPAGATE -> throw new IllegalStateException("PROPAGATE is resolved as a policy exit");
					}
				}
			}
		}

		return new AggregationPopulation(List.copyOf(eligible), List.copyOf(eligibleIndices), judgments.size(),
				explicitAbstainCount, notApplicableCount, errorCount, freeze(errorCodeCounts), ignoredErrorCount,
				errorsTreatedAsAbstainCount, errorsTreatedAsFailCount, notApplicableTreatedAsFailCount, errorPolicy,
				notApplicablePolicy, null);
	}

	/**
	 * The policy that decides the aggregate before anything is reduced, or null when the
	 * reduction proceeds.
	 * @param inputCount how many judgments were submitted
	 * @param notApplicableCount how many arrived excluded
	 * @param errorCount how many arrived errored
	 * @param machineryOriginErrorCount how many of those came from jury machinery
	 * @param errorPolicy the configured error policy
	 * @param notApplicablePolicy the configured exclusion policy
	 * @return the policy exit, or null
	 */
	private static @Nullable PolicyExit policyExitFor(int inputCount, int notApplicableCount, int errorCount,
			int machineryOriginErrorCount, ErrorPolicy errorPolicy, NotApplicablePolicy notApplicablePolicy) {
		if (notApplicablePolicy == NotApplicablePolicy.REFUSE && notApplicableCount > 0) {
			return new PolicyExit(JudgmentReasonCode.NOT_APPLICABLE_REFUSED,
					String.format("%d of %d judgment(s) were not applicable and the not-applicable policy is refuse",
							notApplicableCount, inputCount));
		}
		if (errorPolicy == ErrorPolicy.PROPAGATE && errorCount > 0) {
			return new PolicyExit(JudgmentReasonCode.ERRORS_PROPAGATED, String
				.format("%d of %d judgments errored and the error policy is propagate", errorCount, inputCount));
		}
		if (errorPolicy == ErrorPolicy.TREAT_AS_FAIL && machineryOriginErrorCount > 0) {
			return new PolicyExit(JudgmentReasonCode.ERRORS_PROPAGATED, String.format(
					"%d of %d judgments errored, and %d originated in jury machinery rather than in a judge; "
							+ "a machinery failure is never scored, so it is propagated rather than counted "
							+ "against the subject",
					errorCount, inputCount, machineryOriginErrorCount));
		}
		return null;
	}

	/**
	 * Whether an errored judgment's cause lies in jury machinery rather than in a judge.
	 * <p>
	 * True when the error's own code is a machinery code, and also when it propagates one: a
	 * wrapper stands for the causes beneath it, so a broken reduction wrapped in a propagated
	 * error is still a broken reduction. A wrapper whose origin is missing or unreadable — which
	 * only historical data can be — counts as machinery too, because the one thing that must
	 * never happen is charging an unattributable failure to the subject.
	 * </p>
	 * @param judgment an errored judgment
	 * @return true when the error came from machinery
	 */
	static boolean hasMachineryOrigin(Judgment judgment) {
		JudgmentReasonCode code = judgment.reasonCode();
		if (code == null) {
			return true;
		}
		if (code.originFamily() == JudgmentReasonCode.OriginFamily.MACHINERY) {
			return true;
		}
		if (code != JudgmentReasonCode.ERRORS_PROPAGATED) {
			return false;
		}
		Map<JudgmentReasonCode, Integer> origin = new EnumMap<>(JudgmentReasonCode.class);
		flattenOrigin(judgment, origin);
		if (origin.isEmpty()) {
			return true;
		}
		return origin.keySet()
			.stream()
			.anyMatch(cause -> cause.originFamily() == JudgmentReasonCode.OriginFamily.MACHINERY);
	}

	/**
	 * Add an errored judgment's terminal causes to a running total.
	 * <p>
	 * A propagating wrapper contributes the origins it carries, never itself: counting the
	 * wrapper would record "something propagated" as though it were a cause, and the real
	 * causes would disappear one level up. Totals may therefore exceed the number of errored
	 * inputs, which is correct — one wrapper can stand for several failures.
	 * </p>
	 * @param judgment an errored judgment
	 * @param totals the running total, keyed by terminal code
	 */
	private static void flattenOrigin(Judgment judgment, Map<JudgmentReasonCode, Integer> totals) {
		JudgmentReasonCode code = judgment.reasonCode();
		if (code == null) {
			return;
		}
		if (code != JudgmentReasonCode.ERRORS_PROPAGATED) {
			totals.merge(code, 1, Integer::sum);
			return;
		}
		if (!(judgment.metadata().get(Judgment.AGGREGATION_KEY) instanceof Map<?, ?> evidence)) {
			return;
		}
		if (!(evidence.get(Judgment.ERROR_CODE_COUNTS_KEY) instanceof Map<?, ?> origin)) {
			return;
		}
		origin.forEach((key, value) -> {
			if (value instanceof Number count) {
				totals.merge(JudgmentReasonCode.fromWire(String.valueOf(key)), count.intValue(), Integer::sum);
			}
		});
	}

	private static Map<JudgmentReasonCode, Integer> freeze(Map<JudgmentReasonCode, Integer> counts) {
		return counts.isEmpty() ? Map.of() : Collections.unmodifiableMap(new EnumMap<>(counts));
	}

	boolean isEmpty() {
		return this.eligible.isEmpty();
	}

	/** @return true when a policy decided the aggregate before anything was reduced */
	boolean hasPolicyExit() {
		return this.policyExit != null;
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
			.put(AggregationEvidence.NOT_APPLICABLE_POLICY, this.notApplicablePolicy.token())
			.put(AggregationEvidence.INPUT_COUNT, this.inputCount)
			.put(AggregationEvidence.ELIGIBLE_COUNT, this.eligible.size())
			.put(AggregationEvidence.EXPLICIT_ABSTAIN_COUNT, this.explicitAbstainCount)
			.put(AggregationEvidence.NOT_APPLICABLE_COUNT, this.notApplicableCount)
			.put(AggregationEvidence.ERROR_COUNT, this.errorCount)
			.put(AggregationEvidence.IGNORED_ERROR_COUNT, this.ignoredErrorCount)
			.put(AggregationEvidence.ERRORS_TREATED_AS_ABSTAIN_COUNT, this.errorsTreatedAsAbstainCount)
			.put(AggregationEvidence.ERRORS_TREATED_AS_FAIL_COUNT, this.errorsTreatedAsFailCount)
			.put(AggregationEvidence.NOT_APPLICABLE_TREATED_AS_FAIL_COUNT, this.notApplicableTreatedAsFailCount)
			.put(AggregationEvidence.ERROR_CODE_COUNTS, portableErrorCodeCounts());
	}

	/** @return the flattened origin totals as portable wire-name keys */
	private Map<String, Object> portableErrorCodeCounts() {
		Map<String, Object> portable = new LinkedHashMap<>();
		this.errorCodeCounts.forEach((code, count) -> portable.put(code.wireName(), count));
		return portable;
	}

	/**
	 * Build the ERROR aggregate for a policy that decided before anything was reduced.
	 * <p>
	 * A propagating aggregate is built atomically with its origin rather than assembled from a
	 * bare error: there is no instant at which a judgment claims to propagate causes it cannot
	 * name.
	 * </p>
	 * @param strategyToken the strategy's stable identifier
	 * @return an error judgment carrying the evidence
	 */
	Judgment policyExitAggregate(String strategyToken) {
		PolicyExit exit = Objects.requireNonNull(this.policyExit, "there is no policy exit to build");
		Judgment aggregate = exit.code() == JudgmentReasonCode.ERRORS_PROPAGATED
				? Judgment.propagatedError(this.errorCodeCounts, exit.reasoning())
				: Judgment.error(exit.code(), exit.reasoning());
		return AggregationEvidence.attach(aggregate, evidence(strategyToken).build());
	}

	/**
	 * Build the no-result aggregate for the case where nothing was eligible.
	 * <p>
	 * A population every one of whose members was excluded is itself excluded, and says so:
	 * reporting that as an abstention would claim the jury tried and could not decide, when in
	 * fact nothing here was ever this jury's question. Every other empty population is an
	 * abstention.
	 * </p>
	 * @param strategyToken the strategy's stable identifier
	 * @param extraEvidence additional strategy-specific evidence, may be empty
	 * @return an abstaining or not-applicable judgment carrying the evidence
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

		Judgment aggregate = allExcluded()
				? Judgment.notApplicable(String.format("All %d judgment(s) were not applicable to this subject",
						this.notApplicableCount))
				: Judgment.builder().abstain().reasoning(noResultReasoning()).build();
		return AggregationEvidence.attach(aggregate, evidence.build());
	}

	/**
	 * Whether every submitted judgment was an exclusion this jury was configured to honour.
	 * <p>
	 * "All not applicable" means a non-empty population in which nothing else arrived. One
	 * abstention among the exclusions is an abstention: the jury did have a question here, and
	 * did not answer it.
	 * </p>
	 * @return true when the aggregate is itself not applicable
	 */
	private boolean allExcluded() {
		return this.notApplicablePolicy == NotApplicablePolicy.EXCLUDE && this.notApplicableCount > 0
				&& this.notApplicableCount == this.inputCount;
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
		if (this.notApplicableCount > 0) {
			return String.format("No eligible judgments among %d submitted; %d were not applicable", this.inputCount,
					this.notApplicableCount);
		}
		return String.format("No eligible judgments among %d submitted", this.inputCount);
	}

}
