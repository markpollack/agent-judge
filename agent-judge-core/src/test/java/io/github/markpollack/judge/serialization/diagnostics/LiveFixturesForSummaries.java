/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.serialization.diagnostics;

import io.github.markpollack.judge.completion.CompletionEvidence;

import io.github.markpollack.judge.Judges;
import io.github.markpollack.judge.jury.CascadedJury;
import io.github.markpollack.judge.jury.ConsensusStrategy;
import io.github.markpollack.judge.jury.Jury;
import io.github.markpollack.judge.jury.SimpleJury;
import io.github.markpollack.judge.jury.RoutingRule;
import io.github.markpollack.judge.jury.Verdict;
import io.github.markpollack.judge.judgment.Judgment;

import static io.github.markpollack.judge.serialization.diagnostics.Fixtures.CONTEXT;

/**
 * Live verdicts whose shapes the stored fixtures do not cover, for the summary agreement
 * test.
 */
final class LiveFixturesForSummaries {

	private LiveFixturesForSummaries() {
	}

	static Verdict childUndecidedRejection() {
		return CascadedJury.<CompletionEvidence>builder()
			.tier("gate", Fixtures.undecidedTier(Judgment.pass("a"), Judgment.fail("b")),
					RoutingRule.REJECT_ON_ANY_FAIL)
			.tier("semantic", Fixtures.passingTier("ok", "OK"), RoutingRule.FINAL_TIER)
			.build()
			.vote(CONTEXT);
	}

	static Verdict nestedRejection() {
		Jury<CompletionEvidence> inner = CascadedJury.<CompletionEvidence>builder()
			.tier("rubric", Fixtures.opaqueExcludingTier(Judgment.pass("a"), Judgment.fail("b")),
					RoutingRule.REJECT_ON_ANY_FAIL)
			.tier("semantic", Fixtures.passingTier("ok", "OK"), RoutingRule.FINAL_TIER)
			.build();
		return CascadedJury.<CompletionEvidence>builder()
			.tier("inner", inner, RoutingRule.FINAL_TIER)
			.build()
			.vote(CONTEXT);
	}

	static Verdict propagatedError() {
		return SimpleJury.<CompletionEvidence>builder()
			.judge(Judges.named(context -> Judgment.error("the index was unreachable"), "flaky"))
			.judge(Judges.named(context -> Judgment.pass("fine"), "ok"))
			.votingStrategy(new ConsensusStrategy())
			.build()
			.vote(CONTEXT);
	}

}
