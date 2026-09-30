/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.assertions;

import java.util.*;
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

class AssertionDiagnosticsTest {

	private static final Requirement<String> R = Requirement.text("locks", "1", "Lock order");

	@ParameterizedTest
	@EnumSource(JudgmentStatus.class)
	void assertionsKeepOriginalProducerFactsForEveryStatus(JudgmentStatus status) {
		var judgment = new Judgment(status, null, null, null,
				status == JudgmentStatus.ERROR ? JudgmentReasonCode.JUDGE_REPORTED : null, "native explanation",
				List.of(), null, Map.of());
		var original = Verdict.single("native", judgment).forRequirement(R);
		var result = Evaluations.apply(original,
				v -> new PolicyDecision(PolicyAction.ABSTAIN, "independent confirmation needed"));
		assertThatThrownBy(() -> RequirementAssertions.requireSatisfied(result))
			.isInstanceOfSatisfying(RequirementAssertionError.class, error -> {
				assertThat(error.result()).isSameAs(result);
				assertThat(error.result().verdict()).isSameAs(original);
				assertThat(error.result().verdict().judgment()).isSameAs(judgment);
				assertThat(error.getMessage()).contains("ABSTAIN", "independent confirmation needed",
						"native explanation");
			});
	}

	@Test
	void nativeSupportRemainsDistinctFromFindingAndDoesNotAcquireConfidence() {
		var distribution = new ProbabilityDistribution(FindingTarget.BOOLEAN, "noul:v1",
				List.of(new ProbabilityMass("true", .9), new ProbabilityMass("false", .1)));
		var judgment = new Judgment(JudgmentStatus.PASS, new Finding(new BooleanFinding(true), null, null), null,
				distribution, null, "provider probability", List.of(), null, Map.of());
		var verdict = Verdict.single("native", judgment).forRequirement(R);
		var result = Evaluations.apply(verdict, v -> new PolicyDecision(PolicyAction.ESCALATE, "review probability"));
		var reopened = new VerdictCodec().readEvaluation(new VerdictCodec().write(result));
		assertThat(reopened.verdict().judgment().confidence()).isNull();
		assertThat(reopened.verdict().judgment().probabilityDistribution()).isEqualTo(distribution);
		assertThatThrownBy(() -> RequirementAssertions.requireSatisfied(reopened))
			.isInstanceOf(RequirementAssertionError.class);
	}

	@Test
	void largeExplanationsStayRetainedWhileThePrimaryDiagnosticIsBounded() {
		String huge = "long native explanation ".repeat(2000);
		var original = Judgment.pass(huge);
		var verdict = Verdict.single("native", original).forRequirement(R);
		var result = Evaluations.apply(verdict, v -> new PolicyDecision(PolicyAction.ESCALATE, huge));
		assertThatThrownBy(() -> RequirementAssertions.requireSatisfied(result))
			.isInstanceOfSatisfying(RequirementAssertionError.class, error -> {
				assertThat(error.getMessage()).hasSizeLessThan(5000).contains("full value retained");
				assertThat(error.result().verdict().judgment()).isSameAs(original);
				assertThat(error.result().verdict().judgment().reasoning()).isEqualTo(huge);
			});
	}

}
