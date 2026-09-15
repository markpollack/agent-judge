/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.Judges;
import io.github.markpollack.judge.context.JudgmentContext;
import io.github.markpollack.judge.description.KeySource;
import io.github.markpollack.judge.result.Judgment;
import io.github.markpollack.judge.result.JudgmentReasonCode;
import io.github.markpollack.judge.result.JudgmentStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Part C: a jury contains its own failures instead of taking the run down with them.
 *
 * <p>
 * Before containment, a strategy that threw discarded every judge in its jury and, inside a
 * cascade, collapsed the enclosing tier: a run where nine judges succeeded and one reduction
 * broke reported nothing at all. What replaces that is loud rather than quiet — an
 * {@code ERROR aggregation_failed} verdict, marked undecided, carrying no evidence block
 * because there is no reduction to describe, with every judge's own result intact.
 * </p>
 *
 * <p>
 * The thing containment must never do is invent a finding. A contained failure produces no
 * synthetic FAIL and no score of zero, because a rejection nobody can tell apart from a real one
 * is worse than no rejection at all.
 * </p>
 */
@DisplayName("Containment")
class ContainmentTest {

	private static final JudgmentContext CONTEXT = JudgmentContext.builder().goal("contain failures").build();

	private static final Judgment PASS = Judgment.pass("qualified");

	private static final Judgment FAIL = Judgment.fail("did not qualify");

	/** A strategy that does whatever the test tells it to, instead of aggregating. */
	private record Misbehaving(String name, java.util.function.Supplier<Judgment> behaviour)
			implements VotingStrategy {

		@Override
		public Judgment aggregate(List<Judgment> judgments, Map<String, Double> weights) {
			return this.behaviour.get();
		}

		@Override
		public String getName() {
			return this.name;
		}

		@Override
		public NotApplicablePolicy notApplicablePolicy() {
			return NotApplicablePolicy.EXCLUDE;
		}

	}

