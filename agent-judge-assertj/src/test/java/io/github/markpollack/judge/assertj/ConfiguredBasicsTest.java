/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.assertj;
import io.github.markpollack.judge.construction.NonEmptyJudge;
import io.github.markpollack.judge.voting.AllMustPassStrategy;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import io.github.markpollack.judge.*;
import io.github.markpollack.judge.ai.model.*;
import io.github.markpollack.judge.ai.requirements.*;
import io.github.markpollack.judge.requirement.*;
import io.github.markpollack.judge.jury.*;
import io.github.markpollack.judge.verdict.*;
import io.github.markpollack.judge.voting.*;
import io.github.markpollack.judge.judgment.*;
import io.github.markpollack.judge.policy.*;
import static io.github.markpollack.judge.assertj.Assertions.assertThat;

public class ConfiguredBasicsTest {

	@org.junit.jupiter.api.Test
	void configuredPublicPath() {
		exercise();
	}

	public static void main(String[] args) {
		exercise();
		System.out.println(
				"PASS: actual public configured deterministic, RFC2119, ready Jury and AssertJ; native and policy call counts verified");
	}

	static void exercise() {
		Judge deterministic = NonEmptyJudge.builder().evidence("captured evidence").build();
		assertThat(deterministic).isPassed();
		var source = Requirement.text("source", "1", "Original requirement source").source();
		var security = new Rfc2119Requirement("SECURITY", "1", "Reject unsafe writes",
				new Rfc2119Specification("MUST", "Reject unsafe writes", "Protect state", null), source);
		var compatibility = new Rfc2119Requirement("COMPATIBILITY", "2", "Preserve API",
				new Rfc2119Specification("MUST", "Preserve API", "Support callers", null), source);
		AtomicInteger nativeCalls = new AtomicInteger();
		EvalModel runtime = request -> {
			nativeCalls.incrementAndGet();
			String text = request.messages().get(0).content();
			if (text.contains("SECURITY")) {
				if (!text.contains("security evidence") || !text.contains("Protect state"))
					throw new AssertionError("native spec/evidence lost");
				return new EvalModelResponse("SECURITY: PASS - Checked Security.java:1", "native-model",
						new Usage(10L, 5L, null, null, null, null), Map.of("sessionId", "native-session"));
			}
			if (!text.contains("compatibility evidence") || !text.contains("Preserve API"))
				throw new AssertionError("second native spec/evidence lost");
			return new EvalModelResponse("COMPATIBILITY: PASS - Checked Api.java:1", "native-model", null, Map.of());
		};
		Judge securityJudge = Rfc2119Judge.builder()
			.runtime(runtime)
			.requirement(security)
			.evidence("security evidence")
			.build();
		Judge compatibilityJudge = Rfc2119Judge.builder()
			.runtime(runtime)
			.requirement(compatibility)
			.evidence("compatibility evidence")
			.build();
		Judgment standalone = securityJudge.judge();
		if (standalone.requirement() != security || standalone.invocations().size() != 1
				|| !standalone.invocations().get(0).nativeFacts().get("sessionId").equals("native-session"))
			throw new AssertionError("association/native facts lost");
		Jury panel = VotingJury.builder()
			.judge("security", securityJudge)
			.judge("compatibility", compatibilityJudge)
			.votingStrategy(new AllMustPassStrategy())
			.parallel(false)
			.build();
		AtomicInteger policyCalls = new AtomicInteger();
		var stage = assertThat(panel).withPolicy(v -> {
			policyCalls.incrementAndGet();
			if (v.individual().get(0).requirement() != security || v.individual().get(1).requirement() != compatibility)
				throw new AssertionError("mixed inputs lost");
			return new PolicyDecision(PolicyAction.RELY, "Use retained facts");
		});
		stage.isPassed();
		stage.isPassed();
		stage.evaluate();
		if (nativeCalls.get() != 3 || policyCalls.get() != 1)
			throw new AssertionError("unexpected repeat calls");
		assertThat(stage.evaluate().verdict()).isPassed();
		assertThat(stage.evaluate()).isPassed();
		assertThat(security).judgedBy(Rfc2119Judge.builder().runtime(runtime))
			.withEvidence("security evidence")
			.isSatisfied();
		if (nativeCalls.get() != 4)
			throw new AssertionError("requirement assertion count");
		if (deterministic.judge().requirement() != null || !deterministic.judge().invocations().isEmpty())
			throw new AssertionError("invented deterministic facts");
	}

}
