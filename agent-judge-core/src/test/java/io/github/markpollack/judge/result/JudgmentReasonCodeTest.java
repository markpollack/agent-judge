/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.result;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Part E: one countable code plus mandatory free text, and the origin a propagating error owes.
 *
 * <p>
 * The guards here are the reason the field is worth having. A code on the wrong status, or a
 * propagated error with nothing to attribute it to, would be counted by a reader exactly as
 * confidently as a correct one — and would be wrong. Each is refused at construction, so it
 * cannot reach storage.
 * </p>
 */
@DisplayName("Judgment reason codes")
class JudgmentReasonCodeTest {

	private static Judgment build(JudgmentStatus status, JudgmentReasonCode reasonCode, String reasoning) {
		return new Judgment(status, null, null, reasonCode, reasoning, List.of(), Map.of());
	}

	@Nested
	@DisplayName("Vocabulary")
	class Vocabulary {

		@ParameterizedTest
		@EnumSource(JudgmentReasonCode.class)
		@DisplayName("every code round-trips through its exact, case-sensitive wire name")
		void wireNamesRoundTrip(JudgmentReasonCode code) {
			assertThat(JudgmentReasonCode.fromWire(code.wireName())).isSameAs(code);
			assertThat(code.wireName()).isEqualTo(code.wireName().toLowerCase());
		}

