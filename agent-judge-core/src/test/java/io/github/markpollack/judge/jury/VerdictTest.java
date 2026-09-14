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
