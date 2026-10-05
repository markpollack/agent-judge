/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.assertj;
import io.github.markpollack.judge.jury.JuryEvidenceStep;
import io.github.markpollack.judge.jury.JuryRecipe;
import io.github.markpollack.judge.verdict.Verdict;

import java.util.Objects;
import java.util.function.Supplier;
import org.assertj.core.api.AbstractAssert;
import org.jspecify.annotations.Nullable;
import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.construction.*;
import io.github.markpollack.judge.requirement.Requirement;
import io.github.markpollack.judge.jury.*;
import io.github.markpollack.judge.verdict.*;
import io.github.markpollack.judge.voting.*;
import io.github.markpollack.judge.evaluation.*;
import io.github.markpollack.judge.policy.*;
import io.github.markpollack.judge.assertions.*;

/**
 * Requirement-first construction and once-only live assertions. Retained assertions
 * execute nothing and expose no policy configuration. Stages synchronize completion;
 * independent pre-execution branches represent independent evaluations.
 */
public final class Assertions {

	private Assertions() {
	}

	/**
	 * Begins typed construction for the actual requirement.
	 * @param <S> native specification type
	 * @param requirement immutable actual requirement
	 * @return construction selection
	 */
	public static <S> RequirementStage<S> assertThat(Requirement<S> requirement) {
		return new RequirementStage<>(requirement);
	}

	/**
	 * Begins a live ready-Judge assertion.
	 * @param judge ready evaluator
	 * @return once-only live stage
	 */
	public static LiveStage assertThat(Judge judge) {
		Objects.requireNonNull(judge);
		return new LiveStage(() -> Evaluations.evaluate(judge), null);
	}

	/**
	 * Begins a live ready-Jury assertion retaining its whole Verdict.
	 * @param jury ready composition
	 * @return once-only live stage
	 */
	public static LiveStage assertThat(Jury jury) {
		Objects.requireNonNull(jury);
		return new LiveStage(() -> Evaluations.evaluate(jury), null);
	}

	/**
	 * Inspects a retained Verdict without execution.
	 * @param verdict complete retained result
	 * @return retained assertion
	 */
	public static VerdictAssert assertThat(Verdict verdict) {
		return new VerdictAssert(verdict);
	}

	/**
	 * Inspects retained evaluation and its original policy result.
	 * @param result retained evaluation
	 * @return retained assertion
	 */
	public static EvaluationAssert assertThat(EvaluationResult result) {
		return new EvaluationAssert(result);
	}

	/**
	 * Selection that can only configure the asserted requirement.
	 *
	 * @param <S> specification type
	 */
	public static final class RequirementStage<S> {

		private final Requirement<S> actual;

		private RequirementStage(Requirement<S> actual) {
			Requirement.validate(actual);
			this.actual = actual;
		}

		/**
		 * Selects an unconfigured typed Judge recipe.
		 * @param <E> evidence type
		 * @param recipe construction collaborator
		 * @return evidence configuration
		 */
		public <E> EvidenceStage<E> judgedBy(JudgeRecipe<S, E> recipe) {
			EvidenceStep<E> step = Objects.requireNonNull(recipe).requirement(actual);
			return evidence -> new SatisfactionStage(
					() -> associated(Evaluations.evaluate(step.evidence(evidence).build()), actual), null);
		}

		/**
		 * Selects typed complete-Jury construction.
		 * @param <E> evidence type
		 * @param recipe construction collaborator
		 * @return evidence configuration
		 */
		public <E> EvidenceStage<E> judgedBy(JuryRecipe<S, E> recipe) {
			JuryEvidenceStep<E> step = Objects.requireNonNull(recipe).requirement(actual);
			return evidence -> new SatisfactionStage(
					() -> associated(Evaluations.evaluate(step.evidence(evidence).build()), actual), null);
		}

	}

	private static EvaluationResult associated(EvaluationResult result, Requirement<?> actual) {
		return new EvaluationResult(result.verdict().forRequirement(actual), result.policyResult());
	}

	/**
	 * Evidence is selected exactly once through construction.
	 *
	 * @param <E> evidence type
	 */
	@FunctionalInterface
	public interface EvidenceStage<E> {

		/**
		 * Selects real prepared evidence without invoking a producer.
		 * @param evidence immutable evidence snapshot
		 * @return satisfaction terminal stage
		 */
		SatisfactionStage withEvidence(E evidence);

	}

	/** Live general-conclusion stage, with one cached producer and policy execution. */
	public static class LiveStage {

