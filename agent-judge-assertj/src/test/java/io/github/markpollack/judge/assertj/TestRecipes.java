/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.assertj;

import java.util.*;
import java.util.function.*;
import io.github.markpollack.judge.*;
import io.github.markpollack.judge.construction.*;
import io.github.markpollack.judge.requirement.*;
import io.github.markpollack.judge.jury.*;
import io.github.markpollack.judge.judgment.*;

/**
 * Test-only construction for coded requirement rules; native tests use public producers.
 */
public final class TestRecipes {

	public static <S, E> JudgeRecipe<S, E> judge(BiFunction<Requirement<S>, E, Judgment> rule) {
		return requirement -> EvidenceSteps
			.of(source -> () -> rule.apply(requirement, Objects.requireNonNull(source.get()))
				.forRequirement(requirement));
	}

	public static <S, E> JuryRecipe<S, E> jury(BiFunction<Requirement<S>, E, Verdict> rule) {
		return requirement -> new JuryEvidenceStep<>() {
			public ReadyJury evidence(E e) {
				return evidenceSupplier(() -> e);
			}

			public ReadyJury evidenceSupplier(Supplier<? extends E> source) {
				return () -> () -> rule.apply(requirement, Objects.requireNonNull(source.get()));
			}
		};
	}

	public static <S, E> JuryRecipe<S, E> voting(VotingStrategy strategy, List<JudgeRecipe<S, E>> recipes) {
		var snapshot = List.copyOf(recipes);
		return requirement -> new JuryEvidenceStep<>() {
			public ReadyJury evidence(E e) {
				return evidenceSupplier(() -> e);
			}

			public ReadyJury evidenceSupplier(Supplier<? extends E> source) {
				return () -> new Jury() {
					public Verdict vote() {
						E actual = Objects.requireNonNull(source.get());
						var b = SimpleJury.builder().parallel(false).votingStrategy(strategy);
						snapshot.forEach(recipe -> b.judge(recipe.requirement(requirement).evidence(actual).build()));
						return b.build().vote().forRequirement(requirement);
					}
				};
			}
		};
	}

}
