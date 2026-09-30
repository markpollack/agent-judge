/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.assertions;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import io.github.markpollack.judge.*;
import io.github.markpollack.judge.judgment.*;
import io.github.markpollack.judge.jury.*;
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
		Jury<String> failed = e -> {
			calls.incrementAndGet();
			throw new IllegalStateException("unavailable");
		};
		Jury<String> fallback = e -> {
			calls.incrementAndGet();
			return Verdict.single("fallback", Judgment.fail("rejected"));
		};
		var cascade = CascadedJury.<String>builder()
			.tier("first", failed, RoutingRule.STOP_ON_CONCLUSIVE)
			.tier("last", fallback, RoutingRule.FINAL_TIER)
			.build();
		var result = Evaluations.evaluate(cascade, "evidence", v -> {
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
		var rejected = Verdict.of(Judgment.notApplicable("invalid exclusion"),
				Map.of("known", Judgment.fail("violation")));
		Jury<String> first = e -> rejected;
		var cascade = CascadedJury.<String>builder()
			.tier("first", first, RoutingRule.REJECT_ON_ANY_FAIL)
			.tier("last", (Jury<String>) e -> {
				throw new AssertionError("must not run");
			}, RoutingRule.FINAL_TIER)
			.build();
		var result = Evaluations.evaluate(cascade, "evidence", v -> {
			assertThat(v.judgment().status()).isEqualTo(JudgmentStatus.ERROR);
			assertThat(v.compositeAttempts().getFirst().verdict()).isSameAs(rejected);
			assertThat(v.conclusion()).isEqualTo(Verdict.Conclusion.FAIL);
			return new PolicyDecision(PolicyAction.RELY, "trust rejection");
		});
		assertThat(result.policyResult()).isInstanceOf(PolicyResult.Decided.class);
		var restored = new VerdictCodec().read(new VerdictCodec().write(result.verdict()));
		assertThat(restored.conclusion()).isEqualTo(Verdict.Conclusion.FAIL);
	}

	@Test
	void allFailedAttemptsStillReceiveRequestedPolicy() {
		var calls = new AtomicInteger();
		var cascade = CascadedJury.<String>builder().tier("last", (Jury<String>) e -> {
			throw new IllegalStateException("down");
		}, RoutingRule.FINAL_TIER).build();
		var result = Evaluations.evaluate(cascade, "e", v -> {
			calls.incrementAndGet();
			return new PolicyDecision(PolicyAction.ESCALATE, "all failed");
		});
		assertThat(result.verdict().conclusion()).isEqualTo(Verdict.Conclusion.INCONCLUSIVE);
		assertThat(calls).hasValue(1);
	}

	@Test
	void requirementJuryPreservesFullRecordAndReceivesActualRequirement() {
		var actual = Requirement.text("security", "1", "safe");
		var child = RequirementJuries.<String, String>voting(new MajorityVotingStrategy(), List.of((r, e) -> {
			assertThat(r).isSameAs(actual);
			return Judgment.pass("one");
		}, (r, e) -> Judgment.pass("two"), (r, e) -> Judgment.fail("dissent")));
		var result = Evaluations.evaluate(actual, child, "evidence");
		assertThat(result.verdict().individual()).hasSize(3);
		assertThat(result.verdict().requirement()).isSameAs(actual);
		RequirementAssertions.requireSatisfied(result);
	}

}
