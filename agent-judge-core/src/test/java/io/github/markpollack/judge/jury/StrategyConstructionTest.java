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
import io.github.markpollack.judge.judgment.Judgment;

import static io.github.markpollack.judge.JudgeTestFixtures.passJudgment;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A strategy refuses a null policy when it is built, not when it first aggregates.
 *
 * <p>
 * A null {@link ErrorHandling} used to be accepted by every strategy constructor and then
 * failed every aggregation with a {@code NullPointerException}, even when every judge
 * passed. That failure surfaced only after the judges had run. A null
 * {@link TieBreakRule} on {@link MajorityVotingStrategy} failed only on the first tie.
 * Both are configuration errors knowable before any judge runs, so they now fail at
 * construction, naming the parameter.
 * </p>
 *
 * @author Mark Pollack
 * @since 0.17.0
 */
class StrategyConstructionTest {

	private static final List<Judgment> ALL_PASS = List.of(passJudgment(0.8), passJudgment(0.9));

	@TestFactory
	Stream<DynamicTest> everyConstructorTakingAnErrorHandlingRefusesNullNamingTheParameter() {
		Map<String, ThrowingCallable> constructors = new LinkedHashMap<>();
		constructors.put("AllMustPassStrategy(ErrorHandling)", () -> new AllMustPassStrategy((ErrorHandling) null));
		constructors.put("AverageVotingStrategy(ErrorHandling)", () -> new AverageVotingStrategy((ErrorHandling) null));
		constructors.put("AverageVotingStrategy(double, ErrorHandling)", () -> new AverageVotingStrategy(0.5, null));
		constructors.put("ConjunctiveStrategy(double, ErrorHandling)", () -> new ConjunctiveStrategy(0.5, null));
		constructors.put("ConsensusStrategy(ErrorHandling)", () -> new ConsensusStrategy((ErrorHandling) null));
		constructors.put("MajorityVotingStrategy(TieBreakRule, ErrorHandling)",
				() -> new MajorityVotingStrategy(TieBreakRule.FAIL, null));
		constructors.put("MedianVotingStrategy(ErrorHandling)", () -> new MedianVotingStrategy((ErrorHandling) null));
		constructors.put("MedianVotingStrategy(double, ErrorHandling)", () -> new MedianVotingStrategy(0.5, null));
		constructors.put("WeightedAverageStrategy(ErrorHandling)",
				() -> new WeightedAverageStrategy((ErrorHandling) null));
		constructors.put("WeightedAverageStrategy(double, ErrorHandling)",
				() -> new WeightedAverageStrategy(0.5, null));

		return constructors.entrySet()
			.stream()
			.map(entry -> DynamicTest.dynamicTest(entry.getKey(),
					() -> assertThatThrownBy(entry.getValue()).isInstanceOf(IllegalArgumentException.class)
						.hasMessage("errorPolicy must not be null")));
	}

	@Test
	void majorityRefusesANullTieBreakRuleAtConstructionRatherThanAtTheFirstTie() {
		assertThatThrownBy(() -> new MajorityVotingStrategy(null, ErrorHandling.PROPAGATE))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("tiePolicy must not be null");
	}

	@Test
	void majorityNamesTheFirstNullParameterWhenBothPoliciesAreNull() {
		assertThatThrownBy(() -> new MajorityVotingStrategy(null, null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("tiePolicy must not be null");
	}

	@Test
	void anInvalidThresholdIsStillReportedBeforeANullErrorHandling() {
		// The threshold was validated first before this change; that order is kept, so a
		// caller
		// passing both an invalid threshold and a null policy sees the same message as
		// before.
		assertThatThrownBy(() -> new AverageVotingStrategy(2.0, null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("threshold");
		assertThatThrownBy(() -> new ConjunctiveStrategy(Double.NaN, null)).isInstanceOf(IllegalArgumentException.class)
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
			assertThat(description.errorHandling()).as(strategy.getName()).isEqualTo(ErrorHandling.PROPAGATE);
			assertThat(portableValues(description)).as(strategy.getName()).containsEntry("errorPolicy", "propagate");
			assertThatCode(() -> strategy.aggregate(ALL_PASS, Map.of())).as(strategy.getName())
				.doesNotThrowAnyException();
		}
		assertThat(new MajorityVotingStrategy().describe().parameters()).containsEntry("tiePolicy", "FAIL");
	}

	@TestFactory
	Stream<DynamicTest> everyConstructorStillBuildsWithEveryNonNullPolicy() {
		List<DynamicTest> tests = new ArrayList<>();
		for (ErrorHandling errorPolicy : ErrorHandling.values()) {
			Map<String, Function<ErrorHandling, VotingStrategy>> constructors = new LinkedHashMap<>();
			constructors.put("AllMustPassStrategy(ErrorHandling)", AllMustPassStrategy::new);
			constructors.put("AverageVotingStrategy(ErrorHandling)", AverageVotingStrategy::new);
			constructors.put("AverageVotingStrategy(double, ErrorHandling)", p -> new AverageVotingStrategy(0.6, p));
			constructors.put("ConjunctiveStrategy(double, ErrorHandling)", p -> new ConjunctiveStrategy(0.6, p));
			constructors.put("ConsensusStrategy(ErrorHandling)", ConsensusStrategy::new);
			constructors.put("MedianVotingStrategy(ErrorHandling)", MedianVotingStrategy::new);
			constructors.put("MedianVotingStrategy(double, ErrorHandling)", p -> new MedianVotingStrategy(0.6, p));
			constructors.put("WeightedAverageStrategy(ErrorHandling)", WeightedAverageStrategy::new);
			constructors.put("WeightedAverageStrategy(double, ErrorHandling)",
					p -> new WeightedAverageStrategy(0.6, p));
			for (TieBreakRule tiePolicy : TieBreakRule.values()) {
				constructors.put("MajorityVotingStrategy(" + tiePolicy + ", ErrorHandling)",
						p -> new MajorityVotingStrategy(tiePolicy, p));
			}
			constructors
				.forEach((name, constructor) -> tests.add(DynamicTest.dynamicTest(name + " with " + errorPolicy, () -> {
					VotingStrategy strategy = constructor.apply(errorPolicy);
					assertThat(strategy.describe().errorHandling()).isEqualTo(errorPolicy);
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
