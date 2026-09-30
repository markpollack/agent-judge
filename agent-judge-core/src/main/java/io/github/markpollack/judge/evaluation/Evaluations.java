/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.evaluation;

import java.util.Objects;
import java.util.concurrent.CancellationException;
import io.github.markpollack.judge.*;
import io.github.markpollack.judge.jury.*;
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
	 * @param <E> evidence type
	 * @param judge evaluator
	 * @param evidence supplied evidence
	 * @return retained result with no policy requested
	 */
	public static <E> EvaluationResult evaluate(Judge<E> judge, E evidence) {
		Objects.requireNonNull(judge, "judge");
		Objects.requireNonNull(evidence, "evidence");
		checkCancellation();
		return of(SimpleJury.<E>builder()
			.judge(judge)
			.parallel(false)
			.votingStrategy(new AllMustPassStrategy(ErrorHandling.PROPAGATE, ExclusionHandling.EXCLUDE))
			.build()
			.vote(evidence));
	}

	/**
	 * Evaluate and apply a requested policy.
	 * @param <E> evidence type
	 * @param judge evaluator
	 * @param evidence supplied evidence
	 * @param policy requested policy
	 * @return complete result
	 */
	public static <E> EvaluationResult evaluate(Judge<E> judge, E evidence, Policy policy) {
		Objects.requireNonNull(policy, "policy");
		return apply(evaluate(judge, evidence).verdict(), policy);
	}

	/**
	 * Execute a Jury and retain its entire verdict.
	 * @param <E> evidence type
	 * @param jury configured jury
	 * @param evidence supplied evidence
	 * @return complete result with no policy requested
	 */
	public static <E> EvaluationResult evaluate(Jury<E> jury, E evidence) {
		Objects.requireNonNull(jury, "jury");
		Objects.requireNonNull(evidence, "evidence");
		checkCancellation();
		return of(jury.vote(evidence));
	}

	/**
	 * Execute a Jury then the requested policy.
	 * @param <E> evidence type
	 * @param jury configured jury
	 * @param evidence supplied evidence
	 * @param policy requested policy
	 * @return complete result
	 */
	public static <E> EvaluationResult evaluate(Jury<E> jury, E evidence, Policy policy) {
		Objects.requireNonNull(policy, "policy");
		return apply(evaluate(jury, evidence).verdict(), policy);
	}

	/**
	 * Evaluate the actual supplied requirement.
	 * @param <S> specification type
	 * @param <E> evidence type
	 * @param requirement actual requirement
	 * @param judge requirement-aware evaluator
	 * @param evidence supplied evidence
	 * @return associated verdict with no policy requested
	 */
	public static <S, E> EvaluationResult evaluate(Requirement<S> requirement, RequirementJudge<S, E> judge,
			E evidence) {
		Objects.requireNonNull(requirement, "requirement");
		Objects.requireNonNull(judge, "judge");
		Objects.requireNonNull(evidence, "evidence");
		checkCancellation();
		Judgment judgment;
		Throwable failure = null;
		try {
			judgment = Objects.requireNonNull(judge.judge(requirement, evidence), "Judge returned null");
		}
		catch (Exception ex) {
			preserveCancellation(ex);
			failure = ex;
			judgment = Judgment.error(JudgmentReasonCode.JUDGE_FAILED,
					ex.getClass().getName() + ": " + Objects.toString(ex.getMessage(), "Judge failed"));
		}
		if (failure == null)
			return of(Verdict.single(requirement.id(), judgment).forRequirement(requirement));
		Judgment aggregate = new AllMustPassStrategy(ErrorHandling.PROPAGATE, ExclusionHandling.EXCLUDE)
			.aggregate(java.util.List.of(judgment), java.util.Map.of());
		return of(Verdict.builder()
			.requirement(requirement)
			.judgment(aggregate)
			.individual(java.util.List.of(judgment))
			.individualByName(java.util.Map.of(requirement.id(), judgment))
			.seats(java.util.List
				.of(new Seat(0, requirement.id(), io.github.markpollack.judge.description.KeySource.DECLARED,
						SeatExecution.CONTAINED_FAILURE, Participation.NOT_REDUCED, failure)))
			.declaredCardinality(1)
			.provenance(VerdictProvenance.own())
			.build());
	}

	/**
	 * Evaluate a requirement then the requested policy.
	 * @param <S> specification type
	 * @param <E> evidence type
	 * @param requirement actual requirement
	 * @param judge requirement-aware evaluator
	 * @param evidence supplied evidence
	 * @param policy requested policy
	 * @return complete result
	 */
	public static <S, E> EvaluationResult evaluate(Requirement<S> requirement, RequirementJudge<S, E> judge, E evidence,
			Policy policy) {
		Objects.requireNonNull(policy, "policy");
		return apply(evaluate(requirement, judge, evidence).verdict(), policy);
	}

	/**
	 * Execute a requirement-aware Jury without flattening its record.
	 * @param <S> specification type
	 * @param <E> evidence type
	 * @param requirement actual requirement
	 * @param jury requirement-aware jury
	 * @param evidence supplied evidence
	 * @return complete associated result
	 */
	public static <S, E> EvaluationResult evaluate(Requirement<S> requirement, RequirementJury<S, E> jury, E evidence) {
		Objects.requireNonNull(requirement, "requirement");
		Objects.requireNonNull(jury, "jury");
		Objects.requireNonNull(evidence, "evidence");
		checkCancellation();
		return of(Objects.requireNonNull(jury.vote(requirement, evidence), "Jury returned null")
			.forRequirement(requirement));
	}

	/**
	 * Execute a requirement-aware Jury then the requested policy.
	 * @param <S> specification type
	 * @param <E> evidence type
	 * @param requirement actual requirement
	 * @param jury requirement-aware jury
	 * @param evidence supplied evidence
	 * @param policy requested policy
	 * @return complete result
	 */
	public static <S, E> EvaluationResult evaluate(Requirement<S> requirement, RequirementJury<S, E> jury, E evidence,
			Policy policy) {
		Objects.requireNonNull(policy, "policy");
		return apply(evaluate(requirement, jury, evidence).verdict(), policy);
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
