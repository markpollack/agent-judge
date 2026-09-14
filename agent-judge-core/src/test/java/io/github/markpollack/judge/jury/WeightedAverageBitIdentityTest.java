/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import io.github.markpollack.judge.result.Judgment;
import io.github.markpollack.judge.result.JudgmentStatus;

import static io.github.markpollack.judge.JudgeTestFixtures.booleanFail;
import static io.github.markpollack.judge.JudgeTestFixtures.booleanPass;
import static io.github.markpollack.judge.JudgeTestFixtures.failJudgment;
import static io.github.markpollack.judge.JudgeTestFixtures.passJudgment;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

/**
 * Every weighted-average input that aggregated before the overflow fix still produces a
 * bit-identical result.
 *
 * <p>
 * The expected fingerprints below were captured by running this table against the
 * unmodified sources at {@code 063d234}. A fingerprint records the status, the score's raw
 * IEEE 754 bits, label, checks, metadata keys, reasoning, and every evidence entry. Evidence
 * is compared in key order so that the comparison does not depend on iteration order, which
 * is unspecified for the no-result path. {@code Double.toString} is exact for a
 * {@code double}, and it distinguishes an integer {@code 1} from a double {@code 1.0}.
 * </p>
 *
 * <p>
 * A second, seeded random test compares the strategy against a verbatim copy of the
 * pre-change arithmetic over thousands of inputs whose weight total is finite. Those are
 * exactly the inputs that aggregated before the fix.
 * </p>
 *
 * @author Mark Pollack
 * @since 0.17.0
 */
class WeightedAverageBitIdentityTest {

	private static final double MAX = Double.MAX_VALUE;

	private static final String UNIVERSAL_PASS_2 = "errorCodeCounts={}, errorCount=0, errorPolicy=propagate, "
			+ "errorsTreatedAsAbstainCount=0, errorsTreatedAsFailCount=0, explicitAbstainCount=0, ignoredErrorCount=0, ";

	private record Case(String name, WeightedAverageStrategy strategy, List<Judgment> judgments,
			@Nullable Map<String, Double> weights, String expected) {
	}

