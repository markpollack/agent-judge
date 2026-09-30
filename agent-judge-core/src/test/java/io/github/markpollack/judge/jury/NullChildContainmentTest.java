/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.Judges;
import io.github.markpollack.judge.completion.CompletionEvidence;
import io.github.markpollack.judge.judgment.Judgment;
import io.github.markpollack.judge.judgment.JudgmentReasonCode;
import io.github.markpollack.judge.judgment.JudgmentStatus;

import static io.github.markpollack.judge.jury.ContainmentTest.returning;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A child jury that returns nothing produced nothing, exactly like one that threw.
 *
 * <p>
 * Containment already caught the thrown child. The returned {@code null} slipped past it:
 * it left the invocation try block normally, and the boundary check one line later
 * dereferenced it, so the {@code NullPointerException} was raised <em>outside</em> the
 * handler written to catch exactly this kind of failure. A meta-jury then lost the
 * members that had succeeded, and a cascade never reached the healthy final tier standing
 * behind the broken one.
 * </p>
 *
 * <p>
 * The two failures are the same failure, so they are recorded the same way: the child is
 * validated inside the invocation boundary, and a non-productive return becomes a
 * {@code STAGE_FAILED / EXECUTION_FAILED} attempt with no verdict — because there is no
 * verdict — after which the existing meta and cascade rules apply unchanged.
 * </p>
 */
@DisplayName("A child jury that returns null")
class NullChildContainmentTest {

	private static final CompletionEvidence CONTEXT = CompletionEvidence.builder()
		.request("contain a null child")
		.build();

	private static Jury<CompletionEvidence> silent() {
		return returning(null);
	}

	private static Jury<CompletionEvidence> passing(String judgeName) {
		return SimpleJury.<CompletionEvidence>builder()
			.judge(Judges.named(context -> Judgment.pass("all good"), judgeName))
			.votingStrategy(new AllMustPassStrategy(ErrorPolicy.TREAT_AS_ABSTAIN))
			.build();
	}

	@Nested
	@DisplayName("In a meta-jury")
	class InAMetaJury {

		@Test
		@DisplayName("is a stage failure, and every member that succeeded is kept")
		void isAStageFailureThatKeepsItsOtherMembers() {
			Jury<CompletionEvidence> meta = Juries.meta(new AllMustPassStrategy(ErrorPolicy.TREAT_AS_ABSTAIN),
					new NamedJury<CompletionEvidence>("healthy", passing("first")),
					new NamedJury<CompletionEvidence>("silent", silent()));

			Verdict verdict = meta.vote(CONTEXT);

			assertThat(verdict.judgment().status()).isEqualTo(JudgmentStatus.ERROR);
			assertThat(verdict.judgment().reasonCode()).isEqualTo(JudgmentReasonCode.STAGE_FAILED);
			assertThat(verdict.provenance().kind()).isEqualTo(VerdictProvenanceKind.UNDECIDED);
			assertThat(verdict.individual()).as("the healthy member's work survives").hasSize(1);
			assertThat(verdict.individualByName()).containsOnlyKeys("healthy");

			CompositeAttempt silentAttempt = verdict.compositeAttempts().get(1);
			assertThat(silentAttempt.name()).isEqualTo("silent");
			assertThat(silentAttempt.disposition()).isEqualTo(AttemptDisposition.STAGE_FAILED);
			assertThat(silentAttempt.dispositionReason()).isEqualTo(DispositionReason.EXECUTION_FAILED);
			assertThat(silentAttempt.verdict()).as("there is no verdict, so none is recorded").isNull();
			assertThat(silentAttempt.failure()).isNotNull();
		}

	}

	@Nested
	@DisplayName("In a cascade")
	class InACascade {

