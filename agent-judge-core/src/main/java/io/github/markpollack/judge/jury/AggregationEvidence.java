/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import io.github.markpollack.judge.result.Judgment;

/**
 * Contract constants for the aggregation evidence a voting strategy records.
 *
 * <p>
 * The evidence answers "which judgments actually contributed, and why did the rest not?".
 * Without it, {@link ErrorPolicy#IGNORE} and {@link ErrorPolicy#TREAT_AS_ABSTAIN} are
 * observationally identical to a caller, which is how their implementations previously
 * collapsed into one.
 * </p>
 *
 * <p>
 * The block lives under the reserved {@link Judgment#AGGREGATION_KEY} key rather than at
 * the top level of {@code metadata}, which callers write to freely. A flat {@code
 * passCount} would eventually collide with a caller's own key, silently.
 * </p>
 *
 * <p>
 * This is a factory, not a value type: it produces an immutable, JSON-compatible
 * {@code Map<String, Object>} in declared key order. An {@code AggregationEvidence} object
 * is never placed into {@code metadata}; the portable value algebra {@link Judgment}
 * enforces at construction would refuse it. Every value produced here is an integer, a
 * finite double, or a string, and {@code Judgment} freezes the block again on the way in.
 * </p>
 *
 * @author Mark Pollack
 * @since 0.14.0
 */
public final class AggregationEvidence {

	// ==================== Universal keys ====================

	/** Which strategy produced the aggregate. */
	public static final String STRATEGY = "strategy";

	/** Which policy was applied to errored judgments. */
	public static final String ERROR_POLICY = "errorPolicy";

	/**
	 * Which policy was applied to excluded judgments.
	 *
	 * @since 0.17.0
	 */
	public static final String NOT_APPLICABLE_POLICY = "notApplicablePolicy";

	/** How many judgments were submitted. */
	public static final String INPUT_COUNT = "inputCount";

	/** How many judgments actually contributed to the reduction. */
	public static final String ELIGIBLE_COUNT = "eligibleCount";

	/** How many arrived with ABSTAIN — the judge's own abstention. */
	public static final String EXPLICIT_ABSTAIN_COUNT = "explicitAbstainCount";

	/**
	 * How many arrived with NOT_APPLICABLE — the judge's own exclusion.
	 * <p>
	 * Counted on the submitted originals under every policy, including
	 * {@link NotApplicablePolicy#REFUSE}, so a reader can always say how much of a rubric the
	 * instrument claimed did not apply. The <em>rate</em> is the reader's to derive; a result
	 * stores counts.
	 * </p>
	 *
	 * @since 0.17.0
	 */
	public static final String NOT_APPLICABLE_COUNT = "notApplicableCount";

	/** How many arrived with ERROR. */
	public static final String ERROR_COUNT = "errorCount";

	/** Errors removed from the population under IGNORE. */
	public static final String IGNORED_ERROR_COUNT = "ignoredErrorCount";

	/** Errors converted to non-votes under TREAT_AS_ABSTAIN. */
	public static final String ERRORS_TREATED_AS_ABSTAIN_COUNT = "errorsTreatedAsAbstainCount";

	/** Errors that participated as FAIL under TREAT_AS_FAIL. */
	public static final String ERRORS_TREATED_AS_FAIL_COUNT = "errorsTreatedAsFailCount";

	/**
	 * Exclusions that participated as FAIL under {@link NotApplicablePolicy#TREAT_AS_FAIL}.
	 *
	 * @since 0.17.0
	 */
	public static final String NOT_APPLICABLE_TREATED_AS_FAIL_COUNT = "notApplicableTreatedAsFailCount";

	/**
	 * The terminal causes behind the errored inputs, as wire name to count.
	 * <p>
	 * Flattened through propagating wrappers, so the block names causes rather than the fact
	 * that something propagated. A total here may exceed {@link #ERROR_COUNT}, which counts the
	 * immediate errored inputs: one propagating input can stand for several failures.
	 * </p>
	 * <p>
	 * On an aggregate coded
	 * {@link io.github.markpollack.judge.result.JudgmentReasonCode#ERRORS_PROPAGATED} this block
	 * is not merely evidence but the invariant that makes the code legal, and
	 * {@link Judgment} refuses the code without it.
	 * </p>
	 *
	 * @since 0.17.0
	 */
	public static final String ERROR_CODE_COUNTS = Judgment.ERROR_CODE_COUNTS_KEY;

	// ==================== Status-counting keys ====================

