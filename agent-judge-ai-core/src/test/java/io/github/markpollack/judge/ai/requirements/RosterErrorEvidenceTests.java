package io.github.markpollack.judge.ai.requirements;

import java.util.List;
import java.util.Map;

import io.github.markpollack.judge.ai.model.JudgeModelResponse;
import io.github.markpollack.judge.result.Check;
import io.github.markpollack.judge.result.Judgment;
import io.github.markpollack.judge.result.JudgmentReasonCode;
import io.github.markpollack.judge.result.JudgmentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An incomplete audit is an instrument failure that keeps the evidence it did produce.
 *
 * <p>The roster error and the protocol error are the same kind of event: the audit broke the
 * contract on part of the roster, and the rest of the roster was still established. A protocol
 * error already kept its sibling checks and totals; the roster error returned before any of that
 * was collected, so an ERROR came back with no checks, no {@code criteriaTotal}, and no exclusion
 * count — the instrument's mistake costing far more than it should, and a reader left unable to
 * say how much of the specification had actually been audited.
 *
 * <p>The one thing the retained evidence must never do is launder the gap. A criterion nobody
 * answered was not assessed, so it produces no check; and it was certainly not excluded, so it is
 * never counted as an authorized exclusion. Those two are what separate "we audited two of three"
 * from "one criterion did not apply".
 */
@DisplayName("A roster error keeps its evidence")
class RosterErrorEvidenceTests {

	private static final String CONDITION = "the change set contains Java sources";

	private static final List<EarsCriterion> CRITERIA = List.of(
			new EarsCriterion("UC1-AC1", "first", "When a thing happens, the system shall do the first thing."),
			new EarsCriterion("UC1-AC2", "second", "The system shall use prepared statements.", CONDITION),
			new EarsCriterion("UC1-AC3", "third", "While a state holds, the system shall do the third thing."));

	private static final List<Rfc2119Constraint> CONSTRAINTS = List.of(
			new Rfc2119Constraint("RULE-1", "MUST", "protect reservation-changing transactions", "consistency"),
			new Rfc2119Constraint("RULE-2", "MUST", "use prepared statements", "injection", CONDITION),
			new Rfc2119Constraint("RULE-3", "MUST", "log every rejection", "auditability"));

	@Nested
	@DisplayName("Acceptance criteria")
	class Criteria {

		@Test
		@DisplayName("the sibling checks and the totals survive an unanswered criterion")
		void siblingEvidenceIsRetained() {
			Judgment judgment = ears("""
					UC1-AC1: PASS - Foo.java:10 does it
					UC1-AC3: FAIL - Baz.java:30 does the opposite
					""");

			assertThat(judgment.status()).isEqualTo(JudgmentStatus.ERROR);
			assertThat(judgment.reasonCode()).isEqualTo(JudgmentReasonCode.JUDGE_REPORTED);
			assertThat(judgment.reasoning()).contains("UC1-AC2");
			assertThat(judgment.checks()).extracting(Check::name).containsExactly("UC1-AC1", "UC1-AC3");
			assertThat(judgment.metadata()).containsEntry("criteriaTotal", 3).containsEntry("established", 1L);
		}

		@Test
		@DisplayName("an exclusion answered elsewhere on the roster is retained and counted")
		void validExclusionsSurvive() {
			Judgment judgment = ears("""
					UC1-AC1: PASS - Foo.java:10 does it
					UC1-AC2: NOT_APPLICABLE - the change set contains no Java sources
					""");

			assertThat(judgment.status()).isEqualTo(JudgmentStatus.ERROR);
			assertThat(judgment.metadata()).containsEntry("criteriaTotal", 3).containsEntry("notApplicableCount", 1);
			assertThat(excluded(judgment))
				.containsExactly(Map.of("id", "UC1-AC2", "reason", "the change set contains no Java sources"));
		}

		@Test
		@DisplayName("a criterion nobody answered is neither a check nor an authorized exclusion")
		void anUnansweredCriterionIsNotLaundered() {
			Judgment judgment = ears("""
					UC1-AC1: PASS - Foo.java:10 does it
					""");

			assertThat(judgment.checks()).extracting(Check::name).containsExactly("UC1-AC1");
			assertThat(judgment.metadata()).containsEntry("notApplicableCount", 0);
			assertThat(excluded(judgment)).isEmpty();
		}

		@Test
		@DisplayName("an illegal exclusion beside an unanswered criterion is still never counted")
		void anIllegalExclusionIsStillNotCounted() {
			Judgment judgment = ears("""
					UC1-AC1: NOT_APPLICABLE - I decided this one does not apply
					UC1-AC2: NOT_APPLICABLE - the change set contains no Java sources
					""");

			assertThat(judgment.status()).isEqualTo(JudgmentStatus.ERROR);
			assertThat(judgment.metadata()).containsEntry("criteriaTotal", 3).containsEntry("notApplicableCount", 1);
			assertThat(excluded(judgment)).extracting(entry -> entry.get("id")).containsExactly("UC1-AC2");
		}

	}

	@Nested
	@DisplayName("Architectural constraints")
	class Constraints {

		@Test
		@DisplayName("the sibling checks and the totals survive an unanswered constraint")
		void siblingEvidenceIsRetained() {
			Judgment judgment = rfc("""
					RULE-1: PASS - Tx.java:10 does it
					RULE-3: FAIL - Audit.java:30 does not
					""");

			assertThat(judgment.status()).isEqualTo(JudgmentStatus.ERROR);
			assertThat(judgment.reasonCode()).isEqualTo(JudgmentReasonCode.JUDGE_REPORTED);
			assertThat(judgment.reasoning()).contains("RULE-2");
			assertThat(judgment.checks()).extracting(Check::name).containsExactly("RULE-1", "RULE-3");
			assertThat(judgment.metadata()).containsEntry("constraintsTotal", 3).containsEntry("established", 1L);
		}

		@Test
		@DisplayName("an exclusion answered elsewhere on the roster is retained and counted")
		void validExclusionsSurvive() {
			Judgment judgment = rfc("""
					RULE-1: PASS - Tx.java:10 does it
					RULE-2: NOT_APPLICABLE - the service has no persistence layer
					""");

			assertThat(judgment.status()).isEqualTo(JudgmentStatus.ERROR);
			assertThat(judgment.metadata()).containsEntry("constraintsTotal", 3).containsEntry("notApplicableCount", 1);
			assertThat(excluded(judgment))
				.containsExactly(Map.of("id", "RULE-2", "reason", "the service has no persistence layer"));
		}

		@Test
		@DisplayName("a constraint nobody answered is neither a check nor an authorized exclusion")
		void anUnansweredConstraintIsNotLaundered() {
			Judgment judgment = rfc("""
					RULE-1: PASS - Tx.java:10 does it
					""");

			assertThat(judgment.checks()).extracting(Check::name).containsExactly("RULE-1");
			assertThat(judgment.metadata()).containsEntry("notApplicableCount", 0);
			assertThat(excluded(judgment)).isEmpty();
		}

	}

	// ==================== Helpers ====================

	private static Judgment ears(String answers) {
		return EarsJudge.rollupFor(CRITERIA, answer(answers));
	}

	private static Judgment rfc(String answers) {
		return Rfc2119Judge.rollupFor(CONSTRAINTS, answer(answers));
	}

	private static JudgeModelResponse answer(String text) {
		return new JudgeModelResponse(text, "stub", null, Map.of());
	}

	@SuppressWarnings("unchecked")
	private static List<Map<String, Object>> excluded(Judgment judgment) {
		return (List<Map<String, Object>>) judgment.metadata().get("notApplicable");
	}

}
