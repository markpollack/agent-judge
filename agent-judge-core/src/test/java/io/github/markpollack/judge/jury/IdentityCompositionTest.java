/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.JudgeMetadata;
import io.github.markpollack.judge.JudgeType;
import io.github.markpollack.judge.NamedJudge;
import io.github.markpollack.judge.result.Policies;
import io.github.markpollack.judge.result.Acceptance;
import io.github.markpollack.judge.result.JudgmentReasonCode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import io.github.markpollack.judge.context.JudgmentContext;
import io.github.markpollack.judge.result.AcceptanceAction;
import io.github.markpollack.judge.result.AppliedPolicy;
import io.github.markpollack.judge.result.Assessment;
import io.github.markpollack.judge.result.Judgment;
import io.github.markpollack.judge.result.JudgmentStatus;
import io.github.markpollack.judge.result.NumericAssessment;
import io.github.markpollack.judge.result.NumericKind;
import io.github.markpollack.judge.result.PolicyRef;
import io.github.markpollack.judge.result.QualityDirection;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdentityCompositionTest {

	private static final JudgmentContext CONTEXT = JudgmentContext.builder().goal("identity").build();

	private static final PolicyRef POLICY = new PolicyRef("critical", "1", "a".repeat(64));

	@Test
	void negativeHighOrdinalAssessmentIsNotReversedByOneSeatNumericStrategy() {
		Judgment negative = new Judgment(JudgmentStatus.FAIL, new Assessment(null,
				new NumericAssessment(4, NumericKind.ORDINAL_EXPECTATION, "impact:v1", 0, 4,
						List.of("none", "low", "medium", "high", "critical"), QualityDirection.INCREASING),
				null), null, null, null, "verified violation", List.of(), null, null, Map.of());
		Verdict verdict = SimpleJury.builder()
			.judge(context -> negative)
			.votingStrategy(new AverageVotingStrategy())
			.build()
			.vote(CONTEXT);
		assertThat(verdict.aggregated()).isEqualTo(negative);
	}

	@Test
	void oneSeatDoesNotLoseEscalationIntent() {
		Judgment escalated = new Judgment(JudgmentStatus.FAIL, null, null, null, null, "verified violation", List.of(),
				null, new AppliedPolicy(POLICY, AcceptanceAction.ESCALATE, "independent review required"), Map.of());
		Verdict verdict = SimpleJury.builder()
			.judge(context -> escalated)
			.votingStrategy(new AverageVotingStrategy())
			.build()
			.vote(CONTEXT);
		assertThat(verdict.aggregated()).isEqualTo(escalated);
	}

	@ParameterizedTest
	@EnumSource(JudgmentStatus.class)
	void allValidStatusesRemainWholeInSimpleAndMetaJuries(JudgmentStatus status) {
		Judgment raw = switch (status) {
			case PASS -> Judgment.builder().pass().score(0.1).reasoning("low positive").build();
			case FAIL -> Judgment.builder().fail().score(0.9).reasoning("high negative").build();
			case ABSTAIN -> Judgment.abstain("insufficient");
			case NOT_APPLICABLE -> Judgment.notApplicable("no Java");
			case ERROR -> Judgment.error(JudgmentReasonCode.POLICY_FAILED, "returned machinery failure");
		};
		raw = raw.toBuilder().metadata("elapsedMillis", 7).build();
		Judgment original = raw;
		AtomicInteger reductions = new AtomicInteger();
		VotingStrategy neverReduce = new VotingStrategy() {
			@Override
			public Judgment aggregate(List<Judgment> values, Map<String, Double> weights) {
				reductions.incrementAndGet();
				throw new AssertionError("identity must not reduce");
			}

			@Override
			public String getName() {
				return "never";
			}

			@Override
			public NotApplicablePolicy notApplicablePolicy() {
				return NotApplicablePolicy.TREAT_AS_FAIL;
			}
		};
		Judge judge = new NamedJudge(context -> original,
				new JudgeMetadata("one", "one", JudgeType.DETERMINISTIC, "subject has no Java"));
		for (boolean parallel : List.of(false, true)) {
			SimpleJury simple = SimpleJury.builder()
				.judge(judge)
				.parallel(parallel)
				.votingStrategy(neverReduce)
				.build();
			Verdict first = simple.vote(CONTEXT);
			Verdict meta = Juries.meta(neverReduce, new NamedJury("member", simple)).vote(CONTEXT);
			for (Verdict verdict : List.of(first, meta)) {
				assertThat(verdict.aggregated()).isSameAs(original);
				assertThat(verdict.individual()).containsExactly(original);
				assertThat(verdict.decision()).isEqualTo(Decision.own());
				assertThat(verdict.aggregated().metadata()).doesNotContainKey(Judgment.AGGREGATION_KEY);
			}
			assertThat(simple.aggregateMayBeNotApplicable()).isTrue();
			assertThat(simple.describe().aggregateMayBeNotApplicable()).isTrue();
			assertThat(
					Juries.meta(neverReduce, new NamedJury("member", simple)).describe().aggregateMayBeNotApplicable())
				.isTrue();
		}
		assertThat(reductions.get()).isZero();
		assertThatThrownBy(() -> SimpleJury.builder().judge(judge).votingStrategy(new AverageVotingStrategy()).build())
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void acceptedNegativeAndPolicyFailureRemainIdentity() {
		for (Judgment judgment : List.of(
				Policies.apply(Judgment.fail("violation"), POLICY,
						view -> new Acceptance(AcceptanceAction.USE_ASSESSMENT, "usable negative")),
				Policies.apply(Judgment.pass("finding"), POLICY, view -> {
					throw new IllegalStateException("policy missing");
				}), Policies.apply(Judgment.pass("finding"), POLICY,
						view -> new Acceptance(AcceptanceAction.ESCALATE, "critical consequence")))) {
			SimpleJury simple = SimpleJury.builder()
				.judge(context -> judgment)
				.votingStrategy(new AverageVotingStrategy(0.9, ErrorPolicy.TREAT_AS_FAIL))
				.build();
			assertThat(simple.vote(CONTEXT).aggregated()).isSameAs(judgment);
			assertThat(Juries.meta(new AverageVotingStrategy(ErrorPolicy.TREAT_AS_FAIL), new NamedJury("one", simple))
				.vote(CONTEXT)
				.aggregated()).isSameAs(judgment);
		}
	}

	@Test
	void twoDeclaredSeatsAndDirectStrategyRemainExplicitReductions() {
		Judgment lowPositive = Judgment.builder().pass().score(0.1).reasoning("accepted positive").build();
		AverageVotingStrategy strategy = new AverageVotingStrategy(ErrorPolicy.IGNORE);
		Verdict verdict = SimpleJury.builder()
			.judge(context -> lowPositive)
			.judge(context -> Judgment.error("backend unavailable"))
			.votingStrategy(strategy)
			.build()
			.vote(CONTEXT);
		assertThat(verdict.aggregated().status()).isEqualTo(JudgmentStatus.FAIL);
		assertThat(verdict.aggregated()).isNotEqualTo(lowPositive);
		assertThat(strategy.aggregate(List.of(lowPositive), Map.of()).status()).isEqualTo(JudgmentStatus.FAIL);
		Jury broken = new Jury() {
			@Override
			public Verdict vote(JudgmentContext context) {
				throw new IllegalStateException("member failed");
			}

			@Override
			public List<Judge> getJudges() {
				return List.of();
			}

			@Override
			public VotingStrategy getVotingStrategy() {
				return strategy;
			}
		};
		Verdict meta = Juries
			.meta(strategy,
					new NamedJury("valid",
							SimpleJury.builder().judge(context -> lowPositive).votingStrategy(strategy).build()),
					new NamedJury("broken", broken))
			.vote(CONTEXT);
		assertThat(meta.aggregated().status()).isEqualTo(JudgmentStatus.ERROR);
		assertThat(meta.individual()).containsExactly(lowPositive);
		assertThat(meta.compositeAttempts()).hasSize(2);
	}

}
