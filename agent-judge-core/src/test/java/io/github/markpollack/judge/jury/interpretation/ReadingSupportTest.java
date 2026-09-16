/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury.interpretation;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.github.markpollack.judge.jury.interpretation.Fixtures.A068E50A;
import static io.github.markpollack.judge.jury.interpretation.Fixtures.BOUNDARY_GOLDEN;
import static io.github.markpollack.judge.jury.interpretation.Fixtures.EXAMPLE_ONE;
import static io.github.markpollack.judge.jury.interpretation.Fixtures.assertDefect;
import static io.github.markpollack.judge.jury.interpretation.Fixtures.at;
import static io.github.markpollack.judge.jury.interpretation.Fixtures.listAt;
import static io.github.markpollack.judge.jury.interpretation.Fixtures.mutableCopy;
import static io.github.markpollack.judge.jury.interpretation.Fixtures.readMap;
import static io.github.markpollack.judge.jury.interpretation.Fixtures.stored;
import static org.assertj.core.api.Assertions.assertThat;

/** A8 and A13: whether the recorded facts support the reading. */
@DisplayName("Reading support")
class ReadingSupportTest {

	@SuppressWarnings("unchecked")
	private static Map<String, Object> attempt(Map<String, Object> verdict, int index) {
		return (Map<String, Object>) listAt(verdict, "compositeAttempts").get(index);
	}

	@Test
	@DisplayName("A13: evidence that contradicts the recorded status is CONTRADICTED, and the reading is still what the record says")
	void contradictingEvidence() {
		Map<String, Object> damaged = mutableCopy(stored(A068E50A));
		at(damaged, "aggregated").put("status", "pass");

		Interpretation interpretation = Verdicts.interpret(damaged);

		assertThat(interpretation.reading()).isEqualTo(VerdictReading.ACCEPTED);
		assertThat(interpretation.readingSupport()).isEqualTo(ReadingSupport.CONTRADICTED);
		assertDefect(interpretation.defects(), "verdict.aggregated", "status", DefectKind.INCONSISTENT);
		assertThat(interpretation.defects()).filteredOn(d -> d.kind() == DefectKind.INCONSISTENT).hasSize(1);
	}

	@Test
	@DisplayName("A13: 0.13 consensus semantics on an evidence block — 1 pass, 1 fail recorded as fail — is a contradiction")
	void legacyConsensusSemanticsContradict() {
		Map<String, Object> damaged = mutableCopy(stored(EXAMPLE_ONE));
		at(damaged, "aggregated").put("status", "fail");
		at(attempt(damaged, 0), "verdict", "aggregated").put("status", "fail");

		Interpretation interpretation = Verdicts.interpret(damaged);

		assertThat(interpretation.reading()).isEqualTo(VerdictReading.REJECTED);
		assertThat(interpretation.readingSupport()).isEqualTo(ReadingSupport.CONTRADICTED);
		assertThat(interpretation.defects()).anyMatch(d -> d.kind() == DefectKind.INCONSISTENT && d.field().equals("status"));
	}

	@Test
	@DisplayName("A8: a named attempt that cannot support its D1 claim is INCONSISTENT; the reading still reports the record")
	void aD1ClaimTheAttemptCannotSupport() {
		Map<String, Object> damaged = mutableCopy(readMap(BOUNDARY_GOLDEN));
		attempt(damaged, 0).put("policy", "FINAL_TIER");

		Interpretation interpretation = Verdicts.interpret(damaged);

		assertThat(interpretation.reading()).isEqualTo(VerdictReading.REJECTED);
		assertThat(interpretation.decidedBy()).isEqualTo(new DecidedBy("rubric", List.of("rubric"), "individual_rejection"));
		assertThat(interpretation.readingSupport()).isEqualTo(ReadingSupport.CONTRADICTED);
		assertThat(interpretation.defects()).filteredOn(d -> d.kind() == DefectKind.INCONSISTENT)
			.hasSize(1)
			.first()
			.satisfies(defect -> {
				assertThat(defect.path()).isEqualTo("verdict.decision");
				assertThat(defect.note()).contains("REJECT_ON_ANY_FAIL").contains("FINAL_TIER");
			});
	}

