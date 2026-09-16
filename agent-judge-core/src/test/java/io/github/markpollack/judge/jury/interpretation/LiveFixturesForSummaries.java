/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury.interpretation;

import io.github.markpollack.judge.Judges;
import io.github.markpollack.judge.jury.CascadedJury;
import io.github.markpollack.judge.jury.ConsensusStrategy;
import io.github.markpollack.judge.jury.Jury;
import io.github.markpollack.judge.jury.SimpleJury;
import io.github.markpollack.judge.jury.TierPolicy;
import io.github.markpollack.judge.jury.Verdict;
import io.github.markpollack.judge.result.Judgment;

import static io.github.markpollack.judge.jury.interpretation.Fixtures.CONTEXT;

/** Live verdicts whose shapes the stored fixtures do not cover, for the summary agreement test. */
final class LiveFixturesForSummaries {

	private LiveFixturesForSummaries() {
	}

	static Verdict childUndecidedRejection() {
		return CascadedJury.builder()
			.tier("gate", Fixtures.undecidedTier(Judgment.pass("a"), Judgment.fail("b")), TierPolicy.REJECT_ON_ANY_FAIL)
			.tier("semantic", Fixtures.passingTier("ok", "OK"), TierPolicy.FINAL_TIER)
			.build()
			.vote(CONTEXT);
	}

	static Verdict nestedRejection() {
		Jury inner = CascadedJury.builder()
			.tier("rubric", Fixtures.opaqueExcludingTier(Judgment.pass("a"), Judgment.fail("b")),
					TierPolicy.REJECT_ON_ANY_FAIL)
			.tier("semantic", Fixtures.passingTier("ok", "OK"), TierPolicy.FINAL_TIER)
			.build();
		return CascadedJury.builder().tier("inner", inner, TierPolicy.FINAL_TIER).build().vote(CONTEXT);
	}

	static Verdict propagatedError() {
		return SimpleJury.builder()
			.judge(Judges.named(context -> Judgment.error("the index was unreachable"), "flaky"))
			.judge(Judges.named(context -> Judgment.pass("fine"), "ok"))
			.votingStrategy(new ConsensusStrategy())
			.build()
			.vote(CONTEXT);
	}

}
