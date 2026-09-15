/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.markpollack.judge.Judges;
import io.github.markpollack.judge.context.JudgmentContext;
import io.github.markpollack.judge.result.Judgment;
import io.github.markpollack.judge.result.JudgmentReasonCode;

import static io.github.markpollack.judge.jury.ContainmentTest.returning;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A refused exclusion is explained, except where a later selected tier already explains the
 * outcome.
 *
 * <p>
 * When a child jury returns {@code NOT_APPLICABLE} without having declared that it may, the
 * parent refuses the exclusion. At a sole final cascade tier, or at a meta-jury member, the only
 * record that this is what happened was the disposition enum on the attempt: the root's own
 * prose said the stage "did not produce a determination", which is equally true of a stage that
 * threw, one whose reduction broke, and one that tried to exclude the subject. A reader holding
 * the root cannot tell those apart, and the child's verdict does not help — it says the criterion
 * did not apply, which is precisely the claim the parent rejected.
 * </p>
 *
 * <p>
 * R-E's enum-only allowance is scoped: it covers a boundary rejection followed by a later
 * selected tier, where the root's reasoning belongs to that tier and a parent note would displace
 * it. Outside that scope §7.3's free-text requirement stands, so the parent says which stage
 * tried to exclude and that the exclusion was not honoured.
 * </p>
 */
@DisplayName("A refused exclusion is explained")
class RejectedExclusionExplanationTest {

	private static final JudgmentContext CONTEXT = JudgmentContext.builder().goal("explain the refusal").build();

	private static final Judgment FAILING = Judgment.fail("a requirement was not met");

	private static final Judgment PASSING = Judgment.pass("every requirement was met");

	/** An opaque child that excludes the subject while declaring no capability to do so. */
	private static Jury excluding(Judgment... individuals) {
		return returning(
				Verdict.of(Judgment.notApplicable("the change set contains no Java sources"), byName(individuals)));
	}

	private static Map<String, Judgment> byName(Judgment... individuals) {
		Map<String, Judgment> map = new java.util.LinkedHashMap<>();
		for (int index = 0; index < individuals.length; index++) {
			map.put("judge-" + (index + 1), individuals[index]);
		}
		return map;
	}

	private static Jury passing() {
		return SimpleJury.builder()
			.judge(Judges.named(context -> PASSING, "backstop"))
			.votingStrategy(new AllMustPassStrategy(ErrorPolicy.TREAT_AS_ABSTAIN))
			.build();
	}

	@Nested
	@DisplayName("Where no later tier explains the outcome")
	class ExplanationRequired {

		@Test
		@DisplayName("a sole final tier's refused exclusion is named in the root reasoning")
		void aSoleFinalTierIsExplained() {
			Verdict verdict = CascadedJury.builder()
				.tier("rubric", excluding(PASSING), TierPolicy.FINAL_TIER)
				.build()
				.vote(CONTEXT);

			assertThat(verdict.aggregated().reasonCode()).isEqualTo(JudgmentReasonCode.NO_TIER_DECIDED);
			assertThat(verdict.aggregated().reasoning()).contains("rubric")
				.contains("NOT_APPLICABLE")
				.containsIgnoringCase("not honoured");
			assertThat(verdict.compositeAttempts().get(0).dispositionReason())
				.isEqualTo(DispositionReason.UNDECLARED_NOT_APPLICABLE);
		}

		@Test
		@DisplayName("a refused exclusion at an earlier tier is named when nothing later decided")
		void anEarlierTierIsExplainedWhenNothingDecided() {
			Verdict verdict = CascadedJury.builder()
				.tier("rubric", excluding(PASSING), TierPolicy.REJECT_ON_ANY_FAIL)
				.tier("semantic", ContainmentTest.throwing(new IllegalStateException("the backend was unreachable")),
						TierPolicy.FINAL_TIER)
				.build()
				.vote(CONTEXT);

			assertThat(verdict.aggregated().reasonCode()).isEqualTo(JudgmentReasonCode.NO_TIER_DECIDED);
			assertThat(verdict.aggregated().reasoning()).contains("rubric").contains("NOT_APPLICABLE");
		}

