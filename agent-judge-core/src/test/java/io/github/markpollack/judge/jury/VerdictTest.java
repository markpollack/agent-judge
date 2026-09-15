/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.markpollack.judge.description.KeySource;
import io.github.markpollack.judge.result.Judgment;
import io.github.markpollack.judge.result.JudgmentReasonCode;

import static io.github.markpollack.judge.JudgeTestFixtures.booleanFail;
import static io.github.markpollack.judge.JudgeTestFixtures.booleanPass;
import static io.github.markpollack.judge.JudgeTestFixtures.split;
import static io.github.markpollack.judge.JudgeTestFixtures.unanimousPass;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * What a verdict must record, and what it refuses to record.
 *
 * <p>
 * A verdict is read long after the run that produced it, by something that cannot ask the jury
 * any questions. Two facts it used to leave implicit are the ones that make it readable on its
 * own: which judge each judgment came from, and what produced the aggregate. Both are now
 * required, and both are checked against the rest of the verdict — a marker that can be wrong
 * is worse than one that is absent, because a reader trusts it either way.
 * </p>
 *
 * @author Mark Pollack
 * @since 0.1.0
 */
@DisplayName("Verdict")
class VerdictTest {

	private static List<Seat> declaredSeats(String... keys) {
		List<Seat> seats = new ArrayList<>(keys.length);
		for (int position = 0; position < keys.length; position++) {
			seats.add(new Seat(position, keys[position], KeySource.DECLARED));
		}
		return seats;
	}

	private static Verdict.Builder leaf(Judgment aggregated, Map<String, Judgment> byName) {
		return Verdict.builder()
			.aggregated(aggregated)
			.individual(List.copyOf(byName.values()))
			.individualByName(byName)
			.seats(declaredSeats(byName.keySet().toArray(new String[0])))
			.decision(Decision.own());
	}

	private static Map<String, Judgment> named(String first, Judgment firstJudgment, String second,
			Judgment secondJudgment) {
		Map<String, Judgment> byName = new LinkedHashMap<>();
		byName.put(first, firstJudgment);
		byName.put(second, secondJudgment);
		return byName;
	}

	@Nested
	@DisplayName("Construction")
	class Construction {

		@Test
		void buildsWithEveryComponent() {
			Judgment one = booleanPass("Judge 1");
			Judgment two = booleanFail("Judge 2");
			Verdict verdict = leaf(booleanPass("Majority passed"), named("first", one, "second", two))
				.weights(Map.of("0", 0.3, "1", 0.7))
				.build();

			assertThat(verdict.individual()).containsExactly(one, two);
			assertThat(verdict.individualByName()).containsExactly(Map.entry("first", one), Map.entry("second", two));
			assertThat(verdict.weights()).containsEntry("0", 0.3).containsEntry("1", 0.7);
			assertThat(verdict.seats()).containsExactly(new Seat(0, "first", KeySource.DECLARED),
					new Seat(1, "second", KeySource.DECLARED));
			assertThat(verdict.decision()).isEqualTo(Decision.own());
			assertThat(verdict.compositeAttempts()).isEmpty();
		}

		@Test
		@DisplayName("a verdict must say what produced its aggregate; no default would be true")
		void aDecisionIsRequired() {
			assertThatThrownBy(() -> Verdict.builder().aggregated(booleanPass("ok")).build())
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("what produced its aggregate");
		}

		@Test
		void rejectsMissingAggregatedJudgment() {
			assertThatThrownBy(() -> Verdict.builder().decision(Decision.own()).build())
				.isInstanceOf(NullPointerException.class)
				.hasMessageContaining("aggregated judgment");
			assertThatThrownBy(() -> Verdict.builder().aggregated(null))
				.isInstanceOf(NullPointerException.class)
				.hasMessageContaining("aggregated judgment");
		}

