package io.github.markpollack.judge.ai.requirements;

import java.util.List;
import java.util.Map;

import io.github.markpollack.judge.ai.model.JudgeModel;
import io.github.markpollack.judge.ai.model.JudgeModelResponse;
import io.github.markpollack.judge.context.ExecutionStatus;
import io.github.markpollack.judge.context.JudgmentContext;
import io.github.markpollack.judge.jury.AllMustPassStrategy;
import io.github.markpollack.judge.result.Judgment;
import io.github.markpollack.judge.result.JudgmentStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Why the strict rollup lives inside the judge and not in a voting strategy.
 *
 * <p>{@code ABSTAIN} means two different things in this library, and both readings are correct in
 * their own place. A jury reads it as "this judge does not apply, so it casts no vote", and
 * therefore drops it from the eligible population. These judges read it as "a required requirement
 * could not be established", which is not a thing that can be dropped.
 *
 * <p>These cases pin that divergence so it stays a documented design decision rather than becoming
 * a surprise. Reconciling the two — a roster-aware aggregation — would be a deliberate change, and
 * these tests are what would notice it.
 */
class FixedRosterAggregationTests {

	private static final List<EarsCriterion> THREE = List.of(
		new EarsCriterion("UC1-AC1", "first", "When a thing happens, the system shall do the first thing."),
		new EarsCriterion("UC1-AC2", "second", "If a thing happens, then the system shall do the second thing."),
		new EarsCriterion("UC1-AC3", "third", "While a state holds, the system shall do the third thing."));

	@Test
	void anUnestablishedRequirementAbstainsRatherThanPassing() {
		// The judge's own rollup, which is the contract these judges exist to keep.
		assertThat(almostEverythingEstablished().status()).isEqualTo(JudgmentStatus.ABSTAIN);
	}

	@Test
	void aVotingStrategyWouldAbsorbThatAbstentionIntoAPass() {
		// Documented, deliberate, and the reason these judgments must not be routed through a
		// jury that resolves its population first. Two of three requirements established plus a
		// passing judge from elsewhere would report the specification as satisfied.
		Judgment aggregated = new AllMustPassStrategy()
			.aggregate(List.of(almostEverythingEstablished(), somethingElseThatPassed()), Map.of());

		assertThat(aggregated.status())
			.as("the abstention is removed from the eligible population, and the roster is lost with it")
			.isEqualTo(JudgmentStatus.PASS);
	}

	@Test
	void anAbstainingJudgeAloneDoesNotAggregateToPass() {
		// The strategy's own empty-population rule still holds, so the absorption above needs a
		// second passing judge to appear. That is the shape to watch for.
		Judgment aggregated = new AllMustPassStrategy()
			.aggregate(List.of(almostEverythingEstablished()), Map.of());

		assertThat(aggregated.status()).isEqualTo(JudgmentStatus.ABSTAIN);
	}

	@Test
	void aFailedRequirementIsCarriedByAVotingStrategyUnchanged() {
		// The divergence is only about abstention. A requirement the implementation demonstrably
		// does not satisfy binds either way.
		Judgment aggregated = new AllMustPassStrategy()
			.aggregate(List.of(oneRequirementViolated(), somethingElseThatPassed()), Map.of());

		assertThat(aggregated.status()).isEqualTo(JudgmentStatus.FAIL);
	}

	private static Judgment almostEverythingEstablished() {
		return judge("""
			    UC1-AC1: PASS - Foo.java:10 does it
			    UC1-AC2: CANNOT_DETERMINE - nothing here exercises it
			    UC1-AC3: PASS - Baz.java:30 does it
			    """);
	}

	private static Judgment oneRequirementViolated() {
		return judge("""
			    UC1-AC1: PASS - Foo.java:10 does it
			    UC1-AC2: FAIL - Bar.java:20 does the opposite
			    UC1-AC3: PASS - Baz.java:30 does it
			    """);
	}

	private static Judgment somethingElseThatPassed() {
		return Judgment.builder().pass().reasoning("the build succeeded").build();
	}

	private static Judgment judge(String answers) {
		JudgeModel model = request -> new JudgeModelResponse(answers, "stub", null, Map.of());
		return EarsJudge.create("audit", THREE, model).judge(JudgmentContext.builder()
			.goal("audit the requirements")
			.status(ExecutionStatus.SUCCESS)
			.build());
	}

}
