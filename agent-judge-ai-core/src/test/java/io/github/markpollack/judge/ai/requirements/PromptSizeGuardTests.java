/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.ai.requirements;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.markpollack.judge.ai.model.EvalModel;
import io.github.markpollack.judge.ai.model.EvalModelResponse;
import io.github.markpollack.judge.judgment.Judgment;
import io.github.markpollack.judge.judgment.JudgmentReasonCode;
import io.github.markpollack.judge.judgment.JudgmentStatus;
import io.github.markpollack.judge.verdict.AttemptDisposition;
import io.github.markpollack.judge.verdict.Verdict;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A generated roster audit is one model call. A prompt far beyond what a judge needs is a
 * configuration mistake, usually evidence pasted where a workspace path belongs, and it
 * must be refused before the call: as an ERROR that names the sizes, never as a FAIL.
 */
class PromptSizeGuardTests {

	/** Comfortably above the 100,000-character default, like the pasted Actualize evidence. */
	private static final String PASTED = "x".repeat(120_000);

	private final List<EarsRequirement> ears = List.of(
			EarsRequirement.of("UC1-AC1", "rev1", "Record", "When a booking is saved, the system shall record it.",
					null),
			EarsRequirement.of("UC1-AC2", "rev1", "Confirm",
					"When a booking is saved, the system shall send exactly five confirmations.", null));

	private final List<Rfc2119Requirement> rules = List.of(
			Rfc2119Requirement.of("RULE-1", "rev1", "MUST", "record the booking", "audit", null));

	private static EvalModel counting(AtomicInteger calls, String answer) {
		return request -> {
			calls.incrementAndGet();
			return new EvalModelResponse(answer, "model", null, Map.of());
		};
	}

	private static void assertRefusedBeforeTheCall(Judgment judgment, AtomicInteger calls) {
		assertThat(calls).hasValue(0);
		assertThat(judgment.status()).isEqualTo(JudgmentStatus.ERROR);
		assertThat(judgment.reasonCode()).isEqualTo(JudgmentReasonCode.JUDGE_FAILED);
		assertThat(judgment.reasoning()).contains("100000", "requirement headers", "workspace slot",
				"template and criteria");
	}

	@Test
	void earsRosterOverTheDefaultLimitIsAnErrorWithNoCall() {
		var calls = new AtomicInteger();
		Verdict verdict = EarsJury.builder()
			.runtime(counting(calls, "UC1-AC1: PASS - ok\nUC1-AC2: PASS - ok"))
			.requirements(ears)
			.evidence(PASTED)
			.build()
			.vote();

		assertRefusedBeforeTheCall(verdict.judgment(), calls);
		assertThat(verdict.invocations()).isEmpty();
		assertThat(verdict.compositeAttempts()).hasSize(2)
			.allSatisfy(attempt -> assertThat(attempt.disposition()).isNotEqualTo(AttemptDisposition.USED));
	}

	@Test
	void rfc2119RosterOverTheDefaultLimitIsAnErrorWithNoCall() {
		var calls = new AtomicInteger();
		Verdict verdict = Rfc2119Jury.builder()
			.runtime(counting(calls, "RULE-1: PASS - ok"))
			.requirements(rules)
			.evidence(PASTED)
			.build()
			.vote();

		assertRefusedBeforeTheCall(verdict.judgment(), calls);
		assertThat(verdict.invocations()).isEmpty();
	}

	@Test
	void singleRequirementJudgesOverTheDefaultLimitAreErrorsWithNoCall() {
		var calls = new AtomicInteger();
		Judgment earsJudgment = EarsJudge.builder()
			.runtime(counting(calls, "UC1-AC1: PASS - ok"))
			.requirement(ears.getFirst())
			.evidence(PASTED)
			.build()
			.judge();
		Judgment ruleJudgment = Rfc2119Judge.builder()
			.runtime(counting(calls, "RULE-1: PASS - ok"))
			.requirement(rules.getFirst())
			.evidence(PASTED)
			.build()
			.judge();

		assertRefusedBeforeTheCall(earsJudgment, calls);
		assertRefusedBeforeTheCall(ruleJudgment, calls);
		assertThat(earsJudgment.invocations()).isEmpty();
	}

	@Test
	void theRefusalStatesTheActualSizeAndItsBreakdown() {
		var calls = new AtomicInteger();
		String reasoning = EarsJury.builder()
			.runtime(counting(calls, ""))
			.requirements(ears)
			.evidence(PASTED)
			.build()
			.vote()
			.judgment()
			.reasoning();

		// The workspace slot holds the prepared evidence and its label.
		assertThat(reasoning).contains("workspace slot " + ("Prepared evidence:\n" + PASTED).length());
		assertThat(reasoning).containsPattern("prompt of \\d+ characters exceeds the 100000-character limit");
	}

	@Test
	void aPromptExactlyAtTheConfiguredLimitIsSentAndOneCharacterLessRefuses() {
		var sizes = new java.util.ArrayList<Integer>();
		EvalModel measuring = request -> {
			sizes.add(request.messages().getFirst().content().length());
			return new EvalModelResponse("UC1-AC1: PASS - ok\nUC1-AC2: PASS - ok", "model", null, Map.of());
		};
		EarsJury.builder().runtime(measuring).requirements(ears).evidence("evidence").build().vote();
		int exact = sizes.getFirst();

		var calls = new AtomicInteger();
		Verdict atLimit = EarsJury.builder()
			.runtime(counting(calls, "UC1-AC1: PASS - ok\nUC1-AC2: PASS - ok"))
			.maxPromptCharacters(exact)
			.requirements(ears)
			.evidence("evidence")
			.build()
			.vote();
		assertThat(calls).hasValue(1);
		assertThat(atLimit.conclusion()).isEqualTo(Verdict.Conclusion.PASS);

		Verdict below = EarsJury.builder()
			.runtime(counting(calls, ""))
			.maxPromptCharacters(exact - 1)
			.requirements(ears)
			.evidence("evidence")
			.build()
			.vote();
		assertThat(calls).hasValue(1);
		assertThat(below.judgment().status()).isEqualTo(JudgmentStatus.ERROR);
		assertThat(below.judgment().reasoning()).contains("prompt of " + exact + " characters exceeds the "
				+ (exact - 1) + "-character limit");
	}

	@Test
	void theLimitIsConfigurableOnEveryGeneratedBuilderAndMustBePositive() {
		var calls = new AtomicInteger();
		Judgment raised = EarsJudge.builder()
			.runtime(counting(calls, "UC1-AC1: PASS - ok"))
			.maxPromptCharacters(200_000)
			.requirement(ears.getFirst())
			.evidence(PASTED)
			.build()
			.judge();
		assertThat(calls).hasValue(1);
		assertThat(raised.status()).isEqualTo(JudgmentStatus.PASS);

		Verdict lowered = Rfc2119Jury.builder()
			.runtime(counting(calls, "RULE-1: PASS - ok"))
			.maxPromptCharacters(100)
			.requirements(rules)
			.evidence("evidence")
			.build()
			.vote();
		assertThat(calls).hasValue(1);
		assertThat(lowered.judgment().status()).isEqualTo(JudgmentStatus.ERROR);

		assertThat(PromptLimits.DEFAULT_MAX_PROMPT_CHARACTERS).isEqualTo(100_000);
		org.assertj.core.api.Assertions.assertThatThrownBy(() -> Rfc2119Judge.builder()
			.runtime(counting(calls, ""))
			.maxPromptCharacters(0)).isInstanceOf(IllegalArgumentException.class);
	}

}
