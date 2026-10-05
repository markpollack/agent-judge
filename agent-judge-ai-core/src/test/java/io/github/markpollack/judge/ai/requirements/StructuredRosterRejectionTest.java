/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.ai.requirements;
import io.github.markpollack.judge.verdict.DispositionReason;
import io.github.markpollack.judge.verdict.InvocationRecords;
import io.github.markpollack.judge.verdict.Verdict;

import java.util.*;
import java.util.function.Function;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.Test;
import io.github.markpollack.judge.execution.*;
import io.github.markpollack.judge.judgment.*;
import io.github.markpollack.judge.jury.*;
import io.github.markpollack.judge.verdict.*;
import io.github.markpollack.judge.voting.*;
import io.github.markpollack.judge.requirement.Requirement;
import io.github.markpollack.judge.provenance.Invocation;
import io.github.markpollack.judge.reporting.VerdictReport;
import static org.assertj.core.api.Assertions.*;

class StructuredRosterRejectionTest {

	@Test
	void rfcRetainsMismatchedOriginalInsteadOfRecordingThrownInvocation() {
		var a = Rfc2119Requirement.of("A", "1", "MUST", "retain", "audit", null);
		var alien = Rfc2119Requirement.of("ALIEN", "2", "MUST", "alien", "audit", null);
		verifyRejection(a, alien,
				runtime -> Rfc2119Jury.builder().runtime(runtime).requirements(List.of(a)).evidence("actual").build());
	}

	@Test
	void rfcCancellationEscapesStructuredRosterContainment() {
		var a = Rfc2119Requirement.of("A", "1", "MUST", "retain", "audit", null);
		var cancelled = new CancellationException("caller cancelled");
		EvalRuntime<RequirementRequest<Rfc2119Specification, String>, Judgment> runtime = request -> {
			throw cancelled;
		};
		assertThatThrownBy(
				() -> Rfc2119Jury.builder().runtime(runtime).requirements(List.of(a)).evidence("actual").build().vote())
			.isSameAs(cancelled);
	}

	@Test
	void earsCancellationEscapesStructuredRosterContainment() {
		var a = EarsRequirement.of("A", "1", "retain", "The system shall retain facts", null);
		var cancelled = new CancellationException("caller cancelled");
		EvalRuntime<RequirementRequest<EarsSpecification, String>, Judgment> runtime = request -> {
			throw cancelled;
		};
		assertThatThrownBy(
				() -> EarsJury.builder().runtime(runtime).requirements(List.of(a)).evidence("actual").build().vote())
			.isSameAs(cancelled);
	}

	@Test
	void earsRetainsMismatchedOriginalInsteadOfRecordingThrownInvocation() {
		var a = EarsRequirement.of("A", "1", "retain", "The system shall retain facts", null);
		var alien = EarsRequirement.of("ALIEN", "2", "alien", "The system shall retain alien facts", null);
		verifyRejection(a, alien,
				runtime -> EarsJury.builder().runtime(runtime).requirements(List.of(a)).evidence("actual").build());
	}

	private <S> void verifyRejection(Requirement<S> a, Requirement<S> alien,
			Function<EvalRuntime<RequirementRequest<S, String>, Judgment>, Jury> configure) {
		var calls = new AtomicInteger();
		var lower = new Invocation("lower", "fixture:v1", true, "native", 2, Map.of("original", "retained"), List.of());
		var returned = Judgment.fail("Apparent unbound violation").forRequirement(alien).withInvocation(lower);
		var invocation = new Invocation("item", "fixture:v1", true, "native", 3, Map.of("rawAnswer", "ALIEN: FAIL"),
				List.of());
		EvalRuntime<RequirementRequest<S, String>, Judgment> runtime = request -> {
			calls.incrementAndGet();
			assertThat(request.requirement()).isSameAs(a);
			return new NativeExecution<>(returned, invocation);
		};
		var result = configure.apply(runtime).vote();
		assertThat(result.conclusion()).isEqualTo(Verdict.Conclusion.INCONCLUSIVE);
		var attempt = result.compositeAttempts().getFirst();
		assertThat(attempt.failure()).isNull();
		assertThat(attempt.dispositionReason()).isEqualTo(DispositionReason.PROTOCOL_UNBOUND);
		assertThat(attempt.verdict()).isNotNull();
		var original = attempt.verdict().individual().getFirst();
		assertThat(original.requirement()).isSameAs(alien);
		assertThat(original.status()).isEqualTo(JudgmentStatus.FAIL);
		assertThat(original.reasoning()).isEqualTo(returned.reasoning());
		assertThat(original.invocationIds()).contains("item", "lower");
		assertThat(InvocationRecords.of(result)).containsExactly(invocation, lower);
		var codec = NativeRequirementCodecs.codec();
		var read = codec.read(codec.write(result));
		assertThat(read).isEqualTo(result);
		assertThat(read.conclusion()).isEqualTo(Verdict.Conclusion.INCONCLUSIVE);
		assertThat(read.compositeAttempts().getFirst().verdict().individual().getFirst().requirement())
			.isEqualTo(alien);
		assertThat(VerdictReport.of(read).summary()).contains("PROTOCOL_UNBOUND", "ALIEN@2");
		var json = codec.write(result);
		assertThatThrownBy(() -> codec
			.read(json.replace("stage_failed", "used").replace(",\"dispositionReason\":\"protocol_unbound\"", "")))
			.hasMessageContaining("Roster association mismatch");
		assertThat(calls).hasValue(1);
	}

}
