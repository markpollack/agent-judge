/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.assertions;

import io.github.markpollack.judge.verdict.RoutingRule;
import io.github.markpollack.judge.verdict.Verdict;
import io.github.markpollack.judge.voting.MajorityVotingStrategy;
import io.github.markpollack.judge.voting.VotingStrategy;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import io.github.markpollack.judge.*;
import io.github.markpollack.judge.judgment.*;
import io.github.markpollack.judge.jury.*;
import io.github.markpollack.judge.verdict.*;
import io.github.markpollack.judge.voting.*;
import io.github.markpollack.judge.requirement.*;
import io.github.markpollack.judge.policy.*;
import io.github.markpollack.judge.evaluation.*;
import io.github.markpollack.judge.serialization.VerdictCodec;
import static org.assertj.core.api.Assertions.*;

class JuryApplicationBoundaryTest {

	@ParameterizedTest
	@EnumSource(PolicyAction.class)
	void finalPolicyReceivesEveryChildAndNeverRestartsRouting(PolicyAction action) {
		var calls = new AtomicInteger();
		var policyCalls = new AtomicInteger();
		Jury failed = () -> {
			calls.incrementAndGet();
			throw new IllegalStateException("unavailable");
		};
		Jury fallback = () -> {
			calls.incrementAndGet();
			return Verdict.single("fallback", Judgment.fail("rejected"));
		};
		var cascade = CascadedJury.builder()
			.tier("first", failed, RoutingRule.STOP_ON_CONCLUSIVE)
			.tier("last", fallback, RoutingRule.FINAL_TIER)
			.build();
		var result = Evaluations.evaluate(cascade, v -> {
			policyCalls.incrementAndGet();
			assertThat(v.compositeAttempts()).hasSize(2);
			return new PolicyDecision(action, "retained");
		});
		assertThat(result.verdict().conclusion()).isEqualTo(Verdict.Conclusion.FAIL);
		assertThat(result.verdict().compositeAttempts().getFirst().failure()).isNotNull();
		assertThat(calls).hasValue(2);
		assertThat(policyCalls).hasValue(1);
	}

	@Test
	void individualRejectionAndCollectiveErrorBothReachPolicy() {
		var failedRule = new VotingStrategy() {
				public Judgment aggregate(List<io.github.markpollack.judge.voting.Ballot> ballots) {
					var values = io.github.markpollack.judge.voting.Ballots.judgments(ballots);
					throw new IllegalStateException("reduction broken");
				}

				public String getName() {
					return "broken";
				}
		};
		var rejected = SimpleJury.builder()
			.judge("known", () -> Judgment.fail("violation"))
			.judge("second", () -> Judgment.pass("present"))
			.votingStrategy(failedRule)
			.build()
			.vote();

		Jury first = () -> rejected;
		var cascade = CascadedJury.builder()
			.tier("first", first, RoutingRule.STOP_ON_ANY_OPINION_FAIL)
			.tier("last", (Jury) () -> {
				throw new AssertionError("must not run");
			}, RoutingRule.FINAL_TIER)
			.build();
		var result = Evaluations.evaluate(cascade, v -> {
			assertThat(v.judgment().status()).isEqualTo(JudgmentStatus.ERROR);
			assertThat(v.compositeAttempts().getFirst().verdict()).isSameAs(rejected);
			assertThat(v.conclusion()).isEqualTo(Verdict.Conclusion.FAIL);
			return new PolicyDecision(PolicyAction.RELY, "trust rejection");
		});
		assertThat(result.policyResult()).isInstanceOf(PolicyResult.Decided.class);
		var codec = new VerdictCodec().withVotingRules(java.util.Map.of("broken", configuration -> failedRule));
		var restored = codec.read(codec.write(result.verdict()));
		assertThat(restored.conclusion()).isEqualTo(Verdict.Conclusion.FAIL);
	}

	@Test
	void allFailedAttemptsStillReceiveRequestedPolicy() {
		var calls = new AtomicInteger();
		var cascade = CascadedJury.builder().tier("last", (Jury) () -> {
			throw new IllegalStateException("down");
		}, RoutingRule.FINAL_TIER).build();
		var result = Evaluations.evaluate(cascade, v -> {
			calls.incrementAndGet();
			return new PolicyDecision(PolicyAction.ESCALATE, "all failed");
		});
		assertThat(result.verdict().conclusion()).isEqualTo(Verdict.Conclusion.INCONCLUSIVE);
		assertThat(calls).hasValue(1);
	}

	@Test
	void requirementJuryPreservesFullRecordAndReceivesActualRequirement() {
		var actual = Requirement.text("security", "1", "safe");
		var ready = SimpleJury.builder().judge("one", ConfiguredRules.<String, String>rule((r, e) -> {
			assertThat(r).isSameAs(actual);
			return Judgment.pass("one");
		}).requirement(actual).evidence("evidence").build())
			.judge("two",
					ConfiguredRules.<String, String>rule((r, e) -> Judgment.pass("two"))
						.requirement(actual)
						.evidence("evidence")
						.build())
			.judge("dissent",
					ConfiguredRules.<String, String>rule((r, e) -> Judgment.fail("dissent"))
						.requirement(actual)
						.evidence("evidence")
						.build())
			.votingStrategy(new MajorityVotingStrategy())
			.build();
		var result = Evaluations.of(ready.vote().forRequirement(actual));

		assertThat(result.verdict().individual()).hasSize(3);
		assertThat(result.verdict().requirement()).isSameAs(actual);
		RequirementAssertions.requireSatisfied(result);
	}

}
