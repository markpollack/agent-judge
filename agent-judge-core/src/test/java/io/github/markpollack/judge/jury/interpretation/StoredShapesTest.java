/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury.interpretation;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.markpollack.judge.Judges;
import io.github.markpollack.judge.jury.ConsensusStrategy;
import io.github.markpollack.judge.jury.SimpleJury;
import io.github.markpollack.judge.jury.Verdict;
import io.github.markpollack.judge.result.Judgment;

import static io.github.markpollack.judge.jury.interpretation.Fixtures.A068E50A;
import static io.github.markpollack.judge.jury.interpretation.Fixtures.A36A7598C;
import static io.github.markpollack.judge.jury.interpretation.Fixtures.ACP_13_RUN;
import static io.github.markpollack.judge.jury.interpretation.Fixtures.ACP_13_SESSION;
import static io.github.markpollack.judge.jury.interpretation.Fixtures.BUD_EVAL_7E423DE9;
import static io.github.markpollack.judge.jury.interpretation.Fixtures.CONTEXT;
import static io.github.markpollack.judge.jury.interpretation.Fixtures.asMap;
import static io.github.markpollack.judge.jury.interpretation.Fixtures.assertDefect;
import static io.github.markpollack.judge.jury.interpretation.Fixtures.at;
import static io.github.markpollack.judge.jury.interpretation.Fixtures.mutableCopy;
import static io.github.markpollack.judge.jury.interpretation.Fixtures.stored;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A3, A4 (stored), §7.7 items 2, 3 and 5, and the flat root: every stored shape in the archive.
 */
@DisplayName("The stored shapes")
class StoredShapesTest {

	@Test
	@DisplayName("A3: a068e50a — two tiers, structure failed: REJECTED, decidedBy null, the stated defects")
	void a068e50a() {
		Interpretation interpretation = Verdicts.interpret(stored(A068E50A));

		assertThat(interpretation.sourceVersion()).isEqualTo(0);
		assertThat(interpretation.reading()).isEqualTo(VerdictReading.REJECTED);
		assertThat(interpretation.decidedBy()).as("the last-attempt-failed inference is not made").isNull();
		assertThat(interpretation.readingSupport()).isEqualTo(ReadingSupport.SUPPORTED);

		assertThat(interpretation.stages()).extracting(Stage::stage).containsExactly("artifacts", "structure");
		assertThat(interpretation.stages()).extracting(Stage::path).containsExactly(List.of("artifacts"),
				List.of("structure"));
		assertThat(interpretation.stages()).extracting(Stage::policy).containsOnly("REJECT_ON_ANY_FAIL");
		assertThat(interpretation.stages()).extracting(Stage::status).containsExactly("pass", "fail");
		assertThat(interpretation.stages()).extracting(Stage::usedByParent).containsOnlyNulls();
		assertThat(interpretation.stages()).extracting(Stage::disposition).containsOnlyNulls();
		assertThat(interpretation.root().status()).isEqualTo("fail");
		assertThat(interpretation.root().judges()).extracting(JudgeSeat::name).containsExactly("reportStructure");
		assertThat(interpretation.root().judges().get(0).checks()).hasSize(5);
		assertThat(interpretation.root().evidence()).isEqualTo(new Evidence("consensus", "propagate", null, 1, 1, 0,
				null, 0, 0, 0, 0, null, null, 0, 1, null, null, null, null));

		List<Defect> defects = interpretation.defects();
		assertDefect(defects, "verdict", "decision", DefectKind.ABSENT);
		assertDefect(defects, "verdict", "seats", DefectKind.ABSENT);
		assertDefect(defects, "verdict.compositeAttempts[0]", "disposition", DefectKind.ABSENT);
		assertDefect(defects, "verdict.compositeAttempts[1]", "disposition", DefectKind.ABSENT);
		assertThat(defects).hasSize(4);
	}

