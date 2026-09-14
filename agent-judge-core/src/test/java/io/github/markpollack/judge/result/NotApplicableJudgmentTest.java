/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.result;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Part N: {@code NOT_APPLICABLE} is its own status, and it is not a second spelling of
 * {@code ABSTAIN}.
 *
 * <p>
 * The two differ in what a denominator does with them, which is the only difference that
 * matters to a rate. An abstention was a fair question with no answer; an exclusion says the
 * question should not have been asked. Collapsing them is how a rubric reports a pass rate over
 * criteria half of which never applied.
 * </p>
 */
@DisplayName("NOT_APPLICABLE")
class NotApplicableJudgmentTest {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	@Nested
	@DisplayName("The status")
	class Status {

		@Test
		@DisplayName("it has its own stable wire name, parsed exactly")
		void wireName() {
			assertThat(JudgmentStatus.NOT_APPLICABLE.wireName()).isEqualTo("not_applicable");
			assertThat(JudgmentStatus.fromWire("not_applicable")).isSameAs(JudgmentStatus.NOT_APPLICABLE);
		}

		@Test
		@DisplayName("fromWire stays exact: an unknown or differently-cased name is refused, never widened")
		void fromWireStaysExact() {
			assertThatThrownBy(() -> JudgmentStatus.fromWire("NOT_APPLICABLE"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Unknown judgment status");
			assertThatThrownBy(() -> JudgmentStatus.fromWire("notApplicable"))
				.isInstanceOf(IllegalArgumentException.class);
			assertThatThrownBy(() -> JudgmentStatus.fromWire("skipped"))
				.isInstanceOf(IllegalArgumentException.class);
		}

		@Test
		@DisplayName("the five statuses are exactly the vocabulary, in a stable order")
		void theVocabulary() {
			assertThat(Arrays.stream(JudgmentStatus.values()).map(JudgmentStatus::wireName))
				.containsExactly("pass", "fail", "abstain", "not_applicable", "error");
		}

	}

	@Nested
	@DisplayName("Invariants")
	class Invariants {

		@Test
		@DisplayName("it carries no score: an excluded criterion was never assessed, and zero is an assessment")
		void carriesNoScore() {
			assertThatThrownBy(() -> new Judgment(JudgmentStatus.NOT_APPLICABLE, 0.0, null, null,
					"the repository contains no Java", List.of(), Map.of()))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("must not carry a score");
			assertThatThrownBy(() -> new Judgment(JudgmentStatus.NOT_APPLICABLE, 1.0, null, null,
					"the repository contains no Java", List.of(), Map.of()))
				.isInstanceOf(IllegalArgumentException.class);
		}

		@Test
		@DisplayName("reasoning is required: an exclusion nobody explained cannot be audited")
		void reasoningIsRequired() {
			assertThatThrownBy(() -> new Judgment(JudgmentStatus.NOT_APPLICABLE, null, null, null, "   ", List.of(),
					Map.of()))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("NOT_APPLICABLE requires non-blank reasoning");
			assertThatThrownBy(() -> Judgment.builder().notApplicable().reasoning(""))
				.isInstanceOf(IllegalArgumentException.class);
		}

		@Test
		@DisplayName("a label is allowed: the judge completed and may have classified why")
		void labelIsAllowed() {
			Judgment excluded = Judgment.builder()
				.notApplicable()
				.reasoning("the repository contains no Java source")
				.label("no_java_files")
				.build();

			assertThat(excluded.label()).isEqualTo("no_java_files");
		}

		@Test
		@DisplayName("it carries no reason code: nothing failed")
		void carriesNoReasonCode() {
			assertThatThrownBy(() -> new Judgment(JudgmentStatus.NOT_APPLICABLE, null, null,
					JudgmentReasonCode.SUBJECT_EMPTY, "the repository contains no Java", List.of(), Map.of()))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("must not carry a reasonCode");
		}

	}

	@Nested
	@DisplayName("Accessors")
	class Accessors {

		private final Judgment excluded = Judgment.notApplicable("the repository contains no Java source");

		@Test
		@DisplayName("effectiveScore is empty: there is no numeric contribution to make")
		void effectiveScoreIsEmpty() {
			assertThat(excluded.effectiveScore()).isEmpty();
		}

		@Test
		@DisplayName("it neither passes nor errored, and says so under its own name")
		void ownAccessor() {
			assertThat(excluded.pass()).isFalse();
			assertThat(excluded.hasError()).isFalse();
			assertThat(excluded.notApplicable()).isTrue();
			assertThat(Judgment.abstain("could not decide").notApplicable())
				.as("an abstention is not an exclusion")
				.isFalse();
		}

		@Test
		@DisplayName("the factory produces the status and keeps the reason")
		void factory() {
			assertThat(excluded.status()).isEqualTo(JudgmentStatus.NOT_APPLICABLE);
			assertThat(excluded.reasoning()).isEqualTo("the repository contains no Java source");
		}

	}

	@Test
	@DisplayName("it projects to and parses back from its own wire name")
	void roundTrips() throws Exception {
		Judgment excluded = Judgment.builder()
			.notApplicable()
			.reasoning("the repository contains no Java source")
			.label("no_java_files")
			.build();

		String json = MAPPER.writeValueAsString(excluded);
		assertThat(json).contains("\"status\":\"not_applicable\"").doesNotContain("abstain");
		assertThat(MAPPER.readValue(json, Judgment.class)).isEqualTo(excluded);
	}

}
