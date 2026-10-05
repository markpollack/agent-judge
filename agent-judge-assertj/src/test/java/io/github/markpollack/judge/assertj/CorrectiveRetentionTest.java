/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.assertj;
import io.github.markpollack.judge.verdict.Verdict;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import io.github.markpollack.judge.ai.requirements.*;
import io.github.markpollack.judge.execution.*;
import io.github.markpollack.judge.judgment.*;
import io.github.markpollack.judge.jury.*;
import io.github.markpollack.judge.verdict.*;
import io.github.markpollack.judge.voting.*;
import io.github.markpollack.judge.policy.*;
import io.github.markpollack.judge.provenance.Invocation;
import io.github.markpollack.judge.requirement.*;
import io.github.markpollack.judge.serialization.VerdictCodec;
import static org.assertj.core.api.Assertions.*;

class CorrectiveRetentionTest {

	@ParameterizedTest
	@ValueSource(booleans = { true, false })
	void allOfRejectionReachesPolicyOnceAndRetainedAssertionsExecuteNothing(boolean failingSibling) {
		var a = Requirement.text("A", "1", "A");
		var b = Requirement.text("B", "1", "B");
		var parent = new GeneralRequirement<>("parent", "1", "A and B", new AllOf(List.of(a, b)), a.source());
		var returned = Verdict.single("B", Judgment.fail("Unusable apparent violation"));
		var invalid = new Verdict(returned.judgment(), returned.individual(), returned.individualByName(),
				returned.weights(), returned.seats(), returned.provenance(), returned.compositeAttempts(), 2, b);
		var calls = new AtomicInteger();
		var jury = Assignments.<String>forRequirement(parent).jury(a, TestRecipes.jury((r, e) -> {
			calls.incrementAndGet();
			return Verdict.single("A", failingSibling ? Judgment.fail("Violation") : Judgment.pass("A holds"));
		})).jury(b, TestRecipes.jury((r, e) -> {
			calls.incrementAndGet();
			return invalid;
		})).validate().evidence("fixture").build();
		var policies = new AtomicInteger();
		var stage = Assertions.assertThat(jury).withPolicy(verdict -> {
			policies.incrementAndGet();
			assertThat(verdict.compositeAttempts().getLast().verdict()).isSameAs(invalid);
			return new PolicyDecision(PolicyAction.RELY, "Inspect all original facts");
		});
		var result = stage.evaluate();
		assertThat(result.verdict().conclusion())
			.isEqualTo(failingSibling ? Verdict.Conclusion.FAIL : Verdict.Conclusion.INCONCLUSIVE);
		assertThat(stage.evaluate()).isSameAs(result);
		assertThatThrownBy(stage::isPassed).isInstanceOf(AssertionError.class)
			.hasMessageContaining("INVALID_TIER_RESULT");
		var codec = new VerdictCodec();
		var read = codec.readEvaluation(codec.write(result));
		assertThatThrownBy(() -> Assertions.assertThat(read).isPassed()).isInstanceOf(AssertionError.class);
		assertThatThrownBy(() -> Assertions.assertThat(read.verdict()).isPassed()).isInstanceOf(AssertionError.class);
		assertThat(calls).hasValue(2);
		assertThat(policies).hasValue(1);
	}

	@ParameterizedTest
	@ValueSource(booleans = { true, false })
	void rejectedStructuredRosterCannotPassWithRelianceAndReadsExecuteNothing(boolean ears) {
		var calls = new AtomicInteger();
		var fact = new Invocation("item", "fixture:v1", true, "native", 0, Map.of("raw", "ALIEN: FAIL"), List.of());
		Jury jury;
		if (ears) {
			var a = EarsRequirement.of("A", "1", "retain", "The system shall retain facts", null);
			var alien = EarsRequirement.of("ALIEN", "1", "alien", "The system shall retain alien facts", null);
			NativeRuntime<RequirementRequest<EarsSpecification, String>, Judgment> runtime = request -> {
				calls.incrementAndGet();
				return new NativeExecution<>(Judgment.fail("Unbound").forRequirement(alien), fact);
			};
			jury = EarsJury.builder().runtime(runtime).requirements(List.of(a)).evidence("actual").build();
		}
		else {
			var a = Rfc2119Requirement.of("A", "1", "MUST", "retain", "audit", null);
			var alien = Rfc2119Requirement.of("ALIEN", "1", "MUST", "alien", "audit", null);
			NativeRuntime<RequirementRequest<Rfc2119Specification, String>, Judgment> runtime = request -> {
				calls.incrementAndGet();
				return new NativeExecution<>(Judgment.fail("Unbound").forRequirement(alien), fact);
			};
			jury = Rfc2119Jury.builder().runtime(runtime).requirements(List.of(a)).evidence("actual").build();
		}
		var policies = new AtomicInteger();
		var stage = Assertions.assertThat(jury).withPolicy(verdict -> {
			policies.incrementAndGet();
			return new PolicyDecision(PolicyAction.RELY, "Inspect the incomplete result");
		});
		var result = stage.evaluate();
		assertThat(result.verdict().conclusion()).isEqualTo(Verdict.Conclusion.INCONCLUSIVE);
		assertThatThrownBy(stage::isPassed).isInstanceOf(AssertionError.class).hasMessageContaining("PROTOCOL_UNBOUND");
		var codec = NativeRequirementCodecs.codec();
		var read = codec.readEvaluation(codec.write(result));
		assertThatThrownBy(() -> Assertions.assertThat(read).isPassed()).isInstanceOf(AssertionError.class);
		assertThat(calls).hasValue(1);
		assertThat(policies).hasValue(1);
	}

}
