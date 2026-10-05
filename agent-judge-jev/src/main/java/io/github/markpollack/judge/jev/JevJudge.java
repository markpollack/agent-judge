/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jev;

import java.util.*;
import java.util.function.Supplier;
import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.construction.*;
import io.github.markpollack.judge.execution.*;
import io.github.markpollack.judge.requirement.Requirement;
import io.github.markpollack.judge.judgment.Judgment;

/**
 * A configured structured Judge. Reuses JevRuntime without a generated-text detour.
 * Construction is inert and evidence is acquired once per direct operation. Concurrent
 * reuse requires concurrent-safe collaborators.
 */
public final class JevJudge implements Judge {

	private final Supplier<Judgment> operation;

	private JevJudge(Supplier<Judgment> operation) {
		this.operation = operation;
	}

	@Override
	public Judgment judge() {
		return operation.get();
	}

	/**
	 * Begins typed runtime selection.
	 * @return runtime stage
	 */
	public static RuntimeStep builder() {
		return new RuntimeStep();
	}

	/** Selects the structured protocol before requirement and evidence. */
	public static final class RuntimeStep {

		private RuntimeStep() {
		}

		/**
		 * Selects the native text-requirement Jev transport.
		 * @param runtime native setup
		 * @return requirement recipe
		 */
		public JudgeRecipe<String, JevEvidence> runtime(JevRuntime runtime) {
			return runtime(runtime.rendering(java.util.function.Function.identity()));
		}

		/**
		 * Selects native rendering for an external specification type.
		 * @param <S> specification type
		 * @param runtime typed native request/answer harness
		 * @return requirement construction
		 */
		public <S> JudgeRecipe<S, JevEvidence> runtime(
				EvalRuntime<RequirementRequest<S, JevEvidence>, Judgment> runtime) {
			Objects.requireNonNull(runtime);
			return requirement -> {
				Requirement.validate(requirement);
				return new EvidenceStep<>() {
					public ReadyJudge evidence(JevEvidence value) {
						Objects.requireNonNull(value);
						return evidenceSupplier(() -> value);
					}

					public ReadyJudge evidenceSupplier(Supplier<? extends JevEvidence> evidence) {
						Objects.requireNonNull(evidence);
						return () -> new JevJudge(() -> {
							NativeExecution<Judgment> result = runtime.execute(new RequirementRequest<>(requirement,
									Objects.requireNonNull(evidence.get(), "acquired evidence")));
							return result.answer().forRequirement(requirement).withInvocation(result.invocation());
						});
					}
				};
			};
		}

	}

}
