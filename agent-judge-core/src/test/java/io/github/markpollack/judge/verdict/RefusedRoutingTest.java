/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.verdict;

import java.util.*;
import org.junit.jupiter.api.Test;
import io.github.markpollack.judge.judgment.*;
import io.github.markpollack.judge.provenance.Invocation;
import io.github.markpollack.judge.requirement.Requirement;
import io.github.markpollack.judge.jury.*;
import io.github.markpollack.judge.voting.*;
import static org.assertj.core.api.Assertions.*;

class RefusedRoutingTest {

	Judgment refused() {
		var original = Judgment.fail("unbound FAIL")
			.forRequirement(Requirement.text("actual", "1", "actual"))
			.toBuilder()
			.check(Check.pass("own", "child"))
			.build();
		return Judgment.refuse(original, Requirement.text("expected", "1", "expected"),
				new Invocation("item", "fixture", true, null, 1, Map.of(), List.of()));
	}

	@Test
	void guidedObservedPanelsRetainCompleteRefusalsAndDoNotPromoteUnboundFailures() {
		var refused = refused();
		var single = Verdict.builder().panel(new AllEligiblePassStrategy()).opinion("refused", refused, 2).build();
		assertThat(single.individual()).containsExactly(refused.refusedReturn().original());
		assertThat(single.seats().getFirst().rejection()).isSameAs(refused);
		assertThat(single.conclusion()).isEqualTo(Verdict.Conclusion.INCONCLUSIVE);
		for (boolean siblingFails : List.of(false, true)) {
			var panel = Verdict.builder()
				.panel(new AllEligiblePassStrategy())
				.opinion("refused", refused)
				.opinion("legal", siblingFails ? Judgment.fail("bound failure") : Judgment.pass("bound success"))
				.build();
			var cascade = CascadedJury.builder()
				.tier("observed", () -> panel, RoutingRule.STOP_ON_ANY_OPINION_FAIL)
				.tier("fallback", () -> Verdict.single("fallback", Judgment.pass("fallback")), RoutingRule.FINAL_TIER)
				.build()
				.vote()
				.requireUsable();
			assertThat(cascade.conclusion())
				.isEqualTo(siblingFails ? Verdict.Conclusion.INCONCLUSIVE : Verdict.Conclusion.PASS);
			assertThat(cascade.provenance().tier()).isEqualTo(siblingFails ? "observed" : "fallback");
			assertThat(cascade.compositeAttempts().getFirst().verdict()).isSameAs(panel);
		}
	}

	@Test
	void postReturnInterruptionEscapesSequentialAndParallelJudging() {
		for (boolean parallel : List.of(false, true)) {
			try {
				assertThatThrownBy(() -> SimpleJury.builder().judge(() -> {
					Thread.currentThread().interrupt();
					return Judgment.pass("interrupted return");
				}).parallel(parallel).votingStrategy(new AllEligiblePassStrategy()).build().vote())
					.isInstanceOf(java.util.concurrent.CancellationException.class);
			}
			finally {
				Thread.interrupted();
			}
		}
	}

}
