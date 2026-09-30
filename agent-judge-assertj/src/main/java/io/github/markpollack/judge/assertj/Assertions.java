/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.assertj;

import java.util.Objects;
import java.util.function.Supplier;
import org.assertj.core.api.AbstractAssert;
import org.jspecify.annotations.Nullable;
import io.github.markpollack.judge.*;
import io.github.markpollack.judge.requirement.Requirement;
import io.github.markpollack.judge.jury.*;
import io.github.markpollack.judge.evaluation.*;
import io.github.markpollack.judge.policy.*;
import io.github.markpollack.judge.assertions.*;

/** Thin assertions over retained facts and staged, once-only evaluation. */
public final class Assertions {

	private Assertions() {
	}

	/**
	 * Begin a requirement-aware assertion.
	 * @param <S> specification type
	 * @param requirement actual specification
	 * @return evaluator selection
	 */
	public static <S> RequirementStage<S> assertThat(Requirement<S> requirement) {
		return new RequirementStage<>(requirement);
	}

	/**
	 * Inspect a retained verdict without execution.
	 * @param verdict complete verdict
	 * @return verdict assertion
	 */
	public static VerdictAssert assertThat(Verdict verdict) {
		return new VerdictAssert(verdict);
	}

	/**
	 * Inspect a retained execution without execution.
	 * @param result complete result
	 * @return retained assertion
	 */
	public static EvaluationAssert assertThat(EvaluationResult result) {
		return new EvaluationAssert(result);
	}

	/**
	 * Begin an evidence-only check without inventing a Requirement.
	 * @param <E> evidence type
	 * @param evidence actual evidence
	 * @return evidence-only evaluator selection
	 */
	public static <E> EvidenceOnlyStage<E> assertThatEvidence(E evidence) {
		return new EvidenceOnlyStage<>(evidence);
	}

	/**
	 * Requirement selects a sibling requirement-aware evaluator.
	 *
	 * @param <S> native specification
	 */
	public static final class RequirementStage<S> extends AbstractAssert<RequirementStage<S>, Requirement<S>> {

		private RequirementStage(Requirement<S> requirement) {
			super(Objects.requireNonNull(requirement), RequirementStage.class);
		}

		/**
		 * Choose a requirement-aware Judge. Ordinary evidence-only judges are not
		 * accepted here.
		 * @param <E> evidence type
		 * @param judge evaluator
		 * @return evidence stage
		 */
		public <E> EvidenceStage<E> judgedBy(RequirementJudge<S, E> judge) {
			Objects.requireNonNull(judge);
			return evidence -> new SatisfactionStage(() -> Evaluations.evaluate(actual, judge, evidence),
					descriptionText(), null);
		}

		/**
		 * Choose a requirement-aware Jury; retain its complete verdict.
		 * @param <E> evidence type
		 * @param jury jury
		 * @return evidence stage
		 */
		public <E> EvidenceStage<E> judgedBy(RequirementJury<S, E> jury) {
			Objects.requireNonNull(jury);
			return evidence -> new SatisfactionStage(() -> Evaluations.evaluate(actual, jury, evidence),
					descriptionText(), null);
		}

	}

	/**
	 * Require typed evidence before a terminal or policy can be chosen.
	 *
	 * @param <E> evidence type
	 */
	public interface EvidenceStage<E> {

		/**
		 * Supply actual evidence, without executing yet.
		 * @param evidence evidence
		 * @return terminal stage
		 */
		SatisfactionStage withEvidence(E evidence);

	}

	/**
	 * An immutable configured evaluation; repeated terminals inspect its cached result.
	 */
	public static final class SatisfactionStage {

		private final Supplier<EvaluationResult> evaluation;

		private final String description;

		private final @Nullable Policy policy;

		private @Nullable EvaluationResult result;

