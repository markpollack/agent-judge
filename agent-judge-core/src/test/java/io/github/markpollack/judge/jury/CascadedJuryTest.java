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

import org.junit.jupiter.api.Test;
import io.github.markpollack.judge.context.JudgmentContext;
import io.github.markpollack.judge.result.JudgmentStatus;

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

	private final JudgmentContext context = simpleContext("Test goal");

	// ==================== REJECT_ON_ANY_FAIL policy ====================

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
			.tier("deterministic", tier1, TierPolicy.REJECT_ON_ANY_FAIL)
			.tier("final", finalTier, TierPolicy.FINAL_TIER)
			.build();

		Verdict verdict = jury.vote(context);

		assertThat(verdict.aggregated().status()).isEqualTo(JudgmentStatus.FAIL);
		assertThat(verdict.subVerdicts()).hasSize(1); // only tier1 executed
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
			.tier("deterministic", tier1, TierPolicy.REJECT_ON_ANY_FAIL)
			.tier("final", finalTier, TierPolicy.FINAL_TIER)
			.build();

		Verdict verdict = jury.vote(context);

		assertThat(verdict.aggregated().status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(verdict.subVerdicts()).hasSize(2); // both tiers executed
	}

	// ==================== ACCEPT_ON_ALL_PASS policy ====================

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
			.tier("structural", tier2, TierPolicy.ACCEPT_ON_ALL_PASS)
			.tier("final", finalTier, TierPolicy.FINAL_TIER)
			.build();

		Verdict verdict = jury.vote(context);

		assertThat(verdict.aggregated().status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(verdict.subVerdicts()).hasSize(1); // accepted at tier2
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
			.tier("structural", tier2, TierPolicy.ACCEPT_ON_ALL_PASS)
			.tier("final", finalTier, TierPolicy.FINAL_TIER)
			.build();

		Verdict verdict = jury.vote(context);

		assertThat(verdict.aggregated().status()).isEqualTo(JudgmentStatus.PASS); // final
																					// tier
																					// passes
		assertThat(verdict.subVerdicts()).hasSize(2); // escalated to final tier
	}

	// ==================== FINAL_TIER policy ====================

	@Test
	void finalTierAlwaysProducesVerdict() {
		Jury finalTier = SimpleJury.builder()
			.judge(alwaysFail("Semantic"))
			.votingStrategy(new MajorityVotingStrategy())
			.build();

		CascadedJury jury = CascadedJury.builder().tier("semantic", finalTier, TierPolicy.FINAL_TIER).build();

		Verdict verdict = jury.vote(context);

		assertThat(verdict.aggregated().status()).isEqualTo(JudgmentStatus.FAIL);
		assertThat(verdict.subVerdicts()).hasSize(1);
	}

	// ==================== Tier tracing ====================

	@Test
	void subVerdictsContainsCorrectPerTierVerdicts() {
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
			.tier("deterministic", tier1, TierPolicy.REJECT_ON_ANY_FAIL)
			.tier("structural", tier2, TierPolicy.ACCEPT_ON_ALL_PASS)
			.tier("semantic", finalTier, TierPolicy.FINAL_TIER)
			.build();

		Verdict verdict = jury.vote(context);

		// Tier 1 passes (no fail) → escalate; Tier 2 all pass → accept
		assertThat(verdict.subVerdicts()).hasSize(2);
		assertThat(verdict.subVerdicts().get(0).aggregated().status()).isEqualTo(JudgmentStatus.PASS); // tier1
		assertThat(verdict.subVerdicts().get(1).aggregated().status()).isEqualTo(JudgmentStatus.PASS); // tier2
	}

	@Test
	void onlyExecutedTiersAppearInSubVerdicts() {
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
			.tier("deterministic", tier1, TierPolicy.REJECT_ON_ANY_FAIL)
			.tier("structural", tier2, TierPolicy.ACCEPT_ON_ALL_PASS)
			.tier("semantic", finalTier, TierPolicy.FINAL_TIER)
			.build();

		Verdict verdict = jury.vote(context);

		// Tier 1 has a FAIL → rejected at tier 1
		assertThat(verdict.aggregated().status()).isEqualTo(JudgmentStatus.FAIL);
		assertThat(verdict.subVerdicts()).hasSize(1); // only tier1 ran
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
			.tier("broken", throwingTier, TierPolicy.REJECT_ON_ANY_FAIL)
			.tier("final", finalTier, TierPolicy.FINAL_TIER)
			.build();

		Verdict verdict = jury.vote(context);

		assertThat(verdict.aggregated().status()).isEqualTo(JudgmentStatus.PASS);
		// The throwing tier's verdict is not in subVerdicts (it never produced one)
		assertThat(verdict.subVerdicts()).hasSize(1);
	}

	@Test
	void finalTierExceptionReturnsErrorVerdict() {
		Jury throwingFinal = new ThrowingJury();

		CascadedJury jury = CascadedJury.builder().tier("final", throwingFinal, TierPolicy.FINAL_TIER).build();

		Verdict verdict = jury.vote(context);

		assertThat(verdict.aggregated().status()).isEqualTo(JudgmentStatus.ERROR);
		assertThat(verdict.aggregated().reasoning()).contains("threw exception");
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

		CascadedJury jury = CascadedJury.builder().tier("only", onlyTier, TierPolicy.FINAL_TIER).build();

		Verdict verdict = jury.vote(context);

		assertThat(verdict.aggregated().status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(verdict.subVerdicts()).hasSize(1);
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
			.tier("deterministic", tier1, TierPolicy.REJECT_ON_ANY_FAIL)
			.tier("final", finalTier, TierPolicy.FINAL_TIER)
			.build();

		Verdict verdict = jury.vote(context);

		// ABSTAIN is not FAIL, so REJECT_ON_ANY_FAIL escalates
		assertThat(verdict.subVerdicts()).hasSize(2);
		assertThat(verdict.aggregated().status()).isEqualTo(JudgmentStatus.PASS);
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
			.tier("deterministic", tier1, TierPolicy.REJECT_ON_ANY_FAIL)
			.tier("final", finalTier, TierPolicy.FINAL_TIER)
			.build();

		Verdict verdict = jury.vote(context);

		// ABSTAIN is not FAIL → no rejection → escalate to final
		assertThat(verdict.subVerdicts()).hasSize(2);
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
			.tier("structural", tier2, TierPolicy.ACCEPT_ON_ALL_PASS)
			.tier("final", finalTier, TierPolicy.FINAL_TIER)
			.build();

		Verdict verdict = jury.vote(context);

		// ABSTAIN is not PASS → ACCEPT_ON_ALL_PASS escalates
		assertThat(verdict.subVerdicts()).hasSize(2);
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

		assertThatThrownBy(() -> CascadedJury.builder().tier("only", jury, TierPolicy.REJECT_ON_ANY_FAIL).build())
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
			.tier("deterministic", tier1, TierPolicy.REJECT_ON_ANY_FAIL)
			.tier("structural", tier2, TierPolicy.ACCEPT_ON_ALL_PASS)
			.tier("semantic", tier3, TierPolicy.FINAL_TIER)
			.build();

		Verdict verdict = jury.vote(context);

		assertThat(verdict.aggregated().status()).isEqualTo(JudgmentStatus.PASS);
		// Tier 1: no fails → escalate; Tier 2: all pass → accept
		assertThat(verdict.subVerdicts()).hasSize(2);
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
			.tier("deterministic", tier1, TierPolicy.REJECT_ON_ANY_FAIL)
			.tier("structural", tier2, TierPolicy.ACCEPT_ON_ALL_PASS)
			.tier("semantic", tier3, TierPolicy.FINAL_TIER)
			.build();

		Verdict verdict = jury.vote(context);

		assertThat(verdict.aggregated().status()).isEqualTo(JudgmentStatus.PASS);
		// Tier 1: no fail → escalate; Tier 2: AST failed → escalate; Tier 3: pass
		assertThat(verdict.subVerdicts()).hasSize(3);
	}

	// ==================== getJudges / getVotingStrategy ====================

	@Test
	void getJudgesReturnsFlattenedJudgesFromAllTiers() {
		Jury tier1 = SimpleJury.builder()
			.judge(alwaysPass("J1"))
			.judge(alwaysPass("J2"))
			.votingStrategy(new MajorityVotingStrategy())
			.build();

		Jury tier2 = SimpleJury.builder().judge(alwaysPass("J3")).votingStrategy(new MajorityVotingStrategy()).build();

		CascadedJury jury = CascadedJury.builder()
			.tier("t1", tier1, TierPolicy.REJECT_ON_ANY_FAIL)
			.tier("t2", tier2, TierPolicy.FINAL_TIER)
			.build();

		assertThat(jury.getJudges()).hasSize(3);
	}

	@Test
	void getVotingStrategyReturnsNull() {
		Jury tier = SimpleJury.builder().judge(alwaysPass("J1")).votingStrategy(new MajorityVotingStrategy()).build();

		CascadedJury jury = CascadedJury.builder().tier("final", tier, TierPolicy.FINAL_TIER).build();

		assertThat(jury.getVotingStrategy()).isNull();
	}

	// ==================== Helper ====================

	/**
	 * A jury that throws an exception on vote().
	 */
	private static class ThrowingJury implements Jury {

		@Override
		public java.util.List<io.github.markpollack.judge.Judge> getJudges() {
			return java.util.List.of();
		}

		@Override
		public VotingStrategy getVotingStrategy() {
			return null;
		}

		@Override
		public Verdict vote(JudgmentContext context) {
			throw new RuntimeException("Tier exploded");
		}

	}

}
