/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.evaluation;
import io.github.markpollack.judge.verdict.InvocationRecords;
import io.github.markpollack.judge.verdict.Verdict;
import io.github.markpollack.judge.voting.AllMustPassStrategy;
import io.github.markpollack.judge.voting.ErrorHandling;
import io.github.markpollack.judge.voting.ExclusionHandling;

import java.util.Objects;
import java.util.concurrent.CancellationException;
import io.github.markpollack.judge.*;
import io.github.markpollack.judge.jury.*;
import io.github.markpollack.judge.verdict.*;
import io.github.markpollack.judge.voting.*;
import io.github.markpollack.judge.judgment.*;
import io.github.markpollack.judge.requirement.Requirement;
import io.github.markpollack.judge.policy.*;

/** Invoke configured evaluators once, then apply an optional complete-verdict policy. */
public final class Evaluations {

	private Evaluations() {
	}

	/**
	 * Retain a usable completed verdict without requesting a policy.
	 * @param verdict complete verdict
	 * @return execution result
	 */
	public static EvaluationResult of(Verdict verdict) {
		return new EvaluationResult(verdict, new PolicyResult.NotRequested());
	}

	/**
	 * Invoke policy once for every usable conclusion, including all-attempts-failed.
	 * @param verdict original complete verdict
	 * @param policy requested policy
	 * @return original verdict and the decision or original failure
	 */
	public static EvaluationResult apply(Verdict verdict, Policy policy) {
		Objects.requireNonNull(verdict, "verdict").conclusion();
		io.github.markpollack.judge.verdict.InvocationRecords.of(verdict);
		Objects.requireNonNull(policy, "policy");
		checkCancellation();
		PolicyResult result;
		try {
			result = new PolicyResult.Decided(Objects.requireNonNull(policy.decide(verdict),
					"Policy returned null; a PolicyDecision is required"));
		}
		catch (Exception ex) {
			preserveCancellation(ex);
			result = new PolicyResult.Failed(ex);
		}
		return new EvaluationResult(verdict, result);
	}

	/**
	 * Evaluate an evidence-only Judge once.
	 * @param judge evaluator
	 * @return retained result with no policy requested
	 */
	public static EvaluationResult evaluate(Judge judge) {
		Objects.requireNonNull(judge, "judge");
		checkCancellation();
		return of(SimpleJury.builder()
			.judge(judge)
			.parallel(false)
			.votingStrategy(new AllMustPassStrategy(ErrorHandling.PROPAGATE, ExclusionHandling.EXCLUDE))
			.build()
			.vote());
	}

	/**
	 * Evaluate and apply a requested policy.
	 * @param judge evaluator
	 * @param policy requested policy
	 * @return complete result
	 */
	public static EvaluationResult evaluate(Judge judge, Policy policy) {
		Objects.requireNonNull(policy, "policy");
		return apply(evaluate(judge).verdict(), policy);
	}

	/**
	 * Execute a Jury and retain its entire verdict.
	 * @param jury configured jury
	 * @return complete result with no policy requested
	 */
	public static EvaluationResult evaluate(Jury jury) {
		Objects.requireNonNull(jury, "jury");
		checkCancellation();
		return of(jury.vote());
	}

	/**
	 * Execute a Jury then the requested policy.
	 * @param jury configured jury
	 * @param policy requested policy
	 * @return complete result
	 */
	public static EvaluationResult evaluate(Jury jury, Policy policy) {
		Objects.requireNonNull(policy, "policy");
		return apply(evaluate(jury).verdict(), policy);
	}

	private static void checkCancellation() {
		if (Thread.currentThread().isInterrupted())
			throw new CancellationException("Evaluation interrupted");
	}

	private static void preserveCancellation(Exception ex) {
		if (ex instanceof CancellationException cancellation)
			throw cancellation;
		if (ex instanceof InterruptedException)
			Thread.currentThread().interrupt();
		checkCancellation();
	}

}
