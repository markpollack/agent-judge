/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury;

import io.github.markpollack.judge.verdict.AttemptDisposition;
import io.github.markpollack.judge.verdict.CompositeAttempt;
import io.github.markpollack.judge.verdict.CompositeFailure;
import io.github.markpollack.judge.verdict.CompositeFailureCode;
import io.github.markpollack.judge.verdict.CompositeRelation;
import io.github.markpollack.judge.verdict.DispositionReason;
import io.github.markpollack.judge.verdict.RoutingRule;
import io.github.markpollack.judge.verdict.Seat;
import io.github.markpollack.judge.verdict.Verdict;
import io.github.markpollack.judge.verdict.VerdictProvenance;
import io.github.markpollack.judge.verdict.VerdictProvenanceBasis;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.markpollack.judge.completion.CompletionEvidence;
import io.github.markpollack.judge.verdict.KeySource;
import io.github.markpollack.judge.judgment.Judgment;
import io.github.markpollack.judge.judgment.JudgmentReasonCode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A disposition reason must describe the stage the attempt actually holds.
 *
 * <p>
 * The attempt checked only that a verdict was <em>present</em>, so a stage that passed
 * with its own reduction could be recorded {@code CHILD_UNDECIDED} or
 * {@code UNDECLARED_NOT_APPLICABLE}, and a {@code USED} attempt could hold a child that
 * decided nothing. Those are not merely unhelpful labels — a downstream reader counts
 * boundary dispositions by reason and classifies items by following the provenance chain,
 * and it trusts these markers because it cannot re-derive them. A marker that can be
 * false is worse than one that is absent: absence says "not recorded", and a reader can
 * act on that.
 * </p>
 *
 * <p>
 * So the check is on the content, not the presence, and it lives in the compact
 * constructor — the one place every factory, every Jackson read, and every hand-built D1
 * copy has to pass through.
 * </p>
 */
@DisplayName("Attempt disposition agreement")
class AttemptDispositionAgreementTest {

	private static final ObjectMapper MAPPER = new ObjectMapper()
		.registerModule(io.github.markpollack.judge.serialization.ResultJson.module());

	private static final Judgment FAILING = Judgment.fail("a requirement was not met");

	/** A child that reduced normally and passed. */
	private static Verdict decided() {
		return Verdict.of(Judgment.pass("every requirement was met"), Map.of("strict", FAILING));
	}

	/** A child whose reduction broke, so it determined nothing. */
	private static Verdict undecided() {
		return Verdict.advancedBuilder()
			.judgment(Judgment.error(JudgmentReasonCode.AGGREGATION_FAILED, "the strategy threw"))
			.individual(List.of(FAILING, Judgment.pass("other")))
			.individualByName(Map.of("strict", FAILING, "other", Judgment.pass("other")))
			.seats(List.of(new Seat(0, "strict", KeySource.DECLARED), new Seat(1, "other", KeySource.DECLARED)))
			.provenance(VerdictProvenance.undecided())
			.build();
	}

	/** A child that excluded the whole subject. */
	private static Verdict excluded() {
		return Verdict.of(Judgment.notApplicable("nothing in this rubric applies"), Map.of("strict", FAILING));
	}

	private static CompositeAttempt attempt(AttemptDisposition disposition, DispositionReason reason, Verdict verdict) {
		return new CompositeAttempt("rubric", CompositeRelation.CASCADE_TIER, RoutingRule.STOP_ON_ANY_OPINION_FAIL,
				disposition, reason, verdict, null);
	}

	@Nested
	@DisplayName("A reason that does not match its child")
	class FalseReasons {

		@Test
		@DisplayName("CHILD_UNDECIDED over a child that decided is refused")
		void childUndecidedRequiresAnUndecidedChild() {
			assertThatThrownBy(
					() -> attempt(AttemptDisposition.STAGE_FAILED, DispositionReason.CHILD_UNDECIDED, decided()))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("CHILD_UNDECIDED");

			assertThatThrownBy(() -> CompositeAttempt.stageFailed("rubric", CompositeRelation.CASCADE_TIER,
					RoutingRule.STOP_ON_ANY_OPINION_FAIL, DispositionReason.CHILD_UNDECIDED, excluded()))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("CHILD_UNDECIDED");
		}

