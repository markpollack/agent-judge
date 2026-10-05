/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.ai.requirements;
import io.github.markpollack.judge.verdict.InvocationRecords;
import io.github.markpollack.judge.verdict.Verdict;

import java.util.*;
import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.ai.model.*;
import io.github.markpollack.judge.execution.*;
import io.github.markpollack.judge.judgment.*;
import io.github.markpollack.judge.jury.*;
import io.github.markpollack.judge.verdict.*;
import io.github.markpollack.judge.voting.*;
import io.github.markpollack.judge.evaluation.Evaluations;
import io.github.markpollack.judge.provenance.Invocation;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.*;

class SingleAdmissionTest {

	@ParameterizedTest
	@CsvSource({ "rfc,PASS", "rfc,FAIL", "ears,PASS", "ears,FAIL" })
	void generatedSingleRefusesAlienEnvelope(String kind, String status) {
		String text = "A: " + status + " - apparent result\nALIEN: PASS - undeclared";
		JudgeModel model = request -> new JudgeModelResponse(text, "fixture", null, Map.of());
		Judge judge;
		Jury roster;
		if (kind.equals("rfc")) {
			var a = Rfc2119Requirement.of("A", "1", "MUST", "retain", "audit", null);
			judge = Rfc2119Judge.builder().runtime(model).requirement(a).evidence("actual").build();
			roster = Rfc2119Jury.builder().runtime(model).requirements(List.of(a)).evidence("actual").build();
		}
		else {
			var a = EarsRequirement.of("A", "1", "retain", "The system shall retain facts", null);
			judge = EarsJudge.builder().runtime(model).requirement(a).evidence("actual").build();
			roster = EarsJury.builder().runtime(model).requirements(List.of(a)).evidence("actual").build();
		}
		assertThat(roster.vote().conclusion()).isEqualTo(Verdict.Conclusion.INCONCLUSIVE);
		var result = Evaluations.evaluate(judge).verdict();
		assertThat(result.conclusion()).isEqualTo(Verdict.Conclusion.INCONCLUSIVE);
		assertThat(InvocationRecords.of(result)).hasSize(1);
		assertThat(result.judgment().checks().getFirst().judgment().status()).isEqualTo(JudgmentStatus.valueOf(status));
		var codec = NativeRequirementCodecs.codec();
		var read = codec.read(codec.write(result));
		assertThat(read).isEqualTo(result);
		assertThat(read.conclusion()).isEqualTo(Verdict.Conclusion.INCONCLUSIVE);
		assertThat(InvocationRecords.of(read)).isEqualTo(InvocationRecords.of(result));
		io.github.markpollack.judge.reporting.VerdictReport.of(read).summary();
	}

	@ParameterizedTest
	@CsvSource({ "rfc,PASS", "rfc,FAIL", "rfc,CANNOT_DETERMINE", "ears,PASS", "ears,FAIL", "ears,CANNOT_DETERMINE" })
	void boundSingleRetainsNativeAndStatus(String kind, String status) {
		var calls = new java.util.concurrent.atomic.AtomicInteger();
		JudgeModel model = request -> {
			calls.incrementAndGet();
			return new JudgeModelResponse("A: " + status + " - observed", "fixture", null, Map.of());
		};
		Judge judge = kind.equals("rfc")
				? Rfc2119Judge.builder()
					.runtime(model)
					.requirement(Rfc2119Requirement.of("A", "1", "MUST", "retain", "audit", null))
					.evidence("actual")
					.build()
				: EarsJudge.builder()
					.runtime(model)
					.requirement(EarsRequirement.of("A", "1", "retain", "The system shall retain facts", null))
					.evidence("actual")
					.build();
		var result = Evaluations.evaluate(judge).verdict();
		assertThat(result.judgment().status())
			.isEqualTo(status.equals("CANNOT_DETERMINE") ? JudgmentStatus.ABSTAIN : JudgmentStatus.valueOf(status));
		var codec = NativeRequirementCodecs.codec();
		assertThat(codec.read(codec.write(result))).isEqualTo(result);
		assertThat(calls).hasValue(1);
	}

	@ParameterizedTest
	@CsvSource({ "rfc", "ears" })
	void generatedSinglePropagatesCancellationAndInterruption(String kind) {
		var cancelled = new java.util.concurrent.CancellationException("cancelled");
		JudgeModel model = request -> {
			throw cancelled;
		};
		Judge judge = kind.equals("rfc")
				? Rfc2119Judge.builder()
					.runtime(model)
					.requirement(Rfc2119Requirement.of("A", "1", "MUST", "retain", "audit", null))
					.evidence("actual")
					.build()
				: EarsJudge.builder()
					.runtime(model)
					.requirement(EarsRequirement.of("A", "1", "retain", "The system shall retain facts", null))
					.evidence("actual")
					.build();
		assertThatThrownBy(judge::judge).isSameAs(cancelled);
		Thread.currentThread().interrupt();
		try {
			assertThatThrownBy(judge::judge).isInstanceOf(java.util.concurrent.CancellationException.class);
		}
		finally {
			Thread.interrupted();
		}
	}

}
