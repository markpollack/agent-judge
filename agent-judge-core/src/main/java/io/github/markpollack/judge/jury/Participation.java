/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.jury;

/** How the parent used a seat's unchanged producer judgment. */
public enum Participation {

	/** A manually authored record does not declare treatment. */
	NOT_RECORDED,
	/** One returned opinion was retained by identity, without reduction. */
	IDENTITY,
	/** Contributed its native opinion to the reduction. */
	INCLUDED,
	/** Producer abstained, so supplied no voting contribution. */
	ABSTAINED,
	/** Non-applicable opinion was excluded from voting. */
	EXCLUDED,
	/** An error was explicitly ignored. */
	ERROR_IGNORED,
	/** An error was treated as an abstention. */
	ERROR_AS_ABSTENTION,
	/** An error supplied a failing contribution without rewriting its judgment. */
	ERROR_AS_FAIL,
	/** An exclusion supplied a failing contribution without rewriting its judgment. */
	EXCLUSION_AS_FAIL,
	/** The parent performed no reduction, including a propagated error. */
	NOT_REDUCED;

	static Participation forJudgment(io.github.markpollack.judge.judgment.Judgment input,
			io.github.markpollack.judge.judgment.Judgment aggregate, boolean identity) {
		if (identity)
			return IDENTITY;
		if (aggregate.status() == io.github.markpollack.judge.judgment.JudgmentStatus.ERROR)
			return NOT_REDUCED;
		if (!(aggregate.metadata()
			.get(io.github.markpollack.judge.judgment.Judgment.AGGREGATION_KEY) instanceof java.util.Map<?, ?> rules))
			return NOT_RECORDED;
		return switch (input.status()) {
			case PASS, FAIL -> INCLUDED;
			case ABSTAIN -> ABSTAINED;
			case NOT_APPLICABLE -> "exclude".equals(rules.get("notApplicablePolicy")) ? EXCLUDED : EXCLUSION_AS_FAIL;
			case ERROR -> switch (java.util.Objects.toString(rules.get("errorPolicy"))) {
				case "ignore" -> ERROR_IGNORED;
				case "treatAsAbstain" -> ERROR_AS_ABSTENTION;
				case "treatAsFail" -> ERROR_AS_FAIL;
				default -> NOT_REDUCED;
			};
		};
	}

}