		@Test
		@DisplayName("a meta member's refused exclusion is named in the root reasoning")
		void aMetaMemberIsExplained() {
			Verdict verdict = Juries
				.meta(new AllMustPassStrategy(ErrorPolicy.TREAT_AS_ABSTAIN), new NamedJury("healthy", passing()),
						new NamedJury("rubric", excluding(PASSING)))
				.vote(CONTEXT);

			assertThat(verdict.aggregated().reasonCode()).isEqualTo(JudgmentReasonCode.STAGE_FAILED);
			assertThat(verdict.aggregated().reasoning()).contains("rubric")
				.contains("NOT_APPLICABLE")
				.containsIgnoringCase("not honoured");
			assertThat(verdict.individualByName()).as("the healthy member's work is still kept")
				.containsOnlyKeys("healthy");
		}

	}

	@Nested
	@DisplayName("Where the scoped allowance applies, nothing changes")
	class UnchangedCases {

		@Test
		@DisplayName("R-E: a later selected tier keeps its own reasoning as the root")
		void aLaterSelectedTierKeepsItsReasoning() {
			Verdict verdict = CascadedJury.builder()
				.tier("rubric", excluding(PASSING), TierPolicy.REJECT_ON_ANY_FAIL)
				.tier("semantic", passing(), TierPolicy.FINAL_TIER)
				.build()
				.vote(CONTEXT);

			assertThat(verdict.decision().tier()).isEqualTo("semantic");
			assertThat(verdict.aggregated().reasoning()).isEqualTo("All 1 applicable requirement(s) passed");
			assertThat(verdict.compositeAttempts().get(0).dispositionReason())
				.as("the disposition is still countable")
				.isEqualTo(DispositionReason.UNDECLARED_NOT_APPLICABLE);
		}

		@Test
		@DisplayName("D1: the parent-built stage_failed root keeps its own text")
		void theD1RootIsUnchanged() {
			Verdict verdict = CascadedJury.builder()
				.tier("rubric", excluding(FAILING), TierPolicy.REJECT_ON_ANY_FAIL)
				.tier("semantic", passing(), TierPolicy.FINAL_TIER)
				.build()
				.vote(CONTEXT);

			assertThat(verdict.decision().basis()).isEqualTo(DecisionBasis.INDIVIDUAL_REJECTION);
			assertThat(verdict.aggregated().reasonCode()).isEqualTo(JudgmentReasonCode.STAGE_FAILED);
			assertThat(verdict.aggregated().reasoning())
				.isEqualTo("Tier 'rubric' returned NOT_APPLICABLE without declaring that its aggregate may be "
						+ "excluded, so its reduction is a stage failure; the cascade stopped because a genuine "
						+ "individual FAIL in that tier established the rejection.");
		}

		@Test
		@DisplayName("a tier that threw still reports exactly what it reported before")
		void aThrownTierIsUnchanged() {
			Verdict verdict = CascadedJury.builder()
				.tier("rubric", ContainmentTest.throwing(new IllegalStateException("the backend was unreachable")),
						TierPolicy.FINAL_TIER)
				.build()
				.vote(CONTEXT);

			assertThat(verdict.aggregated().reasoning()).isEqualTo("The final cascade tier failed to execute.");
		}

		@Test
		@DisplayName("a member that threw still reports exactly what it reported before")
		void aThrownMemberIsUnchanged() {
			Verdict verdict = Juries
				.meta(new AllMustPassStrategy(ErrorPolicy.TREAT_AS_ABSTAIN), new NamedJury("healthy", passing()),
						new NamedJury("broken",
								ContainmentTest.throwing(new IllegalStateException("the backend was unreachable"))))
				.vote(CONTEXT);

			assertThat(verdict.aggregated().reasoning()).isEqualTo(
					"One or more jury members did not produce a usable determination, so this jury reduced nothing.");
		}

	}

}
