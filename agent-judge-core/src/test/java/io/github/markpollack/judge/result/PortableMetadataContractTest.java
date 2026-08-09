/*
 * Copyright (c) 2026 Mark Pollack
 * See LICENSE.txt in the repository root for license terms.
 */

package io.github.markpollack.judge.result;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The M3 contract: every constructed {@link Judgment} carries recursively portable,
 * recursively immutable result metadata.
 *
 * <p>
 * Portability is a property of the constructed value, not of caller restraint. These cases
 * therefore run through the canonical constructor — the one path a staged builder cannot
 * police — and assert that acceptance, rejection, and freezing all happen at construction
 * rather than at some later serializer boundary.
 * </p>
 *
 * <p>
 * The accepted and rejected vectors are the Agent Workflow Layer 1 portable-value profile
 * translated into Java. Keys are asserted as literals because consumers depend on them.
 * </p>
 *
 * @author Mark Pollack
 * @since 0.14.0
 */
class PortableMetadataContractTest {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	/** The largest integer a JSON consumer using IEEE-754 doubles can represent exactly. */
	private static final long MAX_INTEROPERABLE_INTEGER = 9007199254740991L;

	private static Judgment withMetadata(Map<String, Object> metadata) {
		return new Judgment(JudgmentStatus.PASS, null, null, "ok", List.of(), metadata);
	}

	private static Map<String, Object> singleton(String key, Object value) {
		Map<String, Object> metadata = new HashMap<>();
		metadata.put(key, value);
		return metadata;
	}

	@Nested
	@DisplayName("Accepted Layer 1 vectors")
	class Accepted {

		@Test
		@DisplayName("absence is an empty map, not a null-valued key")
		void absence() {
			assertThat(Judgment.pass("ok").metadata()).isEmpty();
			assertThat(withMetadata(Map.of()).metadata()).isEmpty();
		}

		@Test
		@DisplayName("scalars: strings, booleans, interoperable integers, finite numbers")
		void scalars() {
			Map<String, Object> metadata = new LinkedHashMap<>();
			metadata.put("text", "a plain string");
			metadata.put("unicode", "café ✓ 👍");
			metadata.put("emptyString", "");
			metadata.put("flag", true);
			metadata.put("intValue", 42);
			metadata.put("longValue", MAX_INTEROPERABLE_INTEGER);
			metadata.put("negativeLong", -MAX_INTEROPERABLE_INTEGER);
			metadata.put("zero", 0);
			metadata.put("doubleValue", 0.125d);
			metadata.put("negativeDouble", -273.15d);

			Judgment judgment = withMetadata(metadata);

			assertThat(judgment.metadata()).containsEntry("text", "a plain string")
				.containsEntry("unicode", "café ✓ 👍")
				.containsEntry("emptyString", "")
				.containsEntry("flag", true)
				.containsEntry("intValue", 42)
				.containsEntry("longValue", MAX_INTEROPERABLE_INTEGER)
				.containsEntry("doubleValue", 0.125d);
		}

		@Test
		@DisplayName("arrays and lists at several nesting depths")
		void arraysAndLists() {
			Map<String, Object> metadata = new LinkedHashMap<>();
			metadata.put("strings", List.of("a", "b"));
			metadata.put("primitiveArray", new int[] { 1, 2, 3 });
			metadata.put("objectArray", new String[] { "x", "y" });
			metadata.put("empty", List.of());
			metadata.put("nested", List.of(List.of(1, 2), List.of(3)));

			Judgment judgment = withMetadata(metadata);

			assertThat(judgment.metadata().get("strings")).isEqualTo(List.of("a", "b"));
			assertThat(judgment.metadata().get("primitiveArray"))
				.as("a Java array is normalized into a list, because an array cannot be frozen")
				.isEqualTo(List.of(1, 2, 3));
			assertThat(judgment.metadata().get("objectArray")).isEqualTo(List.of("x", "y"));
			assertThat(judgment.metadata().get("empty")).isEqualTo(List.of());
			assertThat(judgment.metadata().get("nested")).isEqualTo(List.of(List.of(1, 2), List.of(3)));
		}

		@Test
		@DisplayName("string-keyed objects at several nesting depths")
		void stringKeyedObjects() {
			Map<String, Object> deep = new LinkedHashMap<>();
			deep.put("tokens", 128);
			deep.put("finishReason", "stop");

			Map<String, Object> middle = new LinkedHashMap<>();
			middle.put("usage", deep);
			middle.put("samples", List.of(Map.of("index", 0), Map.of("index", 1)));

			Judgment judgment = withMetadata(singleton("response", middle));

			assertThat(judgment.metadata().get("response")).isEqualTo(middle);
			@SuppressWarnings("unchecked")
			Map<String, Object> response = (Map<String, Object>) judgment.metadata().get("response");
			@SuppressWarnings("unchecked")
			Map<String, Object> usage = (Map<String, Object>) response.get("usage");
			assertThat(usage).containsEntry("tokens", 128).containsEntry("finishReason", "stop");
		}

