/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.JudgeMetadata;
import io.github.markpollack.judge.JudgeType;
import io.github.markpollack.judge.JudgeWithMetadata;
import io.github.markpollack.judge.Judges;
import io.github.markpollack.judge.context.JudgmentContext;
import io.github.markpollack.judge.result.Judgment;
import io.github.markpollack.judge.result.JudgmentReasonCode;
import io.github.markpollack.judge.result.JudgmentStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A name a seat cannot be built from never reaches the seat.
 *
 * <p>
 * A blank judge name is not a naming preference, it is a jury that cannot say who voted. The
 * seat rejects it — correctly — but rejecting it <em>after</em> every judge has already run puts
 * the exception outside containment, and one such seat then discards every other judge's result
 * and collapses the enclosing cascade tier. That is exactly the invariant a jury exists to hold:
 * it must never vote with fewer judges than it lists.
 * </p>
 *
 * <p>
 * So the name is refused where it is made, before anything is spent. A judge that builds its
 * metadata lazily cannot be caught that early, and for it the existing unreadable-metadata
 * containment applies unchanged: the seat is an {@code ERROR judge_metadata_unreadable} under
 * its positional key, the judge does not run, and every other judge is untouched.
 * </p>
 */
@DisplayName("Seat name validity")
class SeatNameValidityTest {

	private static final JudgmentContext CONTEXT = JudgmentContext.builder().goal("name the seats").build();

	/** A judge whose metadata is only built when it is asked for. */
	private record LazilyNamed(String name, Judgment judgment) implements JudgeWithMetadata {

		@Override
		public Judgment judge(JudgmentContext context) {
			return this.judgment;
		}

		@Override
		public JudgeMetadata metadata() {
			return new JudgeMetadata(this.name, "built on demand", JudgeType.DETERMINISTIC, null);
		}

	}

	private static SimpleJury juryWith(Judge blankNamed, boolean parallel) {
		return SimpleJury.builder()
			.judge(Judges.named(context -> Judgment.pass("the build succeeded"), "healthy"))
			.judge(blankNamed)
			.votingStrategy(new MajorityVotingStrategy(TiePolicy.FAIL, ErrorPolicy.TREAT_AS_ABSTAIN))
			.parallel(parallel)
			.build();
	}

	@Nested
	@DisplayName("Refused where it is made")
	class RefusedEarly {

		@ParameterizedTest
		@ValueSource(strings = { "", " ", "   ", "\t", "\n" })
		@DisplayName("a blank judge name is refused by the metadata that would carry it")
		void metadataRefusesABlankName(String blank) {
			assertThatThrownBy(() -> new JudgeMetadata(blank, "a judge", JudgeType.DETERMINISTIC, null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("name must be non-blank");
		}

		@Test
		@DisplayName("a blank name is refused by the naming wrapper, before any jury is assembled")
		void namedRefusesABlankName() {
			Judge judge = context -> Judgment.pass("ok");

			assertThatThrownBy(() -> Judges.named(judge, "   ")).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("name must be non-blank");
		}

		@Test
		@DisplayName("an ordinary name is unaffected")
		void anOrdinaryNameIsUnaffected() {
			assertThatCode(() -> new JudgeMetadata("style", "a judge", JudgeType.DETERMINISTIC, null))
				.doesNotThrowAnyException();
		}

	}

	@Nested
	@DisplayName("Contained when it is built too late to refuse")
	class ContainedLate {

		@Test
		@DisplayName("a blank-named judge leaves every other result intact, in parallel")
		void aBlankNameIsContainedInParallel() {
			assertContained(juryWith(new LazilyNamed("   ", Judgment.pass("a judgment the jury must not keep")), true));
		}

		@Test
		@DisplayName("a blank-named judge leaves every other result intact, sequentially")
		void aBlankNameIsContainedSequentially() {
			assertContained(juryWith(new LazilyNamed("   ", Judgment.pass("a judgment the jury must not keep")), false));
		}

		private static void assertContained(SimpleJury jury) {
			Verdict verdict = jury.vote(CONTEXT);

			assertThat(verdict.individual()).as("every configured judge is represented").hasSize(2);
			assertThat(verdict.individual().get(0).status()).as("the healthy judge's result survives")
				.isEqualTo(JudgmentStatus.PASS);

			Judgment blankSeat = verdict.individual().get(1);
			assertThat(blankSeat.status()).isEqualTo(JudgmentStatus.ERROR);
			assertThat(blankSeat.reasonCode()).isEqualTo(JudgmentReasonCode.JUDGE_METADATA_UNREADABLE);
			assertThat(blankSeat.reasoning()).contains("position 1").contains("name must be non-blank");

			assertThat(verdict.individualByName().keySet()).containsExactly("healthy", "Judge#2");
			assertThat(verdict.seats().get(1).verdictKey()).isEqualTo("Judge#2");
			assertThat(verdict.aggregated().status()).as("the jury still reaches a verdict")
				.isEqualTo(JudgmentStatus.PASS);
		}

		@Test
		@DisplayName("the jury still builds, and describing it fails loudly naming the seat")
		void buildAndDescribeAgreeWithTheVote() {
			SimpleJury jury = juryWith(new LazilyNamed("   ", Judgment.pass("unused")), false);

			assertThatThrownBy(jury::describe).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("seats[1]")
				.hasMessageContaining("name must be non-blank");
		}

	}

	@Nested
	@DisplayName("Inside a cascade")
	class InsideACascade {

		@Test
		@DisplayName("a blank-named seat does not collapse its tier")
		void aBlankNameDoesNotCollapseItsTier() {
			Jury tier = juryWith(new LazilyNamed("   ", Judgment.pass("unused")), false);

			Verdict verdict = CascadedJury.builder()
				.tier("gate", tier, TierPolicy.REJECT_ON_ANY_FAIL)
				.tier("final", SimpleJury.builder()
					.judge(Judges.named(context -> Judgment.pass("also fine"), "backstop"))
					.votingStrategy(new AllMustPassStrategy(ErrorPolicy.TREAT_AS_ABSTAIN))
					.build(), TierPolicy.FINAL_TIER)
				.build()
				.vote(CONTEXT);

			assertThat(verdict.compositeAttempts()).isNotEmpty();
			assertThat(verdict.compositeAttempts().get(0).verdict()).as("the tier produced a verdict").isNotNull();
			assertThat(verdict.compositeAttempts().get(0).verdict().individual()).hasSize(2);
		}

	}

}
