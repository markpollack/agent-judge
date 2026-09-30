/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge;

import io.github.markpollack.judge.judgment.Judgment;
import io.github.markpollack.judge.requirement.Requirement;

/**
 * An evaluator receiving the actual specification for each invocation.
 *
 * @param <S> native specification type
 * @param <E> evidence type
 */
@FunctionalInterface
public interface RequirementJudge<S, E> {

	/**
	 * Judge the supplied requirement against the supplied evidence.
	 * @param requirement actual requirement for this invocation
	 * @param evidence typed evidence
	 * @return producer judgment
	 */
	Judgment judge(Requirement<S> requirement, E evidence);

}
