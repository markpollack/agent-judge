/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.ai.requirements;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import io.github.markpollack.judge.ai.model.*;
import io.github.markpollack.judge.execution.*;
import io.github.markpollack.judge.provenance.Invocation;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** Configured input capability, independent of native provider brand. */
class ConfiguredInputModesTest {

	@Test
	void generatedSingleAndRosterSupportEveryDeclaredInputMode() {
		var rfc = Rfc2119Requirement.of("R", "1", "MUST", "retain evidence", "audit", null);
		var ears = EarsRequirement.of("E", "1", "Evidence", "The system shall retain evidence", null);
		AtomicInteger calls = new AtomicInteger();
		AtomicInteger acquired = new AtomicInteger();
		JudgeModel nativeHarness = request -> {
			calls.incrementAndGet();
			return new JudgeModelResponse("R: PASS - retained\nE: PASS - retained", "fixture", null, Map.of());
		};
		var prepared = nativeHarness.withInputs(GeneratedInput.PREPARED_EVIDENCE);
		var investigative = nativeHarness.withInputs(GeneratedInput.INTEGRATED_INVESTIGATION);
		var both = nativeHarness.withInputs(GeneratedInput.values());
		for (var runtime : List.of(prepared, investigative, both)) {
			var rfcSingle = Rfc2119Judge.builder().runtime(runtime).requirement(rfc);
			var earsSingle = EarsJudge.builder().runtime(runtime).requirement(ears);
			var rfcRoster = Rfc2119Jury.builder().runtime(runtime).requirements(List.of(rfc));
			var earsRoster = EarsJury.builder().runtime(runtime).requirements(List.of(ears));
			if (runtime.supportedInputs().contains(GeneratedInput.PREPARED_EVIDENCE)) {
				rfcSingle.evidence("actual evidence").build().judge();
				earsSingle.evidence("actual evidence").build().judge();
				rfcRoster.evidenceSupplier(() -> {
					acquired.incrementAndGet();
					return "snapshot";
				}).build().vote();
				earsRoster.evidence("actual evidence").build().vote();
			}
			else {
				int before = calls.get();
				assertThatThrownBy(() -> rfcSingle.evidence("must not be discarded"))
					.hasMessageContaining("PREPARED_EVIDENCE");
				assertThatThrownBy(() -> earsSingle.evidence("must not be discarded"))
					.hasMessageContaining("PREPARED_EVIDENCE");
				assertThatThrownBy(() -> rfcRoster.evidenceSupplier(() -> {
					acquired.incrementAndGet();
					return "unused";
				})).hasMessageContaining("PREPARED_EVIDENCE");
				assertThatThrownBy(() -> earsRoster.evidence("must not be discarded"))
					.hasMessageContaining("PREPARED_EVIDENCE");
				assertThat(calls).hasValue(before);
			}
			if (runtime.supportedInputs().contains(GeneratedInput.INTEGRATED_INVESTIGATION)) {
				rfcSingle.build().judge();
				earsSingle.build().judge();
				rfcRoster.build().vote();
				earsRoster.build().vote();
			}
			else {
				int before = calls.get();
				assertThatThrownBy(rfcSingle::build).hasMessageContaining("INTEGRATED_INVESTIGATION");
				assertThatThrownBy(earsSingle::build).hasMessageContaining("INTEGRATED_INVESTIGATION");
				assertThatThrownBy(rfcRoster::build).hasMessageContaining("INTEGRATED_INVESTIGATION");
				assertThatThrownBy(earsRoster::build).hasMessageContaining("INTEGRATED_INVESTIGATION");
				assertThat(calls).hasValue(before);
			}
		}
		assertThat(calls).hasValue(16);
		assertThat(acquired).hasValue(2);
		assertThatThrownBy(() -> prepared.withInputs(GeneratedInput.INTEGRATED_INVESTIGATION))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> nativeHarness.withInputs()).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void capabilityWrapperPreservesTheExactNativeExecution() {
		var invocation = new Invocation("original", "native-fixture", true, "fixture", 7, Map.of("usage", 11),
				List.of());
		var original = new NativeExecution<>(new JudgeModelResponse("answer", "fixture", null, Map.of()), invocation);
		JudgeModel delegate = new JudgeModel() {
			public JudgeModelResponse generate(JudgeModelRequest request) {
				throw new AssertionError("must use native execution");
			}

			public NativeExecution<JudgeModelResponse> execute(JudgeModelRequest request) {
				return original;
			}
		};
		assertThat(delegate.withInputs(GeneratedInput.PREPARED_EVIDENCE).execute(JudgeModelRequest.user("evidence")))
			.isSameAs(original);
	}

}
