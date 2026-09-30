/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.evaluation;

import java.util.Objects;
import io.github.markpollack.judge.jury.Verdict;

/**
 * Complete reusable execution result; construction executes no judge or policy.
 *
 * @param verdict original complete usable verdict
 * @param policyResult policy execution facts
 */
public record EvaluationResult(Verdict verdict, PolicyResult policyResult) {
	/** Reject absent or unusable records. */
	public EvaluationResult {
		Objects.requireNonNull(verdict, "verdict").conclusion();
		Objects.requireNonNull(policyResult, "policyResult");
	}
}
