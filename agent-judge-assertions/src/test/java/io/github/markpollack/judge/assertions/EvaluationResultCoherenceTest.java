/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.assertions;

import io.github.markpollack.judge.verdict.Verdict;

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

class EvaluationResultCoherenceTest {

	private final Verdict verdict = Verdict.single("checked", Judgment.pass("yes"))
		.forRequirement(Requirement.text("r", "1", "r"));

	@Test
	void completeResultHasExactlyOneVerdictAndOnePolicyResult() {
		assertThat(Arrays.stream(EvaluationResult.class.getRecordComponents())
			.map(java.lang.reflect.RecordComponent::getName)).containsExactly("verdict", "policyResult");
		assertThat(Arrays.stream(PolicyResult.class.getPermittedSubclasses()).map(Class::getSimpleName))
			.containsExactlyInAnyOrder("NotRequested", "Decided", "Failed");
		assertThatNullPointerException().isThrownBy(() -> new EvaluationResult(null, new PolicyResult.NotRequested()));
		assertThatNullPointerException().isThrownBy(() -> new EvaluationResult(verdict, null));
	}

	@ParameterizedTest
	@EnumSource(PolicyAction.class)
	void reopeningExecutionPreservesDecisionWithoutRunningAnything(PolicyAction action) {
		var calls = new AtomicInteger();
		var decision = new PolicyDecision(action, "reviewed");
		var original = Evaluations.apply(verdict, v -> {
			calls.incrementAndGet();
			assertThat(v).isSameAs(verdict);
			return decision;
		});
		var codec = new VerdictCodec();
		var reopened = codec.readEvaluation(codec.write(original));
		assertThat(reopened).isEqualTo(original);
		assertThat(calls).hasValue(1);
	}

	@Test
	void policyFailureReopensAsExplicitDataWithStableTypeAndMessage() {
		var original = Evaluations.apply(verdict, v -> {
			throw new IllegalStateException("offline");
		});
		var codec = new VerdictCodec();
		String json = codec.write(original);
		assertThat(json).doesNotContain("stackTrace", "@class");
		var reopened = codec.readEvaluation(json);
		assertThat(((PolicyResult.Failed) reopened.policyResult()).cause()).isInstanceOfSatisfying(
				VerdictCodec.StoredPolicyFailure.class,
				cause -> assertThat(cause.originalType()).isEqualTo(IllegalStateException.class.getName()));
		assertThat(codec.write(reopened)).isEqualTo(json);
	}

	@Test
	void unsupportedOrContradictoryRecordsCannotCreateAnEvaluationOrRunPolicy() {
		var calls = new AtomicInteger();
		var codec = new VerdictCodec();
		for (String malformed : List.of(codec.write(verdict).replace("\"schemaVersion\":6", "\"schemaVersion\":99"),
				"{}")) {
			assertThatThrownBy(() -> Evaluations.apply(codec.read(malformed), v -> {
				calls.incrementAndGet();
				return new PolicyDecision(PolicyAction.RELY, "yes");
			})).isInstanceOf(IllegalArgumentException.class);
		}
		assertThat(calls).hasValue(0);
		var invalid = Verdict.of(Judgment.pass("forged"), Map.of("seat", Judgment.fail("real")));
		assertThatThrownBy(() -> Evaluations.of(invalid)).isInstanceOf(IllegalArgumentException.class);
	}

}
