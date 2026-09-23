/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.assertions;

import io.github.markpollack.judge.context.JudgmentContext;

/** Immutable per-call evidence binding; terminal operations evaluate eagerly. */
public final class SemanticAssertion {

	private final SemanticAssertions assertions;

	private final JudgmentContext evidence;

	SemanticAssertion(SemanticAssertions assertions, JudgmentContext evidence) {
		this.assertions = assertions;
		this.evidence = evidence;
	}

	/**
	 * Evaluate the exact requirement under its override or configured default.
	 * @param requirement exact requirement
	 * @throws SemanticAssertionError if the authoritative reading cannot pass
	 */
	public void satisfies(Requirement requirement) {
		SemanticAssertions.requireSatisfied(assertions.evaluate(evidence, requirement));
	}

	/**
	 * Evaluate exact text under the configured default policy. The content-addressed
	 * requirement identity is deterministic; route resolution still receives exact text.
	 * @param requirement exact requirement text
	 * @throws IllegalStateException when no default policy is configured, before
	 * inference
	 * @throws SemanticAssertionError if the authoritative reading cannot pass
	 */
	public void satisfies(String requirement) {
		satisfies(assertions.stringRequirement(requirement));
	}

}