	private static List<Case> cases() {
		WeightedAverageStrategy standard = new WeightedAverageStrategy();
		List<Case> cases = new ArrayList<>();
		cases.add(new Case("empty weight map", standard, List.of(passJudgment(0.8), passJudgment(0.6)), Map.of(),
				"PASS score=3fe6666666666666 label=null checks=0 metadataKeys=[aggregation] reasoning=Weighted average: 0.70 across 2 applicable judge(s) (threshold: 0.50, result: pass) evidence={eligibleCount=2, eligibleWeight=2.0, "
						+ UNIVERSAL_PASS_2 + "inputCount=2, inputWeight=2.0, notApplicableCount=0, notApplicablePolicy=refuse, notApplicableTreatedAsFailCount=0, strategy=weightedAverage, threshold=0.5}"));
		cases.add(new Case("null weight map", standard, List.of(passJudgment(0.8), passJudgment(0.6)), null,
				"PASS score=3fe6666666666666 label=null checks=0 metadataKeys=[aggregation] reasoning=Weighted average: 0.70 across 2 applicable judge(s) (threshold: 0.50, result: pass) evidence={eligibleCount=2, eligibleWeight=2.0, "
						+ UNIVERSAL_PASS_2 + "inputCount=2, inputWeight=2.0, notApplicableCount=0, notApplicablePolicy=refuse, notApplicableTreatedAsFailCount=0, strategy=weightedAverage, threshold=0.5}"));
		cases.add(new Case("weights summing to one", standard, List.of(passJudgment(0.8), passJudgment(0.6)),
				Map.of("0", 0.3, "1", 0.7),
				"PASS score=3fe51eb851eb851e label=null checks=0 metadataKeys=[aggregation] reasoning=Weighted average: 0.66 across 2 applicable judge(s) (threshold: 0.50, result: pass) evidence={eligibleCount=2, eligibleWeight=1.0, "
						+ UNIVERSAL_PASS_2 + "inputCount=2, inputWeight=1.0, notApplicableCount=0, notApplicablePolicy=refuse, notApplicableTreatedAsFailCount=0, strategy=weightedAverage, threshold=0.5}"));
		cases.add(new Case("unnormalized weights", standard, List.of(passJudgment(0.8), passJudgment(0.6)),
				Map.of("0", 3.0, "1", 7.0),
				"PASS score=3fe51eb851eb851f label=null checks=0 metadataKeys=[aggregation] reasoning=Weighted average: 0.66 across 2 applicable judge(s) (threshold: 0.50, result: pass) evidence={eligibleCount=2, eligibleWeight=10.0, "
						+ UNIVERSAL_PASS_2 + "inputCount=2, inputWeight=10.0, notApplicableCount=0, notApplicablePolicy=refuse, notApplicableTreatedAsFailCount=0, strategy=weightedAverage, threshold=0.5}"));
		cases.add(new Case("missing weights default to one", standard,
				List.of(passJudgment(0.8), passJudgment(0.6), passJudgment(0.4)), Map.of("0", 2.0),
				"PASS score=3fe4cccccccccccd label=null checks=0 metadataKeys=[aggregation] reasoning=Weighted average: 0.65 across 3 applicable judge(s) (threshold: 0.50, result: pass) evidence={eligibleCount=3, eligibleWeight=4.0, "
						+ UNIVERSAL_PASS_2 + "inputCount=3, inputWeight=4.0, notApplicableCount=0, notApplicablePolicy=refuse, notApplicableTreatedAsFailCount=0, strategy=weightedAverage, threshold=0.5}"));
		cases.add(new Case("boolean outcomes", standard, List.of(booleanPass("a"), booleanFail("b")),
				Map.of("0", 0.7, "1", 0.3),
				"PASS score=3fe6666666666666 label=null checks=0 metadataKeys=[aggregation] reasoning=Weighted average: 0.70 across 2 applicable judge(s) (threshold: 0.50, result: pass) evidence={eligibleCount=2, eligibleWeight=1.0, "
						+ UNIVERSAL_PASS_2 + "inputCount=2, inputWeight=1.0, notApplicableCount=0, notApplicablePolicy=refuse, notApplicableTreatedAsFailCount=0, strategy=weightedAverage, threshold=0.5}"));
		cases.add(new Case("an individual zero weight", standard, List.of(passJudgment(0.8), passJudgment(0.2)),
				Map.of("0", 1.0, "1", 0.0),
				"PASS score=3fe999999999999a label=null checks=0 metadataKeys=[aggregation] reasoning=Weighted average: 0.80 across 2 applicable judge(s) (threshold: 0.50, result: pass) evidence={eligibleCount=2, eligibleWeight=1.0, "
						+ UNIVERSAL_PASS_2 + "inputCount=2, inputWeight=1.0, notApplicableCount=0, notApplicablePolicy=refuse, notApplicableTreatedAsFailCount=0, strategy=weightedAverage, threshold=0.5}"));
		cases.add(new Case("below the default bar", standard, List.of(passJudgment(0.8), failJudgment(0.2)),
				Map.of("0", 0.2, "1", 0.8),
				"FAIL score=3fd47ae147ae147c label=null checks=0 metadataKeys=[aggregation] reasoning=Weighted average: 0.32 across 2 applicable judge(s) (threshold: 0.50, result: fail) evidence={eligibleCount=2, eligibleWeight=1.0, "
						+ UNIVERSAL_PASS_2 + "inputCount=2, inputWeight=1.0, notApplicableCount=0, notApplicablePolicy=refuse, notApplicableTreatedAsFailCount=0, strategy=weightedAverage, threshold=0.5}"));
		cases.add(new Case("exactly the default bar", standard, List.of(passJudgment(1.0), failJudgment(0.0)),
				Map.of("0", 1.0, "1", 1.0),
				"PASS score=3fe0000000000000 label=null checks=0 metadataKeys=[aggregation] reasoning=Weighted average: 0.50 across 2 applicable judge(s) (threshold: 0.50, result: pass) evidence={eligibleCount=2, eligibleWeight=2.0, "
						+ UNIVERSAL_PASS_2 + "inputCount=2, inputWeight=2.0, notApplicableCount=0, notApplicablePolicy=refuse, notApplicableTreatedAsFailCount=0, strategy=weightedAverage, threshold=0.5}"));
		cases.add(new Case("rounding-sensitive tenths", standard,
				List.of(passJudgment(0.1), passJudgment(0.2), failJudgment(0.3)), Map.of("0", 0.1, "1", 0.2, "2", 0.7),
				"FAIL score=3fd0a3d70a3d70a4 label=null checks=0 metadataKeys=[aggregation] reasoning=Weighted average: 0.26 across 3 applicable judge(s) (threshold: 0.50, result: fail) evidence={eligibleCount=3, eligibleWeight=1.0, "
						+ UNIVERSAL_PASS_2 + "inputCount=3, inputWeight=1.0, notApplicableCount=0, notApplicablePolicy=refuse, notApplicableTreatedAsFailCount=0, strategy=weightedAverage, threshold=0.5}"));
		cases.add(new Case("a single MAX_VALUE weight", standard, List.of(passJudgment(0.8)), Map.of("0", MAX),
				"PASS score=3fe999999999999a label=null checks=0 metadataKeys=[aggregation] reasoning=Weighted average: 0.80 across 1 applicable judge(s) (threshold: 0.50, result: pass) evidence={eligibleCount=1, eligibleWeight=1.7976931348623157E308, "
						+ UNIVERSAL_PASS_2
						+ "inputCount=1, inputWeight=1.7976931348623157E308, notApplicableCount=0, notApplicablePolicy=refuse, notApplicableTreatedAsFailCount=0, strategy=weightedAverage, threshold=0.5}"));
		cases.add(new Case("two halves of MAX_VALUE total exactly MAX_VALUE", standard,
				List.of(passJudgment(0.9), failJudgment(0.1)), Map.of("0", MAX / 2, "1", MAX / 2),
				"PASS score=3fe0000000000000 label=null checks=0 metadataKeys=[aggregation] reasoning=Weighted average: 0.50 across 2 applicable judge(s) (threshold: 0.50, result: pass) evidence={eligibleCount=2, eligibleWeight=1.7976931348623157E308, "
						+ UNIVERSAL_PASS_2
						+ "inputCount=2, inputWeight=1.7976931348623157E308, notApplicableCount=0, notApplicablePolicy=refuse, notApplicableTreatedAsFailCount=0, strategy=weightedAverage, threshold=0.5}"));
		cases.add(new Case("large weights just below overflow", standard,
				List.of(passJudgment(0.75), failJudgment(0.25), passJudgment(0.5)),
				Map.of("0", 1e308, "1", 5e307, "2", 1e307),
				"PASS score=3fe2800000000001 label=null checks=0 metadataKeys=[aggregation] reasoning=Weighted average: 0.58 across 3 applicable judge(s) (threshold: 0.50, result: pass) evidence={eligibleCount=3, eligibleWeight=1.6E308, "
						+ UNIVERSAL_PASS_2 + "inputCount=3, inputWeight=1.6E308, notApplicableCount=0, notApplicablePolicy=refuse, notApplicableTreatedAsFailCount=0, strategy=weightedAverage, threshold=0.5}"));
		cases.add(new Case("MAX_VALUE absorbs small weights without overflowing", standard,
				List.of(passJudgment(0.9), failJudgment(0.1), failJudgment(0.2)), Map.of("0", MAX, "1", 1.0, "2", 3.0),
				"PASS score=3feccccccccccccd label=null checks=0 metadataKeys=[aggregation] reasoning=Weighted average: 0.90 across 3 applicable judge(s) (threshold: 0.50, result: pass) evidence={eligibleCount=3, eligibleWeight=1.7976931348623157E308, "
						+ UNIVERSAL_PASS_2
						+ "inputCount=3, inputWeight=1.7976931348623157E308, notApplicableCount=0, notApplicablePolicy=refuse, notApplicableTreatedAsFailCount=0, strategy=weightedAverage, threshold=0.5}"));
		cases.add(new Case("subnormal weights", standard, List.of(passJudgment(0.8), failJudgment(0.3)),
				Map.of("0", Double.MIN_VALUE, "1", 3 * Double.MIN_VALUE),
				"PASS score=3fe0000000000000 label=null checks=0 metadataKeys=[aggregation] reasoning=Weighted average: 0.50 across 2 applicable judge(s) (threshold: 0.50, result: pass) evidence={eligibleCount=2, eligibleWeight=2.0E-323, "
						+ UNIVERSAL_PASS_2 + "inputCount=2, inputWeight=2.0E-323, notApplicableCount=0, notApplicablePolicy=refuse, notApplicableTreatedAsFailCount=0, strategy=weightedAverage, threshold=0.5}"));
		cases.add(new Case("extreme weight ratio", standard, List.of(passJudgment(0.9), failJudgment(0.1)),
				Map.of("0", 1e-300, "1", 1e300),
				"FAIL score=3fb999999999999a label=null checks=0 metadataKeys=[aggregation] reasoning=Weighted average: 0.10 across 2 applicable judge(s) (threshold: 0.50, result: fail) evidence={eligibleCount=2, eligibleWeight=1.0E300, "
						+ UNIVERSAL_PASS_2 + "inputCount=2, inputWeight=1.0E300, notApplicableCount=0, notApplicablePolicy=refuse, notApplicableTreatedAsFailCount=0, strategy=weightedAverage, threshold=0.5}"));
		cases.add(new Case("a stated bar", new WeightedAverageStrategy(0.8),
				List.of(passJudgment(0.7), passJudgment(0.9)), Map.of("0", 1.0, "1", 3.0),
				"PASS score=3feb333333333334 label=null checks=0 metadataKeys=[aggregation] reasoning=Weighted average: 0.85 across 2 applicable judge(s) (threshold: 0.80, result: pass) evidence={eligibleCount=2, eligibleWeight=4.0, "
						+ UNIVERSAL_PASS_2 + "inputCount=2, inputWeight=4.0, notApplicableCount=0, notApplicablePolicy=refuse, notApplicableTreatedAsFailCount=0, strategy=weightedAverage, threshold=0.8}"));
		cases.add(new Case("IGNORE drops a heavily weighted error", new WeightedAverageStrategy(ErrorPolicy.IGNORE),
				List.of(Judgment.error("boom"), passJudgment(0.6), failJudgment(0.4)),
				Map.of("0", 5.0, "1", 1.0, "2", 2.0),
				"FAIL score=3fdddddddddddddd label=null checks=0 metadataKeys=[aggregation] reasoning=Weighted average: 0.47 across 2 applicable judge(s) (threshold: 0.50, result: fail) evidence={eligibleCount=2, eligibleWeight=3.0, errorCodeCounts={judge_reported=1}, errorCount=1, errorPolicy=ignore, errorsTreatedAsAbstainCount=0, errorsTreatedAsFailCount=0, explicitAbstainCount=0, ignoredErrorCount=1, inputCount=3, inputWeight=8.0, notApplicableCount=0, notApplicablePolicy=refuse, notApplicableTreatedAsFailCount=0, strategy=weightedAverage, threshold=0.5}"));
		cases.add(new Case("TREAT_AS_FAIL keeps the error's weight",
				new WeightedAverageStrategy(ErrorPolicy.TREAT_AS_FAIL), List.of(Judgment.error("boom"), passJudgment(0.9)),
				Map.of("0", 1.0, "1", 1.0),
				"FAIL score=3fdccccccccccccd label=null checks=0 metadataKeys=[aggregation] reasoning=Weighted average: 0.45 across 2 applicable judge(s) (threshold: 0.50, result: fail) evidence={eligibleCount=2, eligibleWeight=2.0, errorCodeCounts={judge_reported=1}, errorCount=1, errorPolicy=treatAsFail, errorsTreatedAsAbstainCount=0, errorsTreatedAsFailCount=1, explicitAbstainCount=0, ignoredErrorCount=0, inputCount=2, inputWeight=2.0, notApplicableCount=0, notApplicablePolicy=refuse, notApplicableTreatedAsFailCount=0, strategy=weightedAverage, threshold=0.5}"));
		cases.add(new Case("TREAT_AS_ABSTAIN removes the error's weight",
				new WeightedAverageStrategy(ErrorPolicy.TREAT_AS_ABSTAIN),
				List.of(Judgment.error("boom"), passJudgment(0.9)), Map.of("0", 2.0, "1", 1.0),
				"PASS score=3feccccccccccccd label=null checks=0 metadataKeys=[aggregation] reasoning=Weighted average: 0.90 across 1 applicable judge(s) (threshold: 0.50, result: pass) evidence={eligibleCount=1, eligibleWeight=1.0, errorCodeCounts={judge_reported=1}, errorCount=1, errorPolicy=treatAsAbstain, errorsTreatedAsAbstainCount=1, errorsTreatedAsFailCount=0, explicitAbstainCount=0, ignoredErrorCount=0, inputCount=2, inputWeight=3.0, notApplicableCount=0, notApplicablePolicy=refuse, notApplicableTreatedAsFailCount=0, strategy=weightedAverage, threshold=0.5}"));
		cases.add(new Case("PROPAGATE returns ERROR", standard, List.of(Judgment.error("boom"), passJudgment(0.9)),
				Map.of(),
				"ERROR score=none label=null checks=0 metadataKeys=[aggregation] reasoning=1 of 2 judgments errored and the error policy is propagate evidence={eligibleCount=0, errorCodeCounts={judge_reported=1}, errorCount=1, errorPolicy=propagate, errorsTreatedAsAbstainCount=0, errorsTreatedAsFailCount=0, explicitAbstainCount=0, ignoredErrorCount=0, inputCount=2, notApplicableCount=0, notApplicablePolicy=refuse, notApplicableTreatedAsFailCount=0, strategy=weightedAverage}"));
		cases.add(new Case("PROPAGATE returns ERROR even with overflowing weights", standard,
				List.of(Judgment.error("boom"), passJudgment(0.9)), Map.of("0", MAX, "1", MAX),
				"ERROR score=none label=null checks=0 metadataKeys=[aggregation] reasoning=1 of 2 judgments errored and the error policy is propagate evidence={eligibleCount=0, errorCodeCounts={judge_reported=1}, errorCount=1, errorPolicy=propagate, errorsTreatedAsAbstainCount=0, errorsTreatedAsFailCount=0, explicitAbstainCount=0, ignoredErrorCount=0, inputCount=2, notApplicableCount=0, notApplicablePolicy=refuse, notApplicableTreatedAsFailCount=0, strategy=weightedAverage}"));
		cases.add(new Case("positive input weight, zero eligible weight", standard,
				List.of(Judgment.abstain("n/a"), passJudgment(0.8)), Map.of("0", 1.0, "1", 0.0),
				"ABSTAIN score=none label=null checks=0 metadataKeys=[aggregation] reasoning=No eligible judgments among 2 submitted evidence={eligibleCount=1, eligibleWeight=0.0, errorCodeCounts={}, errorCount=0, errorPolicy=propagate, errorsTreatedAsAbstainCount=0, errorsTreatedAsFailCount=0, explicitAbstainCount=1, ignoredErrorCount=0, inputCount=2, inputWeight=1.0, notApplicableCount=0, notApplicablePolicy=refuse, notApplicableTreatedAsFailCount=0, strategy=weightedAverage}"));
		cases.add(new Case("every judge abstains", standard, List.of(Judgment.abstain("n/a"), Judgment.abstain("n/a")),
				Map.of(),
				"ABSTAIN score=none label=null checks=0 metadataKeys=[aggregation] reasoning=All 2 judge(s) abstained evidence={eligibleCount=0, eligibleWeight=0.0, errorCodeCounts={}, errorCount=0, errorPolicy=propagate, errorsTreatedAsAbstainCount=0, errorsTreatedAsFailCount=0, explicitAbstainCount=2, ignoredErrorCount=0, inputCount=2, inputWeight=2.0, notApplicableCount=0, notApplicablePolicy=refuse, notApplicableTreatedAsFailCount=0, strategy=weightedAverage}"));
		cases.add(new Case("IGNORE empties the population", new WeightedAverageStrategy(ErrorPolicy.IGNORE),
				List.of(Judgment.error("boom"), Judgment.error("boom")), Map.of("0", 2.0),
				"ABSTAIN score=none label=null checks=0 metadataKeys=[aggregation] reasoning=No eligible judgments; 2 error(s) ignored evidence={eligibleCount=0, eligibleWeight=0.0, errorCodeCounts={judge_reported=2}, errorCount=2, errorPolicy=ignore, errorsTreatedAsAbstainCount=0, errorsTreatedAsFailCount=0, explicitAbstainCount=0, ignoredErrorCount=2, inputCount=2, inputWeight=3.0, notApplicableCount=0, notApplicablePolicy=refuse, notApplicableTreatedAsFailCount=0, strategy=weightedAverage}"));
		cases.add(new Case("an abstention leaves the denominator", standard,
				List.of(Judgment.abstain("n/a"), passJudgment(0.8), failJudgment(0.2)),
				Map.of("0", 100.0, "1", 1.0, "2", 3.0),
				"FAIL score=3fd6666666666667 label=null checks=0 metadataKeys=[aggregation] reasoning=Weighted average: 0.35 across 2 applicable judge(s) (threshold: 0.50, result: fail) evidence={eligibleCount=2, eligibleWeight=4.0, errorCodeCounts={}, errorCount=0, errorPolicy=propagate, errorsTreatedAsAbstainCount=0, errorsTreatedAsFailCount=0, explicitAbstainCount=1, ignoredErrorCount=0, inputCount=3, inputWeight=104.0, notApplicableCount=0, notApplicablePolicy=refuse, notApplicableTreatedAsFailCount=0, strategy=weightedAverage, threshold=0.5}"));
		List<Judgment> ten = new ArrayList<>();
		Map<String, Double> tenWeights = new HashMap<>();
		for (int i = 0; i < 10; i++) {
			ten.add(i % 3 == 0 ? failJudgment(i / 10.0) : passJudgment(i / 10.0));
			tenWeights.put(String.valueOf(i), i + 0.5);
		}
		cases.add(new Case("ten judges with fractional weights", standard, ten, tenWeights,
				"PASS score=3fe3ae147ae147af label=null checks=0 metadataKeys=[aggregation] reasoning=Weighted average: 0.62 across 10 applicable judge(s) (threshold: 0.50, result: pass) evidence={eligibleCount=10, eligibleWeight=50.0, "
						+ UNIVERSAL_PASS_2 + "inputCount=10, inputWeight=50.0, notApplicableCount=0, notApplicablePolicy=refuse, notApplicableTreatedAsFailCount=0, strategy=weightedAverage, threshold=0.5}"));
		return cases;
	}

