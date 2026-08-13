/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury;

import org.junit.jupiter.api.Test;
import io.github.markpollack.judge.result.Judgment;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static io.github.markpollack.judge.JudgeTestFixtures.*;

/**
 * Tests for {@link Verdict}.
 *
 * @author Mark Pollack
 * @since 0.1.0
 */
class VerdictTest {

	@Test
	void shouldBuildVerdictWithBuilder() {
		Judgment aggregated = booleanPass("Majority passed");
		List<Judgment> individual = List.of(booleanPass("Judge 1"), booleanFail("Judge 2"), booleanPass("Judge 3"));
		Map<String, Judgment> byName = Map.of("Judge1", individual.get(0), "Judge2", individual.get(1), "Judge3",
				individual.get(2));
		Map<String, Double> weights = Map.of("0", 0.3, "1", 0.5, "2", 0.2);

		Verdict verdict = Verdict.builder()
			.aggregated(aggregated)
			.individual(individual)
			.individualByName(byName)
			.weights(weights)
			.build();

		assertThat(verdict.aggregated()).isEqualTo(aggregated);
		assertThat(verdict.individual()).hasSize(3);
		assertThat(verdict.individualByName()).hasSize(3);
		assertThat(verdict.weights()).hasSize(3);
		assertThat(verdict.compositeAttempts()).isEmpty();
	}

	@Test
	void shouldSupportCompleteAttemptsForMetaJury() {
		Verdict subVerdict1 = unanimousPass(2);
		Verdict subVerdict2 = split(1, 1);

		Judgment metaAggregated = booleanPass("Meta-jury passed");

		Verdict metaVerdict = Verdict.builder()
			.aggregated(metaAggregated)
			.individual(List.of(subVerdict1.aggregated(), subVerdict2.aggregated()))
			.compositeAttempts(List.of(
					new CompositeAttempt("first", CompositeRelation.META_MEMBER, null, subVerdict1, null),
					new CompositeAttempt("second", CompositeRelation.META_MEMBER, null, subVerdict2, null)))
			.build();

		assertThat(metaVerdict.aggregated()).isEqualTo(metaAggregated);
		assertThat(metaVerdict.compositeAttempts()).hasSize(2);
		assertThat(metaVerdict.compositeAttempts().get(0).verdict()).isEqualTo(subVerdict1);
		assertThat(metaVerdict.compositeAttempts().get(1).verdict()).isEqualTo(subVerdict2);
	}

	@Test
	void shouldProvideDefensiveCopiesForImmutability() {
		List<Judgment> originalIndividual = new java.util.ArrayList<>();
		originalIndividual.add(booleanPass("Judge 1"));
		originalIndividual.add(booleanFail("Judge 2"));

		Map<String, Judgment> originalByName = new java.util.HashMap<>();
		originalByName.put("Judge1", booleanPass("Judge 1"));

		List<CompositeAttempt> originalAttempts = new java.util.ArrayList<>();
		originalAttempts.add(new CompositeAttempt("member", CompositeRelation.META_MEMBER, null,
				Verdict.single("leaf", booleanPass("Leaf")), null));

		Verdict verdict = Verdict.builder()
			.aggregated(booleanPass("Aggregated"))
			.individual(originalIndividual)
			.individualByName(originalByName)
			.compositeAttempts(originalAttempts)
			.build();

		// Modify originals - should not affect verdict
		originalIndividual.add(booleanPass("Judge 3"));
		originalByName.put("Judge2", booleanFail("Judge 2"));
		originalAttempts.clear();

		assertThat(verdict.individual()).hasSize(2);
		assertThat(verdict.individualByName()).hasSize(1);
		assertThat(verdict.compositeAttempts()).hasSize(1).isUnmodifiable();
	}

	@Test
	void shouldPreserveMapInsertionOrderAndContainedIdentity() {
		Judgment first = booleanPass("First");
		Judgment second = booleanFail("Second");
		Map<String, Judgment> byName = new LinkedHashMap<>();
		byName.put("first", first);
		byName.put("second", second);
		Map<String, Double> weights = new LinkedHashMap<>();
		weights.put("first", 0.25);
		weights.put("second", 0.75);

		Verdict verdict = Verdict.builder()
			.aggregated(first)
			.individual(List.of(first, second))
			.individualByName(byName)
			.weights(weights)
			.build();

		assertThat(verdict.individual()).containsExactly(first, second);
		assertThat(verdict.individual().get(0)).isSameAs(first);
		assertThat(verdict.individualByName()).containsExactly(Map.entry("first", first), Map.entry("second", second));
		assertThat(verdict.individualByName().get("first")).isSameAs(first);
		assertThat(verdict.weights()).containsExactly(Map.entry("first", 0.25), Map.entry("second", 0.75));
	}

	@Test
	void shouldReturnUnmodifiableCollections() {
		Verdict verdict = Verdict.builder()
			.aggregated(booleanPass("Aggregated"))
			.individual(List.of(booleanPass("Judge 1")))
			.individualByName(Map.of("Judge1", booleanPass("Judge 1")))
			.build();

		// All collections should be immutable
		assertThat(verdict.individual()).isInstanceOf(List.class);
		assertThat(verdict.individualByName()).isInstanceOf(Map.class);

		// Attempting to modify should throw UnsupportedOperationException
		assertThat(verdict.individual()).isUnmodifiable();
		assertThat(verdict.individualByName()).isUnmodifiable();
	}

