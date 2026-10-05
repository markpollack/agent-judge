/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.assertj.usage;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.*;
import io.github.markpollack.judge.*;
import io.github.markpollack.judge.judgment.*;
import io.github.markpollack.judge.jury.*;
import io.github.markpollack.judge.verdict.*;
import io.github.markpollack.judge.voting.*;
import io.github.markpollack.judge.requirement.*;
import io.github.markpollack.judge.policy.*;
import io.github.markpollack.judge.evaluation.*;
import static io.github.markpollack.judge.assertj.Assertions.*;
import static org.assertj.core.api.Assertions.*;

class TutorialExamplesTest {

	@Test
	void deterministicAndTextualChecksUseTheirOwnInputs() {
		int value = 2 + 2;
		Judge arithmetic = () -> value == 4 ? Judgment.pass("correct") : Judgment.fail("incorrect");
		assertThat(arithmetic).isPassed();
		var requirement = Requirement.text("answer", "1", "4");
		io.github.markpollack.judge.construction.JudgeRecipe<String, String> equals = io.github.markpollack.judge.assertj.TestRecipes
			.judge((r, e) -> r.specification().equals(e) ? Judgment.pass("matches") : Judgment.fail("differs"));
		assertThat(requirement).judgedBy(equals)
			.withEvidence("4")
			.withPolicy(v -> new PolicyDecision(PolicyAction.RELY, "checked"))
			.isSatisfied();
	}

}
