/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.assertj;

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
import io.github.markpollack.judge.reporting.VerdictReport;
import io.github.markpollack.judge.serialization.VerdictCodec;
import io.github.markpollack.judge.assertions.RequirementAssertionError;
import static io.github.markpollack.judge.assertj.Assertions.*;
import static org.assertj.core.api.Assertions.*;

class RequirementAssertionTest {

	static final Requirement<String> R = Requirement.text("ready", "1", "READY");
	static final RequirementJudge<String, String> MATCH = (r, e) -> r.specification().equals(e)
			? Judgment.pass("matches") : Judgment.fail("differs");

	@Test
	void terminalAloneInvokesAndRepeatedTerminalsRetainOriginalObjects() {
		var calls = new AtomicInteger();
		var policies = new AtomicInteger();
		var judgment = Judgment.pass("matched");
		var decision = new PolicyDecision(PolicyAction.RELY, "reviewed");
		RequirementJudge<String, String> judge = (r, e) -> {
			assertThat(r).isSameAs(R);
			assertThat(e).isEqualTo("READY");
			calls.incrementAndGet();
			return judgment;
		};
		var terminal = assertThat(R).judgedBy(judge).withEvidence("READY").withPolicy(v -> {
			policies.incrementAndGet();
			return decision;
		});
		assertThat(calls).hasValue(0);
		assertThat(policies).hasValue(0);
		terminal.isSatisfied();
		terminal.isSatisfied();
		var result = terminal.evaluate();
		assertThat(result).isSatisfied();
		assertThat(result.verdict().judgment()).isSameAs(judgment);
		assertThat(((PolicyResult.Decided) result.policyResult()).decision()).isSameAs(decision);
		assertThat(terminal.evaluate()).isSameAs(result);
		assertThat(calls).hasValue(1);
		assertThat(policies).hasValue(1);
		assertThatThrownBy(() -> terminal.withPolicy(v -> decision)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void normalRequirementPathNeedsNoPolicy() {
		var terminal = assertThat(R).judgedBy(MATCH).withEvidence("READY");
		terminal.isSatisfied();
		assertThat(terminal.evaluate().policyResult()).isInstanceOf(PolicyResult.NotRequested.class);
	}

	@Test
	void descriptionKeepsCompleteFailureAsCause() {
		var terminal = assertThat(R).as("release gate").judgedBy(MATCH).withEvidence("NO");
		assertThatThrownBy(terminal::isSatisfied).hasMessageContaining("[release gate]")
			.hasCauseInstanceOf(RequirementAssertionError.class);
		assertThat(terminal.evaluate().verdict()).hasConclusion(Verdict.Conclusion.FAIL);
	}

	@ParameterizedTest
	@EnumSource(PolicyAction.class)
	void explicitPolicyCanWithholdPassingResultWithoutMutatingIt(PolicyAction action) {
		var terminal = assertThat(R).judgedBy(MATCH)
			.withEvidence("READY")
			.withPolicy(v -> new PolicyDecision(action, "reviewed"));
		if (action == PolicyAction.RELY)
			terminal.isSatisfied();
		else
			assertThatThrownBy(terminal::isSatisfied).isInstanceOf(RequirementAssertionError.class);
		assertThat(terminal.evaluate().verdict()).hasConclusion(Verdict.Conclusion.PASS);
		assertThat(terminal.evaluate().verdict().judgment().status()).isEqualTo(JudgmentStatus.PASS);
	}

	@Test
	void requestedPolicyFailureRetainsPassingProducerAndOriginalThrowable() {
		var original = new IllegalStateException("down");
		var calls = new AtomicInteger();
		var terminal = assertThat(R).judgedBy(MATCH).withEvidence("READY").withPolicy(v -> {
			calls.incrementAndGet();
			throw original;
		});
		for (int i = 0; i < 2; i++)
			assertThatThrownBy(terminal::isSatisfied).hasCause(original);
		assertThat(terminal.evaluate().verdict()).hasConclusion(Verdict.Conclusion.PASS);
		assertThat(calls).hasValue(1);
	}

	@Test
	void requirementJuryKeepsEveryOpinionAndDisagreement() {
		var jury = RequirementJuries.<String, String>voting(new MajorityVotingStrategy(),
				List.of(MATCH, MATCH, (r, e) -> Judgment.fail("dissent")));
		var terminal = assertThat(R).judgedBy(jury).withEvidence("READY");
		terminal.isSatisfied();
		assertThat(terminal.evaluate().verdict().individual()).extracting(Judgment::status)
			.containsExactly(JudgmentStatus.PASS, JudgmentStatus.PASS, JudgmentStatus.FAIL);
		assertThat(terminal.evaluate().verdict().seats()).hasSize(3);
	}

	@Test
	void ordinaryEvidenceAndJuryNeedNoRequirement() {
		Judge<Integer> positive = e -> e > 0 ? Judgment.pass("positive") : Judgment.fail("not positive");
		assertThatEvidence(42).judgedBy(positive).isPassed();
		var jury = Juries.fromJudges(new ConsensusStrategy(), positive, positive);
		assertThatEvidence(42).judgedBy(jury).hasConclusion(Verdict.Conclusion.PASS);
	}

	@Test
	void retainedResultRoundTripDoesNotExecuteAgain() {
		var calls = new AtomicInteger();
		RequirementJudge<String, String> judge = (r, e) -> {
			calls.incrementAndGet();
			return MATCH.judge(r, e);
		};
		var result = assertThat(R).judgedBy(judge).withEvidence("READY").evaluate();
		var codec = new VerdictCodec();
		var stored = codec.readEvaluation(codec.write(result));
		assertThat(stored).isSatisfied();
		assertThat(stored.verdict()).hasConclusion(Verdict.Conclusion.PASS);
		VerdictReport.of(stored.verdict()).summary();
		assertThat(calls).hasValue(1);
	}

	@Test
	void nullConfigurationFailsWithoutExecution() {
		var calls = new AtomicInteger();
		RequirementJudge<String, String> judge = (r, e) -> {
			calls.incrementAndGet();
			return Judgment.pass("yes");
		};
		assertThatNullPointerException()
			.isThrownBy(() -> assertThat(R).judgedBy((RequirementJudge<String, String>) null));
		assertThatNullPointerException()
			.isThrownBy(() -> assertThat(R).judgedBy(judge).withEvidence("x").withPolicy(null));
		assertThat(calls).hasValue(0);
	}

}
