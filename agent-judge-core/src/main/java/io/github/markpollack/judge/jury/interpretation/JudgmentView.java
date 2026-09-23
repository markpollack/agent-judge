/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.jury.interpretation;

import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import io.github.markpollack.judge.result.*;

/**
 * Complete semantic reading of a judgment; raw facts and operational policy outcome
 * remain distinct. Historical labels and scores do not manufacture modern assessment
 * domains or certainty.
 *
 * @param producerStatus raw disposition, absent for historical results
 * @param status operational disposition
 * @param reasonCode operational cause
 * @param reasoning operational explanation
 * @param assessment full product assessment
 * @param certainty native/derived support
 * @param distribution native distribution
 * @param provenance complete provenance including provider calibration declarations
 * @param policyApplication recorded policy
 * @param producerReasonCode raw cause
 * @param producerReasoning raw explanation
 * @param checks interpreted children
 * @param metadata recursively immutable incidental recorded metadata
 * @param legacyLabel historical label without an invented domain
 * @param legacyScore historical normalized numeric score
 */
public record JudgmentView(@Nullable String producerStatus, @Nullable String status, @Nullable String reasonCode,
		@Nullable String reasoning, @Nullable Assessment assessment, @Nullable Certainty certainty,
		@Nullable Distribution distribution, @Nullable EvaluationProvenance provenance,
		@Nullable PolicyApplication policyApplication, @Nullable String producerReasonCode,
		@Nullable String producerReasoning, List<Check> checks, @Nullable String legacyLabel,
		@Nullable Double legacyScore, Map<String, Object> metadata) {
	/** Freeze the child roster. */
	public JudgmentView {
		checks = List.copyOf(checks);
		metadata = new Judgment(JudgmentStatus.PASS, null, null, null, null, "", List.of(), null, null, metadata)
			.metadata();
	}

	static JudgmentView of(Judgment value) {
		return new JudgmentView(value.producerStatus().wireName(), value.status().wireName(),
				token(value.operationalReasonCode()), value.operationalReasoning(), value.assessment(),
				value.certainty(), value.distribution(), value.provenance(), value.policyApplication(),
				token(value.reasonCode()), value.reasoning(),
				value.checks()
					.stream()
					.map(check -> new Check(check.id(), null, check.judgment().operationalReasoning(),
							of(check.judgment())))
					.toList(),
				null, null, value.metadata());
	}

	private static @Nullable String token(@Nullable JudgmentReasonCode code) {
		return code == null ? null : code.wireName();
	}
}
