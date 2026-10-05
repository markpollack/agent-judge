/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.serialization;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import io.github.markpollack.judge.evaluation.*;
import io.github.markpollack.judge.judgment.*;
import io.github.markpollack.judge.policy.*;
import io.github.markpollack.judge.verdict.*;
import io.github.markpollack.judge.portable.PreservationLimitException;
import static org.assertj.core.api.Assertions.*;

class V6PreservationContractTest {

	@Test
	void frozenV5BytesAreExplicitlyRefusedAtTheirOriginalProducerPin() throws Exception {
		var bytes = getClass().getResourceAsStream("/history/v5-c0ae61d.json").readAllBytes();
		assertThat(bytes).hasSize(1257);
		String original = new String(bytes, StandardCharsets.UTF_8);
		assertThat(original).contains("\"schemaVersion\":5", "original-check");
		assertThatThrownBy(() -> new VerdictCodec().read(original)).hasMessageContaining("expected 6")
			.hasMessageContaining("c0ae61dda4a65e9278485cb528925d71f27a1007");
	}

	@Test
	void decidedAndFailedAttributionReopenWithoutAnotherPolicyCallAndLeaveOriginalUntouched() {
		var codec = new VerdictCodec();
		var verdict = Verdict.single("rule-only", Judgment.pass("deterministic"));
		var calls = new AtomicInteger();
		var source = new LinkedHashMap<String, Object>();
		source.put("threshold", .7);
		source.put("policyBytes", "caller-owned-v1");
		var attribution = new PolicyAttribution("reliance", "v1", source);
		source.put("threshold", .9);
		for (boolean failed : List.of(false, true)) {
			var cause = new IllegalStateException("fixture failure");
			var result = Evaluations.apply(verdict, actual -> {
				assertThat(actual).isSameAs(verdict);
				calls.incrementAndGet();
				if (failed)
					throw cause;
				return new PolicyDecision(PolicyAction.RELY, "usable original");
			}, attribution);
			assertThat(result.verdict()).isSameAs(verdict);
			var reopened = codec.readEvaluation(codec.write(result));
			assertThat(reopened.verdict()).isEqualTo(verdict);
			if (failed) {
				assertThat(((PolicyResult.Failed) result.policyResult()).cause()).isSameAs(cause);
				assertThat(((PolicyResult.Failed) reopened.policyResult()).attribution()).isEqualTo(attribution);
			}
			else
				assertThat(((PolicyResult.Decided) reopened.policyResult()).attribution()).isEqualTo(attribution);
		}
		assertThat(calls).hasValue(2);
		assertThat(attribution.configuration()).containsEntry("threshold", .7);
	}

	@Test
	void sizeCycleAndParserDepthBoundsRetainCompleteInputInsteadOfGenericErrors() {
		var codec = new VerdictCodec();
		String huge = "x".repeat(VerdictCodec.MAXIMUM_BYTES + 1);
		assertThatThrownBy(() -> codec.read(huge)).isInstanceOfSatisfying(PreservationLimitException.class,
				e -> assertThat(e.original()).isSameAs(huge));
		var verdict = Verdict.single("large", Judgment.pass(huge));
		assertThatThrownBy(() -> codec.write(verdict)).isInstanceOfSatisfying(PreservationLimitException.class,
				e -> assertThat(e.original()).isSameAs(verdict));
		var evaluation = Evaluations.of(verdict);
		assertThatThrownBy(() -> codec.write(evaluation)).isInstanceOfSatisfying(PreservationLimitException.class,
				e -> assertThat(e.original()).isSameAs(evaluation));
		var cyclic = new LinkedHashMap<String, Object>();
		cyclic.put("cycle", cyclic);
		assertThatThrownBy(() -> codec.read(cyclic)).isInstanceOfSatisfying(PreservationLimitException.class,
				e -> assertThat(e.original()).isSameAs(cyclic));
		String deep = "[".repeat(70) + "0" + "]".repeat(70);
		assertThatThrownBy(() -> codec.read(deep)).isInstanceOfSatisfying(PreservationLimitException.class,
				e -> assertThat(e.original()).isSameAs(deep));
	}

	@Test
	void policyCancellationAndPreservationRefusalEscapeWithOriginalUnchanged() {
		var verdict = Verdict.single("rule", Judgment.pass("original"));
		var limit = new PreservationLimitException("bound", verdict);
		assertThatThrownBy(() -> Evaluations.apply(verdict, v -> {
			throw new java.util.concurrent.CompletionException(limit);
		})).isSameAs(limit);
		try {
			assertThatThrownBy(() -> Evaluations.apply(verdict, v -> {
				Thread.currentThread().interrupt();
				return new PolicyDecision(PolicyAction.RELY, "post-return interruption");
			})).isInstanceOf(java.util.concurrent.CancellationException.class);
		}
		finally {
			Thread.interrupted();
		}
	}

}