		@Test
		@DisplayName("an unknown wire name is refused rather than silently absorbed")
		void unknownWireNameIsRefused() {
			assertThatThrownBy(() -> JudgmentReasonCode.fromWire("JUDGE_FAILED"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Unknown judgment reason code: JUDGE_FAILED");
			assertThatThrownBy(() -> JudgmentReasonCode.fromWire("timeout"))
				.isInstanceOf(IllegalArgumentException.class);
		}

		@ParameterizedTest
		@EnumSource(JudgmentReasonCode.class)
		@DisplayName("an instrument code names an origin family and a subject code does not")
		void familiesAndOriginsAgree(JudgmentReasonCode code) {
			if (code.family() == JudgmentReasonCode.Family.INSTRUMENT) {
				assertThat(code.originFamily()).as("%s is an instrument code", code).isNotNull();
			}
			else {
				assertThat(code.originFamily()).as("%s is a subject code", code).isNull();
			}
		}

		@Test
		@DisplayName("only the propagating wrapper is non-terminal")
		void onlyTheWrapperIsNonTerminal() {
			for (JudgmentReasonCode code : JudgmentReasonCode.values()) {
				assertThat(code.terminal()).as("%s terminal", code)
					.isEqualTo(code != JudgmentReasonCode.ERRORS_PROPAGATED);
			}
		}

	}

	@Nested
	@DisplayName("Status and family")
	class StatusAndFamily {

		@Test
		@DisplayName("an ERROR without a code is refused: an uncoded instrument failure cannot be counted")
		void errorRequiresACode() {
			assertThatThrownBy(() -> build(JudgmentStatus.ERROR, null, "the licence index was unreachable"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("ERROR requires a reasonCode");
		}

		@Test
		@DisplayName("an ERROR carrying a subject code is refused")
		void errorRejectsASubjectCode() {
			assertThatThrownBy(() -> build(JudgmentStatus.ERROR, JudgmentReasonCode.SUBJECT_EMPTY, "nothing to assess"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("ERROR requires an instrument reasonCode");
		}

		@Test
		@DisplayName("a FAIL carrying an instrument code is refused: the instrument did not fail")
		void failRejectsAnInstrumentCode() {
			assertThatThrownBy(
					() -> build(JudgmentStatus.FAIL, JudgmentReasonCode.JUDGE_FAILED, "the subject did not qualify"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("FAIL may only carry a subject reasonCode");
		}

		@Test
		@DisplayName("a FAIL may carry a subject code, and may carry none at all")
		void failAcceptsASubjectCodeAndUncodedRejection() {
			Judgment coded = build(JudgmentStatus.FAIL, JudgmentReasonCode.SUBJECT_EMPTY,
					"the diff contained no changed files");
			Judgment uncoded = Judgment.fail("the answer contradicted the retrieved context");

			assertThat(coded.reasonCode()).isEqualTo(JudgmentReasonCode.SUBJECT_EMPTY);
			assertThat(uncoded.reasonCode()).as("an uncoded rejection is deliberate, not an omission").isNull();
		}

		@Test
		@DisplayName("PASS, ABSTAIN and NOT_APPLICABLE carry no code at all")
		void nonRejectionsCarryNoCode() {
			assertThatThrownBy(() -> build(JudgmentStatus.PASS, JudgmentReasonCode.SUBJECT_EMPTY, "ok"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("must not carry a reasonCode");
			assertThatThrownBy(() -> build(JudgmentStatus.ABSTAIN, JudgmentReasonCode.JUDGE_REPORTED, "undecided"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("must not carry a reasonCode");
		}

		@Test
		@DisplayName("reasoning is mandatory wherever a code is present")
		void aCodeRequiresReasoning() {
			assertThatThrownBy(() -> build(JudgmentStatus.FAIL, JudgmentReasonCode.SUBJECT_EMPTY, "   "))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("reasonCode");
		}

		@Test
		@DisplayName("a judge-authored error defaults to judge_reported")
		void judgeAuthoredErrorsDefaultToJudgeReported() {
			assertThat(Judgment.error("the index was unreachable").reasonCode())
				.isEqualTo(JudgmentReasonCode.JUDGE_REPORTED);
			assertThat(Judgment.error(JudgmentReasonCode.AGGREGATION_FAILED, "the strategy threw").reasonCode())
				.isEqualTo(JudgmentReasonCode.AGGREGATION_FAILED);
		}

	}

	@Nested
	@DisplayName("The errors_propagated origin invariant")
	class OriginInvariant {

		@Test
		@DisplayName("a propagated error built through its factory carries a valid origin")
		void factoryBuildsAValidOrigin() {
			Judgment propagated = Judgment.propagatedError(
					Map.of(JudgmentReasonCode.JUDGE_REPORTED, 2L, JudgmentReasonCode.JUDGE_FAILED, 1L),
					"3 of 4 judgments errored and the error policy is propagate");

			assertThat(propagated.reasonCode()).isEqualTo(JudgmentReasonCode.ERRORS_PROPAGATED);
			assertThat(origin(propagated)).containsOnly(Map.entry("judge_reported", 2), Map.entry("judge_failed", 1));
		}

		@Test
		@DisplayName("a propagated error with no origin block at all is refused")
		void missingOriginIsRefused() {
			assertThatThrownBy(
					() -> build(JudgmentStatus.ERROR, JudgmentReasonCode.ERRORS_PROPAGATED, "errors were propagated"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("errors_propagated requires a non-empty '" + Judgment.ERROR_CODE_COUNTS_KEY + "'");
		}

		@Test
		@DisplayName("an empty origin is refused: a propagated error with nothing to attribute is not countable")
		void emptyOriginIsRefused() {
			assertThatThrownBy(() -> propagatedWith(Map.of())).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("non-empty");
		}

		@Test
		@DisplayName("the wrapper's own code is refused as an origin key: flattening never records the wrapper")
		void wrapperAsOriginIsRefused() {
			assertThatThrownBy(() -> propagatedWith(Map.of("errors_propagated", 1)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("errors_propagated");
			assertThatThrownBy(() -> Judgment.propagatedError(Map.of(JudgmentReasonCode.ERRORS_PROPAGATED, 1L), "boom"))
				.isInstanceOf(IllegalArgumentException.class);
		}

		@Test
		@DisplayName("a subject code is refused as an origin key")
		void subjectCodeAsOriginIsRefused() {
			assertThatThrownBy(() -> propagatedWith(Map.of("subject_empty", 1)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("subject_empty");
		}

		@Test
		@DisplayName("an unknown origin key is refused rather than counted as an unknown cause")
		void unknownOriginKeyIsRefused() {
			assertThatThrownBy(() -> propagatedWith(Map.of("timed_out", 1)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("timed_out");
		}

		@Test
		@DisplayName("a count that is not a positive integer is refused")
		void nonPositiveCountsAreRefused() {
			assertThatThrownBy(() -> propagatedWith(Map.of("judge_reported", 0)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("positive");
			assertThatThrownBy(() -> propagatedWith(Map.of("judge_reported", -1)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("positive");
			assertThatThrownBy(() -> propagatedWith(Map.of("judge_reported", "two")))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("positive");
		}

		@Test
		@DisplayName("a machinery origin is legal structurally; what it means is the error policy's business")
		void machineryOriginIsStructurallyValid() {
			Judgment propagated = Judgment.propagatedError(Map.of(JudgmentReasonCode.STAGE_FAILED, 1L),
					"a member stage failed and the error policy is propagate");

			assertThat(origin(propagated)).containsOnly(Map.entry("stage_failed", 1));
		}

		/** A hand-built propagated error whose origin block is whatever the caller supplies. */
		private static Judgment propagatedWith(Map<String, Object> counts) {
			Map<String, Object> evidence = new LinkedHashMap<>();
			evidence.put(Judgment.ERROR_CODE_COUNTS_KEY, counts);
			return new Judgment(JudgmentStatus.ERROR, null, null, JudgmentReasonCode.ERRORS_PROPAGATED,
					"errors were propagated", List.of(), Map.of(Judgment.AGGREGATION_KEY, evidence));
		}

		@SuppressWarnings("unchecked")
		private static Map<String, Object> origin(Judgment judgment) {
			Map<String, Object> aggregation = (Map<String, Object>) judgment.metadata().get(Judgment.AGGREGATION_KEY);
			return (Map<String, Object>) aggregation.get(Judgment.ERROR_CODE_COUNTS_KEY);
		}

	}

}