	@Test
	void shouldHandleNullFieldsWithDefaults() {
		Verdict verdict = Verdict.builder().aggregated(booleanPass("Only aggregated")).build();

		assertThat(verdict.aggregated()).isNotNull();
		assertThat(verdict.individual()).isEmpty();
		assertThat(verdict.individualByName()).isEmpty();
		assertThat(verdict.weights()).isEmpty();
		assertThat(verdict.compositeAttempts()).isEmpty();
	}

	@Test
	void shouldHandleEmptyCollections() {
		Verdict verdict = Verdict.builder()
			.aggregated(booleanPass("Aggregated"))
			.individual(List.of())
			.individualByName(Map.of())
			.weights(Map.of())
			.compositeAttempts(List.of())
			.build();

		assertThat(verdict.individual()).isEmpty();
		assertThat(verdict.individualByName()).isEmpty();
		assertThat(verdict.weights()).isEmpty();
		assertThat(verdict.compositeAttempts()).isEmpty();
	}

	@Test
	void shouldPreserveJudgeIdentity() {
		Judgment judge1 = booleanPass("File exists");
		Judgment judge2 = booleanFail("Correctness failed");
		Judgment judge3 = booleanPass("Build succeeded");

		Map<String, Judgment> byName = Map.of("FileExistsJudge", judge1, "CorrectnessJudge", judge2,
				"BuildSuccessJudge", judge3);

		Verdict verdict = Verdict.builder()
			.aggregated(booleanPass("Majority passed"))
			.individual(List.of(judge1, judge2, judge3))
			.individualByName(byName)
			.build();

		assertThat(verdict.individualByName().get("FileExistsJudge")).isEqualTo(judge1);
		assertThat(verdict.individualByName().get("CorrectnessJudge")).isEqualTo(judge2);
		assertThat(verdict.individualByName().get("BuildSuccessJudge")).isEqualTo(judge3);
	}

	@Test
	void shouldSupportWeightsInVerdict() {
		Map<String, Double> weights = Map.of("0", 0.5, "1", 0.3, "2", 0.2);

		Verdict verdict = Verdict.builder()
			.aggregated(booleanPass("Weighted result"))
			.individual(List.of(booleanPass("J1"), booleanPass("J2"), booleanPass("J3")))
			.weights(weights)
			.build();

		assertThat(verdict.weights()).containsEntry("0", 0.5);
		assertThat(verdict.weights()).containsEntry("1", 0.3);
		assertThat(verdict.weights()).containsEntry("2", 0.2);
	}

	@Test
	void shouldRejectMissingAggregatedJudgment() {
		assertThatThrownBy(() -> Verdict.builder().individual(List.of(booleanPass("Judge 1"))).build())
			.isInstanceOf(NullPointerException.class)
			.hasMessageContaining("aggregated judgment");
		assertThatThrownBy(() -> Verdict.builder().aggregated(null))
			.isInstanceOf(NullPointerException.class)
			.hasMessageContaining("aggregated judgment");
	}

	@Test
	void shouldRequireNonNullCompositeAttemptsOnTheCanonicalConstructor() {
		assertThatThrownBy(() -> new Verdict(booleanPass("Aggregated"), List.of(), Map.of(), Map.of(), null))
			.isInstanceOf(NullPointerException.class)
			.hasMessageContaining("compositeAttempts");
	}

	@Test
	void shouldCreateCompleteSingleJudgeVerdictWithoutRepetition() {
		Judgment judgment = booleanPass("File exists");

		Verdict verdict = Verdict.single("file-exists", judgment);

		assertThat(verdict.aggregated()).isSameAs(judgment);
		assertThat(verdict.individual()).containsExactly(judgment);
		assertThat(verdict.individualByName()).containsExactlyEntriesOf(Map.of("file-exists", judgment));
		assertThat(verdict.weights()).isEmpty();
		assertThat(verdict.compositeAttempts()).isEmpty();
	}

	@Test
	void singleJudgeVerdictRequiresIdentityAndJudgment() {
		assertThatThrownBy(() -> Verdict.single(" ", booleanPass("x")))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Verdict.single("judge", null))
			.isInstanceOf(NullPointerException.class);
	}

	@Test
	void recordShouldProvideEquality() {
		Judgment agg = booleanPass("Aggregated");
		List<Judgment> ind = List.of(booleanPass("J1"));

		Verdict verdict1 = Verdict.builder().aggregated(agg).individual(ind).build();

		Verdict verdict2 = Verdict.builder().aggregated(agg).individual(ind).build();

		assertThat(verdict1).isEqualTo(verdict2);
		assertThat(verdict1.hashCode()).isEqualTo(verdict2.hashCode());
	}

	@Test
	void recordShouldProvideToString() {
		Verdict verdict = Verdict.builder()
			.aggregated(booleanPass("Aggregated"))
			.individual(List.of(booleanPass("J1")))
			.build();

		String toString = verdict.toString();

		assertThat(toString).contains("Verdict");
		assertThat(toString).contains("aggregated");
		assertThat(toString).contains("individual");
	}

}