	@Test
	void representativeInputsThatAggregatedBeforeTheFixAreBitIdentical() {
		List<Case> cases = cases();
		assertThat(cases).hasSize(27);
		assertSoftly(softly -> {
			for (Case c : cases) {
				String actual = fingerprint(inRootLocale(() -> c.strategy().aggregate(c.judgments(), c.weights())));
				softly.assertThat(actual).as(c.name()).isEqualTo(c.expected());
			}
		});
	}

	@Test
	void randomInputsWithAFiniteWeightTotalMatchThePreChangeArithmeticBitForBit() {
		WeightedAverageStrategy strategy = new WeightedAverageStrategy();
		Random random = new Random(22L);
		int compared = 0;
		int nearOverflow = 0;
		for (int attempt = 0; attempt < 50_000 && compared < 5_000; attempt++) {
			int size = 1 + random.nextInt(8);
			List<Judgment> judgments = new ArrayList<>();
			Map<String, Double> weights = new HashMap<>();
			for (int i = 0; i < size; i++) {
				judgments.add(randomJudgment(random));
				if (random.nextDouble() >= 0.2) {
					weights.put(String.valueOf(i), randomWeight(random));
				}
			}
			Reference expected = Reference.of(judgments, weights);
			if (expected == null) {
				// A zero or overflowing total threw before the fix; it is not a working input.
				continue;
			}
			if (expected.inputWeight() > 1e307) {
				nearOverflow++;
			}
			Judgment actual = strategy.aggregate(judgments, weights);
			Map<String, Object> evidence = evidence(actual);

			String context = "judgments=" + judgments + " weights=" + weights;
			assertThat(actual.status()).as(context).isEqualTo(expected.status());
			assertThat(scoreBits(actual.score())).as(context).isEqualTo(scoreBits(expected.score()));
			assertThat(evidence.get(AggregationEvidence.INPUT_WEIGHT)).as(context).isEqualTo(expected.inputWeight());
			assertThat(evidence.get(AggregationEvidence.ELIGIBLE_WEIGHT)).as(context)
				.isEqualTo(expected.eligibleWeight());
			compared++;
		}
		assertThat(compared).isEqualTo(5_000);
		// The sample must reach the region near overflow, or it guards nothing interesting.
		assertThat(nearOverflow).isGreaterThan(500);
	}

