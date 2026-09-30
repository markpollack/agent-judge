/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.jury;

import java.util.*;
import java.util.function.Function;
import io.github.markpollack.judge.RequirementJudge;
import io.github.markpollack.judge.requirement.*;
import io.github.markpollack.judge.judgment.*;

/**
 * Typed evaluator assignments for an all-of parent. Coverage is checked before execution.
 *
 * @param <E> parent evidence type
 */
public final class Assignments<E> {

	private final Requirement<AllOf> parent;

	private final Map<String, Function<E, Verdict>> assignments = new LinkedHashMap<>();

	private Assignments(Requirement<AllOf> parent) {
		this.parent = Objects.requireNonNull(parent);
	}

	/**
	 * Begin assignments for one actual parent.
	 * @param <E> evidence type
	 * @param parent actual all-of requirement
	 * @return mutable configuration, requiring validation before execution
	 */
	public static <E> Assignments<E> forRequirement(Requirement<AllOf> parent) {
		return new Assignments<>(parent);
	}

	/**
	 * Assign a requirement-aware judge using shared evidence.
	 * @param <S> child specification type
	 * @param child constituent reference
	 * @param judge evaluator
	 * @return this configuration
	 */
	public <S> Assignments<E> judge(Requirement<S> child, RequirementJudge<S, E> judge) {
		return judge(child, Function.identity(), judge);
	}

	/**
	 * Assign a requirement-aware jury using shared evidence.
	 * @param <S> child specification type
	 * @param child constituent reference
	 * @param jury evaluator
	 * @return this configuration
	 */
	public <S> Assignments<E> jury(Requirement<S> child, RequirementJury<S, E> jury) {
		return jury(child, Function.identity(), jury);
	}

	/**
	 * Assign a judge with explicit typed evidence selection.
	 * @param <S> child specification type
	 * @param <C> selected evidence type
	 * @param child constituent reference
	 * @param selectEvidence explicit selector
	 * @param judge requirement-aware evaluator
	 * @return this configuration
	 */
	public <S, C> Assignments<E> judge(Requirement<S> child, Function<E, C> selectEvidence,
			RequirementJudge<S, C> judge) {
		Objects.requireNonNull(selectEvidence, "selector");
		Objects.requireNonNull(judge, "judge");
		Requirement<S> actual = constituent(child);
		return add(actual,
				evidence -> Verdict
					.single(actual.id(),
							Objects.requireNonNull(judge.judge(actual,
									Objects.requireNonNull(selectEvidence.apply(evidence), "selected evidence")),
									"judge returned null"))
					.forRequirement(actual));
	}

	/**
	 * Assign a jury with explicit typed evidence selection; preserve its complete
	 * verdict.
	 * @param <S> child specification type
	 * @param <C> selected evidence type
	 * @param child constituent reference
	 * @param selectEvidence explicit selector
	 * @param jury requirement-aware jury
	 * @return this configuration
	 */
	public <S, C> Assignments<E> jury(Requirement<S> child, Function<E, C> selectEvidence, RequirementJury<S, C> jury) {
		Objects.requireNonNull(selectEvidence, "selector");
		Objects.requireNonNull(jury, "jury");
		Requirement<S> actual = constituent(child);
		return add(actual,
				evidence -> Objects.requireNonNull(
						jury.vote(actual, Objects.requireNonNull(selectEvidence.apply(evidence), "selected evidence")),
						"jury returned null"));
	}

	private <S> Requirement<S> constituent(Requirement<S> reference) {
		Objects.requireNonNull(reference, "child");
		for (Requirement<?> candidate : parent.specification().constituents()) {
			if (candidate.id().equals(reference.id())) {
				if (!candidate.equals(reference))
					throw new IllegalArgumentException(
							"Constituent reference differs from parent entry: " + reference.id());
				// Complete structural equality includes the native specification. Execute
				// the parent's instance.
				@SuppressWarnings("unchecked")
				Requirement<S> actual = (Requirement<S>) candidate;
				return actual;
			}
		}
		throw new IllegalArgumentException("Unrelated constituent: " + reference.id());
	}

