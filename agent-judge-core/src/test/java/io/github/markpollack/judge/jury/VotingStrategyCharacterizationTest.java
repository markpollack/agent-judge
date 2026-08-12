/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury;

import java.util.List;
import java.util.Map;

import io.github.markpollack.judge.result.Judgment;
import io.github.markpollack.judge.result.JudgmentStatus;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Aggregation semantics of every voting strategy, recorded across the normalized-judgment
 * migration.
 *
 * <p>
 * This file began as a pre-migration characterization of the old model and is now the
 * record of what the migration preserved and what it deliberately changed. Every case
 * carries a marker:
 * </p>
 * <ul>
 * <li>{@code PRESERVED} — behavior that held before the migration and still holds. Marked
 * {@code INTENDED} pre-migration.</li>
 * <li>{@code CHANGED} — behavior the migration deliberately changed. Each states what it
 * used to do and why that was wrong. Marked {@code DEFECT} pre-migration, plus the single
 * {@code BASELINE} case covering Majority's default error policy.</li>
 * </ul>
 *
 * @author Mark Pollack
 */
@DisplayName("Voting strategy semantics")
class VotingStrategyCharacterizationTest {

	private static Judgment numeric(double normalized, JudgmentStatus status) {
		Judgment.FindingBuilder builder = switch (status) {
			case PASS -> Judgment.builder().pass();
			case FAIL -> Judgment.builder().fail();
			case ABSTAIN, ERROR -> throw new IllegalArgumentException("A numeric judgment requires PASS or FAIL");
		};
		return builder.score(normalized).reasoning("numeric " + normalized).build();
	}

	private static Judgment booleanJudgment(boolean pass) {
		return (pass ? Judgment.builder().pass() : Judgment.builder().fail()).reasoning("boolean " + pass).build();
	}

	private static Judgment labelled(String label, JudgmentStatus status) {
		return switch (status) {
			case PASS -> Judgment.builder().pass().label(label).reasoning("classified " + label).build();
			case FAIL -> Judgment.builder().fail().label(label).reasoning("classified " + label).build();
			case ABSTAIN -> Judgment.builder().abstain().reasoning("classified " + label).label(label).build();
			case ERROR -> throw new IllegalArgumentException("ERROR cannot carry a classification label");
		};
	}

	private static Judgment statusOnly(JudgmentStatus status) {
		return switch (status) {
			case PASS -> Judgment.builder().pass().reasoning("status only " + status).build();
			case FAIL -> Judgment.builder().fail().reasoning("status only " + status).build();
			case ABSTAIN -> Judgment.builder().abstain().reasoning("status only " + status).build();
			case ERROR -> Judgment.builder().error().reasoning("status only " + status).build();
		};
	}

