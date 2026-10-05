/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.serialization.diagnostics;

import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import io.github.markpollack.judge.judgment.Confidence;
import io.github.markpollack.judge.judgment.Finding;
import io.github.markpollack.judge.judgment.Judgment;
import io.github.markpollack.judge.judgment.JudgmentReasonCode;
import io.github.markpollack.judge.judgment.JudgmentStatus;
import io.github.markpollack.judge.judgment.ProbabilityDistribution;
import io.github.markpollack.judge.provenance.Provenance;

/**
 * Complete semantic reading of a judgment; raw facts and operational policy outcome
 * remain distinct. Historical labels and scores do not manufacture modern finding domains
 * or confidence.
 *
 * @param producerStatus raw disposition, absent for historical results
 * @param status operational disposition
 * @param reasonCode operational cause
 * @param reasoning operational explanation
 * @param finding full product finding
 * @param confidence native/derived support
 * @param probabilityDistribution native probabilityDistribution
 * @param provenance complete provenance including provider calibration declarations
 * @param historicalPolicy historical policy data, never a live policy application
 * @param producerReasonCode raw cause
 * @param producerReasoning raw explanation
 * @param checks interpreted children
 * @param metadata recursively immutable incidental recorded metadata
 * @param legacyLabel historical label without an invented domain
 * @param legacyScore historical normalized numeric score
 */
public record JudgmentView(@Nullable String producerStatus, @Nullable String status, @Nullable String reasonCode,
		@Nullable String reasoning, @Nullable Finding finding, @Nullable Confidence confidence,
		@Nullable ProbabilityDistribution probabilityDistribution, @Nullable Provenance provenance,
		@com.fasterxml.jackson.annotation.JsonProperty("policyApplication") @Nullable Object historicalPolicy, @Nullable String producerReasonCode, @Nullable String producerReasoning,
		List<Check> checks, @Nullable String legacyLabel, @Nullable Double legacyScore, Map<String, Object> metadata) {
	/** Freeze the child roster. */
	public JudgmentView {
		checks = List.copyOf(checks);
		metadata = new Judgment(JudgmentStatus.PASS, null, null, null, null, "", List.of(), null, metadata).metadata();
	}

	static JudgmentView of(Judgment value) {
		return new JudgmentView(value.producerStatus().wireName(), value.status().wireName(), token(value.reasonCode()),
				value.reasoning(), value.finding(), value.confidence(), value.probabilityDistribution(),
				value.provenance(), null, token(value.reasonCode()), value.reasoning(),
				value.checks()
					.stream()
					.map(check -> new Check(check.id(), null, check.judgment().reasoning(), of(check.judgment())))
					.toList(),
				null, null, value.metadata());
	}

	private static @Nullable String token(@Nullable JudgmentReasonCode code) {
		return code == null ? null : code.wireName();
	}
}
