/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.assertions;

import io.github.markpollack.judge.requirement.Requirement;
import io.github.markpollack.judge.result.PolicyBinding;

import io.github.markpollack.judge.context.JudgmentContext;

/**
 * Evidence bound to a configured {@link SemanticAssertions} fixture. Calling
 * {@code satisfies} immediately invokes evaluation and checks its authoritative reading;
 * this object does not defer execution or accept policy changes after that terminal. Bind
 * a named policy beforehand with {@link Requirement#under(PolicyBinding)}.
 */
public final class SemanticAssertion {

	private final SemanticAssertions assertions;

	private final JudgmentContext evidence;

	SemanticAssertion(SemanticAssertions assertions, JudgmentContext evidence) {
		this.assertions = assertions;
		this.evidence = evidence;
	}

	/**
	 * Evaluate the exact requirement under its override or configured default. Only
	 * structurally supported acceptance passes. Accepting a negative assessment still
	 * fails as rejected; withholding or escalation fails as inconclusive while keeping
	 * the original assessment in the error's result.
	 * @param requirement exact requirement
	 * @throws SemanticAssertionError if the authoritative reading cannot pass
	 */
	public void satisfies(Requirement<?> requirement) {
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
