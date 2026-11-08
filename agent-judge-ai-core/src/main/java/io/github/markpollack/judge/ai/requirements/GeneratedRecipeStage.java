/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.ai.requirements;

import io.github.markpollack.judge.ai.model.EvalModel;
import io.github.markpollack.judge.construction.JudgeRecipe;
import io.github.markpollack.judge.requirement.Requirement;

/**
 * Generated judging setup shared by the EARS and RFC 2119 judges: one configured harness,
 * reusable across independent requirements.
 *
 * @param <S> specification type
 * @param <R> the concrete requirement stage
 */
abstract class GeneratedRecipeStage<S, R extends GeneratedRequirementStage<?>> implements JudgeRecipe<S, String> {

	private final EvalModel runtime;

	private final int maxPromptCharacters;

	GeneratedRecipeStage(EvalModel runtime, int maxPromptCharacters) {
		this.runtime = runtime;
		this.maxPromptCharacters = PromptLimits.require(maxPromptCharacters);
	}

	final EvalModel runtime() {
		return this.runtime;
	}

	/**
	 * The concrete stage for one validated requirement.
	 * @param model the configured harness
	 * @param maxPromptCharacters the largest prompt its one call may send
	 * @param requirement the validated requirement
	 * @return its evidence stage
	 */
	abstract R stage(EvalModel model, int maxPromptCharacters, Requirement<S> requirement);

	@Override
	public R requirement(Requirement<S> requirement) {
		Requirement.validate(requirement);
		return stage(runtime, maxPromptCharacters, requirement);
	}

}
