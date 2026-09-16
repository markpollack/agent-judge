/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury.interpretation;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.markpollack.judge.jury.Verdict;
import io.github.markpollack.judge.result.Judgment;

import static io.github.markpollack.judge.jury.interpretation.Fixtures.asMap;
import static io.github.markpollack.judge.jury.interpretation.Fixtures.assertDefect;
import static io.github.markpollack.judge.jury.interpretation.Fixtures.at;
import static io.github.markpollack.judge.jury.interpretation.Fixtures.mutableCopy;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The four stored score shapes: a bounded object is read on its own recorded scale, a bare
 * number in {@code [0, 1]} as is, a boolean object is unparseable, and absent is absent.
 */
@DisplayName("The score rule")
class ScoreRuleTest {

	/** A one-judge verdict whose three copies of the judgment all carry the given score. */
	@SuppressWarnings("unchecked")
	private static Map<String, Object> singleWithScore(Object score) {
		Map<String, Object> stored = mutableCopy(asMap(Verdict.single("grader", Judgment.pass("graded"))));
		for (Map<String, Object> judgment : java.util.List.<Map<String, Object>>of(at(stored, "aggregated"),
				(Map<String, Object>) Fixtures.listAt(stored, "individual").get(0),
				at(stored, "individualByName", "grader"))) {
			if (score == null) {
				judgment.remove("score");
			}
			else {
				judgment.put("score", score);
			}
		}
		return stored;
	}

	/** Only the score defects: a hand-built single verdict carries no evidence block, which is its own defect. */
	private static java.util.List<Defect> scoreDefects(Interpretation interpretation) {
		return interpretation.defects().stream().filter(defect -> defect.field().equals("score")).toList();
	}

	private static Map<String, Object> bounded(Object value, double min, double max) {
		Map<String, Object> score = new LinkedHashMap<>();
		score.put("value", value);
		score.put("min", min);
		score.put("max", max);
		return score;
	}

	private static JudgeSeat grader(Interpretation interpretation) {
		return interpretation.root().judges().get(0);
	}

	@Test
	@DisplayName("a bounded score on (0, 10) is normalised and its scale is reported")
	void aBoundedScoreOnZeroToTen() {
		Interpretation interpretation = Verdicts.interpret(singleWithScore(bounded(7.5, 0.0, 10.0)));

		assertThat(grader(interpretation).score()).isEqualTo(0.75);
		assertThat(grader(interpretation).scoreScale()).isEqualTo(new ScoreScale(0.0, 10.0));
		assertThat(scoreDefects(interpretation)).isEmpty();
	}

	@Test
	@DisplayName("a bounded score on (0, 1) keeps its value and still reports its scale")
	void aBoundedScoreOnZeroToOne() {
		Interpretation interpretation = Verdicts.interpret(singleWithScore(bounded(0.75, 0.0, 1.0)));

		assertThat(grader(interpretation).score()).isEqualTo(0.75);
		assertThat(grader(interpretation).scoreScale()).isEqualTo(new ScoreScale(0.0, 1.0));
		assertThat(scoreDefects(interpretation)).isEmpty();
	}

	@Test
	@DisplayName("a bare number in [0, 1] is read as is, with no scale")
	void aBareNumber() {
		Interpretation interpretation = Verdicts.interpret(singleWithScore(0.75));

		assertThat(grader(interpretation).score()).isEqualTo(0.75);
		assertThat(grader(interpretation).scoreScale()).isNull();
		assertThat(scoreDefects(interpretation)).isEmpty();
	}

	@Test
	@DisplayName("a bare number outside [0, 1] is UNPARSEABLE and the score is null")
	void aBareNumberOutOfRange() {
		Interpretation interpretation = Verdicts.interpret(singleWithScore(1.5));

		assertThat(grader(interpretation).score()).isNull();
		assertThat(grader(interpretation).scoreScale()).isNull();
		assertDefect(interpretation.defects(), "verdict.individual[0]", "score", DefectKind.UNPARSEABLE);
	}

	@Test
	@DisplayName("a boolean score object is UNPARSEABLE and ignored")
	void aBooleanObject() {
		Interpretation interpretation = Verdicts.interpret(singleWithScore(Map.of("value", true)));

		assertThat(grader(interpretation).score()).isNull();
		assertThat(grader(interpretation).scoreScale()).isNull();
		assertDefect(interpretation.defects(), "verdict.individual[0]", "score", DefectKind.UNPARSEABLE);
		assertDefect(interpretation.defects(), "verdict.aggregated", "score", DefectKind.UNPARSEABLE);
	}

	@Test
	@DisplayName("a bounded object whose bounds cannot be used is UNPARSEABLE and reports no scale")
	void unusableBounds() {
		Interpretation interpretation = Verdicts.interpret(singleWithScore(bounded(3.0, 5.0, 5.0)));

		assertThat(grader(interpretation).score()).isNull();
		assertThat(grader(interpretation).scoreScale()).isNull();
		assertDefect(interpretation.defects(), "verdict.individual[0]", "score", DefectKind.UNPARSEABLE);
	}

	@Test
	@DisplayName("an absent or null score is null with no defect")
	void anAbsentScore() {
		assertThat(grader(Verdicts.interpret(singleWithScore(null))).score()).isNull();
		assertThat(scoreDefects(Verdicts.interpret(singleWithScore(null)))).isEmpty();

		Map<String, Object> explicitNull = singleWithScore(null);
		at(explicitNull, "individualByName", "grader").put("score", null);
		assertThat(grader(Verdicts.interpret(explicitNull)).score()).isNull();
		assertThat(scoreDefects(Verdicts.interpret(explicitNull))).isEmpty();
	}

}
