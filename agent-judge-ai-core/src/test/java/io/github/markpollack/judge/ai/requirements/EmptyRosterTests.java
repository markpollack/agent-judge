package io.github.markpollack.judge.ai.requirements;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import io.github.markpollack.judge.ai.model.JudgeModel;
import io.github.markpollack.judge.ai.model.JudgeModelResponse;
import io.github.markpollack.judge.context.ExecutionStatus;
import io.github.markpollack.judge.context.JudgmentContext;
import io.github.markpollack.judge.result.Judgment;
import io.github.markpollack.judge.result.JudgmentReasonCode;
import io.github.markpollack.judge.result.JudgmentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A requirements judge with no requirements does not pass; it refuses to run.
 *
 * <p>This is the shape of the defect. A roster is a denominator, and an empty denominator makes
 * every conjunctive rollup vacuously true: no criterion failed, none was unestablished, so the
 * implementation "satisfies the specification". The result is a green judgment that was computed
 * over nothing and is indistinguishable, in every stored field, from one computed over a
 * specification that was genuinely met. In 0.16.0 that is exactly what happened.
 *
 * <p>It is refused twice, deliberately. At construction, because a jury assembled around an empty
 * roster is a configuration mistake and should never reach a model; and in the rollup, because a
 * roster arriving empty by some other path must still not become a PASS. The second guard costs
 * nothing and is the one that holds if the first is ever bypassed.
 *
 * <p>Parsing is untouched. A document that matches nothing legitimately yields an empty list —
 * that is a fact about the document, and the parser's job is to report it, not to refuse it. The
 * judge is what refuses.
 */
@DisplayName("An empty requirement roster")
class EmptyRosterTests {

	private static final JudgeModel MODEL = request -> new JudgeModelResponse("", "stub", null, Map.of());

	@Test
	@DisplayName("an empty criteria list is refused at construction, before any judge runs")
	void earsRefusesAnEmptyRosterAtConstruction() {
		assertThatThrownBy(() -> EarsJudge.create("audit", List.of(), MODEL))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("at least one acceptance criterion");
		assertThatCode(() -> EarsJudge.create("audit",
				List.of(new EarsCriterion("UC1-AC1", "first", "The system shall do it.")), MODEL))
			.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("an empty constraints list is refused at construction, before any judge runs")
	void rfcRefusesAnEmptyRosterAtConstruction() {
		assertThatThrownBy(() -> Rfc2119Judge.create("audit", List.of(), MODEL))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("at least one constraint");
		assertThatCode(() -> Rfc2119Judge.create("audit",
				List.of(new Rfc2119Constraint("RULE-1", "MUST", "do it", "because")), MODEL))
			.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("an empty criteria roster reaching the rollup is an error, never a pass and never an exclusion")
	void earsRefusesAnEmptyRosterInTheRollup() {
		Judgment judgment = EarsJudge.rollupFor(List.of(), answer("anything at all"));

		assertThat(judgment.status()).isEqualTo(JudgmentStatus.ERROR);
		assertThat(judgment.status()).isNotEqualTo(JudgmentStatus.PASS);
		assertThat(judgment.status()).isNotEqualTo(JudgmentStatus.NOT_APPLICABLE);
		assertThat(judgment.reasonCode()).isEqualTo(JudgmentReasonCode.JUDGE_REPORTED);
		assertThat(judgment.reasoning()).contains("empty");
		assertThat(judgment.metadata()).as("a stored total of 0 identifies a run affected before the fix")
			.containsEntry("criteriaTotal", 0);
	}

	@Test
	@DisplayName("an empty constraints roster reaching the rollup is an error, never a pass and never an exclusion")
	void rfcRefusesAnEmptyRosterInTheRollup() {
		Judgment judgment = Rfc2119Judge.rollupFor(List.of(), answer("anything at all"));

		assertThat(judgment.status()).isEqualTo(JudgmentStatus.ERROR);
		assertThat(judgment.status()).isNotEqualTo(JudgmentStatus.PASS);
		assertThat(judgment.status()).isNotEqualTo(JudgmentStatus.NOT_APPLICABLE);
		assertThat(judgment.reasonCode()).isEqualTo(JudgmentReasonCode.JUDGE_REPORTED);
		assertThat(judgment.reasoning()).contains("empty");
		assertThat(judgment.metadata()).containsEntry("constraintsTotal", 0);
	}

	@Test
	@DisplayName("a document that matches nothing still parses to an empty list: that is a fact, not an error")
	void anEmptyDocumentStillParses(@TempDir Path directory) throws IOException {
		Path criteria = Files.writeString(directory.resolve("criteria.md"), "# Nothing here matches\n");
		Path rules = Files.writeString(directory.resolve("rules.md"), "# Nothing here matches\n");

		assertThat(EarsCriterion.from(criteria)).isEmpty();
		assertThat(Rfc2119Constraint.from(rules)).isEmpty();
	}

	@Test
	@DisplayName("\"all not applicable\" means a non-empty roster in which every criterion was excluded")
	void allNotApplicableRequiresANonEmptyRoster() {
		Judgment excluded = EarsJudge.rollupFor(
				List.of(new EarsCriterion("UC1-AC1", "first", "The system shall use prepared statements.",
						"the change set contains Java sources")),
				answer("UC1-AC1: NOT_APPLICABLE - the change set contains no Java sources"));
		Judgment empty = EarsJudge.rollupFor(List.of(), answer("UC1-AC1: NOT_APPLICABLE - nothing"));

		assertThat(excluded.status()).as("one criterion, excluded: nothing applied")
			.isEqualTo(JudgmentStatus.NOT_APPLICABLE);
		assertThat(empty.status()).as("no criteria at all: vacuously nothing applied, which is not the same claim")
			.isEqualTo(JudgmentStatus.ERROR);
	}

	@Test
	@DisplayName("the same holds for constraints")
	void allNotApplicableRequiresANonEmptyConstraintRoster() {
		Judgment excluded = Rfc2119Judge.rollupFor(
				List.of(new Rfc2119Constraint("RULE-1", "MUST", "use prepared statements", "injection",
						"the service has a persistence layer")),
				answer("RULE-1: NOT_APPLICABLE - the service has no persistence layer"));

		assertThat(excluded.status()).isEqualTo(JudgmentStatus.NOT_APPLICABLE);
		assertThat(Rfc2119Judge.rollupFor(List.of(), answer("RULE-1: NOT_APPLICABLE - nothing")).status())
			.isEqualTo(JudgmentStatus.ERROR);
	}

	private static JudgeModelResponse answer(String text) {
		return new JudgeModelResponse(text, "stub", null, Map.of());
	}

	@SuppressWarnings("unused")
	private static JudgmentContext context() {
		return JudgmentContext.builder().goal("audit").status(ExecutionStatus.SUCCESS).build();
	}

}