	/**
	 * The weighted-average arithmetic exactly as it stood at {@code 063d234}, for the default
	 * {@code PROPAGATE} policy over judgments that include no ERROR.
	 */
	private record Reference(JudgmentStatus status, @Nullable Double score, double inputWeight,
			double eligibleWeight) {

		static @Nullable Reference of(List<Judgment> judgments, Map<String, Double> weights) {
			double[] resolved = new double[judgments.size()];
			for (int i = 0; i < resolved.length; i++) {
				resolved[i] = weights.getOrDefault(String.valueOf(i), 1.0);
			}
			double inputWeight = 0.0;
			for (double weight : resolved) {
				inputWeight += weight;
			}
			if (inputWeight == 0.0 || Double.isInfinite(inputWeight)) {
				return null;
			}
			double weightedSum = 0.0;
			double eligibleWeight = 0.0;
			int eligibleCount = 0;
			for (int i = 0; i < judgments.size(); i++) {
				Judgment judgment = judgments.get(i);
				if (judgment.status() == JudgmentStatus.ABSTAIN) {
					continue;
				}
				eligibleCount++;
				double weight = resolved[i];
				weightedSum += judgment.effectiveScore().orElseThrow() * weight;
				eligibleWeight += weight;
			}
			if (eligibleCount == 0 || eligibleWeight == 0.0) {
				return new Reference(JudgmentStatus.ABSTAIN, null, inputWeight, eligibleWeight);
			}
			double weightedAverage = weightedSum / eligibleWeight;
			return new Reference(weightedAverage >= 0.5 ? JudgmentStatus.PASS : JudgmentStatus.FAIL, weightedAverage,
					inputWeight, eligibleWeight);
		}

	}

