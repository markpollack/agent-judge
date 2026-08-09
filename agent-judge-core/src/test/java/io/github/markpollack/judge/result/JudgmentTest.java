/*
 * Copyright (c) 2026 Mark Pollack
 * See LICENSE.txt in the repository root for license terms.
 */

package io.github.markpollack.judge.result;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link Judgment}.
 *
 * @author Mark Pollack
 * @since 0.1.0
 */
class JudgmentTest {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	@Nested
	@DisplayName("Value invariants")
	class Invariants {

		@Test
		@DisplayName("status is required")
		void statusRequired() {
			assertThatThrownBy(() -> new Judgment(null, null, null, "x", List.of(), Map.of()))
				.isInstanceOf(NullPointerException.class)
				.hasMessageContaining("status");
		}

		@Test
		@DisplayName("reasoning, checks and metadata are required")
		void othersRequired() {
			assertThatThrownBy(() -> new Judgment(JudgmentStatus.PASS, null, null, null, List.of(), Map.of()))
				.isInstanceOf(NullPointerException.class)
				.hasMessageContaining("reasoning");
			assertThatThrownBy(() -> new Judgment(JudgmentStatus.PASS, null, null, "x", null, Map.of()))
				.isInstanceOf(NullPointerException.class)
				.hasMessageContaining("checks");
			assertThatThrownBy(() -> new Judgment(JudgmentStatus.PASS, null, null, "x", List.of(), null))
				.isInstanceOf(NullPointerException.class)
				.hasMessageContaining("metadata");
		}

		@Test
		@DisplayName("score accepts absent, 0.0, 1.0 and interior values")
		void scoreBoundaries() {
			assertThat(Judgment.pass("ok").score()).isNull();
			assertThat(Judgment.builder().fail().score(0.0).reasoning("x").build().score()).isZero();
			assertThat(Judgment.builder().pass().score(1.0).reasoning("x").build().score()).isOne();
			assertThat(Judgment.builder().pass().score(0.42).reasoning("x").build().score())
				.isEqualTo(0.42);
		}

		@Test
		@DisplayName("score rejects out-of-range and non-finite values")
		void scoreRejected() {
			for (double bad : new double[] { -0.1, 1.1, Double.NaN, Double.POSITIVE_INFINITY,
					Double.NEGATIVE_INFINITY }) {
				assertThatThrownBy(() -> new Judgment(JudgmentStatus.PASS, bad, null, "x", List.of(), Map.of()))
					.as("score %s", bad)
					.isInstanceOf(IllegalArgumentException.class);
			}
		}

		@Test
		@DisplayName("builder rejects out-of-range and non-finite scores when supplied")
		void builderScoreRejected() {
			for (double bad : new double[] { -0.1, 1.1, Double.NaN, Double.POSITIVE_INFINITY,
					Double.NEGATIVE_INFINITY }) {
				assertThatThrownBy(() -> Judgment.builder().pass().score(bad).build()).as("score %s", bad)
					.isInstanceOf(IllegalArgumentException.class);
			}
		}

		@Test
		@DisplayName("ABSTAIN and ERROR must not carry a score")
		void noScoreForNonMeasurements() {
			assertThatThrownBy(() -> new Judgment(JudgmentStatus.ABSTAIN, 0.5, null, "x", List.of(), Map.of()))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("no completed measurement");
			assertThatThrownBy(() -> new Judgment(JudgmentStatus.ERROR, 0.0, null, "x", List.of(), Map.of()))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("no completed measurement");
		}

		@Test
		@DisplayName("label must be non-blank when present")
		void labelNonBlank() {
			assertThat(Judgment.builder().pass().label("relevant").reasoning("x").build().label())
				.isEqualTo("relevant");
			assertThat(Judgment.pass("ok").label()).isNull();
			assertThatThrownBy(() -> new Judgment(JudgmentStatus.PASS, null, "  ", "x", List.of(), Map.of()))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("non-blank");
		}

		@Test
		@DisplayName("ABSTAIN may carry a label; ERROR may not")
		void labelAllowedOnAbstainOnly() {
			assertThatCode(() -> new Judgment(JudgmentStatus.ABSTAIN, null, "not_applicable", "x", List.of(), Map.of()))
				.doesNotThrowAnyException();
			assertThatThrownBy(
					() -> new Judgment(JudgmentStatus.ERROR, null, "not_applicable", "x", List.of(), Map.of()))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("must not carry a label");
		}

