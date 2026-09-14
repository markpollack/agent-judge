package io.github.markpollack.judge.ai.requirements;

import java.util.List;
import java.util.Map;

import io.github.markpollack.judge.Judges;
import io.github.markpollack.judge.ai.model.JudgeModel;
import io.github.markpollack.judge.ai.model.JudgeModelResponse;
import io.github.markpollack.judge.context.ExecutionStatus;
import io.github.markpollack.judge.context.JudgmentContext;
import io.github.markpollack.judge.result.Check;
import io.github.markpollack.judge.result.Judgment;
import io.github.markpollack.judge.result.JudgmentReasonCode;
import io.github.markpollack.judge.result.JudgmentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * When a requirement judge may say a criterion does not apply, and when it may not.
 *
 * <p>A written acceptance criterion is required by construction: the document says it applies, so
 * "could not determine" is not "does not apply" and never was. What changes here is that a
 * document may now say, in advance, that a particular criterion is conditional — and only then
 * does {@code NOT_APPLICABLE} become an available answer for it.
 *
 * <p>The failure mode this guards against is specific and quiet. An exclusion removes a criterion
 * from the denominator, so an audit that can exclude at will can make any specification pass by
 * declaring most of it inapplicable, and the result looks exactly like a specification that was
 * mostly satisfied. Every rule below exists to keep the two apart.
 */
@DisplayName("Conditional criteria")
class ConditionalCriterionTests {

	private static final String CONDITION = "the change set contains Java sources";

	private static final List<EarsCriterion> MIXED = List.of(
		new EarsCriterion("UC1-AC1", "first", "When a thing happens, the system shall do the first thing."),
		new EarsCriterion("UC1-AC2", "second", "The system shall use prepared statements.", CONDITION),
		new EarsCriterion("UC1-AC3", "third", "While a state holds, the system shall do the third thing."));

	private static final List<Rfc2119Constraint> CONSTRAINTS = List.of(
		new Rfc2119Constraint("RULE-1", "MUST", "protect reservation-changing transactions", "consistency"),
		new Rfc2119Constraint("RULE-2", "MUST", "use prepared statements", "injection", CONDITION));

	@Nested
	@DisplayName("A conditional criterion may be excluded, with a reason")
	class LegitimateExclusion {

		@Test
		@DisplayName("it is excluded and counted, and everything else still had to be established")
		void excludedAndCounted() {
			Judgment judgment = ears("""
				    UC1-AC1: PASS - Foo.java:10 does it
				    UC1-AC2: NOT_APPLICABLE - the change set contains no Java sources
				    UC1-AC3: PASS - Baz.java:30 does it
				    """);

			assertThat(judgment.status()).isEqualTo(JudgmentStatus.PASS);
			assertThat(judgment.metadata()).containsEntry("criteriaTotal", 3)
				.containsEntry("notApplicableCount", 1);
			assertThat(excluded(judgment)).containsExactly(
					Map.of("id", "UC1-AC2", "reason", "the change set contains no Java sources"));
		}

		@Test
		@DisplayName("an excluded criterion is not a check: it was never assessed")
		void exclusionsAreNotChecks() {
			Judgment judgment = ears("""
				    UC1-AC1: PASS - Foo.java:10 does it
				    UC1-AC2: NOT_APPLICABLE - the change set contains no Java sources
				    UC1-AC3: PASS - Baz.java:30 does it
				    """);

			assertThat(judgment.checks()).extracting(Check::name).containsExactly("UC1-AC1", "UC1-AC3");
		}

		@Test
		@DisplayName("a roster every one of whose criteria was excluded is itself not applicable")
		void allExcludedIsNotApplicable() {
			Judgment judgment = judgeWith(List.of(new EarsCriterion("UC1-AC2", "second",
					"The system shall use prepared statements.", CONDITION)), """
				    UC1-AC2: NOT_APPLICABLE - the change set contains no Java sources
				    """);

			assertThat(judgment.status()).isEqualTo(JudgmentStatus.NOT_APPLICABLE);
			assertThat(judgment.reasoning()).isNotBlank();
			assertThat(judgment.metadata()).containsEntry("criteriaTotal", 1)
				.containsEntry("notApplicableCount", 1);
		}

