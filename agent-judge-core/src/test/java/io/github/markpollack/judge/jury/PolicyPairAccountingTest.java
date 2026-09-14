/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import io.github.markpollack.judge.result.Judgment;
import io.github.markpollack.judge.result.JudgmentReasonCode;
import io.github.markpollack.judge.result.JudgmentStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Part D: the complete population scan, pinned cell by cell.
 *
 * <p>
 * Two policies, four error values and three exclusion values make twelve combinations, and the
 * interesting ones are not the diagonal. A jury that excludes a criterion and a jury that
 * refuses to is the difference between a rate over what applied and a rate over what was asked,
 * and both look like a number. Every cell below is pinned with its aggregate <em>and</em> its
 * counters, because the counters are what a reader derives the rate from.
 * </p>
 *
 * <p>
 * The counts are taken on the submitted originals, before any policy runs. That ordering is the
 * claim being pinned: a count taken after filtering can only describe the survivors.
 * </p>
 */
@DisplayName("The policy-pair table")
class PolicyPairAccountingTest {

	private static final Judgment PASS = Judgment.pass("qualified");

	private static final Judgment FAIL = Judgment.fail("did not qualify");

	private static final Judgment EXCLUDED = Judgment.notApplicable("the repository contains no Java source");

	private static final Judgment JUDGE_ERROR = Judgment.error("the index was unreachable");

	/** One of each status, in a fixed order, so every cell reduces over the same population. */
	private static final List<Judgment> MIXED = List.of(PASS, FAIL, EXCLUDED, JUDGE_ERROR);

	private static VotingStrategy strategy(ErrorPolicy errorPolicy, NotApplicablePolicy notApplicablePolicy) {
		return new ConsensusStrategy(errorPolicy, notApplicablePolicy);
	}

