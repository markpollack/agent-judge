/*
 * Copyright 2024 Spring AI Community
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.markpollack.judge.jury;

import java.util.List;
import java.util.Map;

import io.github.markpollack.judge.result.Judgment;
import io.github.markpollack.judge.result.JudgmentStatus;
import io.github.markpollack.judge.score.BooleanScore;
import io.github.markpollack.judge.score.CategoricalScore;
import io.github.markpollack.judge.score.NumericalScore;
import io.github.markpollack.judge.score.Scores;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Characterization tests pinning the <em>current</em> aggregation semantics of every
 * voting strategy before the normalized-judgment migration.
 *
 * <p>
 * These tests deliberately assert behavior that is in several places wrong. They exist so
 * that the migration can distinguish a mechanical API change from a deliberate semantic
 * change. Every assertion carries a marker:
 * </p>
 * <ul>
 * <li>{@code INTENDED} — behavior the redesign must preserve.</li>
 * <li>{@code DEFECT} — behavior the redesign deliberately changes; the test is updated
 * with a recorded rationale.</li>
 * <li>{@code BASELINE} — current behavior that is demonstrable but was never deliberately
 * chosen: no test pinned it, no Javadoc justified it. It is characterized here so that
 * changing it is evidenced rather than assumed. Deliberately <em>not</em> marked
 * {@code INTENDED}, which would assert a design intent nobody recorded.</li>
 * </ul>
 *
 * @author Mark Pollack
 */
@DisplayName("Voting strategy characterization (pre-migration)")
class VotingStrategyCharacterizationTest {

	private static final List<String> CATEGORIES = List.of("poor", "good", "excellent");

	private static Judgment numeric(double normalized, JudgmentStatus status) {
		return Judgment.builder()
			.score(NumericalScore.normalized(normalized))
			.status(status)
			.reasoning("numeric " + normalized)
			.build();
	}

	private static Judgment booleanJudgment(boolean pass) {
		return Judgment.builder()
			.score(new BooleanScore(pass))
			.status(pass ? JudgmentStatus.PASS : JudgmentStatus.FAIL)
			.reasoning("boolean " + pass)
			.build();
	}

	private static Judgment categorical(String value, JudgmentStatus status) {
		return Judgment.builder()
			.score(new CategoricalScore(value, CATEGORIES))
			.status(status)
			.reasoning("categorical " + value)
			.build();
	}

	private static Judgment statusOnly(JudgmentStatus status) {
		return Judgment.builder().status(status).reasoning("status only " + status).build();
	}

	private static double normalizedOf(Judgment judgment) {
		return ((NumericalScore) judgment.score()).normalized();
	}

	@Nested
	@DisplayName("Judgment factory scores")
	class FactoryScores {

		@Test
		@DisplayName("DEFECT: abstain() stores BooleanScore(false), i.e. a real 0.0 assessment")
		void abstainStoresFalseBooleanScore() {
			Judgment judgment = Judgment.abstain("no evidence");

			assertThat(judgment.status()).isEqualTo(JudgmentStatus.ABSTAIN);
			assertThat(judgment.score()).isEqualTo(new BooleanScore(false));
			assertThat(Scores.toNormalized(judgment.score(), Map.of())).isEqualTo(0.0);
		}

		@Test
		@DisplayName("DEFECT: error() stores BooleanScore(false), indistinguishable from a real failure")
		void errorStoresFalseBooleanScore() {
			Judgment judgment = Judgment.error("service down", new RuntimeException("boom"));

			assertThat(judgment.status()).isEqualTo(JudgmentStatus.ERROR);
			assertThat(judgment.score()).isEqualTo(new BooleanScore(false));
		}

		@Test
		@DisplayName("DEFECT: pass()/fail() duplicate the status as a BooleanScore")
		void passAndFailDuplicateStatus() {
			assertThat(Judgment.pass("ok").score()).isEqualTo(new BooleanScore(true));
			assertThat(Judgment.fail("nope").score()).isEqualTo(new BooleanScore(false));
		}