		@Test
		@DisplayName("ABSTAIN and ERROR require non-blank reasoning; others do not")
		void reasoningRequiredForNonFindings() {
			assertThatThrownBy(() -> new Judgment(JudgmentStatus.ABSTAIN, null, null, "  ", List.of(), Map.of()))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("non-blank reasoning");
			assertThatThrownBy(() -> new Judgment(JudgmentStatus.ERROR, null, null, "", List.of(), Map.of()))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("non-blank reasoning");
			assertThatCode(() -> new Judgment(JudgmentStatus.PASS, null, null, "", List.of(), Map.of()))
				.doesNotThrowAnyException();
		}

		@Test
		@DisplayName("checks and metadata are immutable copies")
		void immutableCollections() {
			Judgment judgment = Judgment.pass("ok");

			assertThatThrownBy(() -> judgment.checks().add(Check.pass("x")))
				.isInstanceOf(UnsupportedOperationException.class);
			assertThatThrownBy(() -> judgment.metadata().put("k", "v"))
				.isInstanceOf(UnsupportedOperationException.class);
		}

	}

	@Nested
	@DisplayName("Fluent API")
	class Fluent {

		@Test
		@DisplayName("verdict(boolean) derives only the corresponding status")
		void verdictDerivesStatusWithoutDuplicateScore() {
			Judgment passed = Judgment.verdict(true).reasoning("matched").build();
			Judgment failed = Judgment.verdict(false).reasoning("differed").build();

			assertThat(passed.status()).isEqualTo(JudgmentStatus.PASS);
			assertThat(failed.status()).isEqualTo(JudgmentStatus.FAIL);
			assertThat(passed.score()).isNull();
			assertThat(failed.score()).isNull();
		}

		@Test
		@DisplayName("scored judgment derives its status at, above, and below the threshold")
		void scoredThresholdBoundaries() {
			assertThat(Judgment.scored(0.69).passingAt(0.70).reasoning("x").build().status())
				.isEqualTo(JudgmentStatus.FAIL);
			assertThat(Judgment.scored(0.70).passingAt(0.70).reasoning("x").build().status())
				.isEqualTo(JudgmentStatus.PASS);
			assertThat(Judgment.scored(0.71).passingAt(0.70).reasoning("x").build().status())
				.isEqualTo(JudgmentStatus.PASS);
		}

		@Test
		@DisplayName("raw-range scoring normalizes once and rejects invalid scales")
		void rawRangeScoring() {
			Judgment judgment = Judgment.scored(82.0, 0.0, 100.0)
				.passingAt(0.80)
				.reasoning("quality")
				.build();

			assertThat(judgment.status()).isEqualTo(JudgmentStatus.PASS);
			assertThat(judgment.score()).isEqualTo(0.82);
			assertThatThrownBy(() -> Judgment.scored(1.0, 1.0, 1.0))
				.isInstanceOf(IllegalArgumentException.class);
			assertThatThrownBy(() -> Judgment.scored(11.0, 0.0, 10.0))
				.isInstanceOf(IllegalArgumentException.class);
			assertThatThrownBy(() -> Judgment.scored(Double.NaN))
				.isInstanceOf(IllegalArgumentException.class);
			assertThatThrownBy(() -> Judgment.scored(0.5).passingAt(Double.NaN))
				.isInstanceOf(IllegalArgumentException.class);
		}

		@Test
		@DisplayName("builder requires an explicit outcome selection")
		void outcomeFirstBuilder() {
			assertThat(Judgment.builder()).isInstanceOf(Judgment.OutcomeStage.class);
			assertThat(Judgment.builder().pass().build().status()).isEqualTo(JudgmentStatus.PASS);
			assertThat(Judgment.builder().fail().build().status()).isEqualTo(JudgmentStatus.FAIL);
		}

		@Test
		@DisplayName("one builder composes scored and classified judgments")
		void conventionalBuilder() {
			Judgment judgment = Judgment.builder().pass()
				.label("excellent")
				.score(1.0)
				.reasoning("all criteria satisfied")
				.build();

			assertThat(judgment.label()).isEqualTo("excellent");
			assertThat(judgment.score()).isOne();
		}

		@Test
		@DisplayName("outcome stages expose only legal next operations")
		void outcomeStagesNarrowTheApi() {
			assertThatThrownBy(() -> Judgment.builder().pass().label("  ").build())
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("non-blank");

			assertThat(methodNames(Judgment.RequiredAbstainReason.class)).containsExactly("reasoning");
			assertThat(methodNames(Judgment.RequiredErrorReason.class)).containsExactly("reasoning");
			assertThat(methodNames(Judgment.AbstainBuilder.class)).doesNotContain("score");
			assertThat(methodNames(Judgment.ErrorBuilder.class)).doesNotContain("score", "label");
		}

