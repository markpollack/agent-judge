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
 * What a set of per-criterion answers means.
 *
 * <p>Every case reads "the answers said X, therefore the judgment must be Y" — written from the
 * rubric, never from the code. A test written from the code's behaviour cannot disagree with it.
 *
 * <p>Every assertion is on {@link Judgment#status()}, never {@code pass()}. {@code pass()} is false
 * for FAIL, ERROR and ABSTAIN alike, so a test asserting it is false cannot tell a rejection from a
 * judge that never ran.
 */
class EarsJudgeTests {

	private static final List<EarsCriterion> THREE = List.of(
		new EarsCriterion("UC1-AC1", "first", "When a thing happens, the system shall do the first thing."),
		new EarsCriterion("UC1-AC2", "second", "If a thing happens, then the system shall do the second thing."),
		new EarsCriterion("UC1-AC3", "third", "While a state holds, the system shall do the third thing."));

	@Test
	void everyRequirementEstablishedIsAPass() {
		Judgment judgment = judge("""
			    UC1-AC1: PASS - Foo.java:10 does it
			    UC1-AC2: PASS - Bar.java:20 does it
			    UC1-AC3: PASS - Baz.java:30 does it
			    """);

		assertThat(judgment.status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(judgment.checks()).as("every answer is evidence and must be kept").hasSize(3);
		assertThat(judgment.reasoning()).isEqualTo("all 3 requirements established");
	}

	@Test
	void oneUnsatisfiedRequirementFailsTheWhole() {
		// Required criteria are conjunctive. Two of three is not two-thirds done.
		Judgment judgment = judge("""
			    UC1-AC1: PASS - Foo.java:10 does it
			    UC1-AC2: FAIL - Bar.java:20 compares the wrong way round
			    UC1-AC3: PASS - Baz.java:30 does it
			    """);

		assertThat(judgment.status()).isEqualTo(JudgmentStatus.FAIL);
		assertThat(check(judgment, "UC1-AC2").passed()).isFalse();
		assertThat(check(judgment, "UC1-AC2").message())
			.as("the binding item's evidence survives")
			.contains("compares the wrong way round");
	}

	@Test
	void oneAbstentionMakesTheWholeAbstain() {
		// The load-bearing rule. A written acceptance criterion is required by construction:
		// the specification says it applies. So CANNOT_DETERMINE means "could not establish",
		// not "does not apply", and it must not be absorbed into a passing population.
		// PASS means every required criterion was affirmatively established.
		Judgment judgment = judge("""
			    UC1-AC1: PASS - Foo.java:10 does it
			    UC1-AC2: CANNOT_DETERMINE - nothing here exercises it
			    UC1-AC3: PASS - Baz.java:30 does it
			    """);

		assertThat(judgment.status())
			.as("51 of 52 established is not the specification passing")
			.isEqualTo(JudgmentStatus.ABSTAIN);
		assertThat(judgment.reasoning()).contains("UC1-AC2");
		assertThat(judgment.metadata()).containsEntry("unestablished", "UC1-AC2");
	}

	@Test
	void aFailureOutranksAnAbstention() {
		Judgment judgment = judge("""
			    UC1-AC1: CANNOT_DETERMINE - nothing here exercises it
			    UC1-AC2: FAIL - Bar.java:20 does the opposite
			    UC1-AC3: PASS - Baz.java:30 does it
			    """);

		assertThat(judgment.status()).isEqualTo(JudgmentStatus.FAIL);
	}

	@Test
	void nothingEstablishedIsAnAbstentionNotAPass() {
		Judgment judgment = judge("""
			    UC1-AC1: CANNOT_DETERMINE - nothing here exercises it
			    UC1-AC2: CANNOT_DETERMINE - nothing here exercises it
			    UC1-AC3: CANNOT_DETERMINE - nothing here exercises it
			    """);

		assertThat(judgment.status()).isEqualTo(JudgmentStatus.ABSTAIN);
	}

	@Test
	void anUnansweredRequirementIsAnErrorNotAPass() {
		// Answering two of three is not an audit of three. The subject is not at fault:
		// the audit is incomplete, which is an ERROR.
		Judgment judgment = judge("""
			    UC1-AC1: PASS - Foo.java:10 does it
			    UC1-AC2: PASS - Bar.java:20 does it
			    """);

		assertThat(judgment.status()).isEqualTo(JudgmentStatus.ERROR);
		assertThat(judgment.reasoning()).contains("1 of 3").contains("UC1-AC3");
	}

	@Test
	void answersForRequirementsNobodyAskedAboutAreIgnored() {
		// An invented identifier must not satisfy the roster.
		Judgment judgment = judge("""
			    UC1-AC1: PASS - Foo.java:10 does it
			    UC1-AC2: PASS - Bar.java:20 does it
			    UC9-AC9: PASS - a criterion nobody wrote
			    """);

		assertThat(judgment.status()).isEqualTo(JudgmentStatus.ERROR);
		assertThat(judgment.reasoning()).contains("UC1-AC3");
	}

	@Test
	void answersOutOfOrderAreStillAnswers() {
		// The real agent emitted AC1-AC46, then AC48-AC52, then AC47. Order is not part of
		// the contract; completeness is.
		Judgment judgment = judge("""
			    UC1-AC3: PASS - Baz.java:30 does it
			    UC1-AC1: PASS - Foo.java:10 does it
			    UC1-AC2: PASS - Bar.java:20 does it
			    """);

		assertThat(judgment.status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(judgment.checks().stream().map(Check::name).toList())
			.as("reported in the document's order, not the agent's")
			.containsExactly("UC1-AC1", "UC1-AC2", "UC1-AC3");
	}

	@Test
	void aRepeatedAnswerDoesNotCountTwice() {
		Judgment judgment = judge("""
			    UC1-AC1: PASS - Foo.java:10 does it
			    UC1-AC1: FAIL - changed my mind
			    UC1-AC2: PASS - Bar.java:20 does it
			    UC1-AC3: PASS - Baz.java:30 does it
			    """);

		assertThat(judgment.status()).as("the first answer stands").isEqualTo(JudgmentStatus.PASS);
		assertThat(judgment.checks()).hasSize(3);
	}

	@Test
	void anEmptyAuditIsAnErrorNotAPass() {
		assertThat(judge("").status()).isEqualTo(JudgmentStatus.ERROR);
		assertThat(judge("   \n  \n").status()).isEqualTo(JudgmentStatus.ERROR);
		assertThat(judge("I looked at the codebase and everything appears to be in order.").status())
			.isEqualTo(JudgmentStatus.ERROR);
	}

	@Test
	void aBackendThatCouldNotAnswerBlamesTheJudgeNotTheSubject() {
		// DD-8. An unrunnable judge is an ERROR about the judge, never a FAIL about the subject.
		// The backend is the only component that knows which operator problem occurred, so its
		// text is carried through verbatim: an ERROR reading "the agent did not complete" would
		// send the reader to look at the wrong thing.
		JudgeModel model = request -> new JudgeModelResponse(
			"No API credentials are configured for this backend", "recorded", null, Map.of("successful", false));
		Judgment judgment = EarsJudge.create("audit", THREE, model).judge(context());

		assertThat(judgment.status()).isEqualTo(JudgmentStatus.ERROR);
		assertThat(judgment.reasoning()).isEqualTo("No API credentials are configured for this backend");
	}

	@Test
	void aSilentlyUnsuccessfulBackendStillNamesItselfAsTheProblem() {
		JudgeModel model = request -> new JudgeModelResponse("", "recorded", null, Map.of("successful", false));
		Judgment judgment = EarsJudge.create("audit", THREE, model).judge(context());

		assertThat(judgment.status()).isEqualTo(JudgmentStatus.ERROR);
		assertThat(judgment.reasoning()).isEqualTo("The judging agent did not complete its run");
	}

	@Test
	void noNumericScoreAppearsAnywhere() {
		// DD-12. A score of 6 out of 10 is meaningless if you do not know what makes it 7.
		Judgment judgment = judge("""
			    UC1-AC1: PASS - Foo.java:10 does it
			    UC1-AC2: CANNOT_DETERMINE - nothing here exercises it
			    UC1-AC3: PASS - Baz.java:30 does it
			    """);

		assertThat(judgment.score()).as("no score is set, because none is meaningful here").isNull();
		assertThat(judgment.reasoning())
			.as("the report counts requirements; it does not rate them")
			.matches(".*\\d+ of \\d+ established.*");
	}

	// --- Observations: useful evidence, and never a verdict -------------------------------

	private static final String WITH_OBSERVATION = """
		UC1-AC1: PASS - Foo.java:10 does it
		UC1-AC2: PASS - Bar.java:20 does it
		UC1-AC3: PASS - Baz.java:30 does it
		OBSERVATION UC1-AC2: no existing test exercises the exact-equality boundary, only the after-start case at BarTests.java:191
		""";

	@Test
	void anObservationDoesNotChangeTheVerdict() {
		// The requirement says the implementation must behave correctly. It does not say a test
		// must exist. So the criterion passes, and the gap is kept beside it, not inside it.
		Judgment judgment = judge(WITH_OBSERVATION);

		assertThat(judgment.status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(judgment.checks()).allMatch(Check::passed);
		assertThat(judgment.reasoning()).isEqualTo("all 3 requirements established");
	}

	@Test
	void theObservationIsPreservedAndAttributed() {
		List<Observation> found = Observation.of(judge(WITH_OBSERVATION));

		assertThat(found).hasSize(1);
		assertThat(found.get(0).requirementId())
			.as("attributed to the criterion it was noticed under")
			.isEqualTo("UC1-AC2");
		assertThat(found.get(0).message()).contains("exact-equality boundary");
	}

	@Test
	void aLocationIsExtractedWhenOneWasGiven() {
		assertThat(Observation.of(judge(WITH_OBSERVATION)).get(0).locations())
			.containsExactly("BarTests.java:191");
	}

	@Test
	void anObservationDoesNotJoinTheRoster() {
		// Three criteria were asked; three checks come back. An observation is not a fourth.
		Judgment judgment = judge(WITH_OBSERVATION);

		assertThat(judgment.checks()).hasSize(3);
		assertThat(judgment.metadata()).containsEntry("criteriaTotal", 3);
		assertThat(judgment.checks()).noneMatch(c -> c.name().startsWith("OBSERVATION"));
	}

	@Test
	void anObservationCannotRescueOrDamageARollup() {
		// Observed alongside a genuine failure, the verdict is still decided by the failure.
		Judgment failing = judge("""
			    UC1-AC1: PASS - Foo.java:10 does it
			    UC1-AC2: FAIL - Bar.java:20 does the opposite
			    UC1-AC3: PASS - Baz.java:30 does it
			    OBSERVATION UC1-AC1: an aside about Foo.java:10
			    """);

		assertThat(failing.status()).isEqualTo(JudgmentStatus.FAIL);
		assertThat(Observation.of(failing)).hasSize(1);
	}

	@Test
	void malformedOrUnknownObservationsAreDroppedNotFatal() {
		// The roster parsing is strict. This channel is forgiving on purpose: a cosmetic change
		// in non-binding model prose must never break a valid judgment.
		Judgment judgment = judge("""
			    UC1-AC1: PASS - Foo.java:10 does it
			    UC1-AC2: PASS - Bar.java:20 does it
			    UC1-AC3: PASS - Baz.java:30 does it
			    OBSERVATION
			    OBSERVATION UC9-AC9: about a criterion nobody asked for
			    OBSERVATION UC1-AC1:
			    """);

		assertThat(judgment.status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(Observation.of(judgment)).isEmpty();
	}

	@Test
	void noObservationsIsNormal() {
		Judgment judgment = judge("""
			    UC1-AC1: PASS - Foo.java:10 does it
			    UC1-AC2: PASS - Bar.java:20 does it
			    UC1-AC3: PASS - Baz.java:30 does it
			    """);

		assertThat(judgment.status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(Observation.of(judgment)).isEmpty();
	}

	@Test
	void theRosterAndItsCountReachThePrompt() {
		// The model is told how many answers it owes, and asked about each criterion by name.
		String prompt = EarsJudge.templateFor("audit", THREE).render(context());

		assertThat(prompt).contains("Answer every one of the 3 criteria");
		assertThat(prompt).contains("UC1-AC1:").contains("UC1-AC2:").contains("UC1-AC3:");
		assertThat(prompt).as("the judge asks for no overall verdict").contains("Do not state an overall verdict");
	}

	private static Check check(Judgment judgment, String name) {
		return judgment.checks().stream().filter(c -> c.name().equals(name)).findFirst().orElseThrow();
	}

	private static Judgment judge(String answers) {
		JudgeModel model = request -> new JudgeModelResponse(answers, "stub", null, Map.of());
		return EarsJudge.create("audit", THREE, model).judge(context());
	}

	private static JudgmentContext context() {
		return JudgmentContext.builder()
			.goal("audit the requirements")
			.status(ExecutionStatus.SUCCESS)
			.build();
	}

}