		@Test
		@DisplayName("UNDECLARED_NOT_APPLICABLE over a child that did not exclude is refused")
		void undeclaredNotApplicableRequiresAnExcludedChild() {
			assertThatThrownBy(() -> attempt(AttemptDisposition.STAGE_FAILED,
					DispositionReason.UNDECLARED_NOT_APPLICABLE, decided()))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("UNDECLARED_NOT_APPLICABLE");

			assertThatThrownBy(() -> CompositeAttempt.stageFailed("rubric", CompositeRelation.CASCADE_TIER,
					RoutingRule.STOP_ON_ANY_OPINION_FAIL, DispositionReason.UNDECLARED_NOT_APPLICABLE, undecided()))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("UNDECLARED_NOT_APPLICABLE");
		}

		@Test
		@DisplayName("USED over a child that decided nothing is refused")
		void usedRefusesAnUndecidedChild() {
			assertThatCode(() -> CompositeAttempt.used("rubric", CompositeRelation.CASCADE_TIER,
					RoutingRule.STOP_ON_ANY_OPINION_FAIL, undecided()))
				.doesNotThrowAnyException();
			assertThat(CompositeAttempt
				.used("rubric", CompositeRelation.CASCADE_TIER, RoutingRule.STOP_ON_ANY_OPINION_FAIL, undecided())
				.routingDecision()
				.stops()).isTrue();
		}

	}

	@Nested
	@DisplayName("A reason that matches its child")
	class TrueReasons {

		@Test
		@DisplayName("every honest combination is still accepted")
		void honestCombinationsAreAccepted() {
			assertThatCode(() -> CompositeAttempt.stageFailed("rubric", CompositeRelation.CASCADE_TIER,
					RoutingRule.STOP_ON_ANY_OPINION_FAIL, DispositionReason.CHILD_UNDECIDED, undecided()))
				.doesNotThrowAnyException();
			assertThatCode(() -> CompositeAttempt.stageFailed("rubric", CompositeRelation.CASCADE_TIER,
					RoutingRule.STOP_ON_ANY_OPINION_FAIL, DispositionReason.UNDECLARED_NOT_APPLICABLE, excluded()))
				.doesNotThrowAnyException();
			assertThatCode(() -> CompositeAttempt.used("rubric", CompositeRelation.CASCADE_TIER,
					RoutingRule.STOP_ON_ANY_OPINION_FAIL, decided()))
				.doesNotThrowAnyException();
			assertThatCode(() -> CompositeAttempt.executionFailed("rubric", CompositeRelation.CASCADE_TIER,
					RoutingRule.STOP_ON_ANY_OPINION_FAIL,
					new CompositeFailure(CompositeFailureCode.JURY_EXECUTION_FAILED)))
				.doesNotThrowAnyException();
		}

		@Test
		@DisplayName("a determined D1 child is a legitimate USED member, since it decided")
		void aDeterminedRejectionIsUsable() {
			Verdict rejecting = CascadedJury.builder()
				.tier("rubric", ContainmentTest.returning(undecided()), RoutingRule.STOP_ON_ANY_OPINION_FAIL)
				.tier("semantic", ContainmentTest.returning(decided()), RoutingRule.FINAL_TIER)
				.build()
				.vote();

			assertThat(rejecting.provenance().basis()).isEqualTo(VerdictProvenanceBasis.INDIVIDUAL_REJECTION);
			assertThatCode(() -> CompositeAttempt.used("member", CompositeRelation.META_MEMBER, null, rejecting))
				.doesNotThrowAnyException();
		}

	}

	@Nested
	@DisplayName("Through the wire")
	class OnTheWire {

		@Test
		@DisplayName("a false reason in stored data is refused where it is read")
		void aFalseReasonIsRefusedOnRead() throws Exception {
			String honest = MAPPER
				.writeValueAsString(CompositeAttempt.stageFailed("rubric", CompositeRelation.CASCADE_TIER,
						RoutingRule.STOP_ON_ANY_OPINION_FAIL, DispositionReason.UNDECLARED_NOT_APPLICABLE, excluded()));
			String swapped = honest.replace("\"undeclared_not_applicable\"", "\"child_undecided\"");

			assertThat(swapped).isNotEqualTo(honest);
			assertThatThrownBy(() -> MAPPER.readValue(swapped, CompositeAttempt.class))
				.hasRootCauseInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("CHILD_UNDECIDED");
		}

		@Test
		@DisplayName("an honest attempt still round-trips")
		void anHonestAttemptRoundTrips() throws Exception {
			CompositeAttempt honest = CompositeAttempt.stageFailed("rubric", CompositeRelation.CASCADE_TIER,
					RoutingRule.STOP_ON_ANY_OPINION_FAIL, DispositionReason.UNDECLARED_NOT_APPLICABLE, excluded());

			assertThat(MAPPER.readValue(MAPPER.writeValueAsString(honest), CompositeAttempt.class)).isEqualTo(honest);
		}

	}

}
