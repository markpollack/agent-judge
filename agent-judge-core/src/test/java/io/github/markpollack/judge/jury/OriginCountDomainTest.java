/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.markpollack.judge.Judges;
import io.github.markpollack.judge.context.JudgmentContext;
import io.github.markpollack.judge.result.Judgment;
import io.github.markpollack.judge.result.JudgmentReasonCode;
import io.github.markpollack.judge.result.JudgmentStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * One checked count domain for origin counts, carried through validation, merging and emission.
 *
 * <p>
 * An origin count is a portable integer, and the portable integer range reaches
 * 9,007,199,254,740,991 — far beyond {@code int}. A count that validation accepts and merging
 * then narrows is not rejected, it is <em>changed</em>: the propagation outcome a reader counts
 * is a different number from the one that was recorded, with nothing anywhere saying so. That is
 * the worst failure a result format can have, because the corrupted value is indistinguishable
 * from a correct one.
 * </p>
 *
 * <p>
 * So the domain is {@code long}, bounded by the portable integer range at every edge — what
 * validation accepts, what merging accumulates, and what emission writes are one domain — and a
 * sum that would leave it fails loudly rather than wrapping into a plausible smaller number.
 * </p>
 */
@DisplayName("Origin count domain")
class OriginCountDomainTest {

	/** Beyond 2^32, so a 32-bit accumulator reduces it to 1. */
	private static final long BEYOND_INT = 4294967297L;

	/** The largest integer that survives a JSON boundary as the value it is. */
	private static final long MAX_PORTABLE = 9007199254740991L;

	/** Two of these exceed {@link #MAX_PORTABLE}; narrowed, two of them look like 1,874,919,424. */
	private static final long HALF_OVER = 5000000000000000L;

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private static final JudgmentContext CONTEXT = JudgmentContext.builder().goal("count origins").build();

