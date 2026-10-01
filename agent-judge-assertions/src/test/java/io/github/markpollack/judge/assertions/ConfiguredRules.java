/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.assertions;

import java.util.*;
import java.util.function.*;
import io.github.markpollack.judge.construction.*;
import io.github.markpollack.judge.requirement.*;
import io.github.markpollack.judge.judgment.*;
import io.github.markpollack.judge.jury.*;
import io.github.markpollack.judge.evaluation.*;
import io.github.markpollack.judge.policy.*;

/**
 * Test construction for coded rules; native fixtures use the actual public Jev producer.
 */
public final class ConfiguredRules {

	private ConfiguredRules() {
	}

	public static <S, E> JudgeRecipe<S, E> rule(BiFunction<Requirement<S>, E, Judgment> rule) {
		return requirement -> {
			Requirement.validate(requirement);
			return EvidenceSteps.of(source -> () -> rule.apply(requirement, Objects.requireNonNull(source.get()))
				.forRequirement(requirement));
		};
	}

	public static <S, E> EvaluationResult evaluate(Requirement<S> requirement, JudgeRecipe<S, E> recipe, E evidence) {
		return new EvaluationResult(Evaluations.evaluate(recipe.requirement(requirement).evidence(evidence).build())
			.verdict()
			.forRequirement(requirement), new PolicyResult.NotRequested());
	}

	public static <S, E> EvaluationResult evaluate(Requirement<S> requirement, JudgeRecipe<S, E> recipe, E evidence,
			Policy policy) {
		Objects.requireNonNull(policy);
		return Evaluations.apply(evaluate(requirement, recipe, evidence).verdict(), policy);
	}

}