	private static Offset<Double> within() {
		return Offset.offset(0.0001);
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> evidence(Judgment judgment) {
		return (Map<String, Object>) judgment.metadata().get(Judgment.AGGREGATION_KEY);
	}

	@Nested
	@DisplayName("Judgment factories")
	class Factories {

		@Test
		@DisplayName("CHANGED: abstain() carries no score (was BooleanScore(false), a real 0.0)")
		void abstainCarriesNoScore() {
			Judgment judgment = Judgment.abstain("no evidence");

			assertThat(judgment.status()).isEqualTo(JudgmentStatus.ABSTAIN);
			assertThat(judgment.score()).isNull();
			assertThat(judgment.effectiveScore()).isEmpty();
		}

		@Test
		@DisplayName("CHANGED: error() carries no score and no exception (was BooleanScore(false) + Throwable)")
		void errorCarriesNoScore() {
			Judgment judgment = Judgment.error("service down");

			assertThat(judgment.status()).isEqualTo(JudgmentStatus.ERROR);
			assertThat(judgment.score()).isNull();
			assertThat(judgment.effectiveScore()).isEmpty();
			assertThat(judgment.hasError()).isTrue();
			assertThat(judgment.metadata()).isEmpty();
		}

		@Test
		@DisplayName("CHANGED: pass()/fail() record only the status (was duplicated as a BooleanScore)")
		void passAndFailRecordOnlyStatus() {
			assertThat(Judgment.pass("ok").score()).isNull();
			assertThat(Judgment.pass("ok").effectiveScore()).hasValue(1.0);
			assertThat(Judgment.fail("nope").score()).isNull();
			assertThat(Judgment.fail("nope").effectiveScore()).hasValue(0.0);
		}

		@Test
		@DisplayName("CHANGED: a null status is refused (was constructible)")
		void nullStatusRefused() {
			assertThatThrownBy(() -> new Judgment(null, null, null, "x", List.of(), Map.of()))
				.isInstanceOf(NullPointerException.class)
				.hasMessageContaining("status");
		}

		@Test
		@DisplayName("CHANGED: score and status can no longer contradict (was constructible)")
		void contradictionRefused() {
			assertThatThrownBy(() -> new Judgment(JudgmentStatus.ABSTAIN, 0.4, null, "x", List.of(), Map.of()))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("no completed measurement");
			assertThatThrownBy(() -> new Judgment(JudgmentStatus.ERROR, 0.0, null, "x", List.of(), Map.of()))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("no completed measurement");
		}

	}

	@Nested
	@DisplayName("AverageVotingStrategy")
	class Average {

		private final VotingStrategy strategy = new AverageVotingStrategy();

		@Test
		@DisplayName("PRESERVED: numeric-only inputs average their normalized values")
		void numericOnly() {
			Judgment result = strategy
				.aggregate(List.of(numeric(0.8, JudgmentStatus.PASS), numeric(0.6, JudgmentStatus.PASS)), Map.of());

			assertThat(result.score()).isCloseTo(0.7, within());
			assertThat(result.status()).isEqualTo(JudgmentStatus.PASS);
		}

		@Test
		@DisplayName("PRESERVED: boolean inputs contribute 1.0/0.0")
		void booleanOnly() {
			Judgment result = strategy.aggregate(List.of(booleanJudgment(true), booleanJudgment(false)), Map.of());

			assertThat(result.score()).isCloseTo(0.5, within());
			assertThat(result.status()).isEqualTo(JudgmentStatus.PASS);
		}

		@Test
		@DisplayName("PRESERVED: mixed boolean and numeric inputs average together")
		void mixed() {
			Judgment result = strategy.aggregate(List.of(booleanJudgment(true), numeric(0.5, JudgmentStatus.PASS)),
					Map.of());

			assertThat(result.score()).isCloseTo(0.75, within());
		}

		@Test
		@DisplayName("PRESERVED: empty input is rejected")
		void emptyRejected() {
			assertThatThrownBy(() -> strategy.aggregate(List.of(), Map.of()))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("empty");
		}

		@Test
		@DisplayName("CHANGED: a labelled judgment contributes its status, not a silent 0.0")
		void labelledContributesStatus() {
			Judgment result = strategy
				.aggregate(List.of(labelled("excellent", JudgmentStatus.PASS), booleanJudgment(true)), Map.of());

			// Was 0.5: the categorical score fell through to 0.0.
			assertThat(result.score()).isCloseTo(1.0, within());
		}

		@Test
		@DisplayName("CHANGED: a status-only judgment contributes 1.0/0.0, not a silent 0.0")
		void statusOnlyContributesEffectiveScore() {
			Judgment result = strategy.aggregate(List.of(statusOnly(JudgmentStatus.PASS), booleanJudgment(true)),
					Map.of());

			// Was 0.5: an absent score fell through to 0.0.
			assertThat(result.score()).isCloseTo(1.0, within());
		}

		@Test
		@DisplayName("CHANGED: ABSTAIN leaves the population entirely (was 0.0, counted in the denominator)")
		void abstainExcluded() {
			Judgment result = strategy.aggregate(List.of(booleanJudgment(true), Judgment.abstain("not applicable")),
					Map.of());

			// Was 0.5, dragging a unanimous pass halfway to failure.
			assertThat(result.score()).isCloseTo(1.0, within());
			assertThat(result.status()).isEqualTo(JudgmentStatus.PASS);
			assertThat(evidence(result)).containsEntry(AggregationEvidence.ELIGIBLE_COUNT, 1)
				.containsEntry(AggregationEvidence.EXPLICIT_ABSTAIN_COUNT, 1);
		}

		@Test
		@DisplayName("CHANGED: ERROR propagates by default and is distinguishable from a real 0.0")
		void errorPropagates() {
			Judgment withError = strategy.aggregate(List.of(booleanJudgment(true), Judgment.error("boom")), Map.of());
			Judgment withRealZero = strategy
				.aggregate(List.of(booleanJudgment(true), numeric(0.0, JudgmentStatus.FAIL)), Map.of());

			// Was: both produced an identical 0.5 score.
			assertThat(withError.status()).isEqualTo(JudgmentStatus.ERROR);
			assertThat(withRealZero.status()).isEqualTo(JudgmentStatus.PASS);
			assertThat(withRealZero.score()).isCloseTo(0.5, within());
		}

		@Test
		@DisplayName("CHANGED: all-ABSTAIN is a no-result ABSTAIN (was FAIL scored 0.0)")
		void allAbstainYieldsNoResult() {
			Judgment result = strategy.aggregate(List.of(Judgment.abstain("a"), Judgment.abstain("b")), Map.of());

			assertThat(result.status()).isEqualTo(JudgmentStatus.ABSTAIN);
			assertThat(result.score()).isNull();
			assertThat(result.reasoning()).contains("abstained");
		}

	}

	@Nested
	@DisplayName("MedianVotingStrategy")
	class Median {

		private final VotingStrategy strategy = new MedianVotingStrategy();

		@Test
		@DisplayName("PRESERVED: odd count takes the middle value")
		void oddCount() {
			Judgment result = strategy.aggregate(List.of(numeric(0.1, JudgmentStatus.FAIL),
					numeric(0.9, JudgmentStatus.PASS), numeric(0.8, JudgmentStatus.PASS)), Map.of());

			assertThat(result.score()).isCloseTo(0.8, within());
		}

		@Test
		@DisplayName("PRESERVED: even count averages the two middle values")
		void evenCount() {
			Judgment result = strategy.aggregate(
					List.of(numeric(0.2, JudgmentStatus.FAIL), numeric(0.4, JudgmentStatus.FAIL),
							numeric(0.6, JudgmentStatus.PASS), numeric(0.8, JudgmentStatus.PASS)),
					Map.of());

			assertThat(result.score()).isCloseTo(0.5, within());
		}

		@Test
		@DisplayName("CHANGED: ABSTAIN leaves the population (was participating as 0.0)")
		void abstainExcluded() {
			Judgment result = strategy.aggregate(List.of(numeric(0.9, JudgmentStatus.PASS),
					numeric(0.8, JudgmentStatus.PASS), Judgment.abstain("n/a")), Map.of());

			// Was 0.8, with the abstention sorted in as a zero; now the median of {0.8, 0.9}.
			assertThat(result.score()).isCloseTo(0.85, within());
		}

		@Test
		@DisplayName("CHANGED: abstentions can no longer flip the verdict (was FAIL at 0.0)")
		void abstainsCannotFlipVerdict() {
			Judgment result = strategy.aggregate(
					List.of(numeric(1.0, JudgmentStatus.PASS), Judgment.abstain("a"), Judgment.abstain("b")), Map.of());

			// Was 0.0 and FAIL: two abstentions dragged the median off a unanimous pass.
			assertThat(result.score()).isCloseTo(1.0, within());
			assertThat(result.status()).isEqualTo(JudgmentStatus.PASS);
		}

	}

	@Nested
	@DisplayName("WeightedAverageStrategy")
	class Weighted {

		private final VotingStrategy strategy = new WeightedAverageStrategy();

		@Test
		@DisplayName("PRESERVED: weights are keyed by judgment index as a string")
		void weightsByIndex() {
			Judgment result = strategy.aggregate(
					List.of(numeric(1.0, JudgmentStatus.PASS), numeric(0.0, JudgmentStatus.FAIL)),
					Map.of("0", 3.0, "1", 1.0));

			assertThat(result.score()).isCloseTo(0.75, within());
		}

		@Test
		@DisplayName("PRESERVED: an absent weight defaults to 1.0")
		void absentWeightDefaultsToOne() {
			Judgment result = strategy.aggregate(
					List.of(numeric(1.0, JudgmentStatus.PASS), numeric(0.0, JudgmentStatus.FAIL)), Map.of("0", 1.0));

			assertThat(result.score()).isCloseTo(0.5, within());
		}

		@Test
		@DisplayName("PRESERVED: empty weights still mean equal weighting; evidence now preserves attribution")
		void emptyWeightsComputeInStrategy() {
			Judgment result = strategy.aggregate(
					List.of(numeric(1.0, JudgmentStatus.PASS), numeric(0.0, JudgmentStatus.FAIL)), Map.of());

			// The value is unchanged; the attribution is not. Delegating would have stamped
			// "average" onto a verdict the caller produced with WeightedAverageStrategy.
			assertThat(result.score()).isCloseTo(0.5, within());
			assertThat(evidence(result)).containsEntry(AggregationEvidence.STRATEGY, "weightedAverage");
		}

		@Test
		@DisplayName("CHANGED: an abstention releases its weight (was consuming it as 0.0)")
		void abstainReleasesWeight() {
			Judgment result = strategy.aggregate(List.of(numeric(1.0, JudgmentStatus.PASS), Judgment.abstain("n/a")),
					Map.of("0", 1.0, "1", 1.0));

			// Was 0.5: the abstention consumed its full weight at a score of zero.
			assertThat(result.score()).isCloseTo(1.0, within());
			assertThat(evidence(result)).containsEntry(AggregationEvidence.INPUT_WEIGHT, 2.0)
				.containsEntry(AggregationEvidence.ELIGIBLE_WEIGHT, 1.0);
		}

		@Test
		@DisplayName("CHANGED: all-zero weights are rejected (was a NaN score that passed validation)")
		void allZeroWeightsRejected() {
			assertThatThrownBy(() -> strategy.aggregate(
					List.of(numeric(1.0, JudgmentStatus.PASS), numeric(1.0, JudgmentStatus.PASS)),
					Map.of("0", 0.0, "1", 0.0)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("All weights are zero");
		}

		@Test
		@DisplayName("CHANGED: negative and non-finite weights are rejected")
		void invalidWeightsRejected() {
			List<Judgment> judgments = List.of(numeric(1.0, JudgmentStatus.PASS), numeric(1.0, JudgmentStatus.PASS));

			assertThatThrownBy(() -> strategy.aggregate(judgments, Map.of("0", -1.0)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("must not be negative");
			assertThatThrownBy(() -> strategy.aggregate(judgments, Map.of("0", Double.NaN)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("must be finite");
			assertThatThrownBy(() -> strategy.aggregate(judgments, Map.of("0", Double.POSITIVE_INFINITY)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("must be finite");
		}

		@Test
		@DisplayName("CHANGED: a single zero weight is legal — it means the judge does not count")
		void singleZeroWeightIsLegal() {
			Judgment result = strategy.aggregate(
					List.of(numeric(1.0, JudgmentStatus.PASS), numeric(0.0, JudgmentStatus.FAIL)),
					Map.of("0", 1.0, "1", 0.0));

			assertThat(result.score()).isCloseTo(1.0, within());
		}

		@Test
		@DisplayName("CHANGED: positive input weight but zero eligible weight yields ABSTAIN, not NaN")
		void zeroEligibleWeightYieldsAbstain() {
			// The only positively weighted judge abstains, leaving a zero-weight survivor.
			Judgment result = strategy.aggregate(List.of(Judgment.abstain("n/a"), numeric(1.0, JudgmentStatus.PASS)),
					Map.of("0", 1.0, "1", 0.0));

			assertThat(result.status()).isEqualTo(JudgmentStatus.ABSTAIN);
			assertThat(result.score()).isNull();
			assertThat(evidence(result)).containsEntry(AggregationEvidence.ELIGIBLE_WEIGHT, 0.0);
		}

	}

	@Nested
	@DisplayName("ConsensusStrategy")
	class Consensus {

		private final VotingStrategy strategy = new ConsensusStrategy();

		@Test
		@DisplayName("PRESERVED: unanimous pass yields PASS")
		void unanimousPass() {
			Judgment result = strategy.aggregate(List.of(booleanJudgment(true), numeric(0.9, JudgmentStatus.PASS)),
					Map.of());

			assertThat(result.status()).isEqualTo(JudgmentStatus.PASS);
		}

		@Test
		@DisplayName("PRESERVED: unanimous failure yields FAIL")
		void unanimousFailure() {
			Judgment result = strategy.aggregate(List.of(booleanJudgment(false), booleanJudgment(false)), Map.of());

			assertThat(result.status()).isEqualTo(JudgmentStatus.FAIL);
			assertThat(result.reasoning()).contains("Unanimous consensus");
		}

		@Test
		@DisplayName("CHANGED: disagreement abstains (was FAIL, indistinguishable from unanimous failure)")
		void disagreementAbstains() {
			Judgment consensusOnFail = strategy.aggregate(List.of(booleanJudgment(false), booleanJudgment(false)),
					Map.of());
			Judgment noConsensus = strategy.aggregate(List.of(booleanJudgment(true), booleanJudgment(false)), Map.of());

			// Was FAIL for both, leaving the difference only in the reasoning string.
			assertThat(consensusOnFail.status()).isEqualTo(JudgmentStatus.FAIL);
			assertThat(noConsensus.status()).isEqualTo(JudgmentStatus.ABSTAIN);
			assertThat(consensusOnFail.reasoning()).contains("Unanimous consensus");
			assertThat(noConsensus.reasoning()).contains("No consensus");
		}

		@Test
		@DisplayName("CHANGED: consensus reads the status (was reading the score and ignoring status)")
		void readsStatus() {
			Judgment result = strategy
				.aggregate(List.of(statusOnly(JudgmentStatus.PASS), statusOnly(JudgmentStatus.PASS)), Map.of());

			// Was FAIL: two PASS judgments with no score fell through toBoolean() to false.
			assertThat(result.status()).isEqualTo(JudgmentStatus.PASS);
		}

		@Test
		@DisplayName("CHANGED: PASS + ABSTAIN passes — an abstention is not a vote")
		void passPlusAbstainPasses() {
			assertThat(strategy.aggregate(List.of(booleanJudgment(true), Judgment.abstain("n/a")), Map.of()).status())
				.isEqualTo(JudgmentStatus.PASS);
			assertThat(strategy
				.aggregate(List.of(booleanJudgment(true), booleanJudgment(true), Judgment.abstain("n/a")), Map.of())
				.status()).isEqualTo(JudgmentStatus.PASS);
		}

		@Test
		@DisplayName("CHANGED: FAIL + ABSTAIN fails, and no longer reports false unanimity")
		void failPlusAbstainFails() {
			Judgment result = strategy.aggregate(List.of(booleanJudgment(false), Judgment.abstain("n/a")), Map.of());

			// Was FAIL too, but reported "Unanimous consensus" over a manufactured fail vote.
			assertThat(result.status()).isEqualTo(JudgmentStatus.FAIL);
			assertThat(result.reasoning()).contains("1 applicable judge");
		}

		@Test
		@DisplayName("CHANGED: all-ABSTAIN is a no-result ABSTAIN")
		void allAbstain() {
			Judgment result = strategy.aggregate(List.of(Judgment.abstain("a"), Judgment.abstain("b")), Map.of());

			assertThat(result.status()).isEqualTo(JudgmentStatus.ABSTAIN);
		}

		@Test
		@DisplayName("CHANGED: a labelled judgment is counted by its status (was a fail vote)")
		void labelledCountedByStatus() {
			Judgment result = strategy
				.aggregate(List.of(labelled("excellent", JudgmentStatus.PASS), booleanJudgment(true)), Map.of());

			// Was FAIL / "No consensus": the categorical score fell through to false.
			assertThat(result.status()).isEqualTo(JudgmentStatus.PASS);
		}

	}

	@Nested
	@DisplayName("MajorityVotingStrategy")
	class Majority {

		private final VotingStrategy strategy = new MajorityVotingStrategy();

		@Test
		@DisplayName("PRESERVED: the majority status wins")
		void majorityWins() {
			Judgment result = strategy
				.aggregate(List.of(booleanJudgment(true), booleanJudgment(true), booleanJudgment(false)), Map.of());

			assertThat(result.status()).isEqualTo(JudgmentStatus.PASS);
		}

		@Test
		@DisplayName("PRESERVED: ABSTAIN is excluded from the pass/fail counts")
		void abstainExcluded() {
			Judgment result = strategy.aggregate(List.of(booleanJudgment(true), Judgment.abstain("n/a")), Map.of());

			assertThat(result.status()).isEqualTo(JudgmentStatus.PASS);
			assertThat(result.reasoning()).contains("1 passed, 0 failed");
		}

		@Test
		@DisplayName("PRESERVED: all-ABSTAIN yields ABSTAIN")
		void allAbstain() {
			Judgment result = strategy.aggregate(List.of(Judgment.abstain("a"), Judgment.abstain("b")), Map.of());

			assertThat(result.status()).isEqualTo(JudgmentStatus.ABSTAIN);
		}

		@Test
		@DisplayName("PRESERVED: TiePolicy resolves an even split")
		void tiePolicy() {
			Judgment failOnTie = new MajorityVotingStrategy(TiePolicy.FAIL, ErrorPolicy.TREAT_AS_FAIL)
				.aggregate(List.of(booleanJudgment(true), booleanJudgment(false)), Map.of());
			Judgment passOnTie = new MajorityVotingStrategy(TiePolicy.PASS, ErrorPolicy.TREAT_AS_FAIL)
				.aggregate(List.of(booleanJudgment(true), booleanJudgment(false)), Map.of());

			assertThat(failOnTie.status()).isEqualTo(JudgmentStatus.FAIL);
			assertThat(passOnTie.status()).isEqualTo(JudgmentStatus.PASS);
		}

		@Test
		@DisplayName("PRESERVED: ErrorPolicy.TREAT_AS_FAIL converts ERROR to a fail vote")
		void errorTreatedAsFail() {
			Judgment result = new MajorityVotingStrategy(TiePolicy.FAIL, ErrorPolicy.TREAT_AS_FAIL)
				.aggregate(List.of(booleanJudgment(true), Judgment.error("boom"), Judgment.error("boom")), Map.of());

			assertThat(result.status()).isEqualTo(JudgmentStatus.FAIL);
		}

		@Test
		@DisplayName("PRESERVED: ErrorPolicy.IGNORE removes ERROR from the counts")
		void errorIgnored() {
			Judgment result = new MajorityVotingStrategy(TiePolicy.FAIL, ErrorPolicy.IGNORE)
				.aggregate(List.of(booleanJudgment(true), Judgment.error("boom")), Map.of());

			assertThat(result.status()).isEqualTo(JudgmentStatus.PASS);
		}

		@Test
		@DisplayName("PRESERVED: Majority reads status and ignores numeric score magnitude")
		void numericScoresDoNotOverrideStatus() {
			Judgment result = strategy.aggregate(
					List.of(numeric(0.9, JudgmentStatus.FAIL), numeric(0.95, JudgmentStatus.FAIL)), Map.of());

			assertThat(result.status()).isEqualTo(JudgmentStatus.FAIL);
		}

		@Test
		@DisplayName("CHANGED: the default error policy is PROPAGATE (was an implicit TREAT_AS_FAIL)")
		void defaultErrorPolicyPropagates() {
			// The pre-migration BASELINE case: the default was demonstrably TREAT_AS_FAIL,
			// but nothing recorded that as a choice. Both shapes below returned FAIL.
			assertThat(strategy.aggregate(List.of(Judgment.error("boom"), Judgment.error("boom")), Map.of()).status())
				.isEqualTo(JudgmentStatus.ERROR);
			assertThat(strategy.aggregate(List.of(booleanJudgment(true), Judgment.error("boom")), Map.of()).status())
				.isEqualTo(JudgmentStatus.ERROR);
		}

		@Test
		@DisplayName("CHANGED: numeric evidence is no longer discarded into a BooleanScore")
		void carriesNoManufacturedScore() {
			Judgment result = strategy
				.aggregate(List.of(numeric(0.9, JudgmentStatus.PASS), numeric(0.95, JudgmentStatus.PASS)), Map.of());

			// Was a BooleanScore(true). A majority verdict's meaning is its outcome; the
			// counts are evidence, not a quality score a threshold could act on.
			assertThat(result.score()).isNull();
			assertThat(result.effectiveScore()).hasValue(1.0);
			assertThat(evidence(result)).containsEntry(AggregationEvidence.PASS_COUNT, 2)
				.containsEntry(AggregationEvidence.FAIL_COUNT, 0);
		}

	}

	@Nested
	@DisplayName("Error policy accounting")
	class ErrorPolicyAccounting {

		@Test
		@DisplayName("IGNORE and TREAT_AS_ABSTAIN reach the same status but account differently")
		void ignoreVersusTreatAsAbstain() {
			List<Judgment> judgments = List.of(booleanJudgment(true), Judgment.error("boom"));

			Judgment ignored = new MajorityVotingStrategy(TiePolicy.FAIL, ErrorPolicy.IGNORE).aggregate(judgments,
					Map.of());
			Judgment abstained = new MajorityVotingStrategy(TiePolicy.FAIL, ErrorPolicy.TREAT_AS_ABSTAIN)
				.aggregate(judgments, Map.of());

			// Same outcome — which is exactly why a status-only assertion cannot tell the
			// two policies apart, and why the evidence block exists.
			assertThat(ignored.status()).isEqualTo(abstained.status()).isEqualTo(JudgmentStatus.PASS);

			assertThat(evidence(ignored)).containsEntry(AggregationEvidence.ERROR_POLICY, "ignore")
				.containsEntry(AggregationEvidence.IGNORED_ERROR_COUNT, 1)
				.containsEntry(AggregationEvidence.ERRORS_TREATED_AS_ABSTAIN_COUNT, 0);
			assertThat(evidence(abstained)).containsEntry(AggregationEvidence.ERROR_POLICY, "treatAsAbstain")
				.containsEntry(AggregationEvidence.IGNORED_ERROR_COUNT, 0)
				.containsEntry(AggregationEvidence.ERRORS_TREATED_AS_ABSTAIN_COUNT, 1);
		}

		@Test
		@DisplayName("CHANGED: every strategy's default policy propagates rather than voting")
		void defaultPolicyPropagatesOnEveryStrategy() {
			// Only Majority had an ErrorPolicy before the migration, defaulting to an
			// implicit TREAT_AS_FAIL; the other four silently scored an error as 0.0. The
			// gap that let that go unnoticed was the absence of a case exercising the
			// default constructor, so the default is now pinned on all five.
			List<Judgment> judgments = List.of(booleanJudgment(true), Judgment.error("boom"));
			List<VotingStrategy> strategies = List.of(new MajorityVotingStrategy(), new ConsensusStrategy(),
					new AverageVotingStrategy(), new MedianVotingStrategy(), new WeightedAverageStrategy());

			for (VotingStrategy strategy : strategies) {
				Judgment result = strategy.aggregate(judgments, Map.of());

				assertThat(result.status()).as("default policy for %s", strategy.getName())
					.isEqualTo(JudgmentStatus.ERROR);
				assertThat(evidence(result)).as("evidence for %s", strategy.getName())
					.containsEntry(AggregationEvidence.ERROR_POLICY, "propagate");
			}
		}

		@Test
		@DisplayName("CHANGED: IGNORE releases an errored judgment's weight instead of consuming it")
		void ignoreReleasesWeightInWeightedAggregation() {
			// DELTA-3: IGNORE must remove the judgment from the population entirely —
			// weight included — rather than zeroing its contribution while still dividing
			// by its weight. Consuming the weight would drag the mean toward zero, which
			// is the silent negative vote the migration exists to remove.
			Judgment result = new WeightedAverageStrategy(ErrorPolicy.IGNORE)
				.aggregate(List.of(numeric(0.8, JudgmentStatus.PASS), Judgment.error("boom")),
						Map.of("0", 1.0, "1", 3.0));

			assertThat(result.score()).isCloseTo(0.8, within());
			assertThat(evidence(result)).containsEntry(AggregationEvidence.INPUT_WEIGHT, 4.0)
				.containsEntry(AggregationEvidence.ELIGIBLE_WEIGHT, 1.0)
				.containsEntry(AggregationEvidence.IGNORED_ERROR_COUNT, 1);
		}

		@Test
		@DisplayName("all-ignored and all-abstained-by-error give different reasoning")
		void noResultReasoningNamesTheCause() {
			List<Judgment> allErrored = List.of(Judgment.error("boom"), Judgment.error("boom"));

			Judgment ignored = new MajorityVotingStrategy(TiePolicy.FAIL, ErrorPolicy.IGNORE).aggregate(allErrored,
					Map.of());
			Judgment abstained = new MajorityVotingStrategy(TiePolicy.FAIL, ErrorPolicy.TREAT_AS_ABSTAIN)
				.aggregate(allErrored, Map.of());

			assertThat(ignored.status()).isEqualTo(JudgmentStatus.ABSTAIN);
			assertThat(ignored.reasoning()).contains("2 error(s) ignored");
			assertThat(abstained.status()).isEqualTo(JudgmentStatus.ABSTAIN);
			assertThat(abstained.reasoning()).contains("abstained because of evaluation errors");
		}

		@Test
		@DisplayName("every strategy emits the universal evidence keys")
		void universalKeysAlwaysPresent() {
			List<Judgment> judgments = List.of(booleanJudgment(true), booleanJudgment(false));
			List<VotingStrategy> strategies = List.of(new MajorityVotingStrategy(), new ConsensusStrategy(),
					new AverageVotingStrategy(), new MedianVotingStrategy(), new WeightedAverageStrategy());

			for (VotingStrategy strategy : strategies) {
				Map<String, Object> evidence = evidence(strategy.aggregate(judgments, Map.of()));

				assertThat(evidence).as("evidence for %s", strategy.getName())
					.containsKeys(AggregationEvidence.STRATEGY, AggregationEvidence.ERROR_POLICY,
							AggregationEvidence.INPUT_COUNT, AggregationEvidence.ELIGIBLE_COUNT,
							AggregationEvidence.EXPLICIT_ABSTAIN_COUNT, AggregationEvidence.ERROR_COUNT,
							AggregationEvidence.IGNORED_ERROR_COUNT,
							AggregationEvidence.ERRORS_TREATED_AS_ABSTAIN_COUNT,
							AggregationEvidence.ERRORS_TREATED_AS_FAIL_COUNT)
					.containsEntry(AggregationEvidence.STRATEGY, strategy.getName())
					.containsEntry(AggregationEvidence.INPUT_COUNT, 2)
					.containsEntry(AggregationEvidence.ELIGIBLE_COUNT, 2);
			}
		}

		@Test
		@DisplayName("only status-counting strategies emit passCount/failCount")
		void statusCountingKeysAreStrategySpecific() {
			List<Judgment> judgments = List.of(booleanJudgment(true), booleanJudgment(false));

			assertThat(evidence(new MajorityVotingStrategy().aggregate(judgments, Map.of())))
				.containsKeys(AggregationEvidence.PASS_COUNT, AggregationEvidence.FAIL_COUNT);
			assertThat(evidence(new ConsensusStrategy().aggregate(judgments, Map.of())))
				.containsKeys(AggregationEvidence.PASS_COUNT, AggregationEvidence.FAIL_COUNT);

			// Numeric strategies count no votes; emitting zeros would read as real counts.
			assertThat(evidence(new AverageVotingStrategy().aggregate(judgments, Map.of())))
				.doesNotContainKeys(AggregationEvidence.PASS_COUNT, AggregationEvidence.FAIL_COUNT);
			assertThat(evidence(new MedianVotingStrategy().aggregate(judgments, Map.of())))
				.doesNotContainKeys(AggregationEvidence.PASS_COUNT, AggregationEvidence.FAIL_COUNT);
		}

	}

}