	private static Jury juryWith(VotingStrategy strategy) {
		return SimpleJury.builder()
			.judge(Judges.named(context -> PASS, "first"))
			.judge(Judges.named(context -> FAIL, "second"))
			.votingStrategy(strategy)
			.build();
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> evidenceOf(Judgment judgment) {
		return (Map<String, Object>) judgment.metadata().get(Judgment.AGGREGATION_KEY);
	}

	@Nested
	@DisplayName("The strategy boundary")
	class StrategyBoundary {

		@Test
		@DisplayName("a strategy that throws becomes an undecided error, and every judge still votes")
		void aThrowIsContained() {
			Verdict verdict = juryWith(new Misbehaving("broken", () -> {
				throw new IllegalStateException("token=opaque-secret");
			})).vote(CONTEXT);

			assertThat(verdict.aggregated().status()).isEqualTo(JudgmentStatus.ERROR);
			assertThat(verdict.aggregated().reasonCode()).isEqualTo(JudgmentReasonCode.AGGREGATION_FAILED);
			assertThat(verdict.aggregated().reasoning()).contains("broken").contains("IllegalStateException");
			assertThat(verdict.decision()).isEqualTo(Decision.undecided());
			assertThat(verdict.individual()).containsExactly(PASS, FAIL);
			assertThat(verdict.individualByName()).containsKeys("first", "second");
			assertThat(verdict.seats()).containsExactly(new Seat(0, "first", KeySource.DECLARED),
					new Seat(1, "second", KeySource.DECLARED));
		}

		@Test
		@DisplayName("a strategy that returns nothing is contained the same way")
		void aNullIsContained() {
			Verdict verdict = juryWith(new Misbehaving("silent", () -> null)).vote(CONTEXT);

			assertThat(verdict.aggregated().reasonCode()).isEqualTo(JudgmentReasonCode.AGGREGATION_FAILED);
			assertThat(verdict.aggregated().reasoning()).contains("returned no aggregate");
			assertThat(verdict.decision()).isEqualTo(Decision.undecided());
		}

		@Test
		@DisplayName("an exclusion no seat was entitled to is contained rather than honoured")
		void anUnauthorizedExclusionIsContained() {
			Verdict verdict = juryWith(
					new Misbehaving("presumptuous", () -> Judgment.notApplicable("I have decided this does not apply")))
				.vote(CONTEXT);

			assertThat(verdict.aggregated().reasonCode()).isEqualTo(JudgmentReasonCode.AGGREGATION_FAILED);
			assertThat(verdict.aggregated().reasoning()).contains("NOT_APPLICABLE");
			assertThat(verdict.decision()).isEqualTo(Decision.undecided());
		}

		@Test
		@DisplayName("an ERROR code outside the reduction's own vocabulary is contained")
		void aMisattributedErrorCodeIsContained() {
			for (JudgmentReasonCode code : List.of(JudgmentReasonCode.JUDGE_FAILED, JudgmentReasonCode.JUDGE_REPORTED,
					JudgmentReasonCode.STAGE_FAILED, JudgmentReasonCode.NO_TIER_DECIDED,
					JudgmentReasonCode.AGGREGATION_FAILED, JudgmentReasonCode.UNDECLARED_NOT_APPLICABLE)) {
				Verdict verdict = juryWith(new Misbehaving("misattributing", () -> Judgment.error(code, "boom")))
					.vote(CONTEXT);

				assertThat(verdict.aggregated().reasoning()).as("%s is not a strategy's to emit", code)
					.contains("names a cause outside the reduction");
			}
		}

		@Test
		@DisplayName("the two codes a reduction really can report are allowed through")
		void theAllowedAggregateErrorsPassThrough() {
			Verdict refused = juryWith(new Misbehaving("refusing",
					() -> Judgment.error(JudgmentReasonCode.NOT_APPLICABLE_REFUSED, "an input was excluded")))
				.vote(CONTEXT);
			Verdict propagated = juryWith(new Misbehaving("propagating",
					() -> Judgment.propagatedError(Map.of(JudgmentReasonCode.JUDGE_REPORTED, 1L), "an input errored")))
				.vote(CONTEXT);

			assertThat(refused.aggregated().reasonCode()).isEqualTo(JudgmentReasonCode.NOT_APPLICABLE_REFUSED);
			assertThat(refused.decision()).as("a refusal is machinery, so nothing was decided")
				.isEqualTo(Decision.undecided());
			assertThat(propagated.aggregated().reasonCode()).isEqualTo(JudgmentReasonCode.ERRORS_PROPAGATED);
			assertThat(propagated.decision()).as("propagation is this jury's own policy outcome")
				.isEqualTo(Decision.own());
		}

		@Test
		@DisplayName("the allow-list is complete: every declared status has been classified here")
		void theAllowListIsComplete() {
			// If a status is added, this fails until someone decides whether a strategy may
			// produce it. That decision is the point; silently inheriting a branch is not.
			for (JudgmentStatus status : JudgmentStatus.values()) {
				assertThat(List.of(JudgmentStatus.PASS, JudgmentStatus.FAIL, JudgmentStatus.ABSTAIN,
						JudgmentStatus.NOT_APPLICABLE, JudgmentStatus.ERROR))
					.as("%s must be classified by the aggregate allow-list", status)
					.contains(status);
			}
		}

		@Test
		@DisplayName("a strategy whose own name is unusable is still named in the diagnostic")
		void theStrategyIsNamedEvenWhenItCannotNameItself() {
			VotingStrategy nameless = new VotingStrategy() {
				@Override
				public Judgment aggregate(List<Judgment> judgments, Map<String, Double> weights) {
					throw new IllegalStateException("boom");
				}

				@Override
				public String getName() {
					throw new IllegalStateException("cannot name myself either");
				}
			};

			Verdict verdict = juryWith(nameless).vote(CONTEXT);

			assertThat(verdict.aggregated().reasoning()).contains(nameless.getClass().getName());
		}

		@Test
		@DisplayName("a contained failure manufactures no finding: no synthetic FAIL, no score of zero")
		void containmentInventsNothing() {
			Judgment aggregate = juryWith(new Misbehaving("broken", () -> {
				throw new IllegalStateException("boom");
			})).vote(CONTEXT).aggregated();

			assertThat(aggregate.status()).isNotEqualTo(JudgmentStatus.FAIL);
			assertThat(aggregate.score()).isNull();
			assertThat(aggregate.effectiveScore()).isEmpty();
			assertThat(evidenceOf(aggregate)).as("there is no reduction to describe").isNull();
		}

		@Test
		@DisplayName("an Error is not a judgment any jury can report on, so it is not caught")
		void errorsAreNotCaught() {
			Jury jury = juryWith(new Misbehaving("fatal", () -> {
				throw new StackOverflowError("simulated");
			}));

			assertThatThrownBy(() -> jury.vote(CONTEXT)).isInstanceOf(StackOverflowError.class);
		}

	}

	@Nested
	@DisplayName("Meta-jury member stage failures")
	class MemberStages {

		@Test
		@DisplayName("a member that throws is a failed stage, and the members that worked are kept")
		void aThrowingMemberIsAStageFailure() {
			Verdict verdict = Juries
				.meta(new ConsensusStrategy(ErrorPolicy.IGNORE, NotApplicablePolicy.EXCLUDE),
						new NamedJury("first", returning(Verdict.single("a", PASS))),
						new NamedJury("broken", throwing(new IllegalArgumentException("boom"))),
						new NamedJury("last", returning(Verdict.single("b", PASS))))
				.vote(CONTEXT);

			assertThat(verdict.aggregated().reasonCode()).isEqualTo(JudgmentReasonCode.STAGE_FAILED);
			assertThat(verdict.decision()).isEqualTo(Decision.undecided());
			assertThat(verdict.compositeAttempts()).extracting(CompositeAttempt::name, CompositeAttempt::disposition,
					CompositeAttempt::dispositionReason)
				.containsExactly(
						org.assertj.core.api.Assertions.tuple("first", AttemptDisposition.USED, null),
						org.assertj.core.api.Assertions.tuple("broken", AttemptDisposition.STAGE_FAILED,
								DispositionReason.EXECUTION_FAILED),
						org.assertj.core.api.Assertions.tuple("last", AttemptDisposition.USED, null));
			assertThat(verdict.individualByName()).containsOnlyKeys("first", "last");
			assertThat(verdict.seats()).as("positions are the configured ones, so the gap records the failure")
				.extracting(Seat::position)
				.containsExactly(0, 2);
		}

		@Test
		@DisplayName("a member that determined nothing is a failed stage, and its actual verdict is kept")
		void anUndecidedMemberIsAStageFailure() {
			Verdict undecided = undecidedVerdict();

			Verdict verdict = Juries
				.meta(new ConsensusStrategy(ErrorPolicy.IGNORE, NotApplicablePolicy.EXCLUDE),
						new NamedJury("broken", returning(undecided)),
						new NamedJury("last", returning(Verdict.single("b", PASS))))
				.vote(CONTEXT);

			CompositeAttempt attempt = verdict.compositeAttempts().get(0);
			assertThat(attempt.dispositionReason()).isEqualTo(DispositionReason.CHILD_UNDECIDED);
			assertThat(attempt.verdict()).as("the child's claim is not rewritten").isSameAs(undecided);
			assertThat(verdict.aggregated().reasonCode()).isEqualTo(JudgmentReasonCode.STAGE_FAILED);
		}

		@Test
		@DisplayName("a member excluding itself without declaring it may is a failed stage")
		void anUndeclaredExclusionIsAStageFailure() {
			Verdict excluded = Verdict.builder()
				.aggregated(Judgment.notApplicable("nothing here applies"))
				.decision(Decision.own())
				.build();

			Verdict verdict = Juries
				.meta(new ConsensusStrategy(ErrorPolicy.IGNORE, NotApplicablePolicy.EXCLUDE),
						new NamedJury("presumptuous", returning(excluded)),
						new NamedJury("last", returning(Verdict.single("b", PASS))))
				.vote(CONTEXT);

			CompositeAttempt attempt = verdict.compositeAttempts().get(0);
			assertThat(attempt.disposition()).isEqualTo(AttemptDisposition.STAGE_FAILED);
			assertThat(attempt.dispositionReason()).isEqualTo(DispositionReason.UNDECLARED_NOT_APPLICABLE);
			assertThat(attempt.verdict().aggregated().status()).isEqualTo(JudgmentStatus.NOT_APPLICABLE);
			assertThat(verdict.aggregated().reasonCode()).isEqualTo(JudgmentReasonCode.STAGE_FAILED);
		}

		@ParameterizedTest
		@EnumSource(ErrorPolicy.class)
		@DisplayName("a member's own policy error is ordinary strategy input, governed by the origin rule")
		void anOwnErrorIsStrategyInput(ErrorPolicy errorPolicy) {
			Judgment propagated = Judgment.propagatedError(Map.of(JudgmentReasonCode.JUDGE_REPORTED, 1L),
					"1 of 1 judgments errored and the error policy is propagate");
			Verdict member = Verdict.builder()
				.aggregated(propagated)
				.decision(Decision.own())
				.build();

			Verdict verdict = Juries
				.meta(new ConsensusStrategy(errorPolicy, NotApplicablePolicy.EXCLUDE),
						new NamedJury("propagating", returning(member)),
						new NamedJury("healthy", returning(Verdict.single("b", PASS))))
				.vote(CONTEXT);

			assertThat(verdict.compositeAttempts().get(0).disposition()).as("it determined an outcome")
				.isEqualTo(AttemptDisposition.USED);
			assertThat(verdict.aggregated().reasonCode()).isNotEqualTo(JudgmentReasonCode.STAGE_FAILED);
		}

		@ParameterizedTest
		@EnumSource(ErrorPolicy.class)
		@DisplayName("a machinery error reaching a member's strategy is never charged to the subject")
		void aMachineryErrorMemberIsNeverScored(ErrorPolicy errorPolicy) {
			Verdict machinery = Verdict.builder()
				.aggregated(Judgment.error(JudgmentReasonCode.AGGREGATION_FAILED, "the strategy threw"))
				.decision(Decision.undecided())
				.build();

			Verdict verdict = Juries
				.meta(new AllMustPassStrategy(errorPolicy, NotApplicablePolicy.EXCLUDE),
						new NamedJury("broken", returning(machinery)),
						new NamedJury("healthy", returning(Verdict.single("b", PASS))))
				.vote(CONTEXT);

			assertThat(verdict.aggregated().status()).as("a machinery failure never becomes a rejection")
				.isNotEqualTo(JudgmentStatus.FAIL);
			assertThat(verdict.aggregated().reasonCode()).isEqualTo(JudgmentReasonCode.STAGE_FAILED);
		}

		@ParameterizedTest
		@MethodSource("io.github.markpollack.judge.jury.ContainmentTest#stageFailureMatrix")
		@DisplayName("every machinery cause, every stage-failure reason, every error policy: never a rejection")
		void theWholeMatrix(ErrorPolicy errorPolicy, JudgmentReasonCode machineryCode, DispositionReason reason) {
			Jury member = memberFailing(reason, machineryCode);

			Verdict verdict = Juries
				.meta(new AllMustPassStrategy(errorPolicy, NotApplicablePolicy.EXCLUDE),
						new NamedJury("broken", member),
						new NamedJury("healthy", returning(Verdict.single("b", PASS))))
				.vote(CONTEXT);

			CompositeAttempt attempt = verdict.compositeAttempts().get(0);
			assertThat(attempt.disposition()).as("%s / %s / %s", errorPolicy, machineryCode, reason)
				.isEqualTo(AttemptDisposition.STAGE_FAILED);
			assertThat(attempt.dispositionReason()).isEqualTo(reason);
			assertThat(verdict.aggregated().reasonCode()).isEqualTo(JudgmentReasonCode.STAGE_FAILED);
			assertThat(verdict.decision()).isEqualTo(Decision.undecided());
			assertThat(verdict.aggregated().status()).as("a contained failure never becomes a rejection")
				.isNotEqualTo(JudgmentStatus.FAIL);
			assertThat(verdict.aggregated().score()).as("and never a score of zero").isNull();
			assertThat(verdict.individualByName()).as("the members that worked are kept")
				.containsOnlyKeys("healthy");
		}

		/** A member that fails its stage in the way the matrix asks for. */
		private Jury memberFailing(DispositionReason reason, JudgmentReasonCode machineryCode) {
			return switch (reason) {
				case EXECUTION_FAILED -> throwing(new IllegalStateException("boom"));
				case CHILD_UNDECIDED -> returning(Verdict.builder()
					.aggregated(Judgment.error(machineryCode, "the stage reached no outcome"))
					.decision(Decision.undecided())
					.build());
				case UNDECLARED_NOT_APPLICABLE -> returning(Verdict.builder()
					.aggregated(Judgment.notApplicable("nothing here applies"))
					.decision(Decision.own())
					.build());
			};
		}

		@Test
		@DisplayName("a nested meta-jury contains its child's failure rather than inheriting it")
		void nestedMetaJuriesContainTheirChildren() {
			Jury inner = Juries.meta(new ConsensusStrategy(ErrorPolicy.IGNORE, NotApplicablePolicy.EXCLUDE),
					new NamedJury("broken", throwing(new IllegalStateException("boom"))));
			Jury outer = Juries.meta(new ConsensusStrategy(ErrorPolicy.IGNORE, NotApplicablePolicy.EXCLUDE),
					new NamedJury("inner", inner), new NamedJury("healthy", returning(Verdict.single("b", PASS))));

			Verdict verdict = outer.vote(CONTEXT);

			assertThat(verdict.compositeAttempts().get(0).dispositionReason())
				.isEqualTo(DispositionReason.CHILD_UNDECIDED);
			assertThat(verdict.compositeAttempts().get(0).verdict().aggregated().reasonCode())
				.isEqualTo(JudgmentReasonCode.STAGE_FAILED);
			assertThat(verdict.aggregated().reasonCode()).isEqualTo(JudgmentReasonCode.STAGE_FAILED);
		}

	}

	// ==================== Helpers ====================

	/**
	 * Every combination the design pins: four error policies, four machinery causes, three ways a
	 * stage can fail. The matrix exists because the rule it checks has no exceptions, and a rule
	 * with no exceptions is exactly the kind that acquires one quietly.
	 * @return the arguments
	 */
	static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> stageFailureMatrix() {
		List<JudgmentReasonCode> machinery = List.of(JudgmentReasonCode.AGGREGATION_FAILED,
				JudgmentReasonCode.STAGE_FAILED, JudgmentReasonCode.NO_TIER_DECIDED,
				JudgmentReasonCode.NOT_APPLICABLE_REFUSED);
		java.util.List<org.junit.jupiter.params.provider.Arguments> arguments = new java.util.ArrayList<>();
		for (ErrorPolicy errorPolicy : ErrorPolicy.values()) {
			for (JudgmentReasonCode code : machinery) {
				for (DispositionReason reason : DispositionReason.values()) {
					arguments.add(org.junit.jupiter.params.provider.Arguments.of(errorPolicy, code, reason));
				}
			}
		}
		return arguments.stream();
	}

	static Verdict undecidedVerdict() {
		return Verdict.builder()
			.aggregated(Judgment.error(JudgmentReasonCode.AGGREGATION_FAILED, "the strategy threw"))
			.decision(Decision.undecided())
			.build();
	}

	static Jury returning(Verdict verdict) {
		return new Jury() {
			@Override
			public List<Judge> getJudges() {
				return List.of();
			}

			@Override
			public VotingStrategy getVotingStrategy() {
				return new ConsensusStrategy();
			}

			@Override
			public Verdict vote(JudgmentContext context) {
				return verdict;
			}
		};
	}

	static Jury throwing(RuntimeException failure) {
		return new Jury() {
			@Override
			public List<Judge> getJudges() {
				return List.of();
			}

			@Override
			public VotingStrategy getVotingStrategy() {
				return new ConsensusStrategy();
			}

			@Override
			public Verdict vote(JudgmentContext context) {
				throw failure;
			}
		};
	}

}
