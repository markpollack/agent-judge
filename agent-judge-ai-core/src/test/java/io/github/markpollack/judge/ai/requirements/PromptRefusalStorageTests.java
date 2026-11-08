/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.ai.requirements;

import java.util.List;
import java.util.Map;

import io.github.markpollack.judge.ai.model.EvalModel;
import io.github.markpollack.judge.ai.model.EvalModelResponse;
import io.github.markpollack.judge.judgment.Judgment;
import io.github.markpollack.judge.judgment.JudgmentReasonCode;
import io.github.markpollack.judge.judgment.JudgmentStatus;
import io.github.markpollack.judge.serialization.VerdictCodec;
import io.github.markpollack.judge.verdict.CompositeAttempt;
import io.github.markpollack.judge.verdict.CompositeFailure;
import io.github.markpollack.judge.verdict.CompositeFailureCode;
import io.github.markpollack.judge.verdict.CompositeRelation;
import io.github.markpollack.judge.verdict.Verdict;
import io.github.markpollack.judge.verdict.VerdictProvenance;
import io.github.markpollack.judge.verdict.VerdictProvenanceKind;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A prompt-size refusal is a result like any other: it must store and read back exactly,
 * so the refusal reaches the record instead of failing at the codec. The codec still
 * rejects a roster whose root contradicts its executed constituents.
 */
class PromptRefusalStorageTests {

	private static final String PASTED = "x".repeat(120_000);

	private final VerdictCodec codec = NativeRequirementCodecs.codec();

	private final List<EarsRequirement> ears = List.of(
			EarsRequirement.of("UC1-AC1", "rev1", "Record", "When a booking is saved, the system shall record it.",
					null),
			EarsRequirement.of("UC1-AC2", "rev1", "Confirm",
					"When a booking is saved, the system shall send exactly five confirmations.", null));

	private final List<Rfc2119Requirement> rules = List.of(
			Rfc2119Requirement.of("RULE-1", "rev1", "MUST", "record the booking.", "audit", null));

	private static EvalModel answering(String answer) {
		return request -> new EvalModelResponse(answer, "model", null, Map.of());
	}

	private void assertRoundTrips(Verdict refused) {
		assertThat(refused.conclusion()).isEqualTo(Verdict.Conclusion.INCONCLUSIVE);
		String json = codec.write(refused);
		Verdict read = codec.read(json);
		assertThat(read).isEqualTo(refused);
		assertThat(codec.write(read)).isEqualTo(json);
		assertThat(read.judgment().status()).isEqualTo(JudgmentStatus.ERROR);
		assertThat(read.judgment().reasonCode()).isEqualTo(JudgmentReasonCode.JUDGE_FAILED);
		assertThat(read.judgment().reasoning()).isEqualTo(refused.judgment().reasoning())
			.contains("requirement headers", "workspace slot", "template and criteria");
		assertThat(read.invocations()).isEmpty();
	}

	@Test
	void aRefusedEarsRosterStoresAndReadsBackExactly() {
		assertRoundTrips(EarsJury.builder()
			.runtime(answering(""))
			.requirements(ears)
			.evidence(PASTED)
			.build()
			.vote());
	}

	@Test
	void aRefusedRfc2119RosterStoresAndReadsBackExactly() {
		assertRoundTrips(Rfc2119Jury.builder()
			.runtime(answering(""))
			.requirements(rules)
			.evidence(PASTED)
			.build()
			.vote());
	}

	@Test
	void refusedSingleRequirementJudgmentsStoreAndReadBackExactly() {
		Judgment earsRefusal = EarsJudge.builder()
			.runtime(answering(""))
			.requirement(ears.getFirst())
			.evidence(PASTED)
			.build()
			.judge();
		Judgment ruleRefusal = Rfc2119Judge.builder()
			.runtime(answering(""))
			.requirement(rules.getFirst())
			.evidence(PASTED)
			.build()
			.judge();
		for (Judgment refusal : List.of(earsRefusal, ruleRefusal)) {
			Verdict single = Verdict.single("refused", refusal);
			Verdict read = codec.read(codec.write(single));
			assertThat(read).isEqualTo(single);
			assertThat(read.individual().getFirst().reasoning()).contains("workspace slot");
		}
	}

	@Test
	void aRosterRootThatContradictsItsExecutedConstituentsIsStillRejected() {
		Verdict passed = EarsJury.builder()
			.runtime(answering("UC1-AC1: PASS - ok\nUC1-AC2: PASS - ok"))
			.requirements(ears)
			.evidence("evidence")
			.build()
			.vote();
		assertThat(passed.conclusion()).isEqualTo(Verdict.Conclusion.PASS);

		Verdict contradicted = Verdict.advancedBuilder()
			.judgment(Judgment.error(JudgmentReasonCode.JUDGE_FAILED, "contradiction"))
			.roster(passed.roster())
			.declaredCardinality(passed.declaredCardinality())
			.invocations(passed.invocations())
			.compositeAttempts(passed.compositeAttempts())
			.provenance(new VerdictProvenance(VerdictProvenanceKind.ROSTER, null, null))
			.build();

		assertThatThrownBy(() -> codec.write(contradicted)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Roster rollup contradicts retained accepted constituents");
	}

	@Test
	void anErrorRootOverFailedAttemptsIsRejectedWhenAnInvocationIsRetained() {
		Verdict executed = EarsJury.builder()
			.runtime(answering("UC1-AC1: PASS - ok\nUC1-AC2: PASS - ok"))
			.requirements(ears)
			.evidence("evidence")
			.build()
			.vote();
		List<CompositeAttempt> failed = ears.stream()
			.map(req -> CompositeAttempt.executionFailed(req.id(), CompositeRelation.ROSTER_ITEM, null,
					new CompositeFailure(CompositeFailureCode.JURY_EXECUTION_FAILED, new IllegalStateException("x"))))
			.toList();

		Verdict invoked = Verdict.advancedBuilder()
			.judgment(Judgment.error(JudgmentReasonCode.JUDGE_FAILED, "a call was made"))
			.roster(executed.roster())
			.declaredCardinality(executed.declaredCardinality())
			.invocations(executed.invocations())
			.compositeAttempts(failed)
			.provenance(new VerdictProvenance(VerdictProvenanceKind.ROSTER, null, null))
			.build();

		assertThatThrownBy(() -> codec.write(invoked)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Roster rollup contradicts retained accepted constituents");
	}

}