		@Test
		@DisplayName("accepted vectors round-trip through ordinary JSON without type metadata")
		void roundTrip() throws Exception {
			Map<String, Object> metadata = new LinkedHashMap<>();
			metadata.put("text", "value");
			metadata.put("flag", false);
			metadata.put("count", 7);
			metadata.put("big", MAX_INTEROPERABLE_INTEGER);
			metadata.put("ratio", 0.25d);
			metadata.put("list", List.of(1, "two", true));
			metadata.put("object", Map.of("nested", List.of(Map.of("depth", 3))));

			Judgment judgment = withMetadata(metadata);
			String json = MAPPER.writeValueAsString(judgment);

			assertThat(json).doesNotContain("@class").doesNotContain("@type");
			assertThat(MAPPER.readTree(MAPPER.writeValueAsString(MAPPER.readValue(json, Judgment.class))))
				.as("every accepted value survives serialize -> parse -> serialize unchanged")
				.isEqualTo(MAPPER.readTree(json));
		}

		@Test
		@DisplayName("encounter order is preserved rather than rehashed")
		void encounterOrderPreserved() throws Exception {
			Map<String, Object> metadata = new LinkedHashMap<>();
			metadata.put("zebra", 1);
			metadata.put("alpha", 2);
			metadata.put("mike", 3);

			Judgment judgment = withMetadata(metadata);

			assertThat(judgment.metadata().keySet()).containsExactly("zebra", "alpha", "mike");
			assertThat(MAPPER.writeValueAsString(judgment)).contains("\"zebra\":1,\"alpha\":2,\"mike\":3");
		}

	}

	@Nested
	@DisplayName("Rejected Layer 1 vectors")
	class Rejected {

