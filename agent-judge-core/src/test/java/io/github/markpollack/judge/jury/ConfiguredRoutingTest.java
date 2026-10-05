/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury;
import io.github.markpollack.judge.verdict.AttemptDisposition;
import io.github.markpollack.judge.verdict.DispositionReason;
import io.github.markpollack.judge.verdict.RoutingDecision;
import io.github.markpollack.judge.verdict.RoutingRule;
import io.github.markpollack.judge.verdict.SeatExecution;
import io.github.markpollack.judge.verdict.Verdict;
import io.github.markpollack.judge.verdict.VerdictProvenance;
import io.github.markpollack.judge.voting.ConsensusStrategy;
import io.github.markpollack.judge.voting.ErrorHandling;
import io.github.markpollack.judge.voting.ExclusionHandling;
import io.github.markpollack.judge.voting.VotingStrategy;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import io.github.markpollack.judge.judgment.*;
import io.github.markpollack.judge.description.*;
import io.github.markpollack.judge.evaluation.*;
import io.github.markpollack.judge.policy.*;
import io.github.markpollack.judge.reporting.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.assertj.core.api.Assertions.*;

/**
 * Public compositions repeat the persisted counterexamples against the real semantics.
 */
class ConfiguredRoutingTest {

	static Jury identity(Jury child, int levels) {
		for (int i = 0; i < levels; i++) {
			child = Juries.meta(new ConsensusStrategy(ErrorHandling.PROPAGATE, ExclusionHandling.EXCLUDE),
					new NamedJury("wrap" + i, child));
		}
		return child;
	}

	static Jury broken(boolean fail) {
		return SimpleJury.builder()
			.judge("A", () -> fail ? Judgment.fail("violation") : Judgment.pass("A"))
			.judge("B", () -> Judgment.pass("B"))
			.votingStrategy(new VotingStrategy() {
				public String getName() {
					return "broken";
				}

				public Judgment aggregate(List<Judgment> input, Map<String, Double> weights) {
					throw new IllegalStateException("reduction unavailable");
				}
			})
			.parallel(false)
			.build();
	}

	@ParameterizedTest
	@EnumSource(RoutingRule.class)
	void repeatedIdentitiesPreserveAllConclusionRouting(RoutingRule rule) {
		for (Judgment original : List.of(Judgment.pass("passed"), Judgment.fail("failed"),
				Judgment.abstain("uncertain"), Judgment.notApplicable("absent"), Judgment.error("native error"))) {
			for (int levels : List.of(0, 1, 3)) {
				Jury child = SimpleJury.builder()
					.seat(JudgeSeat.named("one", () -> original).notApplicableWhen("feature absent"))
					.votingStrategy(new ConsensusStrategy(ErrorHandling.PROPAGATE, ExclusionHandling.EXCLUDE))
					.build();
				Verdict raw = child.vote();
				Jury retained = identity(new Jury() {
					public Verdict vote() {
						return raw;
					}

					public JuryDescription describe() {
						return child.describe();
					}
				}, levels);
				assertThat(retained.vote().conclusion()).isEqualTo(raw.conclusion());
				var fallbackCalls = new AtomicInteger();
				Jury fallback = () -> {
					fallbackCalls.incrementAndGet();
					return Verdict.single("fallback", Judgment.pass("fallback"));
				};
				var builder = CascadedJury.builder().tier("candidate", retained, rule);
				if (rule != RoutingRule.FINAL_TIER)
					builder.tier("fallback", fallback, RoutingRule.FINAL_TIER);
				Verdict result = builder.build().vote();
				boolean stop = switch (rule) {
					case FINAL_TIER -> true;
					case STOP_ON_ANY_OPINION_FAIL -> original.status() == JudgmentStatus.FAIL;
					case STOP_ON_ALL_OPINIONS_PASS -> original.pass();
					case STOP_ON_CONCLUSION_PASS -> raw.conclusion() == Verdict.Conclusion.PASS;
					case STOP_ON_CONCLUSION_FAIL -> raw.conclusion() == Verdict.Conclusion.FAIL;
					case STOP_ON_CONCLUSIVE ->
						raw.conclusion() == Verdict.Conclusion.PASS || raw.conclusion() == Verdict.Conclusion.FAIL;
				};
				assertThat(fallbackCalls).hasValue(stop ? 0 : 1);
				assertThat(result.conclusion()).isEqualTo(stop ? raw.conclusion() : Verdict.Conclusion.PASS);
				var policies = new AtomicInteger();
				Evaluations.apply(result, v -> {
					policies.incrementAndGet();
					assertThat(v).isSameAs(result);
					return new PolicyDecision(PolicyAction.ABSTAIN, "review");
				});
				assertThat(policies).hasValue(1);
				VerdictReport.of(result).summary();
				assertThat(fallbackCalls).hasValue(stop ? 0 : 1);
			}
		}
	}