		@Test
		void rejectsNullCompositeAttemptsOnTheCanonicalConstructor() {
			assertThatThrownBy(() -> new Verdict(booleanPass("Aggregated"), List.of(), Map.of(), Map.of(), List.of(),
					Decision.own(), null))
				.isInstanceOf(NullPointerException.class)
				.hasMessageContaining("compositeAttempts");
		}

		@Test
		void anEmptyVerdictIsLegalWhenNothingWasReduced() {
			Verdict verdict = Verdict.builder()
				.aggregated(Judgment.error(JudgmentReasonCode.NO_TIER_DECIDED, "no tier decided"))
				.decision(Decision.undecided())
				.build();

			assertThat(verdict.individual()).isEmpty();
			assertThat(verdict.seats()).isEmpty();
			assertThat(verdict.weights()).isEmpty();
			assertThat(verdict.compositeAttempts()).isEmpty();
		}

		@Test
		void defensiveCopiesKeepACallersLaterMutationOut() {
			Judgment only = booleanPass("Judge 1");
			List<Judgment> individual = new ArrayList<>(List.of(only));
			Map<String, Judgment> byName = new LinkedHashMap<>();
			byName.put("first", only);
			List<Seat> seats = new ArrayList<>(declaredSeats("first"));

			Verdict verdict = Verdict.builder()
				.aggregated(booleanPass("Aggregated"))
				.individual(individual)
				.individualByName(byName)
				.seats(seats)
				.decision(Decision.own())
				.build();

			individual.add(booleanPass("Judge 2"));
			byName.put("second", booleanFail("Judge 2"));
			seats.clear();

			assertThat(verdict.individual()).hasSize(1).isUnmodifiable();
			assertThat(verdict.individualByName()).hasSize(1).isUnmodifiable();
			assertThat(verdict.seats()).hasSize(1).isUnmodifiable();
		}

		@Test
		void recordEqualityAndToString() {
			Verdict first = leaf(booleanPass("agg"), named("a", booleanPass("J1"), "b", booleanPass("J2"))).build();
			Verdict second = leaf(booleanPass("agg"), named("a", booleanPass("J1"), "b", booleanPass("J2"))).build();

			assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
			assertThat(first.toString()).contains("Verdict").contains("seats").contains("decision");
		}

	}

	@Nested
	@DisplayName("Seats join the ordered list to the keyed map")
	class Seats {

		@Test
		@DisplayName("one seat per judgment, or the join is a guess")
		void oneSeatPerJudgment() {
			assertThatThrownBy(() -> Verdict.builder()
				.aggregated(booleanPass("agg"))
				.individual(List.of(booleanPass("J1"), booleanPass("J2")))
				.individualByName(Map.of("a", booleanPass("J1")))
				.seats(declaredSeats("a"))
				.decision(Decision.own())
				.build()).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("one entry per individual judgment");
		}

		@Test
		@DisplayName("positions are unique and strictly increasing")
		void positionsAreOrdered() {
			assertThatThrownBy(() -> Verdict.builder()
				.aggregated(booleanPass("agg"))
				.individual(List.of(booleanPass("J1"), booleanPass("J2")))
				.individualByName(named("a", booleanPass("J1"), "b", booleanPass("J2")))
				.seats(List.of(new Seat(1, "a", KeySource.DECLARED), new Seat(0, "b", KeySource.DECLARED)))
				.decision(Decision.own())
				.build()).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("strictly increasing");
			assertThatThrownBy(() -> new Seat(-1, "a", KeySource.DECLARED))
				.isInstanceOf(IllegalArgumentException.class);
		}

		@Test
		@DisplayName("a seat key that is not in the map would attribute a judgment to nothing")
		void seatKeysMustExist() {
			assertThatThrownBy(() -> Verdict.builder()
				.aggregated(booleanPass("agg"))
				.individual(List.of(booleanPass("J1")))
				.individualByName(Map.of("a", booleanPass("J1")))
				.seats(declaredSeats("b"))
				.decision(Decision.own())
				.build()).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("not a key of individualByName");
		}

