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
		public GeneratedBuilder runtime(JudgeModel runtime) {
			return new GeneratedBuilder(Objects.requireNonNull(runtime));
		}

		/**
		 * Selects a typed structured protocol.
		 * @param <E> real evidence type
		 * @param runtime typed request/answer harness
		 * @return requirement construction
		 */
		public <E> JudgeRecipe<Rfc2119Specification, E> runtime(
				NativeRuntime<RequirementRequest<Rfc2119Specification, E>, Judgment> runtime) {
			Objects.requireNonNull(runtime);
			return requirement -> {
				Requirement.validate(requirement);
				return new EvidenceStep<E>() {
					public ReadyJudge evidence(E evidence) {
						Objects.requireNonNull(evidence);
						return evidenceSupplier(() -> evidence);
					}

					public ReadyJudge evidenceSupplier(Supplier<? extends E> evidence) {
						Objects.requireNonNull(evidence);
						return () -> new Rfc2119Judge(() -> {
							NativeExecution<Judgment> result = runtime.execute(new RequirementRequest<>(requirement,
									Objects.requireNonNull(evidence.get(), "acquired evidence")));
							return result.answer().forRequirement(requirement).withInvocation(result.invocation());
						});
					}
				};
			};
		}

	}

	/** Generated judging setup, reusable across independent requirements. */
	public static final class GeneratedBuilder implements JudgeRecipe<Rfc2119Specification, String> {

		private final JudgeModel runtime;

		private GeneratedBuilder(JudgeModel runtime) {
			this.runtime = runtime;
		}

		@Override
		public GeneratedRequirement requirement(Requirement<Rfc2119Specification> requirement) {
			Requirement.validate(requirement);
			return new GeneratedRequirement(runtime, requirement);
		}

	}

	/**
	 * A requirement is fixed; prepared evidence or investigation can be selected once.
	 */
	public static final class GeneratedRequirement implements EvidenceStep<String>, ReadyJudge {

		private final JudgeModel runtime;

		private final Requirement<Rfc2119Specification> requirement;

		private GeneratedRequirement(JudgeModel runtime, Requirement<Rfc2119Specification> requirement) {
			this.runtime = runtime;
			this.requirement = requirement;
		}

		@Override
		public ReadyJudge evidence(String evidence) {
			Objects.requireNonNull(evidence);
			return evidenceSupplier(() -> evidence);
		}

		@Override
		public ReadyJudge evidenceSupplier(Supplier<? extends String> evidence) {
			Objects.requireNonNull(evidence);
			runtime.requireInput(GeneratedInput.PREPARED_EVIDENCE);
			return () -> new Rfc2119Judge(() -> generated(runtime, requirement,
					"Prepared evidence:\n" + Objects.requireNonNull(evidence.get(), "acquired evidence")));
		}

		/**
		 * Builds an integrated investigation; native workspace/tools stay on the harness.
		 * @return ready investigative Judge
		 */
		@Override
		public Rfc2119Judge build() {
			runtime.requireInput(GeneratedInput.INTEGRATED_INVESTIGATION);
			return new Rfc2119Judge(() -> generated(runtime, requirement,
					"Investigate with the configured native workspace and tools."));
		}

	}

	private static Judgment generated(JudgeModel runtime, Requirement<Rfc2119Specification> requirement, String input) {
		Rfc2119Constraint nativeSpec = new Rfc2119Constraint(requirement.id(), requirement.specification().keyword(),
				requirement.specification().requirement(), requirement.specification().reason(),
				requirement.specification().applicability());
		String prompt = Rfc2119Parser.templateFor("Rfc2119", List.of(nativeSpec)).render(Map.of("workspace", input));
		NativeExecution<JudgeModelResponse> result = runtime
			.execute(JudgeModelRequest.user(RequirementPrompts.header(requirement) + prompt));
		Judgment parsed = Rfc2119Parser.rollupFor(List.of(nativeSpec), result.answer());
		if (!Boolean.TRUE.equals(parsed.metadata().get("protocolIdentityBound"))) {
			// Preserve the full refused envelope and observed child; never promote
			// its apparent determination when parser identity admission failed.
			return parsed.forRequirement(requirement).withInvocation(result.invocation());
		}
		Judgment answer = parsed.checks().get(0).judgment();
		// Single operation retains parser diagnostics, the actual input and the original
		// native response facts.
		return answer.toBuilder()
			.metadata(parsed.metadata())
			.build()
			.forRequirement(requirement)
			.withInvocation(result.invocation());
	}

}
