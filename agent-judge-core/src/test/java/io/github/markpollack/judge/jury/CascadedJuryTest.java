/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury;

import org.junit.jupiter.api.Test;
import io.github.markpollack.judge.completion.CompletionEvidence;
import io.github.markpollack.judge.judgment.JudgmentStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static io.github.markpollack.judge.JudgeTestFixtures.*;

/**
 * Tests for {@link CascadedJury}.
 *
 * @author Mark Pollack
 * @since 0.9.0
 */
class CascadedJuryTest {

	private final CompletionEvidence context = simpleContext("Test goal");

	// ==================== STOP_ON_ANY_OPINION_FAIL policy ====================

	@Test
	void rejectOnAnyFailStopsOnFirstFailure() {
		Jury tier1 = SimpleJury.builder()
			.judge(alwaysPass("Build"))
			.judge(alwaysFail("Migration"))
			.votingStrategy(new ConsensusStrategy())
			.parallel(false)
			.build();

		Jury finalTier = SimpleJury.builder()
			.judge(alwaysPass("Final"))
			.votingStrategy(new MajorityVotingStrategy())
			.build();

		CascadedJury jury = CascadedJury.builder()
			.tier("deterministic", tier1, RoutingRule.STOP_ON_ANY_OPINION_FAIL)
			.tier("final", finalTier, RoutingRule.FINAL_TIER)
			.build();

		Verdict verdict = jury.vote();

		// The cascade provenance is unchanged by M5: STOP_ON_ANY_OPINION_FAIL inspects
		// the
		// tier's
		// INDIVIDUAL judgments, sees Migration's FAIL, and stops without escalating.
		assertThat(verdict.compositeAttempts()).hasSize(1); // only tier1 executed
		assertThat(verdict.individual()).anyMatch(j -> j.status() == JudgmentStatus.FAIL);

		// The copied aggregate reports the split panel as ABSTAIN. The gate rejected on
		// the individual FAIL regardless, which is the separation M5 exists to keep: the
		// aggregate states the collective fact, the tier policy decides acceptance.
		assertThat(verdict.judgment().status()).isEqualTo(JudgmentStatus.ABSTAIN);
		assertThat(verdict.judgment().reasoning()).contains("No consensus");
	}