	@ParameterizedTest
	@EnumSource(RoutingRule.class)
	void acceptedUndecidedTierHasOneRuleIndependentTreatment(RoutingRule rule) {
		for (boolean fail : List.of(false, true))
			for (int levels : List.of(0, 1, 3)) {
				Verdict raw = broken(fail).vote();
				Jury child = identity(() -> raw, levels);
				var calls = new AtomicInteger();
				var builder = CascadedJury.builder().tier("candidate", child, rule);
				if (rule != RoutingRule.FINAL_TIER)
					builder.tier("fallback", () -> {
						calls.incrementAndGet();
						return Verdict.single("fallback", Judgment.pass("fallback"));
					}, RoutingRule.FINAL_TIER);
				Verdict result = builder.build().vote();
				boolean rejected = fail && rule == RoutingRule.STOP_ON_ANY_OPINION_FAIL;
				assertThat(result.compositeAttempts().getFirst().disposition()).isEqualTo(AttemptDisposition.USED);
				assertThat(result.conclusion()).isEqualTo(rejected ? Verdict.Conclusion.FAIL
						: rule == RoutingRule.FINAL_TIER ? Verdict.Conclusion.INCONCLUSIVE : Verdict.Conclusion.PASS);
				assertThat(calls).hasValue(rejected || rule == RoutingRule.FINAL_TIER ? 0 : 1);
				assertThat(result.compositeAttempts().getFirst().verdict().judgment()).isSameAs(raw.judgment());
			}
	}

	@ParameterizedTest
	@EnumSource(value = RoutingRule.class, names = { "STOP_ON_ANY_OPINION_FAIL", "STOP_ON_ALL_OPINIONS_PASS" })
	void unknownEmptyHasDerivedContinuationReason(RoutingRule rule) {
		Verdict empty = Verdict.builder()
			.judgment(Judgment.error(JudgmentReasonCode.AGGREGATION_FAILED, "no opinions"))
			.provenance(VerdictProvenance.undecided())
			.build();
		Verdict result = CascadedJury.builder()
			.tier("opaque", () -> empty, rule)
			.tier("fallback", () -> Verdict.single("fallback", Judgment.pass("fallback")), RoutingRule.FINAL_TIER)
			.build()
			.vote();
		assertThat(result.compositeAttempts().getFirst().routingDecision())
			.isEqualTo(new RoutingDecision(false, RoutingDecision.Reason.NO_ROOT_OPINIONS));
		assertThat(VerdictReport.of(result).summary()).contains("NO_ROOT_OPINIONS");
	}

	@ParameterizedTest
	@EnumSource(RoutingRule.class)
	void refusedWholeTierSuppliesNoRoutingEvidence(RoutingRule rule) {
		Verdict excluded = Verdict.single("excluded", Judgment.notApplicable("no feature"));
		var calls = new AtomicInteger();
		var builder = CascadedJury.builder().tier("opaque", () -> excluded, rule);
		if (rule != RoutingRule.FINAL_TIER)
			builder.tier("fallback", () -> {
				calls.incrementAndGet();
				return Verdict.single("fallback", Judgment.pass("fallback"));
			}, RoutingRule.FINAL_TIER);
		Verdict result = builder.build().vote();
		assertThat(result.compositeAttempts().getFirst().verdict()).isSameAs(excluded);
		assertThat(result.compositeAttempts().getFirst().dispositionReason())
			.isEqualTo(DispositionReason.UNDECLARED_NOT_APPLICABLE);
		assertThat(result.conclusion())
			.isEqualTo(rule == RoutingRule.FINAL_TIER ? Verdict.Conclusion.INCONCLUSIVE : Verdict.Conclusion.PASS);
		assertThat(calls).hasValue(rule == RoutingRule.FINAL_TIER ? 0 : 1);
	}

	@Test
	void seatLocalRejectionDoesNotEraseASeparateViolation() {
		Verdict child = SimpleJury.builder()
			.judge("violation", () -> Judgment.fail("established"))
			.judge("undeclared", () -> Judgment.notApplicable("native explanation"))
			.votingStrategy(new VotingStrategy() {
				public String getName() {
					return "broken reduction";
				}

				public Judgment aggregate(List<Judgment> inputs, Map<String, Double> weights) {
					throw new IllegalStateException("reduction unavailable");
				}
			})
			.build()
			.vote();
		assertThat(child.individual().get(1).status()).isEqualTo(JudgmentStatus.NOT_APPLICABLE);
		assertThat(child.seats().get(1).execution()).isEqualTo(SeatExecution.RETURNED_REJECTED);
		var result = CascadedJury.builder()
			.tier("candidate", () -> child, RoutingRule.STOP_ON_ANY_OPINION_FAIL)
			.tier("fallback", () -> {
				throw new AssertionError("must not run");
			}, RoutingRule.FINAL_TIER)
			.build()
			.vote();
		assertThat(result.conclusion()).isEqualTo(Verdict.Conclusion.FAIL);
		assertThat(result.judgment().status()).isEqualTo(JudgmentStatus.ERROR);
	}

}