		@Test
		@DisplayName("direct factories delegate through the same invariants")
		void directFactories() {
			assertThat(Judgment.pass("ok").status()).isEqualTo(JudgmentStatus.PASS);
			assertThat(Judgment.fail("nope").status()).isEqualTo(JudgmentStatus.FAIL);
			assertThat(Judgment.abstain("n/a").status()).isEqualTo(JudgmentStatus.ABSTAIN);
			assertThat(Judgment.error("boom").status()).isEqualTo(JudgmentStatus.ERROR);
			assertThatThrownBy(() -> Judgment.abstain("  ")).isInstanceOf(IllegalArgumentException.class);
			assertThatThrownBy(() -> Judgment.error("")).isInstanceOf(IllegalArgumentException.class);
		}

		@Test
		@DisplayName("effectiveScore derives 1.0/0.0 and is empty for non-measurements")
		void effectiveScore() {
			assertThat(Judgment.pass("x").effectiveScore()).hasValue(1.0);
			assertThat(Judgment.fail("x").effectiveScore()).hasValue(0.0);
			assertThat(Judgment.abstain("x").effectiveScore()).isEmpty();
			assertThat(Judgment.error("x").effectiveScore()).isEmpty();
			assertThat(Judgment.builder().fail().score(0.3).reasoning("x").build().effectiveScore())
				.hasValue(0.3);
		}

		@Test
		@DisplayName("pass() and hasError() report the outcome")
		void predicates() {
			assertThat(Judgment.pass("x").pass()).isTrue();
			assertThat(Judgment.fail("x").pass()).isFalse();
			assertThat(Judgment.error("x").hasError()).isTrue();
			assertThat(Judgment.fail("x").hasError()).isFalse();
		}

		@Test
		@DisplayName("checks and metadata accumulate")
		void checksAndMetadata() {
			Judgment judgment = Judgment.builder().pass()
				.reasoning("x")
				.check(Check.pass("a"))
				.checks(List.of(Check.pass("b"), Check.fail("c", "bad")))
				.metadata("model", "gpt")
				.metadata(Map.of("usage", 12))
				.build();

			assertThat(judgment.checks()).hasSize(3);
			assertThat(judgment.metadata()).containsEntry("model", "gpt").containsEntry("usage", 12);
		}

		@Test
		@DisplayName("toBuilder preserves every component and supports enrichment")
		void copyAndEnrich() {
			Judgment original = Judgment.builder().pass()
				.label("relevant")
				.score(0.8)
				.reasoning("matched")
				.check(Check.pass("shape"))
				.metadata("source", "model")
				.build();

			Judgment enriched = original.toBuilder().metadata("traceId", "abc").build();

			assertThat(enriched.status()).isEqualTo(original.status());
			assertThat(enriched.score()).isEqualTo(original.score());
			assertThat(enriched.label()).isEqualTo(original.label());
			assertThat(enriched.reasoning()).isEqualTo(original.reasoning());
			assertThat(enriched.checks()).isEqualTo(original.checks());
			assertThat(enriched.metadata()).containsEntry("source", "model").containsEntry("traceId", "abc");
		}

		@Test
		@DisplayName("elapsed() derives a Duration from the portable elapsedMillis key")
		void elapsed() {
			assertThat(Judgment.pass("x").elapsed()).isNull();

			Judgment timed = Judgment.builder().pass()
				.reasoning("x")
				.metadata(Judgment.ELAPSED_MILLIS_KEY, 100)
				.build();
			assertThat(timed.elapsed()).isEqualTo(Duration.ofMillis(100));
			assertThat(timed.metadata()).containsEntry("elapsedMillis", 100);
		}

	}

	@Nested
	@DisplayName("Reserved aggregation namespace")
	class ReservedNamespace {