		private final Supplier<EvaluationResult> execution;

		private final @Nullable Policy policy;

		private @Nullable EvaluationResult result;

		private boolean attempted;

		private @Nullable RuntimeException failure;

		private @Nullable Error fatal;

		private LiveStage(Supplier<EvaluationResult> execution, @Nullable Policy policy) {
			this.execution = execution;
			this.policy = policy;
		}

		/**
		 * Selects an independent complete-Verdict policy before execution.
		 * @param policy application policy
		 * @return configured branch
		 * @throws IllegalStateException after execution
		 */
		public synchronized LiveStage withPolicy(Policy policy) {
			requireUnexecuted();
			return new LiveStage(execution, Objects.requireNonNull(policy));
		}

		final void requireUnexecuted() {
			if (attempted)
				throw new IllegalStateException(
						"Evaluation already completed; apply policy explicitly to its retained Verdict");
		}

		/**
		 * Executes once, then returns the original cached evaluation.
		 * @return retained original evaluation
		 */
		public synchronized EvaluationResult evaluate() {
			if (!attempted) {
				attempted = true;
				try {
					EvaluationResult completed = Objects.requireNonNull(execution.get());
					result = policy == null ? completed : Evaluations.apply(completed.verdict(), policy);
				}
				catch (RuntimeException problem) {
					failure = problem;
					throw problem;
				}
				catch (Error problem) {
					fatal = problem;
					throw problem;
				}
			}
			if (failure != null)
				throw failure;
			if (fatal != null)
				throw fatal;
			return Objects.requireNonNull(result);
		}

		/** Requires PASS and successful RELY when a policy was selected. */
		public void isPassed() {
			requirePassed(evaluate());
		}

	}

	/**
	 * Requirement-context terminal; only actual typed requirement construction creates
	 * it.
	 */
	public static final class SatisfactionStage extends LiveStage {

		private final Supplier<EvaluationResult> execution;

		private SatisfactionStage(Supplier<EvaluationResult> execution, @Nullable Policy policy) {
			super(execution, policy);
			this.execution = execution;
		}

		@Override
		public synchronized SatisfactionStage withPolicy(Policy policy) {
			requireUnexecuted();
			return new SatisfactionStage(execution, Objects.requireNonNull(policy));
		}

		/** Establishes satisfaction; repetition reads the same result. */
		public void isSatisfied() {
			RequirementAssertions.requireSatisfied(evaluate());
		}

	}

	private static void requirePassed(EvaluationResult result) {
		if (result.verdict().conclusion() != Verdict.Conclusion.PASS
				|| result.policyResult() instanceof PolicyResult.Failed
				|| result.policyResult() instanceof PolicyResult.Decided decision
						&& decision.decision().action() != PolicyAction.RELY)
			throw new RequirementAssertionError(result);
	}

	/** General assertions on a retained complete Verdict. */
	public static final class VerdictAssert extends AbstractAssert<VerdictAssert, Verdict> {

		private VerdictAssert(Verdict verdict) {
			super(verdict, VerdictAssert.class);
		}

		/**
		 * Requires the derived conclusion.
		 * @param expected expected conclusion
		 * @return this assertion
		 */
		public VerdictAssert hasConclusion(Verdict.Conclusion expected) {
			isNotNull();
			if (actual.conclusion() != expected)
				failWithMessage("Expected conclusion <%s> but was <%s>", expected, actual.conclusion());
			return this;
		}

		/**
		 * Requires retained PASS.
		 * @return this assertion
		 */
		public VerdictAssert isPassed() {
			return hasConclusion(Verdict.Conclusion.PASS);
		}

	}

	/** General assertions on retained evaluation facts, including the selected policy. */
	public static final class EvaluationAssert extends AbstractAssert<EvaluationAssert, EvaluationResult> {

		private EvaluationAssert(EvaluationResult result) {
			super(result, EvaluationAssert.class);
		}

		/**
		 * Requires the original domain conclusion.
		 * @param expected expected conclusion
		 * @return this assertion
		 */
		public EvaluationAssert hasConclusion(Verdict.Conclusion expected) {
			isNotNull();
			assertThat(actual.verdict()).hasConclusion(expected);
			return this;
		}

		/**
		 * Requires PASS and the retained selected policy's successful RELY.
		 * @return this assertion
		 */
		public EvaluationAssert isPassed() {
			isNotNull();
			requirePassed(actual);
			return this;
		}

	}

}