	private static Judgment randomJudgment(Random random) {
		double roll = random.nextDouble();
		if (roll < 0.15) {
			return Judgment.abstain("n/a");
		}
		if (roll < 0.30) {
			return booleanPass("p");
		}
		if (roll < 0.45) {
			return booleanFail("f");
		}
		double score = (roll < 0.5) ? (random.nextBoolean() ? 0.0 : 1.0) : random.nextDouble();
		return random.nextBoolean() ? passJudgment(score) : failJudgment(score);
	}

	private static double randomWeight(Random random) {
		double roll = random.nextDouble();
		double mantissa = 1.0 + random.nextDouble();
		if (roll < 0.15) {
			return 0.0;
		}
		if (roll < 0.40) {
			return Math.scalb(mantissa, 1015 + random.nextInt(8));
		}
		if (roll < 0.50) {
			return Math.scalb(mantissa, -1074 + random.nextInt(60));
		}
		return Math.scalb(mantissa, random.nextInt(41) - 20);
	}

	private static long scoreBits(@Nullable Double score) {
		return score == null ? -1L : Double.doubleToRawLongBits(score);
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> evidence(Judgment judgment) {
		return (Map<String, Object>) judgment.metadata().get(Judgment.AGGREGATION_KEY);
	}

	private static String fingerprint(Judgment judgment) {
		Double score = judgment.score();
		return judgment.status() + " score="
				+ (score == null ? "none" : Long.toHexString(Double.doubleToRawLongBits(score))) + " label="
				+ judgment.label() + " checks=" + judgment.checks().size() + " metadataKeys="
				+ new TreeMap<>(judgment.metadata()).keySet() + " reasoning=" + judgment.reasoning() + " evidence="
				+ new TreeMap<>(evidence(judgment));
	}

	private static Judgment inRootLocale(Supplier<Judgment> aggregation) {
		Locale original = Locale.getDefault();
		Locale.setDefault(Locale.ROOT);
		try {
			return aggregation.get();
		}
		finally {
			Locale.setDefault(original);
		}
	}

}