	@Test
	@DisplayName("A3, A4: 36a7598c — quality errored, root error: NOT_ASSESSED with the reasonCode ABSENT defect")
	void a36a7598c() {
		Interpretation interpretation = Verdicts.interpret(stored(A36A7598C));

		assertThat(interpretation.reading()).as("stored today as passed: false; a propagated judge error is not a rejection")
			.isEqualTo(VerdictReading.NOT_ASSESSED);
		assertThat(interpretation.decidedBy()).isNull();
		assertThat(interpretation.readingSupport()).as("propagate with one error is an error").isEqualTo(ReadingSupport.SUPPORTED);
		assertThat(interpretation.stages()).extracting(Stage::stage).containsExactly("artifacts", "structure", "quality");
		assertThat(interpretation.stages().get(2).status()).isEqualTo("error");
		assertThat(interpretation.stages().get(2).judges()).extracting(JudgeSeat::name, JudgeSeat::status)
			.containsExactly(org.assertj.core.groups.Tuple.tuple("dddQuality", "error"),
					org.assertj.core.groups.Tuple.tuple("goldRecall", "pass"));

		List<Defect> defects = interpretation.defects();
		assertDefect(defects, "verdict.aggregated", "reasonCode", DefectKind.ABSENT);
		assertDefect(defects, "verdict", "decision", DefectKind.ABSENT);
		assertDefect(defects, "verdict", "seats", DefectKind.ABSENT);
		assertDefect(defects, "verdict.compositeAttempts[2]", "disposition", DefectKind.ABSENT);
		assertDefect(defects, "verdict.individualByName[dddQuality]", "reasonCode", DefectKind.ABSENT);
		assertDefect(defects, "verdict.compositeAttempts[2].verdict.aggregated", "reasonCode", DefectKind.ABSENT);
		assertThat(defects).extracting(Defect::kind).containsOnly(DefectKind.ABSENT);
	}

	@Test
	@DisplayName("7e423de9 is three sub-verdicts wide and one deep, and reads ACCEPTED")
	void budEval7e423de9() {
		Interpretation interpretation = Verdicts.interpret(stored(BUD_EVAL_7E423DE9));

		assertThat(interpretation.reading()).isEqualTo(VerdictReading.ACCEPTED);
		assertThat(interpretation.sourceVersion()).isEqualTo(0);
		assertThat(interpretation.decidedBy()).isNull();
		assertThat(interpretation.stages()).hasSize(3);
		assertThat(interpretation.stages()).extracting(Stage::stage).containsOnlyNulls();
		assertThat(interpretation.stages()).extracting(Stage::path).containsOnly(List.of());
		assertThat(interpretation.root().judges()).extracting(JudgeSeat::name).containsExactly("codeQuality");
		JudgeSeat codeQuality = interpretation.root().judges().get(0);
		assertThat(codeQuality.status()).isEqualTo("pass");
		assertThat(codeQuality.score()).isEqualTo(0.7777777777777778);
		assertThat(codeQuality.scoreScale()).isEqualTo(new ScoreScale(0.0, 1.0));
		assertThat(codeQuality.checks()).hasSize(6);
	}

	@ParameterizedTest
	@ValueSource(strings = { ACP_13_RUN, ACP_13_SESSION })
	@DisplayName("§7.7 item 3: a stage that entered and never produced a verdict is not a failure")
	void aVerdictLessAttempt(String fixture) {
		Interpretation interpretation = Verdicts.interpret(stored(fixture));

		assertThat(interpretation.stages()).extracting(Stage::stage).containsExactly("review-produced", "review-quality");
		Stage failed = interpretation.stages().get(1);
		assertThat(failed.failure()).isEqualTo("jury_execution_failed");
		assertThat(failed.status()).isNull();
		assertThat(failed.reasoning()).isNull();
		assertThat(failed.evidence()).isNull();
		assertThat(failed.judges()).isEmpty();
		assertThat(failed.policy()).isEqualTo("FINAL_TIER");
		assertThat(failed.usedByParent()).isNull();
		assertThat(interpretation.defects()).as("an absent status is never a defect of the stage")
			.noneMatch(defect -> defect.path().startsWith("verdict.compositeAttempts[1]")
					&& !defect.field().equals("disposition"));

		assertThat(interpretation.reading()).isEqualTo(VerdictReading.NOT_ASSESSED);
		assertThat(interpretation.root().judges()).isEmpty();
		assertThat(interpretation.stages().get(0).status()).isEqualTo("pass");
	}

