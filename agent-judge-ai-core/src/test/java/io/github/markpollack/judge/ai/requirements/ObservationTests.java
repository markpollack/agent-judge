package io.github.markpollack.judge.ai.requirements;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.github.markpollack.judge.result.Judgment;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Evidence that rides beside a judgment without becoming one.
 *
 * <p>Two properties are load-bearing. It must survive the portable-metadata gate, or it could
 * never be recorded, replayed or shipped anywhere. And reading it back must be forgiving: an
 * absent or malformed observation yields nothing rather than turning a valid judgment into a
 * failure.
 */
class ObservationTests {

	@Test
	void theStoredFormIsOrdinaryJsonShapedData() {
		Observation observation = new Observation("UC6-AC41", "no test covers the boundary at BarTests.java:191",
			List.of("BarTests.java:191"));

		// A JSON object, so these are the entries and not an ordering.
		assertThat(observation.toMetadata()).containsOnly(
			Map.entry("requirementId", "UC6-AC41"),
			Map.entry("message", "no test covers the boundary at BarTests.java:191"),
			Map.entry("locations", List.of("BarTests.java:191")));
	}

	@Test
	void theStoredFormPassesThePortableMetadataGate() {
		// Judgment rejects non-portable metadata at construction. An observation that could not
		// cross that gate could not cross a process boundary either.
		Observation observation = new Observation("UC6-AC41", "an aside", List.of("Foo.java:10"));

		Judgment judgment = Judgment.builder().pass()
			.reasoning("all 1 requirements established")
			.metadata(Observation.METADATA_KEY, List.of(observation.toMetadata()))
			.build();

		assertThat(Observation.of(judgment)).containsExactly(observation);
	}

	@Test
	void aJudgmentCarryingNoObservationsReadsBackAsNone() {
		Judgment judgment = Judgment.builder().pass().reasoning("all 1 requirements established").build();

		assertThat(Observation.of(judgment)).isEmpty();
	}

	@Test
	void anUnexpectedlyShapedEntryIsDroppedRatherThanFatal() {
		// A cosmetic change in non-binding model prose must never break a valid judgment.
		Judgment judgment = Judgment.builder().pass().reasoning("all 1 requirements established")
			.metadata(Observation.METADATA_KEY, List.of(
			Map.of("requirementId", "UC1-AC1"),
			Map.of("message", "an aside with no requirement"),
			Map.of("requirementId", "UC1-AC2", "message", "a well-formed one")))
			.build();

		assertThat(Observation.of(judgment)).singleElement()
			.extracting(Observation::requirementId).isEqualTo("UC1-AC2");
	}

	@Test
	void metadataThatIsNotAListOfObservationsReadsBackAsNone() {
		Judgment judgment = Judgment.builder().pass().reasoning("all 1 requirements established")
			.metadata(Observation.METADATA_KEY, "not a list at all")
			.build();

		assertThat(Observation.of(judgment)).isEmpty();
	}

	@Test
	void anObservationWithNoLocationIsStillAnObservation() {
		// Locations are extracted from the message, and a message need not cite one.
		Judgment judgment = Judgment.builder().pass().reasoning("all 1 requirements established")
			.metadata(Observation.METADATA_KEY,
			List.of(new Observation("UC1-AC1", "an aside", List.of()).toMetadata()))
			.build();

		assertThat(Observation.of(judgment)).singleElement()
			.extracting(Observation::locations).isEqualTo(List.of());
	}

	@Test
	void theLocationsAreCopiedAtConstructionAndCannotBeChangedAfterwards() {
		List<String> mutable = new ArrayList<>(List.of("Foo.java:10"));
		Observation observation = new Observation("UC1-AC1", "an aside", mutable);

		mutable.add("Bar.java:20");

		assertThat(observation.locations()).containsExactly("Foo.java:10");
		assertThatThrownBy(() -> observation.locations().add("Baz.java:30"))
			.isInstanceOf(UnsupportedOperationException.class);
	}

}