	@Test
	@DisplayName("A8: a decision that names a tier the record does not hold is INCONSISTENT")
	void aDecisionNamingAMissingTier() {
		Map<String, Object> damaged = mutableCopy(readMap(BOUNDARY_GOLDEN));
		at(damaged, "decision").put("tier", "ghost");

		Interpretation interpretation = Verdicts.interpret(damaged);

		assertThat(interpretation.reading()).isEqualTo(VerdictReading.REJECTED);
		assertThat(interpretation.decidedBy()).as("what the record says").isEqualTo(new DecidedBy("ghost", List.of("ghost"), "individual_rejection"));
		assertThat(interpretation.readingSupport()).isEqualTo(ReadingSupport.CONTRADICTED);
		assertDefect(interpretation.defects(), "verdict.decision", "tier", DefectKind.INCONSISTENT);
	}

	@Test
	@DisplayName("a tier_outcome root whose aggregate differs from its used stage is INCONSISTENT")
	void aCopiedOutcomeThatDiffers() {
		Map<String, Object> damaged = mutableCopy(stored(EXAMPLE_ONE));
		at(damaged, "aggregated").put("reasoning", "something the tier never said");

		Interpretation interpretation = Verdicts.interpret(damaged);

		assertThat(interpretation.readingSupport()).isEqualTo(ReadingSupport.CONTRADICTED);
		assertDefect(interpretation.defects(), "verdict.decision", "basis", DefectKind.INCONSISTENT);
	}

	@Test
	@DisplayName("an undecided root whose aggregate is not a machinery error is INCONSISTENT")
	void anUndecidedRootWithoutAMachineryError() {
		Map<String, Object> damaged = mutableCopy(readMap(Fixtures.COMPOSITE_GOLDEN));
		at(damaged, "aggregated").put("status", "pass");
		at(damaged, "aggregated").remove("reasonCode");

		Interpretation interpretation = Verdicts.interpret(damaged);

		assertThat(interpretation.reading()).isEqualTo(VerdictReading.ACCEPTED);
		assertThat(interpretation.readingSupport()).isEqualTo(ReadingSupport.CONTRADICTED);
		assertDefect(interpretation.defects(), "verdict.decision", "kind", DefectKind.INCONSISTENT);
	}

	@Test
	@DisplayName("a strategy whose rule is not closed-form is UNDETERMINED, never SUPPORTED")
	void anUnknownStrategyIsUndetermined() {
		Map<String, Object> damaged = mutableCopy(stored(A068E50A));
		at(damaged, "aggregated", "metadata", "aggregation").put("strategy", "oracle");

		Interpretation interpretation = Verdicts.interpret(damaged);

		assertThat(interpretation.readingSupport()).isEqualTo(ReadingSupport.UNDETERMINED);
		assertThat(interpretation.defects()).noneMatch(d -> d.kind() == DefectKind.INCONSISTENT);
	}

	@Test
	@DisplayName("A13: a 0.14 block that agrees with its status is SUPPORTED even though decidedBy is null")
	void aLegacyBlockThatAgrees() {
		Interpretation interpretation = Verdicts.interpret(stored(A068E50A));

		assertThat(interpretation.decidedBy()).isNull();
		assertThat(interpretation.readingSupport()).isEqualTo(ReadingSupport.SUPPORTED);
	}

	@Test
	@DisplayName("a 0.14–0.16 root that lacks its own evidence block is UNDETERMINED even though every child stage has one")
	void onlyTheRootsOwnBlockCounts() {
		Map<String, Object> damaged = mutableCopy(stored(A068E50A));
		at(damaged, "aggregated").put("metadata", Map.of());

		Interpretation interpretation = Verdicts.interpret(damaged);

		assertThat(interpretation.stages()).extracting(Stage::evidence).doesNotContainNull();
		assertThat(interpretation.root().evidence()).isNull();
		assertThat(interpretation.readingSupport()).isEqualTo(ReadingSupport.UNDETERMINED);
		assertDefect(interpretation.defects(), "verdict.aggregated", "metadata.aggregation", DefectKind.ABSENT);
		assertThat(interpretation.defects()).noneMatch(d -> d.kind() == DefectKind.INCONSISTENT);
	}

}
