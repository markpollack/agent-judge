/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury.interpretation;

/**
 * The pinned summaries of the two examples. A change here is a decision somebody makes about
 * the summary's wording, not a diff somebody notices later.
 */
final class SummaryGoldens {

	static final String EXAMPLE_ONE = "1 stage(s) ran: 'structure' (cascade_tier, REJECT_ON_ANY_FAIL, used). "
			+ "'structure' abstained (No consensus: 1 passed, 1 failed among 2 applicable judge(s)); its judges: "
			+ "'structure:ddd-review.md' passed (report present); 'reportStructure' failed (report has no bounded "
			+ "contexts). Evidence: consensus, errors propagate, exclusions refuse, 2 in, 2 eligible, 1 pass, 1 fail, "
			+ "0 error(s), 0 not applicable, 0 explicit abstention(s). The root abstained (No consensus: 1 passed, "
			+ "1 failed among 2 applicable judge(s)); its judges: 'structure:ddd-review.md' passed (report present); "
			+ "'reportStructure' failed (report has no bounded contexts). Evidence: consensus, errors propagate, "
			+ "exclusions refuse, 2 in, 2 eligible, 1 pass, 1 fail, 0 error(s), 0 not applicable, 0 explicit "
			+ "abstention(s). The root adopted the outcome of 'structure' (tier_outcome). Reading: UNDECIDED — the "
			+ "subject was judged and the jury could not decide; not an instrument failure. Support: SUPPORTED — the "
			+ "recorded facts agree with the reading. Source: the seven-component form 0.17 writes (sourceVersion 1). "
			+ "Defects: none.";

	static final String EXAMPLE_TWO = "1 stage(s) ran: an unnamed stage. An unnamed stage failed (No consensus: "
			+ "1 passed, 1 failed (consensus required)); its judges: 'structure:ddd-action-brief.md' passed (File "
			+ "exists at ddd-action-brief.md); 'structure:ddd-review.md' failed (File not found at ddd-review.md). "
			+ "The root failed (No consensus: 1 passed, 1 failed (consensus required)); its judges: "
			+ "'structure:ddd-action-brief.md' passed (File exists at ddd-action-brief.md); 'structure:ddd-review.md' "
			+ "failed (File not found at ddd-review.md). Which stage decided is not recorded and has not been "
			+ "inferred. Reading: REJECTED — the subject was judged and rejected. Support: UNDETERMINED — the facts "
			+ "needed to check the reading are absent or their rule is not closed-form. Source: an unstamped record "
			+ "written before 0.17 (sourceVersion 0). 10 defect(s): verdict.aggregated.score UNPARSEABLE; "
			+ "verdict.seats ABSENT; verdict.individualByName[structure:ddd-action-brief.md].score UNPARSEABLE; "
			+ "verdict.individualByName[structure:ddd-review.md].score UNPARSEABLE; verdict.subVerdicts[0].name "
			+ "ABSENT; verdict.subVerdicts[0].aggregated.score UNPARSEABLE; "
			+ "verdict.subVerdicts[0].individualByName[structure:ddd-action-brief.md].score UNPARSEABLE; "
			+ "verdict.subVerdicts[0].individualByName[structure:ddd-review.md].score UNPARSEABLE; verdict.decision "
			+ "ABSENT; verdict.aggregated.metadata.aggregation ABSENT.";

	private SummaryGoldens() {
	}

}
