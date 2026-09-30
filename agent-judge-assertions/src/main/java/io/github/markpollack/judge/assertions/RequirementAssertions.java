/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.assertions;

import java.util.Objects;
import io.github.markpollack.judge.evaluation.*;
import io.github.markpollack.judge.policy.PolicyAction;
import io.github.markpollack.judge.jury.Verdict;

/**
 * Requirement assertions over retained results. These operations execute no judge or
 * policy.
 */
public final class RequirementAssertions {

	private RequirementAssertions() {
	}

	/**
	 * Require PASS and, if a policy was requested, its successful RELY decision.
	 * @param result retained complete evaluation
	 * @throws IllegalArgumentException when no actual requirement is associated
	 * @throws RequirementAssertionError when satisfaction is not established
	 */
	public static void requireSatisfied(EvaluationResult result) {
		Objects.requireNonNull(result, "result");
		if (result.verdict().requirement() == null)
			throw new IllegalArgumentException(
					"Satisfaction requires an associated Requirement; inspect the neutral conclusion for evidence-only checks");
		var conclusion = result.verdict().conclusion();
		if (conclusion == Verdict.Conclusion.PASS && (result.policyResult() instanceof PolicyResult.NotRequested
				|| result.policyResult() instanceof PolicyResult.Decided d
						&& d.decision().action() == PolicyAction.RELY))
			return;
		throw new RequirementAssertionError(result);
	}

}
