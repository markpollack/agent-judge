/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.assertions;

import org.opentest4j.AssertionFailedError;
import io.github.markpollack.judge.evaluation.*;
import io.github.markpollack.judge.policy.PolicyAction;
import io.github.markpollack.judge.reporting.VerdictReport;

/**
 * An unsuccessful assertion retaining the original complete evaluation and policy facts.
 */
public final class RequirementAssertionError extends AssertionFailedError {

	/** Complete retained evaluation for inspection after failure. */
	private final EvaluationResult result;

	/**
	 * Create a diagnostic without executing anything.
	 * @param result retained evaluation
	 */
	public RequirementAssertionError(EvaluationResult result) {
		super(message(result));
		this.result = result;
		if (result.policyResult() instanceof PolicyResult.Failed failed)
			initCause(failed.cause());
	}

	/**
	 * Complete unchanged retained result.
	 * @return original result
	 */
	public EvaluationResult result() {
		return result;
	}

	private static String message(EvaluationResult result) {
		String meaning = switch (result.verdict().conclusion()) {
			case PASS -> "Requirement satisfied";
			case FAIL -> "Requirement violated";
			case INCONCLUSIVE -> "Requirement satisfaction inconclusive";
			case NOT_APPLICABLE -> "Requirement not applicable";
		};
		String policy = switch (result.policyResult()) {
			case PolicyResult.NotRequested ignored -> "no policy requested";
			case PolicyResult.Decided d ->
				d.decision().action() == PolicyAction.RELY ? "policy permits reliance: " + d.decision().reason()
						: "policy withheld reliance (" + d.decision().action() + "): " + d.decision().reason();
			case PolicyResult.Failed failed ->
				"requested policy failed: " + failed.cause().getClass().getName() + ": " + failed.cause().getMessage();
		};
		return meaning + "; " + bounded(policy) + "\n" + VerdictReport.of(result.verdict()).summary();
	}

	private static String bounded(String value) {
		return value.length() <= 800 ? value : value.substring(0, 800) + "… [full value retained]";
	}

}