		@Test
		@DisplayName("DEFECT: builder permits a judgment with null status")
		void builderPermitsNullStatus() {
			Judgment judgment = Judgment.builder().reasoning("no status").build();

			assertThat(judgment.status()).isNull();
		}

		@Test
		@DisplayName("DEFECT: builder permits score and status to disagree")
		void builderPermitsContradiction() {
			Judgment judgment = Judgment.builder()
				.score(new BooleanScore(false))
				.status(JudgmentStatus.PASS)
				.reasoning("contradiction")
				.build();

			assertThat(judgment.pass()).isTrue();
			assertThat(((BooleanScore) judgment.score()).value()).isFalse();
		}

	}

	@Nested
	@DisplayName("AverageVotingStrategy")
	class Average {

		private final VotingStrategy strategy = new AverageVotingStrategy();

		@Test
		@DisplayName("INTENDED: numeric-only inputs average their normalized values")
		void numericOnly() {
			Judgment result = strategy.aggregate(
					List.of(numeric(0.8, JudgmentStatus.PASS), numeric(0.6, JudgmentStatus.PASS)), Map.of());

			assertThat(normalizedOf(result)).isCloseTo(0.7, within());
			assertThat(result.status()).isEqualTo(JudgmentStatus.PASS);
		}

		@Test
		@DisplayName("INTENDED: boolean inputs contribute 1.0/0.0")
		void booleanOnly() {
			Judgment result = strategy.aggregate(List.of(booleanJudgment(true), booleanJudgment(false)), Map.of());

			assertThat(normalizedOf(result)).isCloseTo(0.5, within());
			assertThat(result.status()).isEqualTo(JudgmentStatus.PASS);
		}

		@Test
		@DisplayName("INTENDED: mixed boolean and numeric inputs average together")
		void mixed() {
			Judgment result = strategy.aggregate(List.of(booleanJudgment(true), numeric(0.5, JudgmentStatus.PASS)),
					Map.of());

			assertThat(normalizedOf(result)).isCloseTo(0.75, within());
		}

		@Test
		@DisplayName("DEFECT: a categorical score silently contributes 0.0")
		void categoricalContributesZero() {
			Judgment result = strategy
				.aggregate(List.of(categorical("excellent", JudgmentStatus.PASS), booleanJudgment(true)), Map.of());

			assertThat(normalizedOf(result)).isCloseTo(0.5, within());
		}

		@Test
		@DisplayName("DEFECT: an absent score silently contributes 0.0")
		void absentScoreContributesZero() {
			Judgment result = strategy.aggregate(List.of(statusOnly(JudgmentStatus.PASS), booleanJudgment(true)),
					Map.of());

			assertThat(normalizedOf(result)).isCloseTo(0.5, within());
			assertThat(result.status()).isEqualTo(JudgmentStatus.PASS);
		}

		@Test
		@DisplayName("DEFECT: ABSTAIN counts as 0.0 and stays in the denominator")
		void abstainCountsAsZero() {
			Judgment result = strategy.aggregate(List.of(booleanJudgment(true), Judgment.abstain("not applicable")),
					Map.of());

			assertThat(normalizedOf(result)).isCloseTo(0.5, within());
			assertThat(result.status()).isEqualTo(JudgmentStatus.PASS);
		}

		@Test
		@DisplayName("DEFECT: ERROR counts as 0.0 and is indistinguishable from a real 0.0 score")
		void errorCountsAsZero() {
			Judgment withError = strategy
				.aggregate(List.of(booleanJudgment(true), Judgment.error("boom", new RuntimeException())), Map.of());
			Judgment withRealZero = strategy
				.aggregate(List.of(booleanJudgment(true), numeric(0.0, JudgmentStatus.FAIL)), Map.of());

			assertThat(normalizedOf(withError)).isEqualTo(normalizedOf(withRealZero));
		}

		@Test
		@DisplayName("DEFECT: all-ABSTAIN yields a FAIL verdict scored 0.0, not a no-result outcome")
		void allAbstainYieldsFail() {
			Judgment result = strategy.aggregate(List.of(Judgment.abstain("a"), Judgment.abstain("b")), Map.of());

			assertThat(result.status()).isEqualTo(JudgmentStatus.FAIL);
			assertThat(normalizedOf(result)).isEqualTo(0.0);
		}

