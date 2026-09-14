/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import io.github.markpollack.judge.description.StrategyDescription;
import io.github.markpollack.judge.result.Judgment;

import static io.github.markpollack.judge.JudgeTestFixtures.passJudgment;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A strategy refuses a null policy when it is built, not when it first aggregates.
 *
 * <p>
 * A null {@link ErrorPolicy} used to be accepted by every strategy constructor and then
 * failed every aggregation with a {@code NullPointerException}, even when every judge passed.
 * That failure surfaced only after the judges had run. A null {@link TiePolicy} on
 * {@link MajorityVotingStrategy} failed only on the first tie. Both are configuration errors
 * knowable before any judge runs, so they now fail at construction, naming the parameter.
 * </p>
 *
 * @author Mark Pollack
 * @since 0.17.0
 */
class StrategyConstructionTest {

	private static final List<Judgment> ALL_PASS = List.of(passJudgment(0.8), passJudgment(0.9));

	@TestFactory
	Stream<DynamicTest> everyConstructorTakingAnErrorPolicyRefusesNullNamingTheParameter() {
		Map<String, ThrowingCallable> constructors = new LinkedHashMap<>();
		constructors.put("AllMustPassStrategy(ErrorPolicy)", () -> new AllMustPassStrategy((ErrorPolicy) null));
		constructors.put("AverageVotingStrategy(ErrorPolicy)", () -> new AverageVotingStrategy((ErrorPolicy) null));
		constructors.put("AverageVotingStrategy(double, ErrorPolicy)", () -> new AverageVotingStrategy(0.5, null));
		constructors.put("ConjunctiveStrategy(double, ErrorPolicy)", () -> new ConjunctiveStrategy(0.5, null));
		constructors.put("ConsensusStrategy(ErrorPolicy)", () -> new ConsensusStrategy((ErrorPolicy) null));
		constructors.put("MajorityVotingStrategy(TiePolicy, ErrorPolicy)",
				() -> new MajorityVotingStrategy(TiePolicy.FAIL, null));
		constructors.put("MedianVotingStrategy(ErrorPolicy)", () -> new MedianVotingStrategy((ErrorPolicy) null));
		constructors.put("MedianVotingStrategy(double, ErrorPolicy)", () -> new MedianVotingStrategy(0.5, null));
		constructors.put("WeightedAverageStrategy(ErrorPolicy)",
				() -> new WeightedAverageStrategy((ErrorPolicy) null));
		constructors.put("WeightedAverageStrategy(double, ErrorPolicy)", () -> new WeightedAverageStrategy(0.5, null));

		return constructors.entrySet()
			.stream()
			.map(entry -> DynamicTest.dynamicTest(entry.getKey(),
					() -> assertThatThrownBy(entry.getValue()).isInstanceOf(IllegalArgumentException.class)
						.hasMessage("errorPolicy must not be null")));
	}

	@Test
	void majorityRefusesANullTiePolicyAtConstructionRatherThanAtTheFirstTie() {
		assertThatThrownBy(() -> new MajorityVotingStrategy(null, ErrorPolicy.PROPAGATE))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("tiePolicy must not be null");
	}

	@Test
	void majorityNamesTheFirstNullParameterWhenBothPoliciesAreNull() {
		assertThatThrownBy(() -> new MajorityVotingStrategy(null, null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("tiePolicy must not be null");
	}

	@Test
	void anInvalidThresholdIsStillReportedBeforeANullErrorPolicy() {
		// The threshold was validated first before this change; that order is kept, so a caller
		// passing both an invalid threshold and a null policy sees the same message as before.
		assertThatThrownBy(() -> new AverageVotingStrategy(2.0, null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("threshold");
		assertThatThrownBy(() -> new ConjunctiveStrategy(Double.NaN, null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("threshold");
	}

	@Test
	void noArgumentConstructorsDeclareNonNullDefaultPolicies() {
		List<VotingStrategy> defaults = List.of(new AllMustPassStrategy(), new AverageVotingStrategy(),
				new ConsensusStrategy(), new MajorityVotingStrategy(), new MedianVotingStrategy(),
				new WeightedAverageStrategy(), new AverageVotingStrategy(0.7), new MedianVotingStrategy(0.7),
				new WeightedAverageStrategy(0.7), new ConjunctiveStrategy(0.7));

		for (VotingStrategy strategy : defaults) {
			StrategyDescription description = strategy.describe();
			assertThat(description.errorPolicy()).as(strategy.getName()).isEqualTo(ErrorPolicy.PROPAGATE);
			assertThat(portableValues(description)).as(strategy.getName()).containsEntry("errorPolicy", "propagate");
			assertThatCode(() -> strategy.aggregate(ALL_PASS, Map.of())).as(strategy.getName())
				.doesNotThrowAnyException();
		}
		assertThat(new MajorityVotingStrategy().describe().parameters()).containsEntry("tiePolicy", "FAIL");
	}

	@TestFactory
	Stream<DynamicTest> everyConstructorStillBuildsWithEveryNonNullPolicy() {
		List<DynamicTest> tests = new ArrayList<>();
		for (ErrorPolicy errorPolicy : ErrorPolicy.values()) {
			Map<String, Function<ErrorPolicy, VotingStrategy>> constructors = new LinkedHashMap<>();
			constructors.put("AllMustPassStrategy(ErrorPolicy)", AllMustPassStrategy::new);
			constructors.put("AverageVotingStrategy(ErrorPolicy)", AverageVotingStrategy::new);
			constructors.put("AverageVotingStrategy(double, ErrorPolicy)", p -> new AverageVotingStrategy(0.6, p));
			constructors.put("ConjunctiveStrategy(double, ErrorPolicy)", p -> new ConjunctiveStrategy(0.6, p));
			constructors.put("ConsensusStrategy(ErrorPolicy)", ConsensusStrategy::new);
			constructors.put("MedianVotingStrategy(ErrorPolicy)", MedianVotingStrategy::new);
			constructors.put("MedianVotingStrategy(double, ErrorPolicy)", p -> new MedianVotingStrategy(0.6, p));
			constructors.put("WeightedAverageStrategy(ErrorPolicy)", WeightedAverageStrategy::new);
			constructors.put("WeightedAverageStrategy(double, ErrorPolicy)",
					p -> new WeightedAverageStrategy(0.6, p));
			for (TiePolicy tiePolicy : TiePolicy.values()) {
				constructors.put("MajorityVotingStrategy(" + tiePolicy + ", ErrorPolicy)",
						p -> new MajorityVotingStrategy(tiePolicy, p));
			}
			constructors.forEach((name, constructor) -> tests
				.add(DynamicTest.dynamicTest(name + " with " + errorPolicy, () -> {
					VotingStrategy strategy = constructor.apply(errorPolicy);
					assertThat(strategy.describe().errorPolicy()).isEqualTo(errorPolicy);
					assertThatCode(() -> strategy.aggregate(ALL_PASS, Map.of())).doesNotThrowAnyException();
				})));
		}
		return tests.stream();
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> portableValues(StrategyDescription description) {
		Map<String, Object> parameters = (Map<String, Object>) description.toPortable().get("parameters");
		return (Map<String, Object>) parameters.get("values");
	}

}