	private Assignments<E> add(Requirement<?> child, Function<E, Verdict> invocation) {
		if (assignments.putIfAbsent(child.id(), invocation) != null)
			throw new IllegalArgumentException("Duplicate assignment: " + child.id());
		return this;
	}

	/**
	 * Check complete coverage and freeze the execution plan. No selector or evaluator
	 * executes here.
	 * @return immutable prepared evaluation
	 * @throws IllegalStateException if any constituent lacks an assignment
	 */
	public Prepared<E> validate() {
		if (parent.specification().constituents().size() > CompositeExecutionScope.MAX_ATTEMPTS)
			throw new IllegalArgumentException(
					"All-of roster exceeds the composite attempt limit of " + CompositeExecutionScope.MAX_ATTEMPTS);
		for (Requirement<?> child : parent.specification().constituents())
			NamedJury.requireValidName(child.id());
		for (Requirement<?> child : parent.specification().constituents())
			if (!assignments.containsKey(child.id()))
				throw new IllegalStateException("Missing assignment: " + child.id());
		return new Prepared<>(parent, Map.copyOf(assignments));
	}

	/**
	 * A validated operation using its captured parent directly.
	 *
	 * @param <E> evidence type
	 */
	public static final class Prepared<E> {

		private final Requirement<AllOf> parent;

		private final Map<String, Function<E, Verdict>> assignments;

		private Prepared(Requirement<AllOf> parent, Map<String, Function<E, Verdict>> assignments) {
			this.parent = parent;
			this.assignments = assignments;
		}

		/**
		 * Evaluate every required constituent, preserving failures and nested verdicts.
		 * @param evidence parent evidence
		 * @return complete parent verdict
		 */
		public Verdict vote(E evidence) {
			Objects.requireNonNull(evidence, "evidence");
			return CompositeExecutionScope.withinCompositeVote(() -> execute(evidence));
		}

		private Verdict execute(E evidence) {
			var attempts = new ArrayList<CompositeAttempt>();
			boolean failed = false, incomplete = false;
			if (parent.specification().applicable())
				for (Requirement<?> child : parent.specification().constituents()) {
					Verdict verdict;
					try {
						verdict = CompositeExecutionScope.invokeChild(child.id(),
								() -> assignments.get(child.id()).apply(evidence));
					}
					catch (CompositeLimitExceededException ex) {
						throw ex;
					}
					catch (Exception ex) {
						SimpleJury.preserveCancellation(ex);
						incomplete = true;
						attempts.add(CompositeAttempt.executionFailed(child.id(), CompositeRelation.CONSTITUENT, null,
								new CompositeFailure(CompositeFailureCode.JURY_EXECUTION_FAILED, ex)));
						continue;
					}
					verdict = verdict.forRequirement(child);
					Verdict.Conclusion conclusion = verdict.conclusion();
					failed |= conclusion == Verdict.Conclusion.FAIL;
					incomplete |= conclusion != Verdict.Conclusion.PASS;
					if (verdict.provenance().kind() == VerdictProvenanceKind.UNDECIDED)
						attempts.add(CompositeAttempt.stageFailed(child.id(), CompositeRelation.CONSTITUENT, null,
								DispositionReason.CHILD_UNDECIDED, verdict));
					else
						attempts.add(CompositeAttempt.used(child.id(), CompositeRelation.CONSTITUENT, null, verdict));
				}
			Judgment judgment = !parent.specification().applicable()
					? Judgment.notApplicable("Parent specification declares non-applicability")
					: failed ? Judgment.fail("A required constituent failed")
							: incomplete ? Judgment.abstain("Not every required constituent established PASS")
									: Judgment.pass("Every required constituent passed");
			return Verdict.builder()
				.requirement(parent)
				.judgment(judgment)
				.declaredCardinality(parent.specification().constituents().size())
				.provenance(new VerdictProvenance(VerdictProvenanceKind.CONSTITUENTS, null, null))
				.compositeAttempts(attempts)
				.build();
		}

	}

}
