package io.github.markpollack.judge.ai.requirements;

import java.util.List;
import java.util.Map;

import io.github.markpollack.judge.ai.model.JudgeModel;
import io.github.markpollack.judge.ai.model.JudgeModelResponse;
import io.github.markpollack.judge.context.ExecutionStatus;
import io.github.markpollack.judge.context.JudgmentContext;
import io.github.markpollack.judge.result.Check;
import io.github.markpollack.judge.result.Judgment;
import io.github.markpollack.judge.result.JudgmentStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What a set of per-constraint answers means.
 *
 * <p>The rollup is the one the acceptance criteria get, for the same reason: a written MUST is
 * required by construction, so "could not be established" is not "does not apply". These cases are
 * deliberately the same shape as {@link EarsJudgeTests}. The duplication between the two judges is
 * intentional, and so is the duplication between their tests — it is what would catch the two
 * drifting apart.
 */
class Rfc2119JudgeTests {

	private static final List<Rfc2119Constraint> THREE = List.of(
		new Rfc2119Constraint("RULE-1", "MUST", "keep transactions on the service boundary", "consistency"),
		new Rfc2119Constraint("RULE-2", "MUST NOT", "expose entities from controllers", "coupling"),
		new Rfc2119Constraint("RULE-3", "SHOULD", "name repositories after aggregates", "readability"));

