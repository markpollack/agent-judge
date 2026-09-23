/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.assertions;

import java.util.Objects;
import io.github.markpollack.judge.result.PolicyRef;
import io.github.markpollack.judge.jury.Verdict;
import io.github.markpollack.judge.jury.interpretation.Interpretation;
import io.github.markpollack.judge.jury.interpretation.Verdicts;

/**
 * Retained local evaluation and authoritative reading. This runtime record is not a new
 * wire contract: serialize requirement identity/text, policy reference/source and the
 * existing portable Verdict and Interpretation separately, never the policy function.
 *
 * @param requirement resolved requirement
 * @param policy resolved policy identity
 * @param policySource where the policy came from
 * @param verdict complete normal jury result
 * @param interpretation authoritative reading, checked against the verdict
 */
public record AssertionResult(Requirement requirement, PolicyRef policy, PolicySource policySource, Verdict verdict,
		Interpretation interpretation) {

	/** Validate retained evaluation facts. */
	public AssertionResult {
		Objects.requireNonNull(requirement);
		Objects.requireNonNull(policy);
		Objects.requireNonNull(policySource);
		Objects.requireNonNull(verdict);
		if (!Verdicts.interpret(verdict).equals(interpretation))
			throw new IllegalArgumentException("Reading must be authoritative for this verdict");
	}
	/** Policy resolution source. */
	public enum PolicySource {

		/** Explicit Requirement.under binding. */
		REQUIREMENT,
		/** Explicit configured facade default. */
		DEFAULT

	}

	/**
	 * Retain the authoritative reading of a normal jury result.
	 * @param requirement resolved requirement
	 * @param policy resolved policy identity
	 * @param policySource resolution source
	 * @param verdict normal jury result
	 */
	public AssertionResult(Requirement requirement, PolicyRef policy, PolicySource policySource, Verdict verdict) {
		this(requirement, policy, policySource, verdict, Verdicts.interpret(verdict));
	}
}