		@Test
		@DisplayName("null values and null elements")
		void nulls() {
			assertThatThrownBy(() -> withMetadata(singleton("missing", null)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("metadata.missing")
				.hasMessageContaining("null");

			List<Object> withNull = new ArrayList<>();
			withNull.add("a");
			withNull.add(null);
			assertThatThrownBy(() -> withMetadata(singleton("items", withNull)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("metadata.items[1]");

			Map<String, Object> nestedNull = new HashMap<>();
			nestedNull.put("inner", null);
			assertThatThrownBy(() -> withMetadata(singleton("outer", nestedNull)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("metadata.outer.inner");
		}

		@Test
		@DisplayName("non-finite numbers")
		void nonFiniteNumbers() {
			assertThatThrownBy(() -> withMetadata(singleton("nan", Double.NaN)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("metadata.nan")
				.hasMessageContaining("finite");
			assertThatThrownBy(() -> withMetadata(singleton("inf", Double.POSITIVE_INFINITY)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("metadata.inf");
			assertThatThrownBy(() -> withMetadata(singleton("negInf", Float.NEGATIVE_INFINITY)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("metadata.negInf");
		}

		@Test
		@DisplayName("integers outside the interoperable domain")
		void outOfDomainIntegers() {
			assertThatCode(() -> withMetadata(singleton("edge", MAX_INTEROPERABLE_INTEGER)))
				.doesNotThrowAnyException();

			assertThatThrownBy(() -> withMetadata(singleton("tooBig", MAX_INTEROPERABLE_INTEGER + 1)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("metadata.tooBig")
				.hasMessageContaining("interoperable");
			assertThatThrownBy(() -> withMetadata(singleton("tooSmall", Long.MIN_VALUE)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("metadata.tooSmall");
		}

		@Test
		@DisplayName("malformed Unicode")
		void malformedUnicode() {
			assertThatThrownBy(() -> withMetadata(singleton("lonelyHigh", "a\uD83Db")))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("metadata.lonelyHigh")
				.hasMessageContaining("surrogate");
			assertThatThrownBy(() -> withMetadata(singleton("lonelyLow", "\uDC4D")))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("metadata.lonelyLow");
		}

		@Test
		@DisplayName("non-string map keys")
		void nonStringKeys() {
			Map<Object, Object> numericKeys = new HashMap<>();
			numericKeys.put(1, "one");

			assertThatThrownBy(() -> withMetadata(singleton("byIndex", numericKeys)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("metadata.byIndex")
				.hasMessageContaining("string");
		}

		@Test
		@DisplayName("live exceptions")
		void liveExceptions() {
			assertThatThrownBy(() -> withMetadata(singleton("cause", new IllegalStateException("boom"))))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("metadata.cause")
				.hasMessageContaining("IllegalStateException");
		}

		@Test
		@DisplayName("durations")
		void durations() {
			assertThatThrownBy(() -> withMetadata(singleton("elapsed", Duration.ofMillis(100))))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("metadata.elapsed")
				.hasMessageContaining("java.time.Duration");
		}

		@Test
		@DisplayName("SDK response objects and arbitrary Java objects")
		void arbitraryJavaObjects() {
			record SdkResponse(String id, Object raw) {
			}

			assertThatThrownBy(() -> withMetadata(singleton("response", new SdkResponse("r-1", new Object()))))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("metadata.response");
			assertThatThrownBy(() -> withMetadata(singleton("opaque", new Object())))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("metadata.opaque");
			assertThatThrownBy(() -> withMetadata(singleton("path", Path.of("/tmp"))))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("metadata.path");
			assertThatThrownBy(() -> withMetadata(singleton("instant", Instant.EPOCH)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("metadata.instant");
			assertThatThrownBy(() -> withMetadata(singleton("status", JudgmentStatus.PASS)))
				.as("an enum is a Java identity, not a portable value; project it to its wire token")
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("metadata.status");
			assertThatThrownBy(() -> withMetadata(singleton("precise", new BigDecimal("0.1"))))
				.as("arbitrary-precision types promise exactness that ordinary JSON consumers cannot keep")
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("metadata.precise");
			assertThatThrownBy(() -> withMetadata(singleton("huge", BigInteger.TEN)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("metadata.huge");
		}

		@Test
		@DisplayName("the reported path locates the exact offending value, however deep")
		void pathLocatesTheOffendingValue() {
			Map<String, Object> depth3 = singleton("when", Duration.ofSeconds(1));
			Map<String, Object> depth2 = singleton("detail", List.of("fine", depth3));
			Map<String, Object> depth1 = singleton("outer", depth2);

			assertThatThrownBy(() -> withMetadata(depth1)).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("metadata.outer.detail[1].when");
		}

	}

	@Nested
	@DisplayName("Recursive immutability")
	class Immutability {

		@Test
		@DisplayName("mutating the original map cannot alter an already-built judgment")
		void originalMapIsCopied() {
			Map<String, Object> original = new LinkedHashMap<>();
			original.put("kept", "value");

			Judgment judgment = withMetadata(original);
			original.put("added", "later");
			original.put("kept", "changed");

			assertThat(judgment.metadata()).containsOnlyKeys("kept").containsEntry("kept", "value");
		}

		@Test
		@DisplayName("mutating a nested map or list cannot alter an already-built judgment")
		void nestedContainersAreCopied() {
			List<Object> items = new ArrayList<>(List.of("a"));
			Map<String, Object> inner = new LinkedHashMap<>();
			inner.put("items", items);
			inner.put("count", 1);

			Judgment judgment = withMetadata(singleton("outer", inner));

			items.add("b");
			inner.put("count", 99);

			@SuppressWarnings("unchecked")
			Map<String, Object> stored = (Map<String, Object>) judgment.metadata().get("outer");
			assertThat(stored).containsEntry("count", 1);
			assertThat(stored.get("items")).isEqualTo(List.of("a"));
		}

		@Test
		@DisplayName("mutating an original array cannot alter an already-built judgment")
		void arraysAreCopied() {
			int[] counts = { 1, 2, 3 };

			Judgment judgment = withMetadata(singleton("counts", counts));
			counts[0] = 99;

			assertThat(judgment.metadata().get("counts")).isEqualTo(List.of(1, 2, 3));
		}

		@Test
		@DisplayName("nested containers reject mutation through the judgment itself")
		void nestedContainersAreFrozen() {
			Map<String, Object> inner = new LinkedHashMap<>();
			inner.put("items", new ArrayList<>(List.of("a")));

			Judgment judgment = withMetadata(singleton("outer", inner));

			@SuppressWarnings("unchecked")
			Map<String, Object> stored = (Map<String, Object>) judgment.metadata().get("outer");
			assertThatThrownBy(() -> stored.put("k", "v")).isInstanceOf(UnsupportedOperationException.class);

			@SuppressWarnings("unchecked")
			List<Object> storedItems = (List<Object>) stored.get("items");
			assertThatThrownBy(() -> storedItems.add("b")).isInstanceOf(UnsupportedOperationException.class);
		}

		@Test
		@DisplayName("aggregation evidence is frozen at the same depth as any other value")
		void reservedEvidenceIsFrozenToo() {
			Judgment judgment = new Judgment(JudgmentStatus.PASS, null, null, "ok", List.of(),
					Map.of(Judgment.AGGREGATION_KEY, new LinkedHashMap<>(Map.of("strategy", "majority"))));

			@SuppressWarnings("unchecked")
			Map<String, Object> evidence = (Map<String, Object>) judgment.metadata().get(Judgment.AGGREGATION_KEY);
			assertThatThrownBy(() -> evidence.put("passCount", 1)).isInstanceOf(UnsupportedOperationException.class);
		}

	}

	@Nested
	@DisplayName("Portable result timing")
	class Timing {

		@Test
		@DisplayName("elapsedMillis is a non-negative interoperable integer")
		void elapsedMillisAccepted() {
			Judgment timed = Judgment.builder().pass().reasoning("x").metadata("elapsedMillis", 100).build();

			assertThat(timed.metadata()).containsEntry("elapsedMillis", 100);
			assertThat(timed.elapsed()).isEqualTo(Duration.ofMillis(100));

			Judgment zero = Judgment.builder().pass().reasoning("x").metadata("elapsedMillis", 0L).build();
			assertThat(zero.elapsed()).isEqualTo(Duration.ZERO);
		}

		@Test
		@DisplayName("absent timing is an omitted key, and elapsed() stays null")
		void absenceIsOmission() {
			assertThat(Judgment.pass("x").elapsed()).isNull();
			assertThat(Judgment.pass("x").metadata()).doesNotContainKey("elapsedMillis");
		}

		@Test
		@DisplayName("elapsedMillis rejects negative, fractional, and out-of-domain values")
		void elapsedMillisRejections() {
			assertThatThrownBy(() -> withMetadata(singleton("elapsedMillis", -1)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("metadata.elapsedMillis")
				.hasMessageContaining("non-negative");
			assertThatThrownBy(() -> withMetadata(singleton("elapsedMillis", 12.5d)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("metadata.elapsedMillis");
			assertThatThrownBy(() -> withMetadata(singleton("elapsedMillis", "100")))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("metadata.elapsedMillis");
			assertThatThrownBy(() -> withMetadata(singleton("elapsedMillis", MAX_INTEROPERABLE_INTEGER + 1)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("metadata.elapsedMillis");
		}

		@Test
		@DisplayName("the old elapsed/Duration convention is gone, not merely discouraged")
		void oldConventionCharacterized() {
			// 0.13: metadata key "elapsed" held a live java.time.Duration and elapsed()
			// cast it. Both halves change: the value is refused at construction, and the
			// accessor no longer reads that key.
			assertThatThrownBy(() -> withMetadata(singleton("elapsed", Duration.ofMillis(100))))
				.isInstanceOf(IllegalArgumentException.class);

			Judgment portableButWrongKey = Judgment.builder().pass().reasoning("x").metadata("elapsed", 100).build();
			assertThat(portableButWrongKey.elapsed())
				.as("elapsed() reads elapsedMillis only; the unit-free legacy key is not a fallback")
				.isNull();
		}

		@Test
		@DisplayName("elapsedMillis survives a JSON round trip as a plain integer")
		void elapsedMillisRoundTrips() throws Exception {
			Judgment timed = Judgment.builder().pass().reasoning("x").metadata("elapsedMillis", 2500L).build();

			String json = MAPPER.writeValueAsString(timed);
			assertThat(json).contains("\"elapsedMillis\":2500");

			Judgment parsed = MAPPER.readValue(json, Judgment.class);
			assertThat(parsed.elapsed()).isEqualTo(Duration.ofMillis(2500));
		}

	}

	@Nested
	@DisplayName("Producer conformance")
	class Producers {

		@Test
		@DisplayName("no Agent Judge producer needs a value this profile refuses")
		void projectionIsAlwaysAvailable() {
			// Every live object a producer might hold has a portable projection: a
			// Duration becomes elapsedMillis, a Path becomes its string form, a token-usage
			// record becomes a string-keyed object of numbers.
			Map<String, Object> projected = new LinkedHashMap<>();
			projected.put("elapsedMillis", Duration.ofSeconds(2).toMillis());
			projected.put("expectedDir", Path.of("/tmp/expected").toString());
			projected.put("usage", Map.of("inputTokens", 10L, "outputTokens", 20L, "reasoningTokens", 5L));

			Judgment judgment = withMetadata(projected);

			assertThat(judgment.elapsed()).isEqualTo(Duration.ofSeconds(2));
			assertThat(judgment.metadata().get("expectedDir")).isEqualTo("/tmp/expected");
			assertThat(judgment.metadata().get("usage")).isEqualTo(Map.of("inputTokens", 10L, "outputTokens", 20L,
					"reasoningTokens", 5L));
		}

		@Test
		@DisplayName("a Collection that is not a List still normalizes to an ordered array")
		void collectionsNormalizeToLists() {
			Judgment judgment = withMetadata(singleton("tags", new java.util.LinkedHashSet<>(Arrays.asList("a", "b"))));

			assertThat(judgment.metadata().get("tags")).isEqualTo(List.of("a", "b"));
		}

	}

}
