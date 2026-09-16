/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury.interpretation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.jspecify.annotations.Nullable;

/**
 * The aggregation evidence a stage's aggregate carries, read leniently.
 *
 * <p>This is the nullable view of the block
 * {@link io.github.markpollack.judge.jury.AggregationEvidence} describes. That class is the
 * contract of the keys a strategy writes and a factory for the block, not a value type, so it
 * cannot be made lenient without changing its live contract; this record is the view a reader
 * needs. Its member names are the block's key names, and they move only with
 * {@link Interpretation#schemaVersion()}.
 *
 * <p>Every member is nullable so that a block written by 0.14–0.16, which lacks the keys 0.17
 * added, still binds: the counts it lacks are null, not zero. A key that is present but cannot
 * be read is null with an {@link DefectKind#UNPARSEABLE} defect naming it.
 *
 * @param strategy which strategy produced the aggregate
 * @param errorPolicy the policy applied to errored judgments
 * @param notApplicablePolicy the policy applied to excluded judgments
 * @param inputCount how many judgments were submitted
 * @param eligibleCount how many contributed to the reduction
 * @param explicitAbstainCount how many arrived abstaining
 * @param notApplicableCount how many arrived excluded
 * @param errorCount how many arrived errored
 * @param ignoredErrorCount errors removed from the population under {@code ignore}
 * @param errorsTreatedAsAbstainCount errors converted to non-votes under {@code treatAsAbstain}
 * @param errorsTreatedAsFailCount errors that participated as FAIL under {@code treatAsFail}
 * @param notApplicableTreatedAsFailCount exclusions that participated as FAIL under
 * {@code treatAsFail}
 * @param errorCodeCounts the terminal causes behind the errored inputs, wire name to count
 * @param passCount eligible judgments with PASS, from status-counting strategies
 * @param failCount eligible judgments with FAIL, from status-counting strategies
 * @param threshold the normalised bar a numeric strategy applied
 * @param bindingIndex the index of the judgment that bound a conjunctive aggregate
 * @param inputWeight the sum of resolved weights before eligibility filtering
 * @param eligibleWeight the sum of resolved weights after eligibility filtering
 * @author Mark Pollack
 * @since 0.17.0
 */
@JsonPropertyOrder({ "strategy", "errorPolicy", "notApplicablePolicy", "inputCount", "eligibleCount",
		"explicitAbstainCount", "notApplicableCount", "errorCount", "ignoredErrorCount", "errorsTreatedAsAbstainCount",
		"errorsTreatedAsFailCount", "notApplicableTreatedAsFailCount", "errorCodeCounts", "passCount", "failCount",
		"threshold", "bindingIndex", "inputWeight", "eligibleWeight" })
public record Evidence(@Nullable String strategy, @Nullable String errorPolicy, @Nullable String notApplicablePolicy,
		@Nullable Integer inputCount, @Nullable Integer eligibleCount, @Nullable Integer explicitAbstainCount,
		@Nullable Integer notApplicableCount, @Nullable Integer errorCount, @Nullable Integer ignoredErrorCount,
		@Nullable Integer errorsTreatedAsAbstainCount, @Nullable Integer errorsTreatedAsFailCount,
		@Nullable Integer notApplicableTreatedAsFailCount, @Nullable Map<String, Long> errorCodeCounts,
		@Nullable Integer passCount, @Nullable Integer failCount, @Nullable Double threshold,
		@Nullable Integer bindingIndex, @Nullable Double inputWeight, @Nullable Double eligibleWeight) {

	/** Copy the origin counts in encounter order. */
	public Evidence {
		if (errorCodeCounts != null) {
			errorCodeCounts = Collections.unmodifiableMap(new LinkedHashMap<>(errorCodeCounts));
		}
	}

}