		@Test
		@DisplayName("INTENDED: empty input is rejected")
		void emptyRejected() {
			assertThatThrownBy(() -> strategy.aggregate(List.of(), Map.of()))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("empty");
		}

	}

	@Nested
	@DisplayName("MedianVotingStrategy")
	class Median {

		private final VotingStrategy strategy = new MedianVotingStrategy();

		@Test
		@DisplayName("INTENDED: odd count takes the middle value")
		void oddCount() {
			Judgment result = strategy.aggregate(List.of(numeric(0.1, JudgmentStatus.FAIL),
					numeric(0.9, JudgmentStatus.PASS), numeric(0.8, JudgmentStatus.PASS)), Map.of());

			assertThat(normalizedOf(result)).isCloseTo(0.8, within());
		}

		@Test
		@DisplayName("INTENDED: even count averages the two middle values")
		void evenCount() {
			Judgment result = strategy.aggregate(List.of(numeric(0.2, JudgmentStatus.FAIL),
					numeric(0.4, JudgmentStatus.FAIL), numeric(0.6, JudgmentStatus.PASS),
					numeric(0.8, JudgmentStatus.PASS)), Map.of());

			assertThat(normalizedOf(result)).isCloseTo(0.5, within());
		}

		@Test
		@DisplayName("DEFECT: ABSTAIN participates in the median as 0.0")
		void abstainParticipatesAsZero() {
			Judgment result = strategy.aggregate(List.of(numeric(0.9, JudgmentStatus.PASS),
					numeric(0.8, JudgmentStatus.PASS), Judgment.abstain("n/a")), Map.of());

			assertThat(normalizedOf(result)).isCloseTo(0.8, within());
		}

		@Test
		@DisplayName("DEFECT: two ABSTAINs drag the median to 0.0 and flip the verdict to FAIL")
		void abstainsFlipVerdict() {
			Judgment result = strategy.aggregate(
					List.of(numeric(1.0, JudgmentStatus.PASS), Judgment.abstain("a"), Judgment.abstain("b")), Map.of());

			assertThat(normalizedOf(result)).isEqualTo(0.0);
			assertThat(result.status()).isEqualTo(JudgmentStatus.FAIL);
		}

	}

	@Nested
	@DisplayName("WeightedAverageStrategy")
	class Weighted {

		private final VotingStrategy strategy = new WeightedAverageStrategy();

		@Test
		@DisplayName("INTENDED: weights are keyed by judgment index as a string")
		void weightsByIndex() {
			Judgment result = strategy.aggregate(
					List.of(numeric(1.0, JudgmentStatus.PASS), numeric(0.0, JudgmentStatus.FAIL)),
					Map.of("0", 3.0, "1", 1.0));

			assertThat(normalizedOf(result)).isCloseTo(0.75, within());
		}

		@Test
		@DisplayName("INTENDED: an absent weight defaults to 1.0")
		void absentWeightDefaultsToOne() {
			Judgment result = strategy.aggregate(
					List.of(numeric(1.0, JudgmentStatus.PASS), numeric(0.0, JudgmentStatus.FAIL)), Map.of("0", 1.0));

			assertThat(normalizedOf(result)).isCloseTo(0.5, within());
		}

		@Test
		@DisplayName("INTENDED: empty weights delegate to the simple average")
		void emptyWeightsDelegate() {
			Judgment result = strategy.aggregate(
					List.of(numeric(1.0, JudgmentStatus.PASS), numeric(0.0, JudgmentStatus.FAIL)), Map.of());

			assertThat(normalizedOf(result)).isCloseTo(0.5, within());
		}

		@Test
		@DisplayName("DEFECT: a weighted ABSTAIN consumes its full weight as 0.0")
		void weightedAbstainConsumesWeight() {
			Judgment result = strategy.aggregate(List.of(numeric(1.0, JudgmentStatus.PASS), Judgment.abstain("n/a")),
					Map.of("0", 1.0, "1", 1.0));

			assertThat(normalizedOf(result)).isCloseTo(0.5, within());
		}

	}