		@Test
		@DisplayName("an exclusion never outranks a real finding")
		void findingsStillBind() {
			assertThat(ears("""
				    UC1-AC1: FAIL - Foo.java:10 does the opposite
				    UC1-AC2: NOT_APPLICABLE - the change set contains no Java sources
				    UC1-AC3: PASS - Baz.java:30 does it
				    """).status()).isEqualTo(JudgmentStatus.FAIL);
			assertThat(ears("""
				    UC1-AC1: CANNOT_DETERMINE - nothing exercises it
				    UC1-AC2: NOT_APPLICABLE - the change set contains no Java sources
				    UC1-AC3: PASS - Baz.java:30 does it
				    """).status()).isEqualTo(JudgmentStatus.ABSTAIN);
		}

		@Test
		@DisplayName("the same rules hold for architectural constraints")
		void constraintsBehaveTheSameWay() {
			Judgment judgment = rfc("""
				    RULE-1: PASS - Tx.java:10 does it
				    RULE-2: NOT_APPLICABLE - the service has no persistence layer
				    """);

			assertThat(judgment.status()).isEqualTo(JudgmentStatus.PASS);
			assertThat(judgment.metadata()).containsEntry("constraintsTotal", 2)
				.containsEntry("notApplicableCount", 1);
			assertThat(excluded(judgment)).containsExactly(
					Map.of("id", "RULE-2", "reason", "the service has no persistence layer"));
		}

	}

	@Nested
	@DisplayName("An exclusion the document never authorized is a protocol error")
	class ProtocolErrors {

		@Test
		@DisplayName("excluding an unconditional criterion is an instrument failure, not a finding")
		void unconditionalExclusionIsAProtocolError() {
			Judgment judgment = ears("""
				    UC1-AC1: NOT_APPLICABLE - I decided this one does not apply
				    UC1-AC2: PASS - Bar.java:20 does it
				    UC1-AC3: PASS - Baz.java:30 does it
				    """);

			assertThat(judgment.status()).isEqualTo(JudgmentStatus.ERROR);
			assertThat(judgment.reasonCode()).isEqualTo(JudgmentReasonCode.JUDGE_REPORTED);
			assertThat(judgment.reasoning()).contains("UC1-AC1").contains("unconditional");
		}

		@Test
		@DisplayName("an exclusion with no reason is a protocol error: an unexplained exclusion cannot be audited")
		void blankReasonIsAProtocolError() {
			Judgment judgment = ears("""
				    UC1-AC1: PASS - Foo.java:10 does it
				    UC1-AC2: NOT_APPLICABLE
				    UC1-AC3: PASS - Baz.java:30 does it
				    """);

			assertThat(judgment.status()).isEqualTo(JudgmentStatus.ERROR);
			assertThat(judgment.reasonCode()).isEqualTo(JudgmentReasonCode.JUDGE_REPORTED);
			assertThat(judgment.reasoning()).contains("UC1-AC2").contains("reason");
		}

		@Test
		@DisplayName("a protocol error keeps its sibling evidence and its totals")
		void siblingEvidenceIsRetained() {
			Judgment judgment = ears("""
				    UC1-AC1: NOT_APPLICABLE - I decided this one does not apply
				    UC1-AC2: PASS - Bar.java:20 does it
				    UC1-AC3: FAIL - Baz.java:30 does the opposite
				    """);

			assertThat(judgment.checks()).extracting(Check::name).containsExactly("UC1-AC2", "UC1-AC3");
			assertThat(judgment.metadata()).containsEntry("criteriaTotal", 3).containsEntry("established", 1L);
		}

		@Test
		@DisplayName("an illegal exclusion is never counted as an authorized one")
		void illegalExclusionsAreNotCounted() {
			Judgment judgment = ears("""
				    UC1-AC1: NOT_APPLICABLE - I decided this one does not apply
				    UC1-AC2: PASS - Bar.java:20 does it
				    UC1-AC3: PASS - Baz.java:30 does it
				    """);

			assertThat(judgment.metadata()).containsEntry("notApplicableCount", 0);
			assertThat(excluded(judgment)).isEmpty();
		}

