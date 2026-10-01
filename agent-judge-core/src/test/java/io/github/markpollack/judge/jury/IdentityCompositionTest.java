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
import io.github.markpollack.judge.judgment.JudgmentReasonCode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import io.github.markpollack.judge.completion.CompletionEvidence;
import io.github.markpollack.judge.judgment.Finding;
import io.github.markpollack.judge.judgment.Judgment;
import io.github.markpollack.judge.judgment.JudgmentStatus;
import io.github.markpollack.judge.judgment.NumericFinding;
import io.github.markpollack.judge.judgment.NumericKind;
import io.github.markpollack.judge.provenance.PolicyRef;
import io.github.markpollack.judge.judgment.QualityDirection;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdentityCompositionTest {

	private static final CompletionEvidence CONTEXT = CompletionEvidence.builder().request("identity").build();

	private static final PolicyRef POLICY = new PolicyRef("critical", "1", "a".repeat(64));

	@Test
	void negativeHighOrdinalAssessmentIsNotReversedByOneSeatNumericStrategy() {
		Judgment negative = new Judgment(JudgmentStatus.FAIL, new Finding(null,
				new NumericFinding(4, NumericKind.ORDINAL_EXPECTATION, "impact:v1", 0, 4,
						List.of("none", "low", "medium", "high", "critical"), QualityDirection.INCREASING),
				null), null, null, null, "verified violation", List.of(), null, Map.of());
		Verdict verdict = SimpleJury.builder()
			.judge(() -> negative)
			.votingStrategy(new AverageVotingStrategy())
			.build()
			.vote();
		assertThat(verdict.judgment()).isEqualTo(negative);
	}

	@Test
	void oneSeatDoesNotLoseEscalationIntent() {
		Judgment escalated = new Judgment(JudgmentStatus.FAIL, null, null, null, null, "verified violation", List.of(),
				null, Map.of());
		Verdict verdict = SimpleJury.builder()
			.judge(() -> escalated)
			.votingStrategy(new AverageVotingStrategy())
			.build()
			.vote();
		assertThat(verdict.judgment()).isEqualTo(escalated);
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
			public ExclusionHandling exclusionHandling() {
				return ExclusionHandling.TREAT_AS_FAIL;
			}
		};
		Judge judge = new NamedJudge(() -> original,
				new JudgeMetadata("one", "one", JudgeType.DETERMINISTIC, "subject has no Java"));
		for (boolean parallel : List.of(false, true)) {
			SimpleJury simple = SimpleJury.builder()
				.seat(declared(judge))
				.parallel(parallel)
				.votingStrategy(neverReduce)
				.build();
			Verdict first = simple.vote();
			Verdict meta = Juries.meta(neverReduce, new NamedJury("member", simple)).vote();
			for (Verdict verdict : List.of(first, meta)) {
				assertThat(verdict.judgment()).isSameAs(original);
				assertThat(verdict.individual()).containsExactly(original);
				assertThat(verdict.provenance()).isEqualTo(VerdictProvenance.own());
				assertThat(verdict.judgment().metadata()).doesNotContainKey(Judgment.AGGREGATION_KEY);
			}
			assertThat(simple.aggregateMayBeNotApplicable()).isTrue();
			assertThat(simple.describe().aggregateMayBeNotApplicable()).isTrue();
			assertThat(
					Juries.meta(neverReduce, new NamedJury("member", simple)).describe().aggregateMayBeNotApplicable())
				.isTrue();
		}
		assertThat(reductions.get()).isZero();
		assertThatThrownBy(
				() -> SimpleJury.builder().seat(declared(judge)).votingStrategy(new AverageVotingStrategy()).build())
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void twoDeclaredSeatsAndDirectStrategyRemainExplicitReductions() {
		Judgment lowPositive = Judgment.builder().pass().score(0.1).reasoning("accepted positive").build();
		AverageVotingStrategy strategy = new AverageVotingStrategy(ErrorHandling.IGNORE);
		Verdict verdict = SimpleJury.builder()
			.judge(() -> lowPositive)
			.judge(() -> Judgment.error("backend unavailable"))
			.votingStrategy(strategy)
			.build()
			.vote();
		assertThat(verdict.judgment().status()).isEqualTo(JudgmentStatus.FAIL);
		assertThat(verdict.judgment()).isNotEqualTo(lowPositive);
		assertThat(strategy.aggregate(List.of(lowPositive), Map.of()).status()).isEqualTo(JudgmentStatus.FAIL);
		Jury broken = new io.github.markpollack.judge.jury.VotingJury() {
			@Override
			public Verdict vote() {
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
							SimpleJury.builder().judge(() -> lowPositive).votingStrategy(strategy).build()),
					new NamedJury("broken", broken))
			.vote();
		assertThat(meta.judgment().status()).isEqualTo(JudgmentStatus.ERROR);
		assertThat(meta.individual()).containsExactly(lowPositive);
		assertThat(meta.compositeAttempts()).hasSize(2);
	}

	private static io.github.markpollack.judge.jury.JudgeSeat declared(io.github.markpollack.judge.Judge producer) {
		return io.github.markpollack.judge.jury.JudgeSeat
			.named(((io.github.markpollack.judge.JudgeWithMetadata) producer).metadata().name(), producer)
			.notApplicableWhen(io.github.markpollack.judge.Judges.notApplicableCapability(producer).orElseThrow());
	}

}
