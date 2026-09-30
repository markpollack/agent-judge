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
import io.github.markpollack.judge.completion.CompletionEvidence;
import io.github.markpollack.judge.acceptance.AcceptanceDecision;
import io.github.markpollack.judge.acceptance.AcceptanceAction;
import io.github.markpollack.judge.acceptance.AppliedPolicy;
import io.github.markpollack.judge.judgment.Judgment;
import io.github.markpollack.judge.judgment.JudgmentReasonCode;
import io.github.markpollack.judge.judgment.JudgmentStatus;
import io.github.markpollack.judge.acceptance.Policies;
import io.github.markpollack.judge.provenance.PolicyRef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class AssessmentCascadeTest {

	private static final CompletionEvidence CONTEXT = CompletionEvidence.builder().request("bounded routing").build();

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
				view -> new AcceptanceDecision(AcceptanceAction.RELY, "use raw"));
		AtomicInteger later = new AtomicInteger();
		Verdict result = cascade(seat(selected), finalSeat(later)).vote(CONTEXT);
		assertThat(result.judgment()).isSameAs(selected);
		assertThat(result.individual()).containsExactly(selected);
		assertThat(result.provenance()).isEqualTo(VerdictProvenance.tier("first", VerdictProvenanceBasis.TIER_OUTCOME));
		assertThat(result.compositeAttempts()).singleElement().satisfies(attempt -> {
			assertThat(attempt.disposition()).isEqualTo(AttemptDisposition.USED);
			assertThat(attempt.verdict().provenance()).isEqualTo(VerdictProvenance.own());
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
			assertThat(result.compositeAttempts().get(0).verdict().judgment()).isSameAs(first);
			assertThat(result.judgment().status()).isEqualTo(escalate ? JudgmentStatus.FAIL : JudgmentStatus.ABSTAIN);
			assertThat(result.provenance().tier()).isEqualTo(escalate ? "last" : "first");
		}
	}

	@Test
	void exhaustedEscalationRetainsFinalAbstentionAndUnfulfilledRequest() {
		Judgment first = apply(Judgment.fail("first violation"), AcceptanceAction.ESCALATE);
		Judgment last = apply(Judgment.pass("last positive"), AcceptanceAction.ESCALATE);
		Verdict result = cascade(seat(first), seat(last)).vote(CONTEXT);
		assertThat(result.judgment()).isSameAs(last);
		assertThat(result.judgment().status()).isEqualTo(JudgmentStatus.ABSTAIN);
		assertThat(((AppliedPolicy) result.judgment().policyApplication()).action())
			.isEqualTo(AcceptanceAction.ESCALATE);
		assertThat(result.provenance().tier()).isEqualTo("last");
		assertThat(result.compositeAttempts()).hasSize(2);
	}

	@Test
	void policyFailureIsTerminalIdentityNotFailedInvocation() {
		Judgment failure = Policies.apply(Judgment.fail("raw violation"), POLICY, view -> {
			throw new IllegalArgumentException("invalid policy configuration");
		});
		AtomicInteger later = new AtomicInteger();
		Verdict result = cascade(seat(failure), finalSeat(later)).vote(CONTEXT);
		assertThat(result.judgment()).isSameAs(failure);
		assertThat(result.judgment().operationalReasonCode()).isEqualTo(JudgmentReasonCode.POLICY_FAILED);
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
		List<Judge<CompletionEvidence>> invalid = List.of(context -> null, context -> {
			throw new IllegalStateException("transport");
		}, context -> Judgment.notApplicable("undeclared"));
		for (Judge<CompletionEvidence> judge : invalid) {
			SimpleJury<CompletionEvidence> first = SimpleJury.<CompletionEvidence>builder()
				.judge(judge)
				.votingStrategy(new AverageVotingStrategy(ErrorPolicy.TREAT_AS_FAIL))
				.build();
			// Ordinary failed invocation reduction semantics are unchanged.
			assertThat(first.vote(CONTEXT).judgment().status()).isEqualTo(JudgmentStatus.FAIL);
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
		Judge<CompletionEvidence> judge = context -> {
			calls.incrementAndGet();
			return apply(Judgment.pass("called"), AcceptanceAction.RELY);
		};
		SimpleJury<CompletionEvidence> two = SimpleJury.<CompletionEvidence>builder()
			.judge(judge)
			.judge(judge)
			.votingStrategy(new AverageVotingStrategy())
			.build();
		Jury<CompletionEvidence> nested = Juries.meta(
				new AverageVotingStrategy(0.5, ErrorPolicy.PROPAGATE, NotApplicablePolicy.TREAT_AS_FAIL),
				new NamedJury<CompletionEvidence>("nested", seat(apply(Judgment.pass("x"), AcceptanceAction.RELY))));
		Jury<CompletionEvidence> forged = new Jury<CompletionEvidence>() {
			@Override
			public Verdict vote(CompletionEvidence context) {
				calls.incrementAndGet();
				return Verdict.single("forged", Judgment.pass("mismatched configuration"));
			}

			@Override
			public List<Judge<CompletionEvidence>> getJudges() {
				return List.of(judge);
			}

			@Override
			public VotingStrategy getVotingStrategy() {
				return new AverageVotingStrategy();
			}
		};
		for (Jury<CompletionEvidence> invalid : List.of(two, nested, forged)) {
			AtomicInteger later = new AtomicInteger();
			Verdict result = cascade(invalid, finalSeat(later)).vote(CONTEXT);
			assertThat(result.judgment().operationalReasonCode()).isEqualTo(JudgmentReasonCode.STAGE_FAILED);
			assertThat(result.compositeAttempts()).singleElement().satisfies(attempt -> {
				assertThat(attempt.disposition()).isEqualTo(AttemptDisposition.STAGE_FAILED);
				assertThat(attempt.failure()).isNotNull();
			});
			assertThat(later.get()).isZero();
		}
		assertThat(calls.get()).isZero();
		Verdict badFinal = cascade(seat(apply(Judgment.pass("first"), AcceptanceAction.ESCALATE)), two).vote(CONTEXT);
		assertThat(badFinal.judgment().status()).isEqualTo(JudgmentStatus.ERROR);
		assertThat(badFinal.compositeAttempts()).hasSize(2);
		assertThat(calls.get()).isZero();
	}

	@Test
	void selectedDescriptionCarriesStableRoutingIdentity() {
		Jury<CompletionEvidence> cascade = cascade(seat(apply(Judgment.pass("first"), AcceptanceAction.ESCALATE)),
				seat(apply(Judgment.fail("negative"), AcceptanceAction.RELY)));
		assertThat(cascade.describe().toPortable().toString()).contains("STOP_ON_RELIED_JUDGMENT", "FINAL_TIER");
		assertThat(TierPolicy.fromWire("STOP_ON_RELIED_JUDGMENT")).isEqualTo(TierPolicy.STOP_ON_RELIED_JUDGMENT);
	}

	private static void assertFailed(Verdict result, int attempts) {
		assertThat(result.judgment().status()).isEqualTo(JudgmentStatus.ERROR);
		assertThat(result.judgment().operationalReasonCode()).isEqualTo(JudgmentReasonCode.STAGE_FAILED);
		assertThat(result.provenance()).isEqualTo(VerdictProvenance.undecided());
		assertThat(result.compositeAttempts()).hasSize(attempts);
		assertThat(result.compositeAttempts().get(attempts - 1).dispositionReason())
			.isEqualTo(DispositionReason.INVALID_TIER_RESULT);
	}

	private static Judgment apply(Judgment raw, AcceptanceAction action) {
		return Policies.apply(raw, POLICY, view -> new AcceptanceDecision(action, "configured consequence"));
	}

	private static SimpleJury<CompletionEvidence> seat(Judgment judgment) {
		Judge<CompletionEvidence> judge = new NamedJudge<CompletionEvidence>(context -> judgment,
				new JudgeMetadata("assessor", "finding", JudgeType.DETERMINISTIC, "outside declared scope"));
		return SimpleJury.<CompletionEvidence>builder()
			.judge(judge)
			.votingStrategy(
					new AverageVotingStrategy(0.9, ErrorPolicy.TREAT_AS_FAIL, NotApplicablePolicy.TREAT_AS_FAIL))
			.build();
	}

	private static SimpleJury<CompletionEvidence> finalSeat(AtomicInteger calls) {
		return SimpleJury.<CompletionEvidence>builder().judge(PolicyJudges.apply(context -> {
			calls.incrementAndGet();
			return Judgment.fail("verified final violation");
		}, POLICY, view -> new AcceptanceDecision(AcceptanceAction.RELY, "usable")))
			.votingStrategy(new AverageVotingStrategy())
			.build();
	}

	private static CascadedJury<CompletionEvidence> cascade(Jury<CompletionEvidence> first,
			Jury<CompletionEvidence> last) {
		return CascadedJury.<CompletionEvidence>builder()
			.tier("first", first, TierPolicy.STOP_ON_RELIED_JUDGMENT)
			.tier("last", last, TierPolicy.FINAL_TIER)
			.build();
	}

}