	@Test
	void everyConstraintEstablishedIsAPass() {
		Judgment judgment = judge("""
			    RULE-1: PASS - ClinicService.java:42 is annotated
			    RULE-2: PASS - no entity leaves OwnerController.java:60
			    RULE-3: PASS - OwnerRepository.java:12 is named for its aggregate
			    """);

		assertThat(judgment.status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(judgment.checks()).hasSize(3);
		assertThat(judgment.reasoning()).isEqualTo("all 3 constraints hold");
	}

	@Test
	void oneViolatedConstraintFailsTheWhole() {
		Judgment judgment = judge("""
			    RULE-1: PASS - ClinicService.java:42 is annotated
			    RULE-2: FAIL - OwnerController.java:60 returns the entity directly
			    RULE-3: PASS - OwnerRepository.java:12 is named for its aggregate
			    """);

		assertThat(judgment.status()).isEqualTo(JudgmentStatus.FAIL);
		assertThat(judgment.reasoning()).isEqualTo("2 of 3 hold, 1 violated");
		assertThat(check(judgment, "RULE-2").message()).contains("returns the entity directly");
	}

	@Test
	void oneAbstentionMakesTheWholeAbstain() {
		// The design says the constraint applies. Not being able to settle it is not the design
		// being satisfied.
		Judgment judgment = judge("""
			    RULE-1: PASS - ClinicService.java:42 is annotated
			    RULE-2: CANNOT_DETERMINE - the boundary is not visible from the code
			    RULE-3: PASS - OwnerRepository.java:12 is named for its aggregate
			    """);

		assertThat(judgment.status()).isEqualTo(JudgmentStatus.ABSTAIN);
		assertThat(judgment.metadata()).containsEntry("unestablished", "RULE-2");
	}

	@Test
	void aViolationOutranksAnAbstention() {
		Judgment judgment = judge("""
			    RULE-1: CANNOT_DETERMINE - not visible from the code
			    RULE-2: FAIL - OwnerController.java:60 returns the entity directly
			    RULE-3: PASS - OwnerRepository.java:12 is named for its aggregate
			    """);

		assertThat(judgment.status()).isEqualTo(JudgmentStatus.FAIL);
	}

	@Test
	void anUnansweredConstraintIsAnErrorNotAPass() {
		Judgment judgment = judge("""
			    RULE-1: PASS - ClinicService.java:42 is annotated
			    RULE-2: PASS - no entity leaves OwnerController.java:60
			    """);

		assertThat(judgment.status()).isEqualTo(JudgmentStatus.ERROR);
		assertThat(judgment.reasoning()).contains("1 of 3").contains("constraints").contains("RULE-3");
	}

	@Test
	void answersOutOfOrderAreReportedInTheDocumentsOrder() {
		Judgment judgment = judge("""
			    RULE-3: PASS - OwnerRepository.java:12 is named for its aggregate
			    RULE-1: PASS - ClinicService.java:42 is annotated
			    RULE-2: PASS - no entity leaves OwnerController.java:60
			    """);

		assertThat(judgment.status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(judgment.checks().stream().map(Check::name).toList())
			.containsExactly("RULE-1", "RULE-2", "RULE-3");
	}

	@Test
	void anEmptyAuditIsAnErrorNotAPass() {
		assertThat(judge("").status()).isEqualTo(JudgmentStatus.ERROR);
		assertThat(judge("The architecture looks fine to me.").status()).isEqualTo(JudgmentStatus.ERROR);
	}

	@Test
	void aBackendThatCouldNotAnswerBlamesTheJudgeNotTheSubject() {
		JudgeModel model = request -> new JudgeModelResponse(
			"No API credentials are configured for this backend", "recorded", null, Map.of("successful", false));
		Judgment judgment = Rfc2119Judge.create("rules", THREE, model).judge(context());

		assertThat(judgment.status()).isEqualTo(JudgmentStatus.ERROR);
		assertThat(judgment.reasoning()).isEqualTo("No API credentials are configured for this backend");
	}

	@Test
	void aSilentlyUnsuccessfulBackendStillNamesItselfAsTheProblem() {
		JudgeModel model = request -> new JudgeModelResponse("", "recorded", null, Map.of("successful", false));
		Judgment judgment = Rfc2119Judge.create("rules", THREE, model).judge(context());

		assertThat(judgment.status()).isEqualTo(JudgmentStatus.ERROR);
		assertThat(judgment.reasoning()).isEqualTo("The judging agent did not complete its run");
	}

	@Test
	void noNumericScoreAppearsAnywhere() {
		Judgment judgment = judge("""
			    RULE-1: PASS - ClinicService.java:42 is annotated
			    RULE-2: FAIL - OwnerController.java:60 returns the entity directly
			    RULE-3: PASS - OwnerRepository.java:12 is named for its aggregate
			    """);

		assertThat(judgment.score()).isNull();
		assertThat(judgment.metadata()).containsEntry("constraintsTotal", 3).containsEntry("established", 2L);
	}

	@Test
	void theConstraintsCountIsItsOwnMetadataKey() {
		// Deliberate duplication: the two judges count their own rosters under their own names,
		// and nothing here tries to unify them.
		Judgment judgment = judge("""
			    RULE-1: PASS - ClinicService.java:42 is annotated
			    RULE-2: PASS - no entity leaves OwnerController.java:60
			    RULE-3: PASS - OwnerRepository.java:12 is named for its aggregate
			    """);

		assertThat(judgment.metadata()).containsKey("constraintsTotal").doesNotContainKey("criteriaTotal");
	}

	@Test
	void anObservationIsKeptAndChangesNothing() {
		Judgment judgment = judge("""
			    RULE-1: PASS - ClinicService.java:42 is annotated
			    RULE-2: PASS - no entity leaves OwnerController.java:60
			    RULE-3: PASS - OwnerRepository.java:12 is named for its aggregate
			    OBSERVATION RULE-1: the boundary is annotated but untested at ClinicServiceTests.java:88
			    """);

		assertThat(judgment.status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(judgment.checks()).hasSize(3);
		assertThat(Observation.of(judgment)).hasSize(1);
		assertThat(Observation.of(judgment).get(0).locations()).containsExactly("ClinicServiceTests.java:88");
	}

	@Test
	void theKeywordAndReasonReachThePrompt() {
		// The document is telling the judge how strictly to read the rule, and why the design
		// chose it. Both travel into the question.
		String prompt = Rfc2119Judge.templateFor("rules", THREE).render(context());

		assertThat(prompt).contains("Answer every one of the 3 constraints");
		assertThat(prompt).contains("RULE-2: MUST NOT expose entities from controllers (Reason: coupling)");
	}

	private static Check check(Judgment judgment, String name) {
		return judgment.checks().stream().filter(c -> c.name().equals(name)).findFirst().orElseThrow();
	}

	private static Judgment judge(String answers) {
		JudgeModel model = request -> new JudgeModelResponse(answers, "stub", null, Map.of());
		return Rfc2119Judge.create("rules", THREE, model).judge(context());
	}

	private static JudgmentContext context() {
		return JudgmentContext.builder()
			.goal("audit the architectural constraints")
			.status(ExecutionStatus.SUCCESS)
			.build();
	}

}