		@Test
		@DisplayName("the same rules hold for architectural constraints")
		void constraintsBehaveTheSameWay() {
			Judgment judgment = rfc("""
				    RULE-1: NOT_APPLICABLE - I decided this one does not apply
				    RULE-2: PASS - Db.java:10 does it
				    """);

			assertThat(judgment.status()).isEqualTo(JudgmentStatus.ERROR);
			assertThat(judgment.reasonCode()).isEqualTo(JudgmentReasonCode.JUDGE_REPORTED);
			assertThat(judgment.metadata()).containsEntry("notApplicableCount", 0);
		}

	}

	@Nested
	@DisplayName("The capability is declared where a jury can see it")
	class DeclaredCapability {

		@Test
		@DisplayName("a roster with a conditional criterion declares it, naming the ids")
		void conditionalRostersDeclare() {
			assertThat(Judges.notApplicableCapability(EarsJudge.create("audit", MIXED, model("")))).hasValueSatisfying(
					declared -> assertThat(declared).contains("UC1-AC2").doesNotContain("UC1-AC1"));
			assertThat(Judges.notApplicableCapability(Rfc2119Judge.create("audit", CONSTRAINTS, model(""))))
				.hasValueSatisfying(declared -> assertThat(declared).contains("RULE-2"));
		}

		@Test
		@DisplayName("a roster with nothing conditional declares nothing, and composes under the default")
		void unconditionalRostersDeclareNothing() {
			List<EarsCriterion> unconditional = List.of(
					new EarsCriterion("UC1-AC1", "first", "The system shall do the first thing."));

			assertThat(Judges.notApplicableCapability(EarsJudge.create("audit", unconditional, model("")))).isEmpty();
		}

		@Test
		@DisplayName("the prompt offers the exclusion only where the document authorized one")
		void thePromptOffersItOnlyWhereAuthorized() {
			assertThat(EarsJudge.templateFor("audit", MIXED).source().load())
				.contains("NOT_APPLICABLE")
				.contains("UC1-AC2");
			assertThat(EarsJudge
				.templateFor("audit", List.of(new EarsCriterion("UC1-AC1", "first", "The system shall do it.")))
				.source()
				.load()).doesNotContain("NOT_APPLICABLE");
		}

		@Test
		@DisplayName("the criterion's own condition travels into the prompt")
		void theConditionTravels() {
			assertThat(MIXED.get(1).asPrompt()).contains("Applies when: " + CONDITION);
			assertThat(MIXED.get(0).asPrompt()).doesNotContain("Applies when");
			assertThat(CONSTRAINTS.get(1).asPrompt()).contains("Applies when: " + CONDITION);
		}

		@Test
		@DisplayName("a blank condition is refused: it claims a capability while stating no condition")
		void blankConditionsAreRefused() {
			org.assertj.core.api.Assertions
				.assertThatThrownBy(() -> new EarsCriterion("UC1-AC1", "first", "requirement", "  "))
				.isInstanceOf(IllegalArgumentException.class);
			org.assertj.core.api.Assertions
				.assertThatThrownBy(() -> new Rfc2119Constraint("RULE-1", "MUST", "requirement", "reason", "  "))
				.isInstanceOf(IllegalArgumentException.class);
		}

	}

	// ==================== Helpers ====================

	@SuppressWarnings("unchecked")
	private static List<Map<String, Object>> excluded(Judgment judgment) {
		return (List<Map<String, Object>>) judgment.metadata().get("notApplicable");
	}

	private static JudgeModel model(String answers) {
		return request -> new JudgeModelResponse(answers, "stub", null, Map.of());
	}

	private static Judgment ears(String answers) {
		return judgeWith(MIXED, answers);
	}

	private static Judgment judgeWith(List<EarsCriterion> criteria, String answers) {
		return EarsJudge.create("audit", criteria, model(answers)).judge(context());
	}

	private static Judgment rfc(String answers) {
		return Rfc2119Judge.create("audit", CONSTRAINTS, model(answers)).judge(context());
	}

	private static JudgmentContext context() {
		return JudgmentContext.builder()
			.goal("audit the requirements")
			.status(ExecutionStatus.SUCCESS)
			.build();
	}

}