	@Nested
	@DisplayName("ConsensusStrategy")
	class Consensus {

		private final VotingStrategy strategy = new ConsensusStrategy();

		@Test
		@DisplayName("INTENDED: unanimous pass yields PASS")
		void unanimousPass() {
			Judgment result = strategy.aggregate(List.of(booleanJudgment(true), numeric(0.9, JudgmentStatus.PASS)),
					Map.of());

			assertThat(result.status()).isEqualTo(JudgmentStatus.PASS);
		}

		@Test
		@DisplayName("DEFECT: unanimous fail yields FAIL, so agreement and disagreement are indistinguishable")
		void unanimousFailYieldsFail() {
			Judgment consensusOnFail = strategy.aggregate(List.of(booleanJudgment(false), booleanJudgment(false)),
					Map.of());
			Judgment noConsensus = strategy.aggregate(List.of(booleanJudgment(true), booleanJudgment(false)), Map.of());

			assertThat(consensusOnFail.status()).isEqualTo(JudgmentStatus.FAIL);
			assertThat(noConsensus.status()).isEqualTo(JudgmentStatus.FAIL);
		}

		@Test
		@DisplayName("DEFECT: consensus reads the score and ignores the status entirely")
		void ignoresStatus() {
			Judgment result = strategy.aggregate(List.of(statusOnly(JudgmentStatus.PASS), statusOnly(JudgmentStatus.PASS)),
					Map.of());

			assertThat(result.status()).isEqualTo(JudgmentStatus.FAIL);
		}

		@Test
		@DisplayName("DEFECT: an ABSTAIN is counted as a fail vote and can manufacture unanimity")
		void abstainCountedAsFail() {
			Judgment result = strategy.aggregate(List.of(booleanJudgment(false), Judgment.abstain("n/a")), Map.of());

			assertThat(result.status()).isEqualTo(JudgmentStatus.FAIL);
			assertThat(result.reasoning()).contains("Unanimous consensus");
		}

		@Test
		@DisplayName("DEFECT: a categorical score is counted as a fail vote")
		void categoricalCountedAsFail() {
			Judgment result = strategy
				.aggregate(List.of(categorical("excellent", JudgmentStatus.PASS), booleanJudgment(true)), Map.of());

			assertThat(result.status()).isEqualTo(JudgmentStatus.FAIL);
			assertThat(result.reasoning()).contains("No consensus");
		}

	}

	@Nested
	@DisplayName("MajorityVotingStrategy")
	class Majority {

		private final VotingStrategy strategy = new MajorityVotingStrategy();

		@Test
		@DisplayName("INTENDED: the majority status wins")
		void majorityWins() {
			Judgment result = strategy
				.aggregate(List.of(booleanJudgment(true), booleanJudgment(true), booleanJudgment(false)), Map.of());

			assertThat(result.status()).isEqualTo(JudgmentStatus.PASS);
		}

		@Test
		@DisplayName("INTENDED: ABSTAIN is excluded from the pass/fail counts")
		void abstainExcluded() {
			Judgment result = strategy.aggregate(List.of(booleanJudgment(true), Judgment.abstain("n/a")), Map.of());

			assertThat(result.status()).isEqualTo(JudgmentStatus.PASS);
			assertThat(result.reasoning()).contains("1 passed, 0 failed");
		}

		@Test
		@DisplayName("INTENDED: all-ABSTAIN yields ABSTAIN")
		void allAbstain() {
			Judgment result = strategy.aggregate(List.of(Judgment.abstain("a"), Judgment.abstain("b")), Map.of());

			assertThat(result.status()).isEqualTo(JudgmentStatus.ABSTAIN);
		}

