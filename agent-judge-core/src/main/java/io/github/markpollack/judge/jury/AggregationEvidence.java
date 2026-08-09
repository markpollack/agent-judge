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

	/** How many judgments were submitted. */
	public static final String INPUT_COUNT = "inputCount";

	/** How many judgments actually contributed to the reduction. */
	public static final String ELIGIBLE_COUNT = "eligibleCount";

	/** How many arrived with ABSTAIN — the judge's own abstention. */
	public static final String EXPLICIT_ABSTAIN_COUNT = "explicitAbstainCount";

	/** How many arrived with ERROR. */
	public static final String ERROR_COUNT = "errorCount";

	/** Errors removed from the population under IGNORE. */
	public static final String IGNORED_ERROR_COUNT = "ignoredErrorCount";

	/** Errors converted to non-votes under TREAT_AS_ABSTAIN. */
	public static final String ERRORS_TREATED_AS_ABSTAIN_COUNT = "errorsTreatedAsAbstainCount";

	/** Errors that participated as FAIL under TREAT_AS_FAIL. */
	public static final String ERRORS_TREATED_AS_FAIL_COUNT = "errorsTreatedAsFailCount";

	// ==================== Status-counting keys ====================

	/** Eligible judgments with PASS. Emitted only by status-counting strategies. */
	public static final String PASS_COUNT = "passCount";

	/** Eligible judgments with FAIL. Emitted only by status-counting strategies. */
	public static final String FAIL_COUNT = "failCount";

	// ==================== Weighted keys ====================

	/** Sum of resolved weights before eligibility filtering. */
	public static final String INPUT_WEIGHT = "inputWeight";

	/** Sum of resolved weights after eligibility filtering. */
	public static final String ELIGIBLE_WEIGHT = "eligibleWeight";

	private AggregationEvidence() {
		// Factory class - no instantiation
	}

	static Builder builder() {
		return new Builder();
	}

	static Judgment attach(Judgment judgment, Map<String, Object> evidence) {
		Map<String, Object> metadata = new LinkedHashMap<>(judgment.metadata());
		metadata.put(Judgment.AGGREGATION_KEY, evidence);
		return new Judgment(judgment.status(), judgment.score(), judgment.label(), judgment.reasoning(), judgment.checks(),
				metadata);
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

		Map<String, Object> build() {
			// Declared order, not hash order: the block is read by humans as often as by
			// machines, and Judgment construction freezes it either way.
			return Collections.unmodifiableMap(new LinkedHashMap<>(this.entries));
		}

	}

}