	/**
	 * A live propagating wrapper carrying one origin count.
	 * <p>
	 * Built through the canonical constructor with the portable value the wire would carry, which
	 * is exactly the shape a jury receives from a child that propagated.
	 * </p>
	 * @param count the origin count; a portable integer
	 * @return the wrapper
	 */
	private static Judgment wrapper(long count) {
		Map<String, Object> counts = new LinkedHashMap<>();
		counts.put(JudgmentReasonCode.JUDGE_REPORTED.wireName(), count);
		Map<String, Object> evidence = new LinkedHashMap<>();
		evidence.put(Judgment.ERROR_CODE_COUNTS_KEY, counts);
		return new Judgment(JudgmentStatus.ERROR, null, null, JudgmentReasonCode.ERRORS_PROPAGATED,
				"an upstream reduction propagated " + count + " judge-reported error(s)", List.of(),
				Map.of(Judgment.AGGREGATION_KEY, evidence));
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> evidenceOf(Judgment judgment) {
		return (Map<String, Object>) judgment.metadata().get(Judgment.AGGREGATION_KEY);
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> originOf(Judgment judgment) {
		return (Map<String, Object>) evidenceOf(judgment).get(Judgment.ERROR_CODE_COUNTS_KEY);
	}

	@Nested
	@DisplayName("A large count")
	class LargeCounts {

		@Test
		@DisplayName("survives a canonical reduction unchanged")
		void survivesACanonicalReduction() {
			Judgment aggregate = new AllMustPassStrategy().aggregate(List.of(wrapper(BEYOND_INT)), Map.of());

			assertThat(aggregate.reasonCode()).isEqualTo(JudgmentReasonCode.ERRORS_PROPAGATED);
			assertThat(originOf(aggregate)).containsEntry("judge_reported", BEYOND_INT);
		}

		@Test
		@DisplayName("survives the wire and a reduction of what came back")
		void survivesTheWire() throws Exception {
			Judgment fromWire = MAPPER.readValue(MAPPER.writeValueAsString(wrapper(BEYOND_INT)), Judgment.class);

			assertThat(originOf(fromWire)).containsEntry("judge_reported", BEYOND_INT);

			Judgment aggregate = new AllMustPassStrategy().aggregate(List.of(fromWire), Map.of());
			assertThat(originOf(aggregate)).containsEntry("judge_reported", BEYOND_INT);
		}

		@Test
		@DisplayName("is carried, not narrowed, when two wrappers merge")
		void mergesWithoutNarrowing() {
			Judgment aggregate = new AllMustPassStrategy().aggregate(List.of(wrapper(BEYOND_INT), wrapper(3L)),
					Map.of());

			assertThat(originOf(aggregate)).containsEntry("judge_reported", BEYOND_INT + 3L);
		}

		@Test
		@DisplayName("is honoured at the top of the portable range")
		void isHonouredAtTheTopOfTheRange() {
			Judgment aggregate = new AllMustPassStrategy().aggregate(List.of(wrapper(MAX_PORTABLE)), Map.of());

			assertThat(originOf(aggregate)).containsEntry("judge_reported", MAX_PORTABLE);
		}

	}

	@Nested
	@DisplayName("A sum that leaves the domain")
	class SumOverflow {

		@Test
		@DisplayName("fails loudly rather than wrapping into a plausible smaller number")
		void failsLoudlyRatherThanWrapping() {
			List<Judgment> inputs = List.of(wrapper(HALF_OVER), wrapper(HALF_OVER));

			assertThatThrownBy(() -> new AllMustPassStrategy().aggregate(inputs, Map.of()))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("judge_reported")
				.hasMessageContaining(String.valueOf(MAX_PORTABLE));
		}

		@Test
		@DisplayName("is contained by the jury boundary, leaving every individual result intact")
		void isContainedByTheJuryBoundary() {
			Verdict verdict = SimpleJury.builder()
				.judge(Judges.named(context -> Judgment.pass("all good"), "healthy"))
				.judge(Judges.named(context -> wrapper(HALF_OVER), "first"))
				.judge(Judges.named(context -> wrapper(HALF_OVER), "second"))
				.votingStrategy(new AllMustPassStrategy())
				.build()
				.vote(CONTEXT);

			assertThat(verdict.aggregated().status()).isEqualTo(JudgmentStatus.ERROR);
			assertThat(verdict.aggregated().reasonCode()).isEqualTo(JudgmentReasonCode.AGGREGATION_FAILED);
			assertThat(verdict.individual()).hasSize(3);
			assertThat(verdict.individualByName().get("healthy").status()).isEqualTo(JudgmentStatus.PASS);
		}

	}

	@Nested
	@DisplayName("Validation")
	class Validation {

		@Test
		@DisplayName("accepts a large count, because the portable range is the domain")
		void acceptsALargeCount() {
			assertThat(originOf(wrapper(BEYOND_INT))).containsEntry("judge_reported", BEYOND_INT);
			assertThat(originOf(wrapper(MAX_PORTABLE))).containsEntry("judge_reported", MAX_PORTABLE);
		}

		@Test
		@DisplayName("refuses a count outside the portable integer range")
		void refusesACountBeyondThePortableRange() {
			assertThatThrownBy(() -> wrapper(MAX_PORTABLE + 1L)).isInstanceOf(IllegalArgumentException.class);
		}

		@Test
		@DisplayName("the propagating factory builds a large count in the same domain")
		void theFactoryBuildsALargeCount() {
			Judgment propagated = Judgment.propagatedError(Map.of(JudgmentReasonCode.JUDGE_REPORTED, BEYOND_INT),
					"an upstream reduction propagated more errors than an int can hold");

			assertThat(originOf(propagated)).containsEntry("judge_reported", BEYOND_INT);
		}

		@Test
		@DisplayName("an ordinary count is boxed as the wire boxes it, so memory and wire stay equal")
		void anOrdinaryCountIsBoxedAsTheWireBoxesIt() throws Exception {
			Judgment propagated = Judgment.propagatedError(Map.of(JudgmentReasonCode.JUDGE_REPORTED, 2L),
					"2 of 3 judgments errored and the error policy is propagate");

			assertThat(originOf(propagated)).containsEntry("judge_reported", 2);
			assertThat(MAPPER.readValue(MAPPER.writeValueAsString(propagated), Judgment.class)).isEqualTo(propagated);
		}

		@Test
		@DisplayName("replacing the evidence block preserves an origin the replacement omits")
		void reattachingEvidenceKeepsTheOrigin() {
			// The origin lives inside the same reserved block as the rest of the evidence, so
			// rebuilding that block without naming the origin would destroy the fact that makes
			// errors_propagated legal. Every built-in reduction writes the key, which is exactly
			// why the obligation needs its own witness: nothing in a normal reduction would
			// notice this carry-across going missing.
			Judgment propagated = Judgment.propagatedError(Map.of(JudgmentReasonCode.JUDGE_REPORTED, 2L),
					"2 of 3 judgments errored and the error policy is propagate");

			Judgment reattached = AggregationEvidence.attach(propagated,
					Map.of(AggregationEvidence.STRATEGY, "custom", AggregationEvidence.INPUT_COUNT, 3));

			assertThat(reattached.reasonCode()).isEqualTo(JudgmentReasonCode.ERRORS_PROPAGATED);
			assertThat(originOf(reattached)).as("the origin survives an evidence block that never mentioned it")
				.containsEntry("judge_reported", 2);
			assertThat(evidenceOf(reattached)).containsEntry(AggregationEvidence.STRATEGY, "custom")
				.containsEntry(AggregationEvidence.INPUT_COUNT, 3);
		}

		@Test
		@DisplayName("an origin the replacement does name is the one that is kept")
		void aNamedOriginIsNotMergedWithTheOldOne() {
			Judgment propagated = Judgment.propagatedError(Map.of(JudgmentReasonCode.JUDGE_REPORTED, 2L),
					"2 of 3 judgments errored and the error policy is propagate");

			Judgment reattached = AggregationEvidence.attach(propagated,
					Map.of(AggregationEvidence.ERROR_CODE_COUNTS,
							Judgment.portableOriginCounts(Map.of(JudgmentReasonCode.JUDGE_FAILED, 1L))));

			assertThat(originOf(reattached)).containsOnlyKeys("judge_failed");
		}

	}

}