	@Test
	void rejectOnAnyFailEscalatesWhenAllPass() {
		Jury tier1 = SimpleJury.builder()
			.judge(alwaysPass("Build"))
			.judge(alwaysPass("Migration"))
			.votingStrategy(new ConsensusStrategy())
			.parallel(false)
			.build();

		Jury finalTier = SimpleJury.builder()
			.judge(alwaysPass("Semantic"))
			.votingStrategy(new MajorityVotingStrategy())
			.build();

		CascadedJury jury = CascadedJury.builder()
			.tier("deterministic", tier1, RoutingRule.STOP_ON_ANY_OPINION_FAIL)
			.tier("final", finalTier, RoutingRule.FINAL_TIER)
			.build();

		Verdict verdict = jury.vote();

		assertThat(verdict.judgment().status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(verdict.compositeAttempts()).hasSize(2); // both tiers executed
	}

	// ==================== STOP_ON_ALL_OPINIONS_PASS policy ====================

	@Test
	void acceptOnAllPassAcceptsWhenAllPass() {
		Jury tier2 = SimpleJury.builder()
			.judge(alwaysPass("Import"))
			.judge(alwaysPass("Annotation"))
			.votingStrategy(new ConsensusStrategy())
			.parallel(false)
			.build();

		Jury finalTier = SimpleJury.builder()
			.judge(alwaysPass("Semantic"))
			.votingStrategy(new MajorityVotingStrategy())
			.build();

		CascadedJury jury = CascadedJury.builder()
			.tier("structural", tier2, RoutingRule.STOP_ON_ALL_OPINIONS_PASS)
			.tier("final", finalTier, RoutingRule.FINAL_TIER)
			.build();

		Verdict verdict = jury.vote();

		assertThat(verdict.judgment().status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(verdict.compositeAttempts()).hasSize(1); // accepted at tier2
	}

	@Test
	void acceptOnAllPassEscalatesWhenAnyFails() {
		Jury tier2 = SimpleJury.builder()
			.judge(alwaysPass("Import"))
			.judge(alwaysFail("Annotation"))
			.votingStrategy(new ConsensusStrategy())
			.parallel(false)
			.build();

		Jury finalTier = SimpleJury.builder()
			.judge(alwaysPass("Semantic"))
			.votingStrategy(new MajorityVotingStrategy())
			.build();

		CascadedJury jury = CascadedJury.builder()
			.tier("structural", tier2, RoutingRule.STOP_ON_ALL_OPINIONS_PASS)
			.tier("final", finalTier, RoutingRule.FINAL_TIER)
			.build();

		Verdict verdict = jury.vote();

		assertThat(verdict.judgment().status()).isEqualTo(JudgmentStatus.PASS); // final
																				// tier
																				// passes
		assertThat(verdict.compositeAttempts()).hasSize(2); // escalated to final tier

		// The escalated tier's own aggregate abstained because its judges disagreed.
		// STOP_ON_ALL_OPINIONS_PASS did not read it, so the split panel could not be
		// accepted.
		assertThat(verdict.compositeAttempts().get(0).verdict().judgment().status()).isEqualTo(JudgmentStatus.ABSTAIN);
	}

	// ==================== M5 gate boundary ====================

	/**
	 * M5 makes a split panel's aggregate ABSTAIN. That must not weaken an all-pass gate:
	 * STOP_ON_ALL_OPINIONS_PASS reads the tier's individual judgments, so a disagreeing
	 * tier is escalated rather than accepted, exactly as when the aggregate still said
	 * FAIL.
	 */
	@Test
	void abstainingConsensusAggregateDoesNotWeakenAcceptOnAllPass() {
		Jury splitTier = SimpleJury.builder()
			.judge(alwaysPass("Import"))
			.judge(alwaysFail("Annotation"))
			.votingStrategy(new ConsensusStrategy())
			.parallel(false)
			.build();

		Jury finalTier = SimpleJury.builder()
			.judge(alwaysFail("Semantic"))
			.votingStrategy(new MajorityVotingStrategy())
			.build();

		CascadedJury jury = CascadedJury.builder()
			.tier("structural", splitTier, RoutingRule.STOP_ON_ALL_OPINIONS_PASS)
			.tier("final", finalTier, RoutingRule.FINAL_TIER)
			.build();

		Verdict verdict = jury.vote();

		// The split tier abstained as an aggregate...
		assertThat(verdict.compositeAttempts()).hasSize(2);
		assertThat(verdict.compositeAttempts().get(0).verdict().judgment().status()).isEqualTo(JudgmentStatus.ABSTAIN);

		// ...and the gate still refused to accept it, ending on the final tier's FAIL.
		// If STOP_ON_ALL_OPINIONS_PASS had read the aggregate, a non-PASS could never
		// accept
		// either; the guard is that an ABSTAIN aggregate does not turn a disagreeing
		// tier into an accepted one.
		assertThat(verdict.judgment().status()).isEqualTo(JudgmentStatus.FAIL);
	}

	/**
	 * The same separation on the rejecting side: a disagreeing tier still stops the
	 * cascade on its individual FAIL, even though the aggregate now abstains.
	 */
	@Test
	void abstainingConsensusAggregateStillRejectsOnAnyIndividualFail() {
		Jury splitTier = SimpleJury.builder()
			.judge(alwaysPass("Build"))
			.judge(alwaysFail("Migration"))
			.votingStrategy(new ConsensusStrategy())
			.parallel(false)
			.build();

		Jury finalTier = SimpleJury.builder()
			.judge(alwaysPass("Semantic"))
			.votingStrategy(new MajorityVotingStrategy())
			.build();

		CascadedJury jury = CascadedJury.builder()
			.tier("deterministic", splitTier, RoutingRule.STOP_ON_ANY_OPINION_FAIL)
			.tier("final", finalTier, RoutingRule.FINAL_TIER)
			.build();

		Verdict verdict = jury.vote();

		assertThat(verdict.compositeAttempts()).hasSize(1); // stopped, never escalated
		assertThat(verdict.judgment().status()).isEqualTo(JudgmentStatus.ABSTAIN);
		assertThat(verdict.individual()).anyMatch(j -> j.status() == JudgmentStatus.FAIL);
	}

	// ==================== FINAL_TIER policy ====================

	@Test
	void finalTierAlwaysProducesVerdict() {
		Jury finalTier = SimpleJury.builder()
			.judge(alwaysFail("Semantic"))
			.votingStrategy(new MajorityVotingStrategy())
			.build();

		CascadedJury jury = CascadedJury.builder().tier("semantic", finalTier, RoutingRule.FINAL_TIER).build();

		Verdict verdict = jury.vote();

		assertThat(verdict.judgment().status()).isEqualTo(JudgmentStatus.FAIL);
		assertThat(verdict.compositeAttempts()).hasSize(1);
	}

	// ==================== Tier tracing ====================

	@Test
	void compositeAttemptsContainCorrectPerTierVerdicts() {
		Jury tier1 = SimpleJury.builder()
			.judge(alwaysPass("Build"))
			.votingStrategy(new MajorityVotingStrategy())
			.parallel(false)
			.build();

		Jury tier2 = SimpleJury.builder()
			.judge(alwaysPass("Import"))
			.votingStrategy(new MajorityVotingStrategy())
			.parallel(false)
			.build();

		Jury finalTier = SimpleJury.builder()
			.judge(alwaysPass("Semantic"))
			.votingStrategy(new MajorityVotingStrategy())
			.build();

		CascadedJury jury = CascadedJury.builder()
			.tier("deterministic", tier1, RoutingRule.STOP_ON_ANY_OPINION_FAIL)
			.tier("structural", tier2, RoutingRule.STOP_ON_ALL_OPINIONS_PASS)
			.tier("semantic", finalTier, RoutingRule.FINAL_TIER)
			.build();

		Verdict verdict = jury.vote();

		// Tier 1 passes (no fail) → escalate; Tier 2 all pass → accept
		assertThat(verdict.compositeAttempts()).hasSize(2);
		assertThat(verdict.compositeAttempts().get(0).verdict().judgment().status()).isEqualTo(JudgmentStatus.PASS); // tier1
		assertThat(verdict.compositeAttempts().get(1).verdict().judgment().status()).isEqualTo(JudgmentStatus.PASS); // tier2
	}

	@Test
	void onlyExecutedTiersAppearInCompositeAttempts() {
		Jury tier1 = SimpleJury.builder()
			.judge(alwaysFail("Build"))
			.votingStrategy(new MajorityVotingStrategy())
			.parallel(false)
			.build();

		Jury tier2 = SimpleJury.builder()
			.judge(alwaysPass("Import"))
			.votingStrategy(new MajorityVotingStrategy())
			.build();

		Jury finalTier = SimpleJury.builder()
			.judge(alwaysPass("Semantic"))
			.votingStrategy(new MajorityVotingStrategy())
			.build();

		CascadedJury jury = CascadedJury.builder()
			.tier("deterministic", tier1, RoutingRule.STOP_ON_ANY_OPINION_FAIL)
			.tier("structural", tier2, RoutingRule.STOP_ON_ALL_OPINIONS_PASS)
			.tier("semantic", finalTier, RoutingRule.FINAL_TIER)
			.build();

		Verdict verdict = jury.vote();

		// Tier 1 has a FAIL → rejected at tier 1
		assertThat(verdict.judgment().status()).isEqualTo(JudgmentStatus.FAIL);
		assertThat(verdict.compositeAttempts()).hasSize(1); // only tier1 ran
	}

	// ==================== Error handling ====================

	@Test
	void tierExceptionCaughtAndEscalated() {
		Jury throwingTier = new ThrowingJury();

		Jury finalTier = SimpleJury.builder()
			.judge(alwaysPass("Fallback"))
			.votingStrategy(new MajorityVotingStrategy())
			.build();

		CascadedJury jury = CascadedJury.builder()
			.tier("broken", throwingTier, RoutingRule.STOP_ON_ANY_OPINION_FAIL)
			.tier("final", finalTier, RoutingRule.FINAL_TIER)
			.build();

		Verdict verdict = jury.vote();

		assertThat(verdict.judgment().status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(verdict.compositeAttempts()).hasSize(2);
		assertThat(verdict.compositeAttempts().get(0).failure().code())
			.isEqualTo(CompositeFailureCode.JURY_EXECUTION_FAILED);
		assertThat(verdict.compositeAttempts().get(1).verdict()).isNotNull();
	}

	@Test
	void finalTierExceptionReturnsErrorVerdict() {
		Jury throwingFinal = new ThrowingJury();

		CascadedJury jury = CascadedJury.builder().tier("final", throwingFinal, RoutingRule.FINAL_TIER).build();

		Verdict verdict = jury.vote();

		assertThat(verdict.judgment().status()).isEqualTo(JudgmentStatus.ERROR);
		assertThat(verdict.judgment().reasoning()).isEqualTo("The final cascade tier failed to execute.");
		assertThat(verdict.compositeAttempts()).singleElement().satisfies(attempt -> {
			assertThat(attempt.verdict()).isNull();
			assertThat(attempt.failure().code()).isEqualTo(CompositeFailureCode.JURY_EXECUTION_FAILED);
		});
	}

	// ==================== Edge cases ====================

	@Test
	void singleTierCascade() {
		Jury onlyTier = SimpleJury.builder()
			.judge(alwaysPass("Judge1"))
			.judge(alwaysPass("Judge2"))
			.votingStrategy(new ConsensusStrategy())
			.parallel(false)
			.build();

		CascadedJury jury = CascadedJury.builder().tier("only", onlyTier, RoutingRule.FINAL_TIER).build();

		Verdict verdict = jury.vote();

		assertThat(verdict.judgment().status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(verdict.compositeAttempts()).hasSize(1);
		assertThat(verdict.individual()).hasSize(2);
	}

	@Test
	void allJudgesAbstainInTierWithRejectPolicy() {
		Jury tier1 = SimpleJury.builder()
			.judge(alwaysAbstain("Abstainer1"))
			.judge(alwaysAbstain("Abstainer2"))
			.votingStrategy(new ConsensusStrategy())
			.parallel(false)
			.build();

		Jury finalTier = SimpleJury.builder()
			.judge(alwaysPass("Semantic"))
			.votingStrategy(new MajorityVotingStrategy())
			.build();

		CascadedJury jury = CascadedJury.builder()
			.tier("deterministic", tier1, RoutingRule.STOP_ON_ANY_OPINION_FAIL)
			.tier("final", finalTier, RoutingRule.FINAL_TIER)
			.build();

		Verdict verdict = jury.vote();

		// ABSTAIN is not FAIL, so STOP_ON_ANY_OPINION_FAIL escalates
		assertThat(verdict.compositeAttempts()).hasSize(2);
		assertThat(verdict.judgment().status()).isEqualTo(JudgmentStatus.PASS);
	}

	@Test
	void mixOfPassAndAbstainEscalatesForRejectPolicy() {
		Jury tier1 = SimpleJury.builder()
			.judge(alwaysPass("Build"))
			.judge(alwaysAbstain("Coverage"))
			.votingStrategy(new ConsensusStrategy())
			.parallel(false)
			.build();

		Jury finalTier = SimpleJury.builder()
			.judge(alwaysPass("Final"))
			.votingStrategy(new MajorityVotingStrategy())
			.build();

		CascadedJury jury = CascadedJury.builder()
			.tier("deterministic", tier1, RoutingRule.STOP_ON_ANY_OPINION_FAIL)
			.tier("final", finalTier, RoutingRule.FINAL_TIER)
			.build();

		Verdict verdict = jury.vote();

		// ABSTAIN is not FAIL → no rejection → escalate to final
		assertThat(verdict.compositeAttempts()).hasSize(2);
	}

	@Test
	void acceptOnAllPassEscalatesWhenAbstainPresent() {
		Jury tier2 = SimpleJury.builder()
			.judge(alwaysPass("Import"))
			.judge(alwaysAbstain("AST"))
			.votingStrategy(new ConsensusStrategy())
			.parallel(false)
			.build();

		Jury finalTier = SimpleJury.builder()
			.judge(alwaysPass("Semantic"))
			.votingStrategy(new MajorityVotingStrategy())
			.build();

		CascadedJury jury = CascadedJury.builder()
			.tier("structural", tier2, RoutingRule.STOP_ON_ALL_OPINIONS_PASS)
			.tier("final", finalTier, RoutingRule.FINAL_TIER)
			.build();

		Verdict verdict = jury.vote();

		// ABSTAIN is not PASS → STOP_ON_ALL_OPINIONS_PASS escalates
		assertThat(verdict.compositeAttempts()).hasSize(2);
	}

	// ==================== Builder validation ====================

	@Test
	void builderRejectsEmptyTiers() {
		assertThatThrownBy(() -> CascadedJury.builder().build()).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("at least one tier");
	}

	@Test
	void builderRejectsNonFinalTierAsLast() {
		Jury jury = SimpleJury.builder()
			.judge(alwaysPass("Judge1"))
			.votingStrategy(new MajorityVotingStrategy())
			.build();

		assertThatThrownBy(
				() -> CascadedJury.builder().tier("only", jury, RoutingRule.STOP_ON_ANY_OPINION_FAIL).build())
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("FINAL_TIER");
	}

	// ==================== Integration: 3-tier cascade ====================

	@Test
	void threeTierCascadeWithAllPassingTiers() {
		Jury tier1 = SimpleJury.builder()
			.judge(alwaysPass("Build"))
			.judge(alwaysPass("Migration"))
			.judge(alwaysPass("Tests"))
			.votingStrategy(new ConsensusStrategy())
			.parallel(false)
			.build();

		Jury tier2 = SimpleJury.builder()
			.judge(alwaysPass("ImportDiff"))
			.judge(alwaysPass("ASTDiff"))
			.votingStrategy(new ConsensusStrategy())
			.parallel(false)
			.build();

		Jury tier3 = SimpleJury.builder()
			.judge(alwaysPass("Semantic"))
			.votingStrategy(new MajorityVotingStrategy())
			.build();

		CascadedJury jury = CascadedJury.builder()
			.tier("deterministic", tier1, RoutingRule.STOP_ON_ANY_OPINION_FAIL)
			.tier("structural", tier2, RoutingRule.STOP_ON_ALL_OPINIONS_PASS)
			.tier("semantic", tier3, RoutingRule.FINAL_TIER)
			.build();

		Verdict verdict = jury.vote();

		assertThat(verdict.judgment().status()).isEqualTo(JudgmentStatus.PASS);
		// Tier 1: no fails → escalate; Tier 2: all pass → accept
		assertThat(verdict.compositeAttempts()).hasSize(2);
	}

	@Test
	void threeTierCascadeEscalatesAllTheWayToFinal() {
		Jury tier1 = SimpleJury.builder()
			.judge(alwaysPass("Build"))
			.votingStrategy(new MajorityVotingStrategy())
			.parallel(false)
			.build();

		Jury tier2 = SimpleJury.builder()
			.judge(alwaysPass("Import"))
			.judge(alwaysFail("AST"))
			.votingStrategy(new ConsensusStrategy())
			.parallel(false)
			.build();

		Jury tier3 = SimpleJury.builder()
			.judge(alwaysPass("Semantic"))
			.votingStrategy(new MajorityVotingStrategy())
			.build();

		CascadedJury jury = CascadedJury.builder()
			.tier("deterministic", tier1, RoutingRule.STOP_ON_ANY_OPINION_FAIL)
			.tier("structural", tier2, RoutingRule.STOP_ON_ALL_OPINIONS_PASS)
			.tier("semantic", tier3, RoutingRule.FINAL_TIER)
			.build();

		Verdict verdict = jury.vote();

		assertThat(verdict.judgment().status()).isEqualTo(JudgmentStatus.PASS);
		// Tier 1: no fail → escalate; Tier 2: AST failed → escalate; Tier 3: pass
		assertThat(verdict.compositeAttempts()).hasSize(3);
	}

	// ==================== getJudges / getVotingStrategy ====================

	@Test
	void exposesConfiguredTiers() {
		Jury tier1 = SimpleJury.builder()
			.judge(alwaysPass("J1"))
			.judge(alwaysPass("J2"))
			.votingStrategy(new MajorityVotingStrategy())
			.build();

		Jury tier2 = SimpleJury.builder().judge(alwaysPass("J3")).votingStrategy(new MajorityVotingStrategy()).build();

		CascadedJury jury = CascadedJury.builder()
			.tier("t1", tier1, RoutingRule.STOP_ON_ANY_OPINION_FAIL)
			.tier("t2", tier2, RoutingRule.FINAL_TIER)
			.build();

		assertThat(jury.tiers()).extracting(TierConfig::name).containsExactly("t1", "t2");
	}

	@Test
	void cascadeDoesNotPretendToBeAVotingJury() {
		Jury tier = SimpleJury.builder().judge(alwaysPass("J1")).votingStrategy(new MajorityVotingStrategy()).build();

		CascadedJury jury = CascadedJury.builder().tier("final", tier, RoutingRule.FINAL_TIER).build();

		assertThat(jury).isNotInstanceOf(VotingJury.class);
	}

	// ==================== Helper ====================

	/**
	 * A jury that throws an exception on vote().
	 */
	private static class ThrowingJury implements VotingJury {

		@Override
		public java.util.List<io.github.markpollack.judge.Judge> getJudges() {
			return java.util.List.of();
		}

		@Override
		public VotingStrategy getVotingStrategy() {
			return null;
		}

		@Override
		public Verdict vote() {
			throw new RuntimeException("Tier exploded");
		}

	}

	// ==================== A failing judge does not collapse its tier
	// ====================

	@Test
	void throwingJudgeDoesNotCollapseItsCascadeTier() {
		// Before the fix, the judge's exception escaped SimpleJury and CascadedJury
		// recorded the whole tier as JURY_EXECUTION_FAILED — every other judge in the
		// tier lost, and the item scored as though the subject had produced nothing.
		Jury scoring = SimpleJury.builder()
			.judge(alwaysPass("Build"))
			.judge(alwaysThrows("Coverage", new IllegalStateException("rate limited")))
			.judge(alwaysPass("Style"))
			.votingStrategy(new MajorityVotingStrategy(TieBreakRule.FAIL, ErrorHandling.TREAT_AS_ABSTAIN))
			.build();

		CascadedJury jury = CascadedJury.builder().tier("scoring", scoring, RoutingRule.FINAL_TIER).build();

		Verdict verdict = jury.vote();

		assertThat(verdict.compositeAttempts()).hasSize(1);
		CompositeAttempt attempt = verdict.compositeAttempts().get(0);
		assertThat(attempt.failure()).isNull();
		assertThat(attempt.verdict()).isNotNull();
		assertThat(verdict.individual()).hasSize(3);
		assertThat(verdict.judgment().status()).isEqualTo(JudgmentStatus.PASS);
	}

	@Test
	@SuppressWarnings("unchecked")
	void judgeWhoseMetadataFailsDoesNotCollapseItsCascadeTier() {
		// Before the fix, reading the judge's name threw out of SimpleJury.vote() and the
		// cascade recorded the whole tier as JURY_EXECUTION_FAILED.
		Jury scoring = SimpleJury.builder()
			.judge(alwaysPass("Build"))
			.judge(nullMetadata(booleanPass("never kept")))
			.judge(throwingMetadata(new IllegalStateException("registry offline"), booleanPass("never kept")))
			.judge(alwaysPass("Style"))
			.votingStrategy(new MajorityVotingStrategy(TieBreakRule.FAIL, ErrorHandling.TREAT_AS_ABSTAIN))
			.build();

		CascadedJury jury = CascadedJury.builder().tier("scoring", scoring, RoutingRule.FINAL_TIER).build();

		Verdict verdict = jury.vote();

		assertThat(verdict.compositeAttempts()).hasSize(1);
		CompositeAttempt attempt = verdict.compositeAttempts().get(0);
		assertThat(attempt.failure()).isNull();
		assertThat(attempt.verdict()).isNotNull();
		assertThat(verdict.individual()).extracting(io.github.markpollack.judge.judgment.Judgment::status)
			.containsExactly(JudgmentStatus.PASS, JudgmentStatus.ERROR, JudgmentStatus.ERROR, JudgmentStatus.PASS);
		assertThat(verdict.judgment().status()).isEqualTo(JudgmentStatus.PASS);
		assertThat((java.util.Map<String, Object>) verdict.judgment()
			.metadata()
			.get(io.github.markpollack.judge.judgment.Judgment.AGGREGATION_KEY))
			.containsEntry(AggregationEvidence.INPUT_COUNT, 4);
	}

}