		private SatisfactionStage(Supplier<EvaluationResult> evaluation, String description, @Nullable Policy policy) {
			this.evaluation = evaluation;
			this.description = description;
			this.policy = policy;
		}

		/**
		 * Configure a policy before execution.
		 * @param policy requested policy
		 * @return configured stage
		 * @throws IllegalStateException after this stage has already executed
		 */
		public synchronized SatisfactionStage withPolicy(Policy policy) {
			if (result != null)
				throw new IllegalStateException(
						"Evaluation already completed; apply another policy explicitly to its retained Verdict");
			return new SatisfactionStage(evaluation, description, Objects.requireNonNull(policy));
		}

		/**
		 * Execute once and retain the result, without asserting satisfaction.
		 * @return retained complete evaluation
		 */
		public synchronized EvaluationResult evaluate() {
			if (result == null) {
				EvaluationResult completed = evaluation.get();
				result = policy == null ? completed : Evaluations.apply(completed.verdict(), policy);
			}
			return result;
		}

		/**
		 * Establish satisfaction from retained facts. Repetition makes no further calls.
		 */
		public void isSatisfied() {
			try {
				RequirementAssertions.requireSatisfied(evaluate());
			}
			catch (RequirementAssertionError ex) {
				if (description.isEmpty())
					throw ex;
				AssertionError described = new AssertionError("[" + description + "] " + ex.getMessage(), ex);
				throw described;
			}
		}

	}

	/**
	 * Evidence-only selection.
	 *
	 * @param <E> evidence type
	 */
	public static final class EvidenceOnlyStage<E> {

		private final E evidence;

		private EvidenceOnlyStage(E evidence) {
			this.evidence = Objects.requireNonNull(evidence);
		}

		/**
		 * Evaluate an ordinary Judge once.
		 * @param judge evaluator
		 * @return retained result assertion
		 */
		public EvaluationAssert judgedBy(Judge<E> judge) {
			return assertThat(Evaluations.evaluate(judge, evidence));
		}

		/**
		 * Execute a complete evidence-only Jury once.
		 * @param jury jury
		 * @return retained result assertion
		 */
		public EvaluationAssert judgedBy(Jury<E> jury) {
			return assertThat(Evaluations.evaluate(jury, evidence));
		}

	}

	/** Neutral domain conclusion assertion. */
	public static final class VerdictAssert extends AbstractAssert<VerdictAssert, Verdict> {

		private VerdictAssert(Verdict actual) {
			super(actual, VerdictAssert.class);
		}

		/**
		 * Require the derived conclusion.
		 * @param expected expected conclusion
		 * @return this assertion
		 */
		public VerdictAssert hasConclusion(Verdict.Conclusion expected) {
			isNotNull();
			if (actual.conclusion() != expected)
				failWithMessage("Expected conclusion <%s> but was <%s>", expected, actual.conclusion());
			return this;
		}

	}

	/** Assertions over a completed invocation, never execution. */
	public static final class EvaluationAssert extends AbstractAssert<EvaluationAssert, EvaluationResult> {

		private EvaluationAssert(EvaluationResult actual) {
			super(actual, EvaluationAssert.class);
		}

		/**
		 * Require the neutral verdict conclusion.
		 * @param expected expected conclusion
		 * @return this assertion
		 */
		public EvaluationAssert hasConclusion(Verdict.Conclusion expected) {
			isNotNull();
			assertThat(actual.verdict()).hasConclusion(expected);
			return this;
		}

		/**
		 * Require that the check passed, independently of whether a Requirement was
		 * supplied.
		 * @return this assertion
		 */
		public EvaluationAssert isPassed() {
			return hasConclusion(Verdict.Conclusion.PASS);
		}

		/**
		 * Require associated requirement satisfaction and any requested reliance
		 * decision.
		 * @return this assertion
		 */
		public EvaluationAssert isSatisfied() {
			isNotNull();
			RequirementAssertions.requireSatisfied(actual);
			return this;
		}

	}

}
