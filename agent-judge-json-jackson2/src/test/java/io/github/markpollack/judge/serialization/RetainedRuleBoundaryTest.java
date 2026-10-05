/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.serialization;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import io.github.markpollack.judge.judgment.*;
import io.github.markpollack.judge.jury.*;
import io.github.markpollack.judge.verdict.*;
import io.github.markpollack.judge.voting.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class RetainedRuleBoundaryTest {

	static final class InputParticipationRule implements VotingStrategy {
		public String getName() { return "input-participation:v1"; }
		public StrategyDescription describe() {
			return StrategyDescription.declared(this, ErrorHandling.PROPAGATE, ExclusionHandling.REFUSE, null, Map.of());
		}
		public Judgment aggregate(List<Ballot> ballots) {
			var population = AggregationPopulation.resolve(Ballots.judgments(ballots), ErrorHandling.PROPAGATE,
					ExclusionHandling.REFUSE);
			if (population.hasPolicyExit()) return population.policyExitAggregate(getName());
			if (population.isEmpty()) return population.noResult(getName(), Map.of());
			int pending = (int) ballots.stream().filter(b -> b.participation() == Participation.NOT_RECORDED).count();
			return AggregationEvidence.attach(Judgment.pass("all opinions pass"),
					population.evidence(getName()).put("inputNotRecordedCount", pending).build());
		}
	}

	@Test
	void liveAndRetainedValidationUseTheSamePreReductionBallots() {
		var panel = SimpleJury.builder().parallel(false).judge(() -> Judgment.pass("a"))
				.judge(() -> Judgment.pass("b")).votingStrategy(new InputParticipationRule()).build();
		var verdict = panel.vote().requireUsable();
		assertThat(verdict.seats()).extracting(Seat::participation).containsExactly(Participation.INCLUDED, Participation.INCLUDED);
		var codec = new VerdictCodec().withVotingRules(Map.of("input-participation:v1", q -> new InputParticipationRule()));
		assertThat(codec.read(codec.write(verdict))).isEqualTo(verdict);
		var meta = Juries.meta(new InputParticipationRule(),
				new NamedJury("a", () -> Verdict.single("a", Judgment.pass("a"))),
				new NamedJury("b", () -> Verdict.single("b", Judgment.pass("b")))).vote().requireUsable();
		assertThat(codec.read(codec.write(meta))).isEqualTo(meta);
		var guided = Verdict.builder().panel(new InputParticipationRule()).opinion("a", Judgment.pass("a"))
				.opinion("b", Judgment.pass("b")).build().requireUsable();
		assertThat(codec.read(codec.write(guided))).isEqualTo(guided);
		var cascade = CascadedJury.builder().tier("custom", panel, RoutingRule.STOP_ON_CONCLUSIVE)
				.tier("fallback", () -> { throw new AssertionError("conclusive tier must stop"); }, RoutingRule.FINAL_TIER).build().vote().requireUsable();
		assertThat(codec.read(codec.write(cascade))).isEqualTo(cascade);
	}

	record FailedRule(String mode, AtomicInteger calls) implements VotingStrategy {
		public String getName() { return "failed-rule:v1"; }
		public Map<String, Object> configuration() {
			if (mode.equals("capture")) throw new IllegalStateException("configuration unavailable");
			return Map.of("mode", mode, "threshold", 0.7);
		}
		public Judgment aggregate(List<Ballot> ballots) {
			calls.incrementAndGet();
			return switch (mode) {
				case "throw" -> throw new IllegalStateException("arithmetic failed");
				case "null" -> null;
				default -> Judgment.error(JudgmentReasonCode.JUDGE_FAILED, "not a reduction cause");
			};
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "throw", "null", "foreign" })
	void failedAttemptKeepsCapturedDeclarationWithoutReexecutingOnReopen(String mode) {
		var calls = new AtomicInteger();
		var verdict = SimpleJury.builder().parallel(false).judge(() -> Judgment.pass("a"))
				.judge(() -> Judgment.fail("b")).votingStrategy(new FailedRule(mode, calls)).build().vote().requireUsable();
		assertThat(verdict.conclusion()).isEqualTo(Verdict.Conclusion.INCONCLUSIVE);
		assertThat(verdict.individual()).hasSize(2);
		assertThat(verdict.reductionFailure()).isNotNull();
		assertThat(verdict.rule()).isNotNull();
		assertThat(verdict.rule().configuration()).containsEntry("mode", mode).containsEntry("threshold", 0.7);
		var codec = new VerdictCodec().withVotingRules(Map.of("failed-rule:v1", q -> new FailedRule((String) q.get("mode"), calls)));
		var reopened = codec.read(codec.write(verdict));
		assertThat(reopened.requireUsable()).isEqualTo(verdict);
		assertThat(calls).hasValue(1);
	}

	@ParameterizedTest
	@ValueSource(strings = { "name", "configuration", "aggregate" })
	void preservationLimitAtRuleBoundaryEscapesWithTheSameOriginal(String phase) {
		var original = new Object();
		var limit = new io.github.markpollack.judge.portable.PreservationLimitException("rule bound", original);
		var rule = new VotingStrategy() {
			public String getName() {
				if (phase.equals("name")) throw new java.util.concurrent.CompletionException(limit);
				return "bounded-rule:v1";
			}
			public Map<String, Object> configuration() {
				if (phase.equals("configuration")) throw new java.util.concurrent.CompletionException(limit);
				return Map.of();
			}
			public Judgment aggregate(List<Ballot> ballots) { throw new java.util.concurrent.CompletionException(limit); }
		};
		assertThatThrownBy(() -> SimpleJury.builder().parallel(false).judge(() -> Judgment.pass("a"))
				.judge(() -> Judgment.pass("b")).votingStrategy(rule).build().vote()).isSameAs(limit);
		assertThat(limit.original()).isSameAs(original);
	}

	@Test
	void unsuccessfulCaptureDoesNotInventADeclarationOrAttemptReduction() {
		var calls = new AtomicInteger();
		var verdict = SimpleJury.builder().parallel(false).judge(() -> Judgment.pass("a"))
				.judge(() -> Judgment.pass("b")).votingStrategy(new FailedRule("capture", calls)).build().vote().requireUsable();
		assertThat(verdict.rule()).isNull();
		assertThat(verdict.reductionFailure()).isNotNull();
		assertThat(new VerdictCodec().read(new VerdictCodec().write(verdict))).isEqualTo(verdict);
		assertThat(calls).hasValue(0);
	}
}
