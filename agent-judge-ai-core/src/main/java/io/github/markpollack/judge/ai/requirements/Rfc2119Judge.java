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
 * A configured native Rfc2119 Judge. Construction is inert; each call evaluates once. Its
 * immutable configuration is safe for concurrent use when its collaborators are. Prepared
 * generated judging and an integrated native investigation share the same operation.
 * Structured runtimes require their real typed evidence.
 */
public final class Rfc2119Judge implements Judge {

	private final Supplier<Judgment> execution;

	private Rfc2119Judge(Supplier<Judgment> execution) {
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
		public <E> JudgeRecipe<Rfc2119Specification, E> runtime(
				EvalRuntime<RequirementRequest<Rfc2119Specification, E>, Judgment> runtime) {
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
						return () -> new Rfc2119Judge(() -> RosterJuries.typed(runtime, requirement, evidence));
					}
				};
			};
		}

	}

	/** Generated judging setup, reusable across independent requirements. */
	public static final class GeneratedBuilder extends GeneratedRecipeStage<Rfc2119Specification, GeneratedRequirement> {

		private GeneratedBuilder(EvalModel runtime) {
			super(runtime);
		}

		@Override
		GeneratedRequirement stage(EvalModel model, Requirement<Rfc2119Specification> requirement) {
			return new GeneratedRequirement(model, requirement);
		}

	}

	/**
	 * A requirement is fixed; prepared evidence or investigation can be selected once.
	 */
	public static final class GeneratedRequirement extends GeneratedRequirementStage<Rfc2119Judge> {

		private final Requirement<Rfc2119Specification> requirement;

		private GeneratedRequirement(EvalModel runtime, Requirement<Rfc2119Specification> requirement) {
			super(runtime);
			this.requirement = requirement;
		}

		@Override
		Rfc2119Judge judge(Supplier<Judgment> execution) {
			return new Rfc2119Judge(execution);
		}

		@Override
		Judgment generated(EvalModel model, String input) {
			return Rfc2119Judge.generated(model, requirement, input);
		}

	}

	private static Judgment generated(EvalModel runtime, Requirement<Rfc2119Specification> requirement, String input) {
		Rfc2119Constraint nativeSpec = new Rfc2119Constraint(requirement.id(), requirement.specification().keyword(),
				requirement.specification().requirement(), requirement.specification().reason(),
				requirement.specification().applicability());
		String prompt = Rfc2119Parser.templateFor("Rfc2119", List.of(nativeSpec)).render(Map.of("workspace", input));
		NativeExecution<EvalModelResponse> result = runtime.execute(RosterJuries.request(List.of(requirement), prompt));
		return RosterJuries.single(requirement, Rfc2119Parser.rollupFor(List.of(nativeSpec), result.answer()), result);
	}

}