	private static Judgment aggregate(ErrorPolicy errorPolicy, NotApplicablePolicy notApplicablePolicy,
			List<Judgment> judgments) {
		return strategy(errorPolicy, notApplicablePolicy).aggregate(judgments, Map.of());
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> evidenceOf(Judgment judgment) {
		Object block = judgment.metadata().get(Judgment.AGGREGATION_KEY);
		assertThat(block).as("the aggregate carries an evidence block").isInstanceOf(Map.class);
		return (Map<String, Object>) block;
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> originOf(Judgment judgment) {
		return (Map<String, Object>) evidenceOf(judgment).get(AggregationEvidence.ERROR_CODE_COUNTS);
	}

	private static List<ErrorPolicy> errorPolicies() {
		return List.of(ErrorPolicy.values());
	}

	@Nested
	@DisplayName("REFUSE takes precedence over every error policy")
	class Refuse {

		@ParameterizedTest
		@MethodSource("io.github.markpollack.judge.jury.PolicyPairAccountingTest#errorPolicies")
		@DisplayName("a single exclusion decides the aggregate, whatever the error policy")
		void refuseWins(ErrorPolicy errorPolicy) {
			Judgment aggregate = aggregate(errorPolicy, NotApplicablePolicy.REFUSE, MIXED);

			assertThat(aggregate.status()).isEqualTo(JudgmentStatus.ERROR);
			assertThat(aggregate.reasonCode()).isEqualTo(JudgmentReasonCode.NOT_APPLICABLE_REFUSED);
			assertThat(aggregate.reasoning()).contains("not applicable").contains("refuse");
		}

		@ParameterizedTest
		@MethodSource("io.github.markpollack.judge.jury.PolicyPairAccountingTest#errorPolicies")
		@DisplayName("a policy exit reduces nothing, so every treatment counter is zero")
		void policyExitPerformsNoTreatment(ErrorPolicy errorPolicy) {
			Map<String, Object> evidence = evidenceOf(aggregate(errorPolicy, NotApplicablePolicy.REFUSE, MIXED));

			assertThat(evidence).containsEntry(AggregationEvidence.INPUT_COUNT, 4)
				.containsEntry(AggregationEvidence.ELIGIBLE_COUNT, 0)
				.containsEntry(AggregationEvidence.NOT_APPLICABLE_COUNT, 1)
				.containsEntry(AggregationEvidence.ERROR_COUNT, 1)
				.containsEntry(AggregationEvidence.EXPLICIT_ABSTAIN_COUNT, 0)
				.containsEntry(AggregationEvidence.IGNORED_ERROR_COUNT, 0)
				.containsEntry(AggregationEvidence.ERRORS_TREATED_AS_ABSTAIN_COUNT, 0)
				.containsEntry(AggregationEvidence.ERRORS_TREATED_AS_FAIL_COUNT, 0)
				.containsEntry(AggregationEvidence.NOT_APPLICABLE_TREATED_AS_FAIL_COUNT, 0);
		}

		@Test
		@DisplayName("with no exclusion submitted, REFUSE changes nothing")
		void refuseIsInertWithoutAnExclusion() {
			Judgment aggregate = aggregate(ErrorPolicy.IGNORE, NotApplicablePolicy.REFUSE, List.of(PASS, PASS));

			assertThat(aggregate.status()).isEqualTo(JudgmentStatus.PASS);
		}

	}

	@Nested
	@DisplayName("PROPAGATE, once exclusions are allowed")
	class Propagate {

		@ParameterizedTest
		@EnumSource(value = NotApplicablePolicy.class, names = { "EXCLUDE", "TREAT_AS_FAIL" })
		@DisplayName("an errored input propagates, carrying the terminal causes it stands for")
		void propagatesWithOrigin(NotApplicablePolicy notApplicablePolicy) {
			Judgment aggregate = aggregate(ErrorPolicy.PROPAGATE, notApplicablePolicy, MIXED);

			assertThat(aggregate.status()).isEqualTo(JudgmentStatus.ERROR);
			assertThat(aggregate.reasonCode()).isEqualTo(JudgmentReasonCode.ERRORS_PROPAGATED);
			assertThat(originOf(aggregate)).containsExactly(Map.entry("judge_reported", 1));
		}

	}

	@Nested
	@DisplayName("EXCLUDE removes the exclusion and the error policy governs the rest")
	class Exclude {

		@Test
		@DisplayName("TREAT_AS_FAIL: the exclusion leaves, the judge error becomes a failing contribution")
		void treatErrorsAsFail() {
			// A gate rather than consensus, so the failing contribution is visible in the
			// aggregate rather than absorbed into a "the judges disagree" abstention.
			Judgment aggregate = new AllMustPassStrategy(ErrorPolicy.TREAT_AS_FAIL, NotApplicablePolicy.EXCLUDE)
				.aggregate(MIXED, Map.of());
			Map<String, Object> evidence = evidenceOf(aggregate);

			assertThat(aggregate.status()).isEqualTo(JudgmentStatus.FAIL);
			assertThat(evidence).containsEntry(AggregationEvidence.FAIL_COUNT, 2);
			assertThat(evidence).containsEntry(AggregationEvidence.ELIGIBLE_COUNT, 3)
				.containsEntry(AggregationEvidence.NOT_APPLICABLE_COUNT, 1)
				.containsEntry(AggregationEvidence.ERRORS_TREATED_AS_FAIL_COUNT, 1)
				.containsEntry(AggregationEvidence.NOT_APPLICABLE_TREATED_AS_FAIL_COUNT, 0);
		}

		@Test
		@DisplayName("TREAT_AS_ABSTAIN: the exclusion leaves, the error casts no vote")
		void treatErrorsAsAbstain() {
			Judgment aggregate = aggregate(ErrorPolicy.TREAT_AS_ABSTAIN, NotApplicablePolicy.EXCLUDE, MIXED);
			Map<String, Object> evidence = evidenceOf(aggregate);

			assertThat(aggregate.status()).isEqualTo(JudgmentStatus.ABSTAIN);
			assertThat(evidence).containsEntry(AggregationEvidence.ELIGIBLE_COUNT, 2)
				.containsEntry(AggregationEvidence.ERRORS_TREATED_AS_ABSTAIN_COUNT, 1)
				.containsEntry(AggregationEvidence.IGNORED_ERROR_COUNT, 0);
		}

		@Test
		@DisplayName("IGNORE: the exclusion leaves, the error is removed with its weight")
		void ignoreErrors() {
			Judgment aggregate = aggregate(ErrorPolicy.IGNORE, NotApplicablePolicy.EXCLUDE, MIXED);
			Map<String, Object> evidence = evidenceOf(aggregate);

			assertThat(aggregate.status()).isEqualTo(JudgmentStatus.ABSTAIN);
			assertThat(evidence).containsEntry(AggregationEvidence.ELIGIBLE_COUNT, 2)
				.containsEntry(AggregationEvidence.IGNORED_ERROR_COUNT, 1)
				.containsEntry(AggregationEvidence.ERRORS_TREATED_AS_ABSTAIN_COUNT, 0);
		}

		@Test
		@DisplayName("TREAT_AS_ABSTAIN with nothing left is an abstention, not an exclusion")
		void emptyUnderTreatAsAbstain() {
			Judgment aggregate = aggregate(ErrorPolicy.TREAT_AS_ABSTAIN, NotApplicablePolicy.EXCLUDE,
					List.of(EXCLUDED, JUDGE_ERROR));

			assertThat(aggregate.status()).isEqualTo(JudgmentStatus.ABSTAIN);
			assertThat(evidenceOf(aggregate)).containsEntry(AggregationEvidence.ELIGIBLE_COUNT, 0)
				.containsEntry(AggregationEvidence.NOT_APPLICABLE_COUNT, 1)
				.containsEntry(AggregationEvidence.ERRORS_TREATED_AS_ABSTAIN_COUNT, 1);
		}

		@Test
		@DisplayName("IGNORE with nothing left is an abstention, not an exclusion")
		void emptyUnderIgnore() {
			Judgment aggregate = aggregate(ErrorPolicy.IGNORE, NotApplicablePolicy.EXCLUDE,
					List.of(EXCLUDED, JUDGE_ERROR));

			assertThat(aggregate.status()).isEqualTo(JudgmentStatus.ABSTAIN);
			assertThat(evidenceOf(aggregate)).containsEntry(AggregationEvidence.ELIGIBLE_COUNT, 0)
				.containsEntry(AggregationEvidence.IGNORED_ERROR_COUNT, 1);
		}

	}

	@Nested
	@DisplayName("TREAT_AS_FAIL makes the exclusion a failing contribution")
	class ExclusionsAsFailures {

		@ParameterizedTest
		@EnumSource(value = ErrorPolicy.class, names = { "TREAT_AS_FAIL", "TREAT_AS_ABSTAIN", "IGNORE" })
		@DisplayName("the exclusion is counted both as submitted and as treated")
		void countedTwiceOverDifferentQuestions(ErrorPolicy errorPolicy) {
			Map<String, Object> evidence = evidenceOf(
					aggregate(errorPolicy, NotApplicablePolicy.TREAT_AS_FAIL, MIXED));

			assertThat(evidence).containsEntry(AggregationEvidence.NOT_APPLICABLE_COUNT, 1)
				.containsEntry(AggregationEvidence.NOT_APPLICABLE_TREATED_AS_FAIL_COUNT, 1);
		}

		@Test
		@DisplayName("the original judgment is never given a score by the treatment")
		void originalJudgmentIsUntouched() {
			aggregate(ErrorPolicy.IGNORE, NotApplicablePolicy.TREAT_AS_FAIL, MIXED);

			assertThat(EXCLUDED.score()).isNull();
			assertThat(EXCLUDED.status()).isEqualTo(JudgmentStatus.NOT_APPLICABLE);
		}

		@Test
		@DisplayName("the failing contribution keeps the exclusion's configured weight")
		void weightIsPreserved() {
			// Position 0 is excluded and weighted 3.0; position 1 passes and is weighted 1.0.
			// Treating the exclusion as a failure at its own weight gives 1/4, not 1/2.
			Judgment aggregate = new WeightedAverageStrategy(0.5, ErrorPolicy.PROPAGATE,
					NotApplicablePolicy.TREAT_AS_FAIL)
				.aggregate(List.of(EXCLUDED, PASS), Map.of("0", 3.0, "1", 1.0));

			assertThat(aggregate.score()).isEqualTo(0.25);
			assertThat(aggregate.status()).isEqualTo(JudgmentStatus.FAIL);
		}

	}

	@Nested
	@DisplayName("An all-excluded population")
	class AllExcluded {

		@Test
		@DisplayName("under EXCLUDE it is itself not applicable, never an abstention")
		void allExcludedIsNotApplicable() {
			Judgment aggregate = aggregate(ErrorPolicy.PROPAGATE, NotApplicablePolicy.EXCLUDE,
					List.of(EXCLUDED, EXCLUDED));

			assertThat(aggregate.status()).isEqualTo(JudgmentStatus.NOT_APPLICABLE);
			assertThat(evidenceOf(aggregate)).containsEntry(AggregationEvidence.NOT_APPLICABLE_COUNT, 2)
				.containsEntry(AggregationEvidence.ELIGIBLE_COUNT, 0);
		}

		@Test
		@DisplayName("one abstention among the exclusions makes it an abstention: the jury did have a question")
		void oneAbstentionIsEnough() {
			Judgment aggregate = aggregate(ErrorPolicy.PROPAGATE, NotApplicablePolicy.EXCLUDE,
					List.of(EXCLUDED, Judgment.abstain("could not decide")));

			assertThat(aggregate.status()).isEqualTo(JudgmentStatus.ABSTAIN);
		}

		@Test
		@DisplayName("under TREAT_AS_FAIL it fails rather than disappearing")
		void allExcludedUnderTreatAsFail() {
			Judgment aggregate = aggregate(ErrorPolicy.PROPAGATE, NotApplicablePolicy.TREAT_AS_FAIL,
					List.of(EXCLUDED, EXCLUDED));

			assertThat(aggregate.status()).isEqualTo(JudgmentStatus.FAIL);
			assertThat(evidenceOf(aggregate)).containsEntry(AggregationEvidence.NOT_APPLICABLE_TREATED_AS_FAIL_COUNT, 2);
		}

	}

	@Nested
	@DisplayName("Machinery-origin errors are never scored")
	class MachineryOrigin {

		private static final Judgment MACHINERY_ERROR = Judgment.error(JudgmentReasonCode.AGGREGATION_FAILED,
				"the strategy threw");

		@Test
		@DisplayName("under TREAT_AS_FAIL they propagate instead of becoming a failing contribution")
		void treatAsFailDoesNotApplyToMachinery() {
			Judgment aggregate = aggregate(ErrorPolicy.TREAT_AS_FAIL, NotApplicablePolicy.EXCLUDE,
					List.of(FAIL, MACHINERY_ERROR));

			assertThat(aggregate.status()).isEqualTo(JudgmentStatus.ERROR);
			assertThat(aggregate.reasonCode()).isEqualTo(JudgmentReasonCode.ERRORS_PROPAGATED);
			assertThat(originOf(aggregate)).containsExactly(Map.entry("aggregation_failed", 1));
		}

		@Test
		@DisplayName("the reasoning names machinery, and does not claim propagate was configured")
		void reasoningIsHonestAboutTheConfiguredPolicy() {
			Judgment aggregate = aggregate(ErrorPolicy.TREAT_AS_FAIL, NotApplicablePolicy.EXCLUDE,
					List.of(FAIL, MACHINERY_ERROR));

			assertThat(aggregate.reasoning()).contains("machinery").doesNotContain("the error policy is propagate");
			assertThat(evidenceOf(aggregate)).containsEntry(AggregationEvidence.ERROR_POLICY,
					ErrorPolicy.TREAT_AS_FAIL.token());
		}

		@Test
		@DisplayName("a wrapper carrying a machinery cause is machinery-origin too")
		void wrappersAreFlattenedForOrigin() {
			Judgment wrapped = Judgment.propagatedError(Map.of(JudgmentReasonCode.STAGE_FAILED, 1),
					"a member stage failed and the error policy is propagate");

			Judgment aggregate = aggregate(ErrorPolicy.TREAT_AS_FAIL, NotApplicablePolicy.EXCLUDE,
					List.of(FAIL, wrapped));

			assertThat(aggregate.reasonCode()).isEqualTo(JudgmentReasonCode.ERRORS_PROPAGATED);
			assertThat(originOf(aggregate)).as("the wrapper contributes its causes, never itself")
				.containsExactly(Map.entry("stage_failed", 1));
		}

		@Test
		@DisplayName("under TREAT_AS_ABSTAIN and IGNORE the configured policy applies as written")
		void otherPoliciesApplyAsConfigured() {
			assertThat(aggregate(ErrorPolicy.TREAT_AS_ABSTAIN, NotApplicablePolicy.EXCLUDE,
					List.of(FAIL, MACHINERY_ERROR)).status())
				.isEqualTo(JudgmentStatus.FAIL);
			assertThat(aggregate(ErrorPolicy.IGNORE, NotApplicablePolicy.EXCLUDE, List.of(FAIL, MACHINERY_ERROR))
				.status()).isEqualTo(JudgmentStatus.FAIL);
		}

		@Test
		@DisplayName("a judge-origin error still becomes a failing contribution under TREAT_AS_FAIL")
		void judgeOriginIsStillGoverned() {
			Judgment aggregate = new AllMustPassStrategy(ErrorPolicy.TREAT_AS_FAIL, NotApplicablePolicy.EXCLUDE)
				.aggregate(List.of(PASS, JUDGE_ERROR), Map.of());

			assertThat(aggregate.status()).isEqualTo(JudgmentStatus.FAIL);
			assertThat(evidenceOf(aggregate)).containsEntry(AggregationEvidence.ERRORS_TREATED_AS_FAIL_COUNT, 1);
		}

	}

	@Nested
	@DisplayName("Flattened origin counts")
	class OriginCounts {

		@Test
		@DisplayName("a wrapper's causes may outnumber the errored inputs")
		void originMayExceedErrorCount() {
			Judgment wrapped = Judgment.propagatedError(
					Map.of(JudgmentReasonCode.JUDGE_FAILED, 2, JudgmentReasonCode.JUDGE_REPORTED, 1),
					"3 of 3 judgments errored and the error policy is propagate");

			Judgment aggregate = aggregate(ErrorPolicy.PROPAGATE, NotApplicablePolicy.EXCLUDE, List.of(PASS, wrapped));

			assertThat(evidenceOf(aggregate)).containsEntry(AggregationEvidence.ERROR_COUNT, 1);
			assertThat(originOf(aggregate)).containsOnly(Map.entry("judge_failed", 2),
					Map.entry("judge_reported", 1));
		}

		@Test
		@DisplayName("the block is universal: it is present, and empty, when nothing errored")
		void alwaysPresent() {
			Judgment aggregate = aggregate(ErrorPolicy.PROPAGATE, NotApplicablePolicy.EXCLUDE, List.of(PASS, PASS));

			assertThat(evidenceOf(aggregate)).containsKey(AggregationEvidence.ERROR_CODE_COUNTS);
			assertThat(originOf(aggregate)).isEmpty();
		}

	}

	@Test
	@DisplayName("every built-in strategy applies both policies and writes the universal keys")
	void everyStrategyAccountsTheSameWay() {
		List<VotingStrategy> strategies = new ArrayList<>(List.of(
				new ConsensusStrategy(ErrorPolicy.IGNORE, NotApplicablePolicy.EXCLUDE),
				new MajorityVotingStrategy(TiePolicy.FAIL, ErrorPolicy.IGNORE, NotApplicablePolicy.EXCLUDE),
				new AllMustPassStrategy(ErrorPolicy.IGNORE, NotApplicablePolicy.EXCLUDE),
				new AverageVotingStrategy(0.5, ErrorPolicy.IGNORE, NotApplicablePolicy.EXCLUDE),
				new MedianVotingStrategy(0.5, ErrorPolicy.IGNORE, NotApplicablePolicy.EXCLUDE),
				new WeightedAverageStrategy(0.5, ErrorPolicy.IGNORE, NotApplicablePolicy.EXCLUDE),
				new ConjunctiveStrategy(0.5, ErrorPolicy.IGNORE, NotApplicablePolicy.EXCLUDE)));

		for (VotingStrategy strategy : strategies) {
			assertThat(strategy.notApplicablePolicy()).as("%s declares its policy", strategy.getName())
				.isEqualTo(NotApplicablePolicy.EXCLUDE);

			Map<String, Object> evidence = evidenceOf(strategy.aggregate(MIXED, Map.of()));
			assertThat(evidence).as("%s writes the universal keys", strategy.getName())
				.containsEntry(AggregationEvidence.NOT_APPLICABLE_POLICY, NotApplicablePolicy.EXCLUDE.token())
				.containsEntry(AggregationEvidence.NOT_APPLICABLE_COUNT, 1)
				.containsEntry(AggregationEvidence.NOT_APPLICABLE_TREATED_AS_FAIL_COUNT, 0)
				.containsKey(AggregationEvidence.ERROR_CODE_COUNTS);
		}
	}

	@Test
	@DisplayName("a strategy that says nothing refuses exclusions")
	void theDefaultIsRefuse() {
		VotingStrategy silent = new VotingStrategy() {
			@Override
			public Judgment aggregate(List<Judgment> judgments, Map<String, Double> weights) {
				return Judgment.pass("ok");
			}

			@Override
			public String getName() {
				return "silent";
			}
		};

		assertThat(silent.notApplicablePolicy()).isEqualTo(NotApplicablePolicy.REFUSE);
	}

}