		@Test
		@DisplayName("ordinary metadata methods reject the reserved key")
		void ordinaryMetadataMethodsRefuseReservedKey() {
			assertThatThrownBy(() -> Judgment.builder().pass().metadata(Judgment.AGGREGATION_KEY, "mine"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("reserved");
			assertThatThrownBy(() -> Judgment.builder().pass()
				.metadata(Map.of(Judgment.AGGREGATION_KEY, "mine")))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("reserved");
		}

		@Test
		@DisplayName("reservation is namespacing, not provenance authentication")
		void publicConstructionDoesNotAuthenticateEvidence() {
			Judgment direct = new Judgment(JudgmentStatus.PASS, null, null, "x", List.of(),
					Map.of(Judgment.AGGREGATION_KEY, Map.of("strategy", "caller")));

			assertThat(direct.metadata()).containsKey(Judgment.AGGREGATION_KEY);
		}

		@Test
		@DisplayName("an unrelated caller key is unaffected")
		void unrelatedKeysFine() {
			Judgment judgment = Judgment.builder().pass()
				.reasoning("x")
				.metadata("aggregations", "mine")
				.build();

			assertThat(judgment.metadata()).containsEntry("aggregations", "mine");
		}

	}

	@Nested
	@DisplayName("Serialization")
	class Serialization {

		@Test
		@DisplayName("boolean verdict omits absent optionals")
		void booleanWire() throws Exception {
			assertThat(MAPPER.writeValueAsString(Judgment.pass("All checks passed")))
				.isEqualTo("{\"status\":\"pass\",\"reasoning\":\"All checks passed\",\"checks\":[],\"metadata\":{}}");
		}

		@Test
		@DisplayName("quantitative verdict includes score")
		void scoredWire() throws Exception {
			Judgment judgment = Judgment.builder().pass()
				.score(0.82)
				.reasoning("Quality exceeded the acceptance threshold")
				.build();

			assertThat(MAPPER.writeValueAsString(judgment)).isEqualTo("{\"status\":\"pass\",\"score\":0.82,"
					+ "\"reasoning\":\"Quality exceeded the acceptance threshold\",\"checks\":[],\"metadata\":{}}");
		}

		@Test
		@DisplayName("categorical verdict includes label")
		void classifiedWire() throws Exception {
			Judgment judgment = Judgment.builder().pass()
				.label("relevant")
				.reasoning("The document directly supports the claim")
				.build();

			assertThat(MAPPER.writeValueAsString(judgment)).isEqualTo("{\"status\":\"pass\",\"label\":\"relevant\","
					+ "\"reasoning\":\"The document directly supports the claim\",\"checks\":[],\"metadata\":{}}");
		}

		@Test
		@DisplayName("an error judgment carries no exception anywhere in its projection")
		void errorWire() throws Exception {
			String json = MAPPER.writeValueAsString(Judgment.error("Judge invocation timed out"));

			assertThat(json).isEqualTo(
					"{\"status\":\"error\",\"reasoning\":\"Judge invocation timed out\",\"checks\":[],\"metadata\":{}}");
			assertThat(json).doesNotContain("stackTrace").doesNotContain("cause").doesNotContain("Exception");
		}

		@Test
		@DisplayName("no null optionals and no polymorphic type metadata")
		void noNullsOrTypeTags() throws Exception {
			String json = MAPPER.writeValueAsString(Judgment.pass("ok"));

			assertThat(json).doesNotContain("null").doesNotContain("@class").doesNotContain("@type");
		}

		@Test
		@DisplayName("wire status names are lower case, exact and case-sensitive")
		void statusWireNames() throws Exception {
			assertThat(MAPPER.writeValueAsString(JudgmentStatus.ABSTAIN)).isEqualTo("\"abstain\"");
			assertThat(MAPPER.readValue("\"abstain\"", JudgmentStatus.class)).isEqualTo(JudgmentStatus.ABSTAIN);

			assertThatThrownBy(() -> MAPPER.readValue("\"ABSTAIN\"", JudgmentStatus.class))
				.as("upper case is refused rather than silently accepted")
				.isInstanceOf(Exception.class);
			assertThatThrownBy(() -> MAPPER.readValue("\"unknown\"", JudgmentStatus.class))
				.isInstanceOf(Exception.class);
		}

		@Test
		@DisplayName("round-trips through deserialization")
		void roundTrip() throws Exception {
			Judgment original = Judgment.builder().fail()
				.score(0.42)
				.reasoning("below bar")
				.check(Check.fail("c", "bad"))
				.build();

			assertThat(MAPPER.readValue(MAPPER.writeValueAsString(original), Judgment.class)).isEqualTo(original);
		}

		@Test
		@DisplayName("a non-JSON-safe metadata value never reaches serialization")
		void nonPortableMetadata() {
			// Portability is a property of the constructed value, not of what a caller
			// happens to attach. A Duration cannot round-trip under a plain ObjectMapper,
			// so it is refused where it is supplied rather than where it is written; no
			// judgment holding one exists to serialize. Vectors live in
			// PortableMetadataContractTest.
			assertThatThrownBy(() -> Judgment.builder().pass()
				.reasoning("x")
				.metadata("elapsed", Duration.ofMillis(100))
				.build())
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("metadata.elapsed")
				.hasMessageContaining("java.time.Duration");
		}

	}

	private static Set<String> methodNames(Class<?> type) {
		return java.util.Arrays.stream(type.getDeclaredMethods())
			.map(java.lang.reflect.Method::getName)
			.collect(Collectors.toSet());
	}

}
