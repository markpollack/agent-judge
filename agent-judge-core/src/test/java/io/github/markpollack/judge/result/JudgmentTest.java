/*
 * Copyright 2024 Spring AI Community
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.markpollack.judge.result;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.assertj.core.data.Offset;
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
			assertThat(Judgment.scored(0.0).withStatus(JudgmentStatus.FAIL).because("x").build().score()).isZero();
			assertThat(Judgment.scored(1.0).withStatus(JudgmentStatus.PASS).because("x").build().score()).isOne();
			assertThat(Judgment.scored(0.42).withStatus(JudgmentStatus.PASS).because("x").build().score())
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
			assertThat(Judgment.classified("relevant").as(JudgmentStatus.PASS).because("x").build().label())
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
		@DisplayName("verdict(boolean) derives only the status, with no score")
		void verdictDerivesStatusOnly() {
			assertThat(Judgment.verdict(true).because("x").build().status()).isEqualTo(JudgmentStatus.PASS);
			assertThat(Judgment.verdict(false).because("x").build().status()).isEqualTo(JudgmentStatus.FAIL);
			assertThat(Judgment.verdict(true).because("x").build().score()).isNull();
		}

		@Test
		@DisplayName("passing()/failing()/abstaining()/erroring()")
		void namedEntryPoints() {
			assertThat(Judgment.passing().because("x").build().status()).isEqualTo(JudgmentStatus.PASS);
			assertThat(Judgment.failing().because("x").build().status()).isEqualTo(JudgmentStatus.FAIL);
			assertThat(Judgment.abstaining().because("x").build().status()).isEqualTo(JudgmentStatus.ABSTAIN);
			assertThat(Judgment.erroring().because("x").build().status()).isEqualTo(JudgmentStatus.ERROR);
		}

		@Test
		@DisplayName("passingAt: below, above, and exactly at the threshold")
		void thresholdBoundaries() {
			assertThat(Judgment.scored(0.69).passingAt(0.70).because("x").build().status())
				.isEqualTo(JudgmentStatus.FAIL);
			assertThat(Judgment.scored(0.71).passingAt(0.70).because("x").build().status())
				.isEqualTo(JudgmentStatus.PASS);
			// Exactly at the threshold passes: the comparison is >=.
			assertThat(Judgment.scored(0.70).passingAt(0.70).because("x").build().status())
				.isEqualTo(JudgmentStatus.PASS);
		}

		@Test
		@DisplayName("raw-range input normalizes at construction")
		void rawRangeNormalizes() {
			assertThat(Judgment.scored(82.0, 0.0, 100.0).passingAt(0.7).because("x").build().score())
				.isCloseTo(0.82, Offset.offset(1e-9));
			assertThat(Judgment.scored(8.5, 0.0, 10.0).passingAt(0.7).because("x").build().score())
				.isCloseTo(0.85, Offset.offset(1e-9));
		}

		@Test
		@DisplayName("invalid ranges are rejected, including max == min")
		void invalidRanges() {
			assertThatThrownBy(() -> Judgment.scored(5.0, 5.0, 5.0)).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("max must be greater than min");
			assertThatThrownBy(() -> Judgment.scored(5.0, 10.0, 0.0)).isInstanceOf(IllegalArgumentException.class);
			assertThatThrownBy(() -> Judgment.scored(20.0, 0.0, 10.0)).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("must be between");
			assertThatThrownBy(() -> Judgment.scored(Double.NaN, 0.0, 10.0))
				.isInstanceOf(IllegalArgumentException.class);
		}

		@Test
		@DisplayName("a scored judgment's explicit status is restricted to PASS/FAIL")
		void scoredStatusRestricted() {
			assertThat(Judgment.scored(0.5).withStatus(JudgmentStatus.PASS).because("x").build().status())
				.isEqualTo(JudgmentStatus.PASS);
			assertThatThrownBy(() -> Judgment.scored(0.5).withStatus(JudgmentStatus.ABSTAIN))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("cannot carry a score");
			assertThatThrownBy(() -> Judgment.scored(0.5).withStatus(JudgmentStatus.ERROR))
				.isInstanceOf(IllegalArgumentException.class);
		}

		@Test
		@DisplayName("a classified judgment can add a declared normalized score")
		void classifiedWithDeclaredScore() {
			Judgment judgment = Judgment.classified("excellent")
				.as(JudgmentStatus.PASS)
				.withNormalizedScore(1.0)
				.because("all criteria satisfied")
				.build();

			assertThat(judgment.label()).isEqualTo("excellent");
			assertThat(judgment.score()).isOne();
		}

		@Test
		@DisplayName("classified rejects a blank label")
		void classifiedRejectsBlank() {
			assertThatThrownBy(() -> Judgment.classified("  ")).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("non-blank");
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
			assertThat(Judgment.scored(0.3).withStatus(JudgmentStatus.FAIL).because("x").build().effectiveScore())
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
			Judgment judgment = Judgment.verdict(true)
				.because("x")
				.withCheck(Check.pass("a"))
				.withChecks(List.of(Check.pass("b"), Check.fail("c", "bad")))
				.metadata("model", "gpt")
				.metadata(Map.of("usage", 12))
				.build();

			assertThat(judgment.checks()).hasSize(3);
			assertThat(judgment.metadata()).containsEntry("model", "gpt").containsEntry("usage", 12);
		}

		@Test
		@DisplayName("elapsed() reads the metadata convention")
		void elapsed() {
			assertThat(Judgment.pass("x").elapsed()).isNull();

			Judgment timed = Judgment.verdict(true).because("x").metadata("elapsed", Duration.ofMillis(100)).build();
			assertThat(timed.elapsed()).isEqualTo(Duration.ofMillis(100));
		}

	}

	@Nested
	@DisplayName("Reserved aggregation namespace")
	class ReservedNamespace {

		@Test
		@DisplayName("callers cannot write the reserved key")
		void callersRefused() {
			assertThatThrownBy(() -> Judgment.verdict(true).metadata(Judgment.AGGREGATION_KEY, "mine"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("reserved");
			assertThatThrownBy(() -> Judgment.verdict(true).metadata(Map.of(Judgment.AGGREGATION_KEY, "mine")))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("reserved");
		}

		@Test
		@DisplayName("an unrelated caller key is unaffected")
		void unrelatedKeysFine() {
			Judgment judgment = Judgment.verdict(true).because("x").metadata("aggregations", "mine").build();

			assertThat(judgment.metadata()).containsEntry("aggregations", "mine");
		}

		@Test
		@DisplayName("the evidence block is deeply immutable")
		void evidenceDeeplyImmutable() {
			Judgment judgment = Judgment.verdict(true)
				.because("x")
				.aggregationEvidence(Map.of("inputCount", 2))
				.build();

			@SuppressWarnings("unchecked")
			Map<String, Object> block = (Map<String, Object>) judgment.metadata().get(Judgment.AGGREGATION_KEY);

			// Map.copyOf on the Judgment is shallow, so the nested block needs its own
			// immutability or a caller could mutate it through the returned metadata.
			assertThatThrownBy(() -> block.put("inputCount", 99)).isInstanceOf(UnsupportedOperationException.class);
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
			Judgment judgment = Judgment.scored(0.82)
				.passingAt(0.70)
				.because("Quality exceeded the acceptance threshold")
				.build();

			assertThat(MAPPER.writeValueAsString(judgment)).isEqualTo("{\"status\":\"pass\",\"score\":0.82,"
					+ "\"reasoning\":\"Quality exceeded the acceptance threshold\",\"checks\":[],\"metadata\":{}}");
		}

		@Test
		@DisplayName("categorical verdict includes label")
		void classifiedWire() throws Exception {
			Judgment judgment = Judgment.classified("relevant")
				.as(JudgmentStatus.PASS)
				.because("The document directly supports the claim")
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
			Judgment original = Judgment.scored(0.42)
				.withStatus(JudgmentStatus.FAIL)
				.because("below bar")
				.withCheck(Check.fail("c", "bad"))
				.build();

			assertThat(MAPPER.readValue(MAPPER.writeValueAsString(original), Judgment.class)).isEqualTo(original);
		}

		@Test
		@DisplayName("a judgment whose metadata holds a non-JSON-safe value is not portable")
		void nonPortableMetadata() {
			// The declared fields are unconditionally serializable; the whole judgment is
			// portable only when every metadata value belongs to the JSON value algebra.
			Judgment judgment = Judgment.verdict(true).because("x").metadata("elapsed", Duration.ofMillis(100)).build();

			assertThatThrownBy(() -> MAPPER.readValue(MAPPER.writeValueAsString(judgment), Judgment.class))
				.as("a Duration does not round-trip under a plain ObjectMapper")
				.isInstanceOf(Exception.class);
		}

	}

}
