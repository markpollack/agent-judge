package io.github.markpollack.judge.ai.model;

import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.github.markpollack.judge.ai.LabelJudgmentClassifier;
import io.github.markpollack.judge.result.Judgment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

/**
 * The recorded model-usage contract: optional provider-reported token quantities, never a
 * price.
 *
 * <p>
 * This corpus reaches {@code Usage} through reflection rather than through its typed
 * constructor on purpose. Written that way it compiles against whatever shape the record
 * currently has, so a contract violation shows up as a failing assertion about the
 * declared contract rather than as a compile error about a constructor that does not
 * exist. It also keeps the guard honest afterwards: the component names are the wire keys
 * a consumer depends on, so renaming one must fail here even though nothing else in the
 * repository would notice.
 * </p>
 */
class PortableUsageContractTest {

	/**
	 * The declared quantities, in the order a portable projection emits them.
	 * Asserted as literals because these are the wire keys, and a test coupled to the
	 * constants cannot detect a constant being renamed.
	 */
	private static final List<String> QUANTITIES = List.of("inputTokens", "outputTokens", "reasoningTokens",
			"cacheCreationTokens", "cacheReadTokens", "reportedTotalTokens");

	@Nested
	@DisplayName("Declared contract")
	class Declaration {

		@Test
		@DisplayName("Usage declares exactly the six token quantities, in the recorded order")
		void declaresExactlyTheSixTokenQuantities() {
			assertThat(componentNames()).containsExactlyElementsOf(QUANTITIES);
		}

		@Test
		@DisplayName("no component of Usage is a price")
		void declaresNoPriceComponent() {
			for (RecordComponent component : Usage.class.getRecordComponents()) {
				assertThat(component.getName().toLowerCase(Locale.ROOT)).doesNotContain("cost").doesNotContain("price");
				assertThat(component.getType()).isNotEqualTo(BigDecimal.class);
			}
		}

		@Test
		@DisplayName("every quantity is an optional Long, so absence is representable and the domain is integral")
		void everyQuantityIsAnOptionalLong() {
			assertThat(Arrays.stream(Usage.class.getRecordComponents()).map(RecordComponent::getType))
				.containsOnly(Long.class);
		}

	}

	@Nested
	@DisplayName("Portable projection")
	class Projection {

		@Test
		@DisplayName("every reported category reaches result metadata, in the declared order")
		void everyReportedCategoryReachesResultMetadata() {
			Map<String, Object> projected = projectedUsage(usage(11L, 22L, 33L, 44L, 55L, 66L));

			assertThat(projected).containsExactly(entry("inputTokens", 11L), entry("outputTokens", 22L),
					entry("reasoningTokens", 33L), entry("cacheCreationTokens", 44L), entry("cacheReadTokens", 55L),
					entry("reportedTotalTokens", 66L));
		}

		@Test
		@DisplayName("an unreported category is omitted rather than carried as a hole")
		void unreportedCategoriesAreOmitted() {
			Map<String, Object> projected = projectedUsage(usage(11L, 22L, null, null, null, null));

			assertThat(projected).containsExactly(entry("inputTokens", 11L), entry("outputTokens", 22L));
		}

	}

	@Nested
	@DisplayName("Reported quantities")
	class Quantities {

		@Test
		@DisplayName("a negative quantity is refused where it is reported")
		void negativeQuantitiesAreRefused() {
			assertThatThrownBy(() -> Usage.builder().outputTokens(-1).build())
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("usage.outputTokens")
				.hasMessageContaining("-1");
		}

		@Test
		@DisplayName("a quantity outside the interoperable integer domain is refused")
		void outOfDomainQuantitiesAreRefused() {
			assertThatThrownBy(() -> Usage.builder().inputTokens(9007199254740992L).build())
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("usage.inputTokens")
				.hasMessageContaining("interoperable integer range");
		}

		@Test
		@DisplayName("zero is a reported quantity and survives as one")
		void zeroIsReported() {
			Usage usage = Usage.builder().inputTokens(0).build();

			assertThat(usage.toPortableMap()).containsExactly(entry("inputTokens", 0L));
		}

		@Test
		@DisplayName("a usage value that reported nothing projects to nothing")
		void nothingReportedProjectsToAnEmptyMap() {
			assertThat(Usage.builder().build().toPortableMap()).isEmpty();
		}

		@Test
		@DisplayName("the projection is immutable")
		void projectionIsImmutable() {
			Map<String, Object> projected = Usage.builder().inputTokens(1).build().toPortableMap();

			assertThatThrownBy(() -> projected.put("outputTokens", 2L))
				.isInstanceOf(UnsupportedOperationException.class);
		}

		@Test
		@DisplayName("the key constants are the wire keys")
		void keyConstantsMatchTheWireKeys() {
			assertThat(List.of(Usage.INPUT_TOKENS_KEY, Usage.OUTPUT_TOKENS_KEY, Usage.REASONING_TOKENS_KEY,
					Usage.CACHE_CREATION_TOKENS_KEY, Usage.CACHE_READ_TOKENS_KEY, Usage.REPORTED_TOTAL_TOKENS_KEY))
				.containsExactlyElementsOf(QUANTITIES);
		}

	}

	private static List<String> componentNames() {
		return Arrays.stream(Usage.class.getRecordComponents()).map(RecordComponent::getName).toList();
	}

	/**
	 * Build a usage value through the canonical six-quantity constructor. A record whose
	 * components cannot express reasoning and cache activity fails here, naming the
	 * contract it does not meet.
	 */
	private static Usage usage(Long inputTokens, Long outputTokens, Long reasoningTokens, Long cacheCreationTokens,
			Long cacheReadTokens, Long reportedTotalTokens) {
		Constructor<Usage> canonical;
		try {
			canonical = Usage.class.getDeclaredConstructor(Long.class, Long.class, Long.class, Long.class, Long.class,
					Long.class);
		}
		catch (NoSuchMethodException ex) {
			throw new AssertionError("Usage cannot represent the six reported token quantities " + QUANTITIES
					+ "; its components are " + componentNames(), ex);
		}
		try {
			return canonical.newInstance(inputTokens, outputTokens, reasoningTokens, cacheCreationTokens,
					cacheReadTokens, reportedTotalTokens);
		}
		catch (ReflectiveOperationException ex) {
			throw new AssertionError("Usage rejected a valid set of reported quantities", ex);
		}
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> projectedUsage(Usage usage) {
		Judgment judgment = LabelJudgmentClassifier.passFail("yes", "no")
			.classify(new JudgeModelResponse("yes", "gpt-4o", usage, null));

		Object projected = judgment.metadata().get("usage");
		assertThat(projected).as("result metadata carries a portable usage object").isInstanceOf(Map.class);
		return (Map<String, Object>) projected;
	}

}
