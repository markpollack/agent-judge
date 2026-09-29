/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.JudgeMetadata;
import io.github.markpollack.judge.JudgeType;
import io.github.markpollack.judge.NamedJudge;
import io.github.markpollack.judge.PolicyJudges;
import io.github.markpollack.judge.context.JudgmentContext;
import io.github.markpollack.judge.result.Acceptance;
import io.github.markpollack.judge.result.AcceptanceAction;
import io.github.markpollack.judge.result.AppliedPolicy;
import io.github.markpollack.judge.result.Judgment;
import io.github.markpollack.judge.result.JudgmentReasonCode;
import io.github.markpollack.judge.result.JudgmentStatus;
import io.github.markpollack.judge.result.Policies;
import io.github.markpollack.judge.result.PolicyRef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class AssessmentCascadeTest {

	private static final JudgmentContext CONTEXT = JudgmentContext.builder().goal("bounded routing").build();

	private static final PolicyRef POLICY = new PolicyRef("consequence", "1", "a".repeat(64));

	@ParameterizedTest
	@EnumSource(JudgmentStatus.class)
	void usableAndTerminalOutcomesStopWithoutCallingAnotherJudge(JudgmentStatus status) {
		Judgment raw = switch (status) {
			case PASS -> Judgment.pass("established");
			case FAIL -> Judgment.fail("violation");
			case ABSTAIN -> Judgment.abstain("no finding");
			case ERROR -> Judgment.error("provider unavailable");
			case NOT_APPLICABLE -> Judgment.notApplicable("outside scope");
		};
		Judgment selected = Policies.apply(raw, POLICY,
				view -> new Acceptance(AcceptanceAction.USE_ASSESSMENT, "use raw"));
		AtomicInteger later = new AtomicInteger();
		Verdict result = cascade(seat(selected), finalSeat(later)).vote(CONTEXT);
		assertThat(result.aggregated()).isSameAs(selected);
		assertThat(result.individual()).containsExactly(selected);
		assertThat(result.decision()).isEqualTo(Decision.tier("first", DecisionBasis.TIER_OUTCOME));
		assertThat(result.compositeAttempts()).singleElement().satisfies(attempt -> {
			assertThat(attempt.disposition()).isEqualTo(AttemptDisposition.USED);
			assertThat(attempt.verdict().decision()).isEqualTo(Decision.own());
		});
		assertThat(later.get()).isZero();
	}

	@Test
	void explicitWithholdingStopsButEscalationContinues() {
		for (AcceptanceAction action : List.of(AcceptanceAction.ABSTAIN, AcceptanceAction.ESCALATE)) {
			Judgment first = apply(Judgment.pass("positive"), action);
			AtomicInteger later = new AtomicInteger();
			Verdict result = cascade(seat(first), finalSeat(later)).vote(CONTEXT);
			boolean escalate = action == AcceptanceAction.ESCALATE;
			assertThat(later.get()).isEqualTo(escalate ? 1 : 0);
			assertThat(result.compositeAttempts()).hasSize(escalate ? 2 : 1);
			assertThat(result.compositeAttempts().get(0).verdict().aggregated()).isSameAs(first);
			assertThat(result.aggregated().status()).isEqualTo(escalate ? JudgmentStatus.FAIL : JudgmentStatus.ABSTAIN);
			assertThat(result.decision().tier()).isEqualTo(escalate ? "last" : "first");
		}
	}

	@Test
	void exhaustedEscalationRetainsFinalAbstentionAndUnfulfilledRequest() {
		Judgment first = apply(Judgment.fail("first violation"), AcceptanceAction.ESCALATE);
		Judgment last = apply(Judgment.pass("last positive"), AcceptanceAction.ESCALATE);
		Verdict result = cascade(seat(first), seat(last)).vote(CONTEXT);
		assertThat(result.aggregated()).isSameAs(last);
		assertThat(result.aggregated().status()).isEqualTo(JudgmentStatus.ABSTAIN);
		assertThat(((AppliedPolicy) result.aggregated().policyApplication()).action())
			.isEqualTo(AcceptanceAction.ESCALATE);
		assertThat(result.decision().tier()).isEqualTo("last");
		assertThat(result.compositeAttempts()).hasSize(2);
	}

	@Test
	void policyFailureIsTerminalIdentityNotFailedInvocation() {
		Judgment failure = Policies.apply(Judgment.fail("raw violation"), POLICY, view -> {
			throw new IllegalArgumentException("invalid policy configuration");
		});
		AtomicInteger later = new AtomicInteger();
		Verdict result = cascade(seat(failure), finalSeat(later)).vote(CONTEXT);
		assertThat(result.aggregated()).isSameAs(failure);
		assertThat(result.aggregated().operationalReasonCode()).isEqualTo(JudgmentReasonCode.POLICY_FAILED);
		assertThat(result.compositeAttempts().get(0).disposition()).isEqualTo(AttemptDisposition.USED);
		assertThat(later.get()).isZero();
	}

	@Test
	void absentPolicyIsContractErrorOnFirstAndFinalTier() {
		AtomicInteger later = new AtomicInteger();
		assertFailed(cascade(seat(Judgment.pass("unwrapped")), finalSeat(later)).vote(CONTEXT), 1);
		assertThat(later.get()).isZero();
		assertFailed(cascade(seat(apply(Judgment.pass("first"), AcceptanceAction.ESCALATE)),
				seat(Judgment.abstain("no application")))
			.vote(CONTEXT), 2);
	}

	@Test
	void failedInvocationsAndUndeclaredExclusionsCannotBecomeUsableFail() {
		List<Judge<JudgmentContext>> invalid = List.of(context -> null, context -> {
			throw new IllegalStateException("transport");
		}, context -> Judgment.notApplicable("undeclared"));
		for (Judge<JudgmentContext> judge : invalid) {
			SimpleJury<JudgmentContext> first = SimpleJury.<JudgmentContext>builder()
				.judge(judge)
				.votingStrategy(new AverageVotingStrategy(ErrorPolicy.TREAT_AS_FAIL))
				.build();
			// Ordinary failed invocation reduction semantics are unchanged.
			assertThat(first.vote(CONTEXT).aggregated().status()).isEqualTo(JudgmentStatus.FAIL);
			AtomicInteger later = new AtomicInteger();
			Verdict result = cascade(first, finalSeat(later)).vote(CONTEXT);
			assertFailed(result, 1);
			assertThat(result.compositeAttempts().get(0).verdict().individual().get(0).status())
				.isEqualTo(JudgmentStatus.ERROR);
			assertThat(later.get()).isZero();
		}
	}

	@Test
	void invalidDeclaredCardinalityAndNestedOrCustomTierStopBeforeSpendingCalls() {
		AtomicInteger calls = new AtomicInteger();
		Judge<JudgmentContext> judge = context -> {
			calls.incrementAndGet();
			return apply(Judgment.pass("called"), AcceptanceAction.USE_ASSESSMENT);
		};
		SimpleJury<JudgmentContext> two = SimpleJury.<JudgmentContext>builder()
			.judge(judge)
			.judge(judge)
			.votingStrategy(new AverageVotingStrategy())
			.build();
		Jury<JudgmentContext> nested = Juries.meta(
				new AverageVotingStrategy(0.5, ErrorPolicy.PROPAGATE, NotApplicablePolicy.TREAT_AS_FAIL),
				new NamedJury<JudgmentContext>("nested", seat(apply(Judgment.pass("x"), AcceptanceAction.USE_ASSESSMENT))));
		Jury<JudgmentContext> forged = new Jury<JudgmentContext>() {
			@Override
			public Verdict vote(JudgmentContext context) {
				calls.incrementAndGet();
				return Verdict.single("forged", Judgment.pass("mismatched configuration"));
			}

			@Override
			public List<Judge<JudgmentContext>> getJudges() {
				return List.of(judge);
			}

			@Override
			public VotingStrategy getVotingStrategy() {
				return new AverageVotingStrategy();
			}
		};
		for (Jury<JudgmentContext> invalid : List.of(two, nested, forged)) {
			AtomicInteger later = new AtomicInteger();
			Verdict result = cascade(invalid, finalSeat(later)).vote(CONTEXT);
			assertThat(result.aggregated().operationalReasonCode()).isEqualTo(JudgmentReasonCode.STAGE_FAILED);
			assertThat(result.compositeAttempts()).singleElement().satisfies(attempt -> {
				assertThat(attempt.disposition()).isEqualTo(AttemptDisposition.STAGE_FAILED);
				assertThat(attempt.failure()).isNotNull();
			});
			assertThat(later.get()).isZero();
		}
		assertThat(calls.get()).isZero();
		Verdict badFinal = cascade(seat(apply(Judgment.pass("first"), AcceptanceAction.ESCALATE)), two).vote(CONTEXT);
		assertThat(badFinal.aggregated().status()).isEqualTo(JudgmentStatus.ERROR);
		assertThat(badFinal.compositeAttempts()).hasSize(2);
		assertThat(calls.get()).isZero();
	}

	@Test
	void selectedDescriptionCarriesStableRoutingIdentity() {
		Jury<JudgmentContext> cascade = cascade(seat(apply(Judgment.pass("first"), AcceptanceAction.ESCALATE)),
				seat(apply(Judgment.fail("negative"), AcceptanceAction.USE_ASSESSMENT)));
		assertThat(cascade.describe().toPortable().toString()).contains("STOP_ON_USABLE_ASSESSMENT", "FINAL_TIER");
		assertThat(TierPolicy.fromWire("STOP_ON_USABLE_ASSESSMENT")).isEqualTo(TierPolicy.STOP_ON_USABLE_ASSESSMENT);
	}

	private static void assertFailed(Verdict result, int attempts) {
		assertThat(result.aggregated().status()).isEqualTo(JudgmentStatus.ERROR);
		assertThat(result.aggregated().operationalReasonCode()).isEqualTo(JudgmentReasonCode.STAGE_FAILED);
		assertThat(result.decision()).isEqualTo(Decision.undecided());
		assertThat(result.compositeAttempts()).hasSize(attempts);
		assertThat(result.compositeAttempts().get(attempts - 1).dispositionReason())
			.isEqualTo(DispositionReason.INVALID_TIER_RESULT);
	}

	private static Judgment apply(Judgment raw, AcceptanceAction action) {
		return Policies.apply(raw, POLICY, view -> new Acceptance(action, "configured consequence"));
	}

	private static SimpleJury<JudgmentContext> seat(Judgment judgment) {
		Judge<JudgmentContext> judge = new NamedJudge<JudgmentContext>(context -> judgment,
				new JudgeMetadata("assessor", "assessment", JudgeType.DETERMINISTIC, "outside declared scope"));
		return SimpleJury.<JudgmentContext>builder()
			.judge(judge)
			.votingStrategy(
					new AverageVotingStrategy(0.9, ErrorPolicy.TREAT_AS_FAIL, NotApplicablePolicy.TREAT_AS_FAIL))
			.build();
	}

	private static SimpleJury<JudgmentContext> finalSeat(AtomicInteger calls) {
		return SimpleJury.<JudgmentContext>builder().judge(PolicyJudges.apply(context -> {
			calls.incrementAndGet();
			return Judgment.fail("verified final violation");
		}, POLICY, view -> new Acceptance(AcceptanceAction.USE_ASSESSMENT, "usable")))
			.votingStrategy(new AverageVotingStrategy())
			.build();
	}

	private static CascadedJury<JudgmentContext> cascade(Jury<JudgmentContext> first, Jury<JudgmentContext> last) {
		return CascadedJury.<JudgmentContext>builder()
			.tier("first", first, TierPolicy.STOP_ON_USABLE_ASSESSMENT)
			.tier("last", last, TierPolicy.FINAL_TIER)
			.build();
	}

}