		@Test
		@DisplayName("INTENDED: TiePolicy resolves an even split")
		void tiePolicy() {
			Judgment failOnTie = new MajorityVotingStrategy(TiePolicy.FAIL, ErrorPolicy.TREAT_AS_FAIL)
				.aggregate(List.of(booleanJudgment(true), booleanJudgment(false)), Map.of());
			Judgment passOnTie = new MajorityVotingStrategy(TiePolicy.PASS, ErrorPolicy.TREAT_AS_FAIL)
				.aggregate(List.of(booleanJudgment(true), booleanJudgment(false)), Map.of());

			assertThat(failOnTie.status()).isEqualTo(JudgmentStatus.FAIL);
			assertThat(passOnTie.status()).isEqualTo(JudgmentStatus.PASS);
		}

		@Test
		@DisplayName("INTENDED: ErrorPolicy.TREAT_AS_FAIL converts ERROR to a fail vote")
		void errorTreatedAsFail() {
			Judgment result = new MajorityVotingStrategy(TiePolicy.FAIL, ErrorPolicy.TREAT_AS_FAIL)
				.aggregate(List.of(booleanJudgment(true), Judgment.error("boom", null), Judgment.error("boom", null)),
						Map.of());

			assertThat(result.status()).isEqualTo(JudgmentStatus.FAIL);
		}

		@Test
		@DisplayName("BASELINE: the default constructor treats ERROR as a fail vote")
		void defaultErrorPolicyTreatsErrorAsFail() {
			// Both existing error cases pass an ErrorPolicy explicitly, so the *default*
			// has no coverage. These two inputs discriminate all four candidate policies:
			//
			//   TREAT_AS_FAIL    -> 0 pass, 2 fail          -> FAIL   <-- current default
			//   TREAT_AS_ABSTAIN -> all abstain             -> ABSTAIN
			//   IGNORE           -> all removed             -> ABSTAIN
			//   PROPAGATE        -> error propagates        -> ERROR
			//
			// No TiePolicy involvement, so FAIL can only come from the error policy.
			Judgment bothErrored = new MajorityVotingStrategy()
				.aggregate(List.of(Judgment.error("boom", null), Judgment.error("boom", null)), Map.of());

			assertThat(bothErrored.status()).isEqualTo(JudgmentStatus.FAIL);

			// A second shape, where the errored judgment must have voted FAIL for the
			// counts to tie: pass=1, fail=1 -> TiePolicy.FAIL. Under TREAT_AS_ABSTAIN or
			// IGNORE this would be pass=1, fail=0 -> PASS.
			Judgment oneErrored = new MajorityVotingStrategy()
				.aggregate(List.of(booleanJudgment(true), Judgment.error("boom", null)), Map.of());

			assertThat(oneErrored.status()).isEqualTo(JudgmentStatus.FAIL);
		}

		@Test
		@DisplayName("INTENDED: ErrorPolicy.IGNORE removes ERROR from the counts")
		void errorIgnored() {
			Judgment result = new MajorityVotingStrategy(TiePolicy.FAIL, ErrorPolicy.IGNORE)
				.aggregate(List.of(booleanJudgment(true), Judgment.error("boom", null)), Map.of());

			assertThat(result.status()).isEqualTo(JudgmentStatus.PASS);
		}

		@Test
		@DisplayName("DEFECT: numerical scores are ignored; only the status is counted")
		void numericScoresIgnored() {
			Judgment result = strategy.aggregate(
					List.of(numeric(0.9, JudgmentStatus.FAIL), numeric(0.95, JudgmentStatus.FAIL)), Map.of());

			assertThat(result.status()).isEqualTo(JudgmentStatus.FAIL);
		}

		@Test
		@DisplayName("DEFECT: the aggregate discards the numeric evidence it aggregated over")
		void aggregateDiscardsNumericEvidence() {
			Judgment result = strategy
				.aggregate(List.of(numeric(0.9, JudgmentStatus.PASS), numeric(0.95, JudgmentStatus.PASS)), Map.of());

			assertThat(result.score()).isInstanceOf(BooleanScore.class);
		}

	}

	private static org.assertj.core.data.Offset<Double> within() {
		return org.assertj.core.data.Offset.offset(0.0001);
	}

}