	/** Eligible judgments with PASS. Emitted only by status-counting strategies. */
	public static final String PASS_COUNT = "passCount";

	/** Eligible judgments with FAIL. Emitted only by status-counting strategies. */
	public static final String FAIL_COUNT = "failCount";

	// ==================== Threshold keys ====================

	/**
	 * The normalized bar a numeric strategy applied, when the strategy takes one.
	 * <p>
	 * Recorded so a stored verdict says which bar produced its outcome. A threshold that
	 * lives only in a constructor argument cannot be recovered from the result, and a bar
	 * nobody can read afterwards is a bar nobody can audit.
	 * </p>
	 *
	 * @since 0.16.0
	 */
	public static final String THRESHOLD = "threshold";

	/**
	 * Index, among the submitted judgments, of the judgment that bound the aggregate.
	 * <p>
	 * Emitted by {@link ConjunctiveStrategy}. The binding judgment is the diagnosis — it
	 * names which contributor held the result down, which a mean cannot report.
	 * </p>
	 *
	 * @since 0.16.0
	 */
	public static final String BINDING_ELIGIBLE_INDEX = "bindingIndex";

	// ==================== Weighted keys ====================

	/**
	 * Sum of resolved weights before eligibility filtering. A sum beyond the largest finite
	 * {@code double} is reported as {@link Double#MAX_VALUE}.
	 */
	public static final String INPUT_WEIGHT = "inputWeight";

	/**
	 * Sum of resolved weights after eligibility filtering. A sum beyond the largest finite
	 * {@code double} is reported as {@link Double#MAX_VALUE}.
	 */
	public static final String ELIGIBLE_WEIGHT = "eligibleWeight";

	private AggregationEvidence() {
		// Factory class - no instantiation
	}

	static Builder builder() {
		return new Builder();
	}

	/**
	 * Replace a judgment's evidence block, preserving an origin the replacement omits.
	 * <p>
	 * A propagating aggregate carries its origin inside the same reserved block, and a
	 * judgment's construction refuses the code without it. Overwriting the block with evidence
	 * that does not name the origin would destroy the fact that made the code legal, so the
	 * existing origin is carried across rather than dropped.
	 * </p>
	 * @param judgment the aggregate
	 * @param evidence the replacement evidence block
	 * @return the aggregate carrying that evidence
	 */
	static Judgment attach(Judgment judgment, Map<String, Object> evidence) {
		Map<String, Object> block = new LinkedHashMap<>(evidence);
		if (!block.containsKey(Judgment.ERROR_CODE_COUNTS_KEY)
				&& judgment.metadata().get(Judgment.AGGREGATION_KEY) instanceof Map<?, ?> existing) {
			Object origin = existing.get(Judgment.ERROR_CODE_COUNTS_KEY);
			if (origin != null) {
				block.put(Judgment.ERROR_CODE_COUNTS_KEY, origin);
			}
		}
		Map<String, Object> metadata = new LinkedHashMap<>(judgment.metadata());
		metadata.put(Judgment.AGGREGATION_KEY, block);
		return new Judgment(judgment.status(), judgment.score(), judgment.label(), judgment.reasonCode(),
				judgment.reasoning(), judgment.checks(), metadata);
	}

	/**
	 * Accumulates evidence entries and produces an immutable map.
	 */
	static final class Builder {

		private final Map<String, Object> entries = new LinkedHashMap<>();

		private Builder() {
		}

		Builder put(String key, int value) {
			this.entries.put(key, value);
			return this;
		}

		Builder put(String key, double value) {
			this.entries.put(key, value);
			return this;
		}

		Builder put(String key, String value) {
			this.entries.put(key, value);
			return this;
		}

		/**
		 * Record a nested block of portable values, such as a count keyed by cause.
		 * <p>
		 * A flat key per cause would put a growing vocabulary into the evidence's own
		 * namespace, where a new code could collide with a strategy's parameter. One block
		 * keeps the vocabulary where it belongs.
		 * </p>
		 * @param key the evidence key
		 * @param value the block, copied in encounter order
		 * @return this builder
		 */
		Builder put(String key, Map<String, Object> value) {
			this.entries.put(key, new LinkedHashMap<>(value));
			return this;
		}

		Map<String, Object> build() {
			// Declared order, not hash order: the block is read by humans as often as by
			// machines, and Judgment construction freezes it either way.
			return Collections.unmodifiableMap(new LinkedHashMap<>(this.entries));
		}

	}

}
