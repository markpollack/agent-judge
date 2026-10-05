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
import static org.assertj.core.api.Assertions.*;

class RetainedCustomRuleTest {

	record PassingWeight(double minimum) implements VotingStrategy {
		PassingWeight {
			if (!Double.isFinite(minimum) || minimum <= 0)
				throw new IllegalArgumentException("Positive minimum required");
		}

		public String getName() {
			return "passingWeight:v1";
		}

		public StrategyDescription describe() {
			return StrategyDescription.declared(this, ErrorHandling.PROPAGATE, ExclusionHandling.REFUSE, null,
					Map.of("minimumPassingWeight", minimum));
		}

		public Judgment aggregate(List<Ballot> ballots) {
			var population = AggregationPopulation.resolve(Ballots.judgments(ballots), ErrorHandling.PROPAGATE,
					ExclusionHandling.REFUSE);
			if (population.hasPolicyExit())
				return population.policyExitAggregate(getName());
			if (population.isEmpty())
				return population.noResult(getName(), Map.of());
			double passing = 0;
			for (var ballot : population.eligibleBallots(ballots))
				if (ballot.treatment().status() == JudgmentStatus.PASS)
					passing += ballot.effectiveWeight();
			return AggregationEvidence.attach(
					passing >= minimum ? Judgment.pass("Required passing weight reached")
							: Judgment.fail("Required passing weight not reached"),
					population.evidence(getName())
						.put("minimumPassingWeight", minimum)
						.put("passingWeight", passing)
						.build());
		}
	}

	VerdictCodec codec() {
		return new VerdictCodec().withVotingRules(Map.of("passingWeight:v1",
				q -> new PassingWeight(((Number) q.get("minimumPassingWeight")).doubleValue())));
	}

	@Test
	void newRuleKeepsTypedWeightsAcrossFilteredOpinionsAndReopensThroughTrustedFactory() {
		var calls = new AtomicInteger();
		io.github.markpollack.judge.Judge same = () -> {
			calls.incrementAndGet();
			return Judgment.pass("same producer, separate seat");
		};
		var panel = SimpleJury.builder()
			.judge(same, 2.0)
			.judge(() -> Judgment.abstain("No opinion"), 50.0)
			.judge(same, 3.0)
			.parallel(false)
			.votingStrategy(new PassingWeight(5.0))
			.build();
		var verdict = panel.vote().requireUsable();
		assertThat(verdict.conclusion()).isEqualTo(Verdict.Conclusion.PASS);
		assertThat(calls).hasValue(2);
		assertThat(verdict.seats()).extracting(Seat::declaredWeight).containsExactly(2.0, 50.0, 3.0);
		assertThat(verdict.rule().configuration()).containsEntry("minimumPassingWeight", 5.0);
		var encoded = codec().write(verdict);
		assertThat(encoded).doesNotContain("\"weights\"", PassingWeight.class.getName());
		var reopened = codec().read(encoded);
		assertThat(reopened).isEqualTo(verdict);
		assertThat(reopened.requireUsable()).isSameAs(reopened);
		assertThatThrownBy(() -> new VerdictCodec().read(encoded)).hasMessageContaining("Unknown voting rule token");
		assertThatThrownBy(
				() -> codec().read(encoded.replace("\"minimumPassingWeight\":5.0", "\"minimumPassingWeight\":6.0")))
			.hasMessageContaining("contradicts");
		assertThat(calls).hasValue(2);
	}

	@Test
	void liveCustomTierIsUsableAndSelectedAfterStorageWithoutFurtherExecution() {
		var calls = new AtomicInteger();
		var panel = SimpleJury.builder().judge(() -> {
			calls.incrementAndGet();
			return Judgment.pass("a");
		}, 2.0).judge(() -> {
			calls.incrementAndGet();
			return Judgment.fail("b");
		}, 1.0).parallel(false).votingStrategy(new PassingWeight(2.0)).build();
		var cascade = CascadedJury.builder()
			.tier("custom", panel, RoutingRule.STOP_ON_CONCLUSIVE)
			.tier("fallback", () -> {
				throw new AssertionError("Usable custom tier must stop");
			}, RoutingRule.FINAL_TIER)
			.build();
		var verdict = cascade.vote().requireUsable();
		assertThat(verdict.provenance().tier()).isEqualTo("custom");
		assertThat(verdict.conclusion()).isEqualTo(Verdict.Conclusion.PASS);
		assertThat(codec().read(codec().write(verdict))).isEqualTo(verdict);
		assertThat(calls).hasValue(2);
	}

	@Test
	void guidedBuildersDeriveOrdinaryFactsAndUnweightedRulesRetainButIgnoreWeights() {
		var original = Judgment.pass("deterministic");
		assertThat(Verdict.builder().single("rule-only").judgment(original).build().requireUsable().judgment())
			.isSameAs(original);
		var result = Verdict.builder()
			.panel(new AllEligiblePassStrategy())
			.opinion("same", original, 0.5)
			.opinion("same", Judgment.fail("fail"), 10.0)
			.build();
		assertThat(result.conclusion()).isEqualTo(Verdict.Conclusion.FAIL);
		assertThat(result.seats()).extracting(Seat::declaredWeight).containsExactly(0.5, 10.0);
		assertThatThrownBy(() -> SimpleJury.builder().judge(() -> {
			throw new AssertionError("preflight");
		}, 0.0)).hasMessageContaining("positive");
	}

}