		@Test
		@DisplayName("two seats may share one key: a duplicate declared name collapses the map, not the list")
		void duplicateKeysAreLegal() {
			Judgment first = booleanPass("J1");
			Judgment second = booleanFail("J2");

			Verdict verdict = Verdict.builder()
				.aggregated(booleanFail("agg"))
				.individual(List.of(first, second))
				.individualByName(Map.of("same", second))
				.seats(List.of(new Seat(0, "same", KeySource.DECLARED), new Seat(1, "same", KeySource.DECLARED)))
				.decision(Decision.own())
				.build();

			assertThat(verdict.individual()).hasSize(2);
			assertThat(verdict.individualByName()).hasSize(1);
			assertThat(verdict.seats()).extracting(Seat::verdictKey).containsExactly("same", "same");
		}

		@Test
		@DisplayName("positions need not be contiguous: a gap is how a meta-jury records a member it could not use")
		void positionsMayHaveGaps() {
			Judgment first = booleanPass("first");
			Judgment third = booleanPass("third");

			Verdict verdict = Verdict.builder()
				.aggregated(booleanPass("agg"))
				.individual(List.of(first, third))
				.individualByName(named("first", first, "third", third))
				.seats(List.of(new Seat(0, "first", KeySource.DECLARED), new Seat(2, "third", KeySource.DECLARED)))
				.decision(Decision.own())
				.build();

			assertThat(verdict.seats()).extracting(Seat::position).containsExactly(0, 2);
		}

		@Test
		@DisplayName("a one-judge verdict seats its sole judge under its declared name")
		void singleSeatsItsJudge() {
			Judgment judgment = booleanPass("File exists");
			Verdict verdict = Verdict.single("file-exists", judgment);

			assertThat(verdict.aggregated()).isSameAs(judgment);
			assertThat(verdict.individual()).containsExactly(judgment);
			assertThat(verdict.individualByName()).containsExactlyEntriesOf(Map.of("file-exists", judgment));
			assertThat(verdict.seats()).containsExactly(new Seat(0, "file-exists", KeySource.DECLARED));
			assertThat(verdict.decision()).isEqualTo(Decision.own());
			assertThatThrownBy(() -> Verdict.single(" ", booleanPass("x")))
				.isInstanceOf(IllegalArgumentException.class);
			assertThatThrownBy(() -> Verdict.single("judge", null)).isInstanceOf(NullPointerException.class);
		}

		@Test
		@DisplayName("a fixture jury keeps every judge's identity")
		void fixtureJuriesAreSeated() {
			Verdict verdict = unanimousPass(3);

			assertThat(verdict.individual()).hasSize(3);
			assertThat(verdict.seats()).extracting(Seat::verdictKey).containsExactly("Judge#1", "Judge#2", "Judge#3");
			assertThat(verdict.seats()).extracting(Seat::keySource).containsOnly(KeySource.POSITIONAL);
		}

	}

	@Nested
	@DisplayName("Decisions are checked against the verdict that carries them")
	class Decisions {

		@Test
		@DisplayName("an UNDECIDED verdict must actually carry a machinery failure")
		void undecidedRequiresAMachineryError() {
			assertThatThrownBy(() -> Verdict.builder()
				.aggregated(booleanPass("ok"))
				.decision(Decision.undecided())
				.build()).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("machinery reason code");
			assertThatThrownBy(() -> Verdict.builder()
				.aggregated(Judgment.error(JudgmentReasonCode.JUDGE_REPORTED, "a judge failed"))
				.decision(Decision.undecided())
				.build()).as("a judge's failure is not the instrument reaching no outcome")
				.isInstanceOf(IllegalArgumentException.class);
			assertThatCode(() -> Verdict.builder()
				.aggregated(Judgment.error(JudgmentReasonCode.AGGREGATION_FAILED, "the strategy threw"))
				.decision(Decision.undecided())
				.build()).doesNotThrowAnyException();
		}

