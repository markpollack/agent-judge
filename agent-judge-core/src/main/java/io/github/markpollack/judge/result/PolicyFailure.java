/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.result;

import java.util.Objects;

/**
 * Policy machinery failure; the original producer facts are retained.
 *
 * @param policy policy identity
 * @param reasonCode instrument machinery cause, never a subject rejection
 * @param reason explanation of the failure
 */
public record PolicyFailure(PolicyRef policy, JudgmentReasonCode reasonCode,
		String reason) implements PolicyApplication {
	/** Validate and freeze this value. */
	public PolicyFailure {
		Objects.requireNonNull(policy, "policy");
		Objects.requireNonNull(reasonCode, "reasonCode");
		ValueRequirements.text(reason, "reason");
		if (reasonCode.family() != JudgmentReasonCode.Family.INSTRUMENT
				|| reasonCode.originFamily() != JudgmentReasonCode.OriginFamily.MACHINERY) {
			throw new IllegalArgumentException("policy failure requires an instrument machinery reasonCode");
		}
	}
}
