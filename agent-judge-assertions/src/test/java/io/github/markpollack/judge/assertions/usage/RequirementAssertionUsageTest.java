/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.assertions.usage;

import io.github.markpollack.judge.assertions.RequirementAssertions;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.*;
import io.github.markpollack.judge.*;
import io.github.markpollack.judge.judgment.*;
import io.github.markpollack.judge.jury.*;
import io.github.markpollack.judge.requirement.*;
import io.github.markpollack.judge.policy.*;
import io.github.markpollack.judge.evaluation.*;
import io.github.markpollack.judge.reporting.VerdictReport;
import static org.assertj.core.api.Assertions.*;

class RequirementAssertionUsageTest {

	@Test
	void explicitNativeRequirementAndEvidence() {
		var latency = new Requirement<Integer>("latency", "1", "Response within budget", 100,
				Requirement.text("source", "1", "Response budget: 100 ms").source());
		RequirementJudge<Integer, Long> withinBudget = (r, e) -> e <= r.specification() ? Judgment.pass("within budget")
				: Judgment.fail("too slow");
		var result = Evaluations.evaluate(latency, withinBudget, 42L);
		RequirementAssertions.requireSatisfied(result);
		assertThat(VerdictReport.of(result.verdict()).conclusion()).isEqualTo(Verdict.Conclusion.PASS);
	}

}