		@Test
		@DisplayName("only a TIER decision names a tier, and it must name one")
		void tierNamesAreRequiredAndForbidden() {
			assertThatThrownBy(() -> new Decision(DecisionKind.TIER, null, DecisionBasis.TIER_OUTCOME))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("requires both");
			assertThatThrownBy(() -> new Decision(DecisionKind.TIER, "fast", null))
				.isInstanceOf(IllegalArgumentException.class);
			assertThatThrownBy(() -> new Decision(DecisionKind.OWN, "fast", null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("names no tier");
			assertThatThrownBy(() -> new Decision(DecisionKind.UNDECIDED, null, DecisionBasis.TIER_OUTCOME))
				.isInstanceOf(IllegalArgumentException.class);
		}

		@Test
		@DisplayName("names are local: a decision must name a direct tier of this verdict")
		void tierNamesAreLocal() {
			assertThatThrownBy(() -> Verdict.builder()
				.aggregated(booleanPass("ok"))
				.decision(Decision.tier("elsewhere", DecisionBasis.TIER_OUTCOME))
				.build()).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("tier names are local");
		}

		@Test
		@DisplayName("a decision that names a tier which returned nothing determines nothing")
		void tierNamedMustHaveReturnedAVerdict() {
			CompositeAttempt threw = CompositeAttempt.executionFailed("gate", CompositeRelation.CASCADE_TIER,
					TierPolicy.REJECT_ON_ANY_FAIL, new CompositeFailure(CompositeFailureCode.JURY_EXECUTION_FAILED));

			assertThatThrownBy(() -> Verdict.builder()
				.aggregated(booleanPass("ok"))
				.decision(Decision.tier("gate", DecisionBasis.TIER_OUTCOME))
				.compositeAttempts(List.of(threw))
				.build()).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("returned no verdict");
		}

		@Test
		@DisplayName("wire names round-trip exactly")
		void wireNames() {
			assertThat(DecisionKind.fromWire("undecided")).isEqualTo(DecisionKind.UNDECIDED);
			assertThat(DecisionBasis.fromWire("individual_rejection")).isEqualTo(DecisionBasis.INDIVIDUAL_REJECTION);
			assertThat(AttemptDisposition.fromWire("stage_failed")).isEqualTo(AttemptDisposition.STAGE_FAILED);
			assertThat(DispositionReason.fromWire("child_undecided")).isEqualTo(DispositionReason.CHILD_UNDECIDED);
			assertThatThrownBy(() -> DecisionKind.fromWire("OWN")).isInstanceOf(IllegalArgumentException.class);
			assertThatThrownBy(() -> DispositionReason.fromWire("nope")).isInstanceOf(IllegalArgumentException.class);
		}

	}

	/**
	 * The cross-checks a {@code TIER} decision owes the verdict that carries it.
	 *
	 * <p>
	 * A cascade's root is a copy: its aggregate, individuals, map, weights and seats come from
	 * the tier the decision names, and a reader counts on that without being able to re-derive
	 * it. Each rule in §7.2 is therefore checked here against the attempt the verdict itself
	 * carries — including R-D's amendment, where the one root that is <em>not</em> a copy must
	 * be a parent-authored {@code ERROR stage_failed}.
	 * </p>
	 *
	 * <p>
	 * These are negative tests by necessity. Every cascade the library builds satisfies the
	 * rules, so an assertion about a cascade's output cannot tell an enforced invariant from an
	 * unenforced one; only a verdict deliberately built wrong can.
	 * </p>
	 */
	@Nested
	@DisplayName("A TIER decision is checked against the tier it names")
	class TierDecisions {

		private static final Judgment PASSED = booleanPass("the first judge was satisfied");

		private static final Judgment FAILED = booleanFail("the second judge was not");

		private static final Judgment BROKEN =
				Judgment.error(JudgmentReasonCode.AGGREGATION_FAILED, "the strategy threw");

		private static Map<String, Judgment> individuals(Judgment second) {
			return named("first", PASSED, "second", second);
		}

		/** A tier holding a genuine FAIL, with the aggregate and decision a caller chooses. */
		private static Verdict tier(Judgment aggregate, Decision decision, Judgment second) {
			Map<String, Judgment> byName = individuals(second);
			return Verdict.builder()
				.aggregated(aggregate)
				.individual(List.copyOf(byName.values()))
				.individualByName(byName)
				.seats(declaredSeats("first", "second"))
				.decision(decision)
				.build();
		}

		private static Verdict undecidedTier() {
			return tier(BROKEN, Decision.undecided(), FAILED);
		}

		private static Verdict excludedTier() {
			return tier(Judgment.notApplicable("no Java sources"), Decision.own(), FAILED);
		}

		private static CompositeAttempt used(Verdict verdict) {
			return CompositeAttempt.used("gate", CompositeRelation.CASCADE_TIER, TierPolicy.REJECT_ON_ANY_FAIL,
					verdict);
		}

		private static CompositeAttempt refused(Verdict verdict, TierPolicy policy) {
			DispositionReason reason = verdict.decision().kind() == DecisionKind.UNDECIDED
					? DispositionReason.CHILD_UNDECIDED : DispositionReason.UNDECLARED_NOT_APPLICABLE;
			return CompositeAttempt.stageFailed("gate", CompositeRelation.CASCADE_TIER, policy, reason, verdict);
		}

		/** A root that copies everything the named tier holds, with a chosen aggregate. */
		private static Verdict.Builder root(Judgment aggregate, Verdict tier, CompositeAttempt attempt,
				DecisionBasis basis) {
			return Verdict.builder()
				.aggregated(aggregate)
				.individual(tier.individual())
				.individualByName(tier.individualByName())
				.weights(tier.weights())
				.seats(tier.seats())
				.decision(Decision.tier("gate", basis))
				.compositeAttempts(List.of(attempt));
		}

		@Test
		@DisplayName("TIER_OUTCOME adopts a determination, so the tier must have been used")
		void tierOutcomeRequiresAUsedAttempt() {
			Verdict tier = undecidedTier();

			assertThatThrownBy(() -> root(BROKEN, tier, refused(tier, TierPolicy.REJECT_ON_ANY_FAIL),
					DecisionBasis.TIER_OUTCOME).build()).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("must be USED");
		}

		@Test
		@DisplayName("TIER_OUTCOME copies the tier's aggregate exactly; a near copy is not a copy")
		void tierOutcomeCopiesTheAggregate() {
			Verdict tier = tier(booleanPass("the tier was satisfied"), Decision.own(), PASSED);

			assertThatThrownBy(() -> root(booleanPass("a sentence of the parent's own"), tier, used(tier),
					DecisionBasis.TIER_OUTCOME).build()).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("the aggregate differs");
			assertThatCode(() -> root(tier.aggregated(), tier, used(tier), DecisionBasis.TIER_OUTCOME).build())
				.doesNotThrowAnyException();
		}

		@Test
		@DisplayName("INDIVIDUAL_REJECTION is a stop on a tier the cascade could not use")
		void rejectionRequiresAFailedStage() {
			Verdict tier = tier(booleanFail("the tier rejected the subject"), Decision.own(), FAILED);

			assertThatThrownBy(() -> root(BROKEN, tier, used(tier), DecisionBasis.INDIVIDUAL_REJECTION).build())
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("must be STAGE_FAILED");
		}

		@Test
		@DisplayName("only REJECT_ON_ANY_FAIL stops on a rejection; a broken stage accepts nothing")
		void rejectionRequiresTheRejectingPolicy() {
			Verdict tier = undecidedTier();

			assertThatThrownBy(() -> root(BROKEN, tier, refused(tier, TierPolicy.ACCEPT_ON_ALL_PASS),
					DecisionBasis.INDIVIDUAL_REJECTION).build()).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("only REJECT_ON_ANY_FAIL");
		}

		@Test
		@DisplayName("a rejection needs a genuine FAIL: neither a pass nor a machinery error is one")
		void rejectionRequiresAGenuineFail() {
			Verdict allPassed = tier(BROKEN, Decision.undecided(), PASSED);
			assertThatThrownBy(() -> root(BROKEN, allPassed, refused(allPassed, TierPolicy.REJECT_ON_ANY_FAIL),
					DecisionBasis.INDIVIDUAL_REJECTION).build()).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("requires a genuine FAIL");

			// The claim D4 rests on: the library's own failure is not rejection evidence, so a
			// tier whose individuals are machinery errors has established nothing to stop on.
			Verdict machineryOnly = tier(BROKEN, Decision.undecided(),
					Judgment.error(JudgmentReasonCode.STAGE_FAILED, "a member did not produce a determination"));
			assertThatThrownBy(() -> root(BROKEN, machineryOnly, refused(machineryOnly, TierPolicy.REJECT_ON_ANY_FAIL),
					DecisionBasis.INDIVIDUAL_REJECTION).build()).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("a broken stage on its own justifies nothing");
		}

		@Test
		@DisplayName("a CHILD_UNDECIDED rejection keeps the child's own machinery error as its root")
		void childUndecidedKeepsTheChildsCode() {
			Verdict tier = undecidedTier();
			CompositeAttempt attempt = refused(tier, TierPolicy.REJECT_ON_ANY_FAIL);

			assertThatThrownBy(() -> root(Judgment.error(JudgmentReasonCode.STAGE_FAILED, "a root of the parent's own"),
					tier, attempt, DecisionBasis.INDIVIDUAL_REJECTION).build())
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("keeps the child's own machinery error");
			assertThatCode(() -> root(tier.aggregated(), tier, attempt, DecisionBasis.INDIVIDUAL_REJECTION).build())
				.doesNotThrowAnyException();
		}

		@Test
		@DisplayName("R-D: a rejection on a refused exclusion builds a parent-authored stage_failed root")
		void refusedExclusionBuildsAStageFailedRoot() {
			Verdict tier = excludedTier();
			CompositeAttempt attempt = refused(tier, TierPolicy.REJECT_ON_ANY_FAIL);

			assertThatThrownBy(() -> root(tier.aggregated(), tier, attempt, DecisionBasis.INDIVIDUAL_REJECTION)
				.build()).as("copying the exclusion the cascade just refused would adopt the claim it rejected")
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("parent-authored");
			assertThatThrownBy(() -> root(BROKEN, tier, attempt, DecisionBasis.INDIVIDUAL_REJECTION).build())
				.as("and any other machinery code would name a cause the parent did not observe")
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("parent-authored");
			assertThatCode(() -> root(Judgment.error(JudgmentReasonCode.STAGE_FAILED,
					"tier 'gate' returned NOT_APPLICABLE without declaring that its aggregate may be excluded"), tier,
					attempt, DecisionBasis.INDIVIDUAL_REJECTION).build()).doesNotThrowAnyException();
		}

		@Test
		@DisplayName("what a cascade copies must really have been copied, whichever basis it stopped on")
		void everythingElseIsCopied() {
			Verdict tier = undecidedTier();
			CompositeAttempt attempt = refused(tier, TierPolicy.REJECT_ON_ANY_FAIL);

			assertThatThrownBy(() -> root(tier.aggregated(), tier, attempt, DecisionBasis.INDIVIDUAL_REJECTION)
				.individual(List.of(FAILED))
				.individualByName(Map.of("second", FAILED))
				.seats(List.of(new Seat(0, "second", KeySource.DECLARED)))
				.build()).as("a root that keeps only the failing individual has rewritten the tier's evidence")
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("copies its individuals, map, weights and seats");

			Verdict decided = tier(booleanPass("the tier was satisfied"), Decision.own(), PASSED);
			assertThatThrownBy(() -> root(decided.aggregated(), decided, used(decided), DecisionBasis.TIER_OUTCOME)
				.weights(Map.of("0", 2.0))
				.build()).as("the weights are part of the copy, because they are the join to the seats")
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("copies its individuals, map, weights and seats");
		}

	}

	@Nested
	@DisplayName("Composite attempts")
	class Attempts {

		@Test
		void metaMemberAttemptsCarryTheirVerdicts() {
			Verdict first = unanimousPass(2);
			Verdict second = split(1, 1);

			Verdict meta = Verdict.builder()
				.aggregated(booleanPass("Meta-jury passed"))
				.individual(List.of(first.aggregated(), second.aggregated()))
				.individualByName(named("first", first.aggregated(), "second", second.aggregated()))
				.seats(declaredSeats("first", "second"))
				.decision(Decision.own())
				.compositeAttempts(
						List.of(CompositeAttempt.used("first", CompositeRelation.META_MEMBER, null, first),
								CompositeAttempt.used("second", CompositeRelation.META_MEMBER, null, second)))
				.build();

			assertThat(meta.compositeAttempts()).extracting(CompositeAttempt::disposition)
				.containsOnly(AttemptDisposition.USED);
			assertThat(meta.compositeAttempts().get(0).verdict()).isEqualTo(first);
			assertThat(meta.compositeAttempts()).isUnmodifiable();
		}

		@Test
		@DisplayName("a reason is required exactly when the stage failed")
		void dispositionAndReasonAgree() {
			Verdict child = Verdict.single("leaf", booleanPass("ok"));

			assertThatThrownBy(() -> new CompositeAttempt("m", CompositeRelation.META_MEMBER, null,
					AttemptDisposition.USED, DispositionReason.CHILD_UNDECIDED, child, null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("required exactly when");
			assertThatThrownBy(() -> new CompositeAttempt("m", CompositeRelation.META_MEMBER, null,
					AttemptDisposition.STAGE_FAILED, null, child, null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("required exactly when");
		}

		@Test
		@DisplayName("the reason must agree with what the attempt actually holds")
		void reasonAgreesWithContent() {
			Verdict child = Verdict.single("leaf", booleanPass("ok"));
			CompositeFailure failure = new CompositeFailure(CompositeFailureCode.JURY_EXECUTION_FAILED);

			assertThatThrownBy(() -> new CompositeAttempt("m", CompositeRelation.META_MEMBER, null,
					AttemptDisposition.STAGE_FAILED, DispositionReason.EXECUTION_FAILED, child, null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("EXECUTION_FAILED");
			assertThatThrownBy(() -> new CompositeAttempt("m", CompositeRelation.META_MEMBER, null,
					AttemptDisposition.STAGE_FAILED, DispositionReason.CHILD_UNDECIDED, null, failure))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("must keep it");
		}

		@Test
		@DisplayName("a stage-failed attempt keeps the child's actual verdict, unchanged")
		void stageFailedKeepsTheChildVerdict() {
			Verdict undecided = Verdict.builder()
				.aggregated(Judgment.error(JudgmentReasonCode.AGGREGATION_FAILED, "the strategy threw"))
				.decision(Decision.undecided())
				.build();

			CompositeAttempt attempt = CompositeAttempt.stageFailed("member", CompositeRelation.META_MEMBER, null,
					DispositionReason.CHILD_UNDECIDED, undecided);

			assertThat(attempt.verdict()).isSameAs(undecided);
			assertThat(attempt.disposition()).isEqualTo(AttemptDisposition.STAGE_FAILED);
			assertThat(attempt.dispositionReason()).isEqualTo(DispositionReason.CHILD_UNDECIDED);
		}

	}

}
