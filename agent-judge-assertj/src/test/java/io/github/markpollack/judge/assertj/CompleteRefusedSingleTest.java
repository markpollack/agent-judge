/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.assertj;

import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.jury.SimpleJury;
import io.github.markpollack.judge.ai.requirements.*;
import io.github.markpollack.judge.jev.*;
import io.github.markpollack.judge.execution.*;
import io.github.markpollack.judge.evaluation.*;
import io.github.markpollack.judge.judgment.*;
import io.github.markpollack.judge.verdict.*;
import io.github.markpollack.judge.voting.*;
import io.github.markpollack.judge.provenance.*;
import io.github.markpollack.judge.requirement.*;
import io.github.markpollack.judge.reporting.VerdictReport;
import io.github.markpollack.judge.portable.PreservationLimitException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class CompleteRefusedSingleTest {

	Invocation invocation(String id) {
		return new Invocation(id, "fixture:v1", true, "native", 2, Map.of("originalBytes", "retained"), List.of());
	}

	record Fixture(Judge judge, Judgment original, Requirement<?> expected, AtomicInteger calls) {
	}

	Fixture fixture(int protocol, boolean wrong, boolean cancelled, boolean overLimit) {
		var calls = new AtomicInteger();
		Requirement<?> expected = protocol == 0 ? Rfc2119Requirement.of("A", "1", "MUST", "retain", "audit", null)
				: protocol == 1 ? EarsRequirement.of("A", "1", "retain", "The system shall retain facts", null)
						: Requirement.text("A", "1", "retain");
		Requirement<?> alien = protocol == 0 ? Rfc2119Requirement.of("ALIEN", "2", "MUST", "different", "audit", null)
				: protocol == 1
						? EarsRequirement.of("ALIEN", "2", "different", "The system shall retain different facts", null)
						: Requirement.text("ALIEN", "2", "different");
		var builder = Judgment.fail("Completed original return")
			.forRequirement(wrong ? alien : expected)
			.withInvocation(invocation("lower"))
			.toBuilder()
			.check(Check.pass("own-check", "complete original child"));
		if (overLimit)
			for (int i = 0; i < 256; i++)
				builder.check(Check.pass("extra-" + i, "retain every child"));
		var original = builder.build();
		Judge judge;
		if (protocol == 0) {
			EvalRuntime<RequirementRequest<Rfc2119Specification, String>, Judgment> runtime = q -> {
				calls.incrementAndGet();
				if (cancelled)
					throw new CancellationException("cancel");
				return new NativeExecution<>(original, invocation("item"));
			};
			judge = Rfc2119Judge.builder()
				.runtime(runtime)
				.requirement((Requirement<Rfc2119Specification>) expected)
				.evidence("facts")
				.build();
		}
		else if (protocol == 1) {
			EvalRuntime<RequirementRequest<EarsSpecification, String>, Judgment> runtime = q -> {
				calls.incrementAndGet();
				if (cancelled)
					throw new CancellationException("cancel");
				return new NativeExecution<>(original, invocation("item"));
			};
			judge = EarsJudge.builder()
				.runtime(runtime)
				.requirement((Requirement<EarsSpecification>) expected)
				.evidence("facts")
				.build();
		}
		else {
			var artifact = ArtifactRef.ofBytes("evidence", "facts".getBytes(java.nio.charset.StandardCharsets.UTF_8),
					null);
			var evidence = new JevEvidence("facts", artifact, artifact, artifact.sha256(), true);
			EvalRuntime<RequirementRequest<String, JevEvidence>, Judgment> runtime = q -> {
				calls.incrementAndGet();
				if (cancelled)
					throw new CancellationException("cancel");
				return new NativeExecution<>(original, invocation("item"));
			};
			judge = JevJudge.builder()
				.runtime(runtime)
				.requirement((Requirement<String>) expected)
				.evidence(evidence)
				.build();
		}
		return new Fixture(judge, original, expected, calls);
	}

	@ParameterizedTest
	@ValueSource(ints = { 0, 1, 2 })
	void fullOriginalSurvivesDirectEvaluatedAssertedReportedAndReopenedPaths(int protocol) {
		var f = fixture(protocol, true, false, false);
		var direct = f.judge().judge();
		assertThat(direct.status()).isEqualTo(JudgmentStatus.ERROR);
		assertThat(direct.reasonCode()).isEqualTo(JudgmentReasonCode.RETURNED_RESULT_REJECTED);
		assertThat(direct.refusedReturn().original()).isSameAs(f.original());
		assertThat(direct.refusedReturn().expected()).isSameAs(f.expected());
		assertThat(direct.checks()).isEmpty();
		var result = Evaluations.evaluate(f.judge());
		var verdict = result.verdict();
		assertThat(verdict.conclusion()).isEqualTo(Verdict.Conclusion.INCONCLUSIVE);
		assertThat(verdict.individual().getFirst()).isSameAs(f.original());
		var seat = verdict.seats().getFirst();
		assertThat(seat.execution()).isEqualTo(SeatExecution.RETURNED_REJECTED);
		assertThat(seat.participation()).isEqualTo(Participation.NOT_RECORDED);
		assertThat(seat.cause()).isNull();
		assertThat(InvocationRecords.of(verdict)).extracting(Invocation::id).containsExactlyInAnyOrder("lower", "item");
		assertThat(verdict.individual().getFirst().checks())
			.containsExactly(Check.pass("own-check", "complete original child"));
		var codec = NativeRequirementCodecs.codec();
		var reopened = codec.readEvaluation(codec.write(result));
		assertThat(reopened.verdict()).isEqualTo(verdict);
		assertThat(VerdictReport.of(reopened.verdict()).judgments()).containsExactly(f.original());
		assertThat(reopened.verdict().conclusion()).isEqualTo(Verdict.Conclusion.INCONCLUSIVE);
		assertThatThrownBy(() -> Assertions.assertThat(reopened).isPassed()).isInstanceOf(AssertionError.class);
		var stage = Assertions.assertThat(f.judge());
		var live = stage.evaluate();
		assertThat(stage.evaluate()).isSameAs(live);
		assertThatThrownBy(stage::isPassed).isInstanceOf(AssertionError.class);
		assertThat(f.calls()).hasValue(3);
	}

	@ParameterizedTest
	@ValueSource(ints = { 0, 1, 2 })
	void correctlyAssociatedAndCancelledControlsRemainFaithful(int protocol) {
		var f = fixture(protocol, false, false, false);
		var result = Evaluations.evaluate(f.judge()).verdict();
		assertThat(result.conclusion()).isEqualTo(Verdict.Conclusion.FAIL);
		assertThat(result.judgment().refusedReturn()).isNull();
		assertThat(result.judgment().checks()).isEqualTo(f.original().checks());
		assertThat(InvocationRecords.of(result)).hasSize(2);
		var cancelled = fixture(protocol, true, true, false);
		assertThatThrownBy(() -> Evaluations.evaluate(cancelled.judge())).isInstanceOf(CancellationException.class);
		assertThat(cancelled.calls()).hasValue(1);
	}

	@ParameterizedTest
	@ValueSource(ints = { 0, 1, 2 })
	void preservationLimitEscapesDirectEvaluationAndAssertJWithoutLosingOriginal(int protocol) {
		var f = fixture(protocol, true, false, true);
		for (Runnable action : List.<Runnable>of(() -> f.judge().judge(), () -> Evaluations.evaluate(f.judge()),
				() -> Assertions.assertThat(f.judge()).evaluate())) {
			try {
				action.run();
				fail("preservation limit must escape");
			}
			catch (PreservationLimitException limit) {
				assertThat(limit.original()).isSameAs(f.original());
				assertThat(f.original().checks()).hasSize(257);
			}
		}
	}

	@ParameterizedTest
	@ValueSource(ints = { 0, 1, 2 })
	void parallelContainmentAndCachedAssertJFailuresPreserveCompleteOriginal(int protocol) {
		var f = fixture(protocol, true, false, true);
		assertThatThrownBy(() -> SimpleJury.builder()
			.judge(f.judge())
			.parallel(true)
			.votingStrategy(new AllEligiblePassStrategy())
			.build()
			.vote()).isInstanceOfSatisfying(PreservationLimitException.class,
					limit -> assertThat(limit.original()).isSameAs(f.original()));
		var stage = Assertions.assertThat(f.judge());
		var failures = java.util.stream.IntStream.range(0, 4)
			.mapToObj(i -> java.util.concurrent.CompletableFuture.supplyAsync(() -> {
				try {
					stage.evaluate();
					throw new AssertionError("limit must escape");
				}
				catch (PreservationLimitException limit) {
					return limit;
				}
			}))
			.toList();
		var first = failures.getFirst().join();
		for (var future : failures)
			assertThat(future.join()).isSameAs(first);
		assertThat(first.original()).isSameAs(f.original());
		assertThat(f.calls()).hasValue(2);
	}

}