	@Test
	@DisplayName("a flat verdict with no composite container: the root carries it and stages is empty")
	void aFlatRoot() {
		Verdict flat = SimpleJury.builder()
			.judge(Judges.named(context -> Judgment.pass("compiled"), "build"))
			.judge(Judges.named(context -> Judgment.fail("two tests failed"), "tests"))
			.votingStrategy(new ConsensusStrategy())
			.build()
			.vote(CONTEXT);

		for (Interpretation interpretation : List.of(Verdicts.interpret(flat), Verdicts.interpret(asMap(flat)))) {
			assertThat(interpretation.stages()).isEmpty();
			assertThat(interpretation.root().status()).isEqualTo("abstain");
			assertThat(interpretation.root().judges()).extracting(JudgeSeat::name, JudgeSeat::status)
				.containsExactly(org.assertj.core.groups.Tuple.tuple("build", "pass"),
						org.assertj.core.groups.Tuple.tuple("tests", "fail"));
			assertThat(interpretation.root().evidence()).isNotNull();
			assertThat(interpretation.decidedBy()).isNull();
			assertThat(interpretation.defects()).isEmpty();
			assertThat(interpretation.reading()).isEqualTo(VerdictReading.UNDECIDED);
		}
	}

	@Test
	@DisplayName("a flat 0.13 record: upper-case statuses are read, and the root alone proves the parse")
	void aFlatLegacyRoot() {
		Map<String, Object> legacy = mutableCopy(stored(Fixtures.EXAMPLE_TWO));
		legacy.remove("subVerdicts");

		Interpretation interpretation = Verdicts.interpret(legacy);

		assertThat(interpretation.stages()).isEmpty();
		assertThat(interpretation.root().status()).isEqualTo("fail");
		assertThat(interpretation.root().judges()).extracting(JudgeSeat::status).containsExactly("pass", "fail");
		assertThat(interpretation.reading()).isEqualTo(VerdictReading.REJECTED);
		assertThat(interpretation.defects()).noneMatch(defect -> defect.kind() == DefectKind.UNKNOWN_VOCABULARY);
	}

	@Test
	@DisplayName("an unknown token is carried as recorded with an UNKNOWN_VOCABULARY defect, never an exception")
	void unknownTokensAreCarried() {
		Map<String, Object> damaged = mutableCopy(stored(A068E50A));
		at(damaged, "aggregated").put("status", "maybe");
		at(damaged, "aggregated").put("reasonCode", "gremlins");
		@SuppressWarnings("unchecked")
		Map<String, Object> attempt = (Map<String, Object>) Fixtures.listAt(damaged, "compositeAttempts").get(0);
		attempt.put("disposition", "sideways");
		attempt.put("policy", "REJECT_ON_TUESDAYS");

		Interpretation interpretation = Verdicts.interpret(damaged);

		assertThat(interpretation.root().status()).isEqualTo("maybe");
		assertThat(interpretation.root().reasonCode()).isEqualTo("gremlins");
		assertThat(interpretation.reading()).as("no reading can be taken from an unknown status").isNull();
		assertThat(interpretation.readingSupport()).isEqualTo(ReadingSupport.UNDETERMINED);
		assertThat(interpretation.stages().get(0).disposition()).isEqualTo("sideways");
		assertThat(interpretation.stages().get(0).usedByParent()).as("unknown is never read as used").isNull();
		assertThat(interpretation.stages().get(0).policy()).isEqualTo("REJECT_ON_TUESDAYS");
		assertDefect(interpretation.defects(), "verdict.aggregated", "status", DefectKind.UNKNOWN_VOCABULARY);
		assertDefect(interpretation.defects(), "verdict.aggregated", "reasonCode", DefectKind.UNKNOWN_VOCABULARY);
		assertDefect(interpretation.defects(), "verdict.compositeAttempts[0]", "disposition",
				DefectKind.UNKNOWN_VOCABULARY);
		assertDefect(interpretation.defects(), "verdict.compositeAttempts[0]", "policy", DefectKind.UNKNOWN_VOCABULARY);
	}

	@Test
	@DisplayName("a map of the wrong shape degrades into missing facts, not an exception")
	void aWrongShapeDegrades() {
		Interpretation interpretation = Verdicts.interpret(Map.of("something", "else"));

		assertThat(interpretation.reading()).isNull();
		assertThat(interpretation.readingSupport()).isEqualTo(ReadingSupport.UNDETERMINED);
		assertThat(interpretation.root().judges()).isEmpty();
		assertThat(interpretation.stages()).isEmpty();
		assertDefect(interpretation.defects(), "verdict", "aggregated", DefectKind.ABSENT);
		assertThat(interpretation.summary()).isNotBlank();
	}

}