		@Test
		@DisplayName("a non-final tier that returns null still lets the final tier decide")
		void aNonFinalNullTierReachesTheFinalTier() {
			Verdict verdict = CascadedJury.<CompletionEvidence>builder()
				.tier("silent", silent(), TierPolicy.REJECT_ON_ANY_FAIL)
				.tier("backstop", passing("backstop"), TierPolicy.FINAL_TIER)
				.build()
				.vote(CONTEXT);

			assertThat(verdict.judgment().status()).as("the healthy final tier decided").isEqualTo(JudgmentStatus.PASS);
			assertThat(verdict.provenance().kind()).isEqualTo(VerdictProvenanceKind.TIER);
			assertThat(verdict.provenance().tier()).isEqualTo("backstop");

			CompositeAttempt silentAttempt = verdict.compositeAttempts().get(0);
			assertThat(silentAttempt.disposition()).isEqualTo(AttemptDisposition.STAGE_FAILED);
			assertThat(silentAttempt.dispositionReason()).isEqualTo(DispositionReason.EXECUTION_FAILED);
			assertThat(silentAttempt.verdict()).isNull();
		}

		@Test
		@DisplayName("a final tier that returns null is no_tier_decided, not an escaping exception")
		void aFinalNullTierIsNoTierDecided() {
			Verdict verdict = CascadedJury.<CompletionEvidence>builder()
				.tier("silent", silent(), TierPolicy.FINAL_TIER)
				.build()
				.vote(CONTEXT);

			assertThat(verdict.judgment().status()).isEqualTo(JudgmentStatus.ERROR);
			assertThat(verdict.judgment().reasonCode()).isEqualTo(JudgmentReasonCode.NO_TIER_DECIDED);
			assertThat(verdict.provenance().kind()).isEqualTo(VerdictProvenanceKind.UNDECIDED);
			assertThat(verdict.compositeAttempts()).hasSize(1);
			assertThat(verdict.compositeAttempts().get(0).dispositionReason())
				.isEqualTo(DispositionReason.EXECUTION_FAILED);
		}

	}

	@Nested
	@DisplayName("Without widening what containment catches")
	class PreservedSemantics {

		@Test
		@DisplayName("a composite limit still escapes both parents")
		void aCompositeLimitStillEscapes() {
			Jury<CompletionEvidence> overLimit = ContainmentTest
				.throwing(new CompositeLimitExceededException("Composite attempt limit of 64 exceeded"));

			assertThatThrownBy(() -> Juries
				.meta(new AllMustPassStrategy(ErrorPolicy.TREAT_AS_ABSTAIN),
						new NamedJury<CompletionEvidence>("healthy", passing("first")),
						new NamedJury<CompletionEvidence>("limit", overLimit))
				.vote(CONTEXT)).isInstanceOf(CompositeLimitExceededException.class);

			assertThatThrownBy(() -> CascadedJury.<CompletionEvidence>builder()
				.tier("limit", overLimit, TierPolicy.REJECT_ON_ANY_FAIL)
				.tier("backstop", passing("backstop"), TierPolicy.FINAL_TIER)
				.build()
				.vote(CONTEXT)).isInstanceOf(CompositeLimitExceededException.class);
		}

		@Test
		@DisplayName("an Error still escapes both parents")
		void anErrorStillEscapes() {
			Jury<CompletionEvidence> broken = new Jury<CompletionEvidence>() {
				@Override
				public List<Judge<CompletionEvidence>> getJudges() {
					return List.of();
				}

				@Override
				public VotingStrategy getVotingStrategy() {
					return new ConsensusStrategy();
				}

				@Override
				public Verdict vote(CompletionEvidence context) {
					throw new StackOverflowError("no stack left to report on");
				}
			};

			assertThatThrownBy(() -> Juries
				.meta(new AllMustPassStrategy(ErrorPolicy.TREAT_AS_ABSTAIN),
						new NamedJury<CompletionEvidence>("broken", broken))
				.vote(CONTEXT)).isInstanceOf(StackOverflowError.class);

			assertThatThrownBy(() -> CascadedJury.<CompletionEvidence>builder()
				.tier("broken", broken, TierPolicy.FINAL_TIER)
				.build()
				.vote(CONTEXT)).isInstanceOf(StackOverflowError.class);
		}

	}

}
