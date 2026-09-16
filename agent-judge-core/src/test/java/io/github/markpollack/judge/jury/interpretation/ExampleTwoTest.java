/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury.interpretation;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.github.markpollack.judge.jury.interpretation.Fixtures.EXAMPLE_TWO;
import static io.github.markpollack.judge.jury.interpretation.Fixtures.assertDefect;
import static io.github.markpollack.judge.jury.interpretation.Fixtures.stored;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A2, A13: example two, a 0.13 record exported honestly.
 *
 * <p>{@code bud-ddd} run {@code 9b68576e}, item {@code review-derived-brief:spring-batch}. Scores
 * are {@code {"value": false}} objects, statuses are upper-case, there is no aggregation
 * evidence, and the composite structure is {@code subVerdicts} with no names. The root
 * duplicates its single sub-verdict entirely — which is exactly the inference the interpretation
 * refuses to make.
 */
@DisplayName("Example two: a 0.13 record")
class ExampleTwoTest {

	private static final List<JudgeSeat> JUDGES = List.of(
			new JudgeSeat(0, "structure:ddd-action-brief.md", null, "pass", null, null, null,
					"File exists at ddd-action-brief.md",
					List.of(new Check("file_exists", true, "File found at ddd-action-brief.md"))),
			new JudgeSeat(1, "structure:ddd-review.md", null, "fail", null, null, null, "File not found at ddd-review.md",
					List.of(new Check("file_exists", false, "File not found at ddd-review.md"))));

	private static final String REASONING = "No consensus: 1 passed, 1 failed (consensus required)";

	@Test
	@DisplayName("A2: the reading is what the record says, and nothing is inferred")
	void theReadingIsHonestAndLimited() {
		Interpretation interpretation = Verdicts.interpret(stored(EXAMPLE_TWO));

		assertThat(interpretation.schemaVersion()).isEqualTo(1);
		assertThat(interpretation.sourceVersion()).as("an unstamped record").isEqualTo(0);
		assertThat(interpretation.reading()).as("the record says FAIL").isEqualTo(VerdictReading.REJECTED);
		assertThat(interpretation.decidedBy()).as("not inferred from the root equalling its sub-verdict").isNull();

		assertThat(interpretation.root()).isEqualTo(new Stage(null, List.of(), null, null, null, null, null, null,
				"fail", null, REASONING, null, JUDGES));
		assertThat(interpretation.stages()).containsExactly(new Stage(null, List.of(), null, null, null, null, null,
				null, "fail", null, REASONING, null, JUDGES));
	}

	@Test
	@DisplayName("A2: the five §2.1 defects are present, each at its path")
	void theFiveDefectsArePresent() {
		List<Defect> defects = Verdicts.interpret(stored(EXAMPLE_TWO)).defects();

		assertDefect(defects, "verdict", "decision", DefectKind.ABSENT);
		assertDefect(defects, "verdict", "seats", DefectKind.ABSENT);
		assertDefect(defects, "verdict.subVerdicts[0]", "name", DefectKind.ABSENT);
		assertDefect(defects, "verdict.aggregated", "score", DefectKind.UNPARSEABLE);
		assertDefect(defects, "verdict.aggregated", "metadata.aggregation", DefectKind.ABSENT);
	}

	@Test
	@DisplayName("A2: every 0.13 Score object is one UNPARSEABLE defect at its own path, and nothing else is wrong")
	void everyScoreObjectIsOneDefect() {
		List<Defect> defects = Verdicts.interpret(stored(EXAMPLE_TWO)).defects();

		assertDefect(defects, "verdict.individualByName[structure:ddd-action-brief.md]", "score",
				DefectKind.UNPARSEABLE);
		assertDefect(defects, "verdict.individualByName[structure:ddd-review.md]", "score", DefectKind.UNPARSEABLE);
		assertDefect(defects, "verdict.subVerdicts[0].aggregated", "score", DefectKind.UNPARSEABLE);
		assertDefect(defects, "verdict.subVerdicts[0].individualByName[structure:ddd-action-brief.md]", "score",
				DefectKind.UNPARSEABLE);
		assertDefect(defects, "verdict.subVerdicts[0].individualByName[structure:ddd-review.md]", "score",
				DefectKind.UNPARSEABLE);
		assertThat(defects).as("five §2.1 defects plus one per remaining Score object").hasSize(10);
		assertThat(defects).filteredOn(defect -> defect.kind() == DefectKind.UNPARSEABLE)
			.allSatisfy(defect -> assertThat(defect.field()).isEqualTo("score"));
	}

	@Test
	@DisplayName("A13: with no evidence block the reading cannot be checked")
	void theReadingIsUndetermined() {
		assertThat(Verdicts.interpret(stored(EXAMPLE_TWO)).readingSupport()).isEqualTo(ReadingSupport.UNDETERMINED);
	}

}
