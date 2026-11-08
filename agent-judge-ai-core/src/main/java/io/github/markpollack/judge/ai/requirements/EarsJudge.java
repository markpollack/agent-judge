/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.ai.requirements;

import java.util.*;
import java.util.function.Supplier;
import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.construction.*;
import io.github.markpollack.judge.execution.*;
import io.github.markpollack.judge.requirement.Requirement;
import io.github.markpollack.judge.judgment.*;
import io.github.markpollack.judge.ai.model.*;

/**
 * A configured native Ears Judge. Construction is inert; each call evaluates once. Its
 * immutable configuration is safe for concurrent use when its collaborators are. Prepared
 * generated judging and an integrated native investigation share the same operation.
 * Structured runtimes require their real typed evidence.
 */
public final class EarsJudge implements Judge {

	private final Supplier<Judgment> execution;

	private EarsJudge(Supplier<Judgment> execution) {
		this.execution = execution;
	}

	@Override
	public Judgment judge() {
		return execution.get();
	}

	/**
	 * Begins native runtime configuration.
	 * @return runtime selection
	 */
	public static RuntimeStep builder() {
		return new RuntimeStep();
	}

	/** Native protocol selection, with no execution. */
	public static final class RuntimeStep {

		private RuntimeStep() {
		}

		/**
		 * Selects a generated-answer harness.
		 * @param runtime configured native model or agent
		 * @return requirement construction
		 */
		public GeneratedBuilder runtime(EvalModel runtime) {
			return new GeneratedBuilder(Objects.requireNonNull(runtime));
		}

		/**
		 * Selects a typed structured protocol.
		 * @param <E> real evidence type
		 * @param runtime typed request/answer harness
		 * @return requirement construction
		 */
		public <E> JudgeRecipe<EarsSpecification, E> runtime(
				EvalRuntime<RequirementRequest<EarsSpecification, E>, Judgment> runtime) {
			Objects.requireNonNull(runtime);
			return requirement -> {
				Requirement.validate(requirement);
				return new EvidenceStep<E>() {
					@Override
					public ReadyJudge evidence(E evidence) {
						Objects.requireNonNull(evidence);
						return evidenceSupplier(() -> evidence);
					}

					@Override
					public ReadyJudge evidenceSupplier(Supplier<? extends E> evidence) {
						Objects.requireNonNull(evidence);
						return () -> new EarsJudge(() -> RosterJuries.typed(runtime, requirement, evidence));
					}
				};
			};
		}

	}

	/** Generated judging setup, reusable across independent requirements. */
	public static final class GeneratedBuilder extends GeneratedRecipeStage<EarsSpecification, GeneratedRequirement> {

		private GeneratedBuilder(EvalModel runtime) {
			this(runtime, PromptLimits.DEFAULT_MAX_PROMPT_CHARACTERS);
		}

		private GeneratedBuilder(EvalModel runtime, int maxPromptCharacters) {
			super(runtime, maxPromptCharacters);
		}

		/**
		 * Sets the largest prompt each judging may send in its one call; a larger one is an
		 * {@code ERROR} with no call. The default is
		 * {@link PromptLimits#DEFAULT_MAX_PROMPT_CHARACTERS}.
		 * @param maxPromptCharacters positive limit in characters
		 * @return this setup with the limit
		 * @since 0.19.0
		 */
		public GeneratedBuilder maxPromptCharacters(int maxPromptCharacters) {
			return new GeneratedBuilder(runtime(), maxPromptCharacters);
		}

		@Override
		GeneratedRequirement stage(EvalModel model, int maxPromptCharacters, Requirement<EarsSpecification> requirement) {
			return new GeneratedRequirement(model, maxPromptCharacters, requirement);
		}

	}

	/**
	 * A requirement is fixed; prepared evidence or investigation can be selected once.
	 */
	public static final class GeneratedRequirement extends GeneratedRequirementStage<EarsJudge> {

		private final Requirement<EarsSpecification> requirement;

		private GeneratedRequirement(EvalModel runtime, int maxPromptCharacters, Requirement<EarsSpecification> requirement) {
			super(runtime, maxPromptCharacters);
			this.requirement = requirement;
		}

		@Override
		EarsJudge judge(Supplier<Judgment> execution) {
			return new EarsJudge(execution);
		}

		@Override
		Judgment generated(EvalModel model, String input) {
			return EarsJudge.generated(model, requirement, maxPromptCharacters(), input);
		}

	}

	private static Judgment generated(EvalModel runtime, Requirement<EarsSpecification> requirement, int maxPromptCharacters,
			String input) {
		EarsCriterion nativeSpec = new EarsCriterion(requirement.id(), requirement.specification().title(),
				requirement.specification().requirement(), requirement.specification().applicability());
		String prompt = EarsParser.templateFor("Ears", List.of(nativeSpec)).render(Map.of("workspace", input));
		String refusal = RosterJuries.oversized(List.of(requirement), prompt, input, maxPromptCharacters);
		if (refusal != null)
			return RosterJuries.refused(requirement, refusal);
		NativeExecution<EvalModelResponse> result = runtime.execute(RosterJuries.request(List.of(requirement), prompt));
		return RosterJuries.single(requirement, EarsParser.rollupFor(List.of(nativeSpec), result.answer()), result);
	}

}
