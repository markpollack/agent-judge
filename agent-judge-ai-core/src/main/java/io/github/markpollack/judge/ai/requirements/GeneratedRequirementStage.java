/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.ai.requirements;

import java.util.Objects;
import java.util.function.Supplier;

import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.ai.model.EvalModel;
import io.github.markpollack.judge.ai.model.GeneratedInput;
import io.github.markpollack.judge.construction.EvidenceStep;
import io.github.markpollack.judge.construction.ReadyJudge;
import io.github.markpollack.judge.judgment.Judgment;

/**
 * The evidence stage of a generated single-requirement judge, shared by the EARS and RFC
 * 2119 judges: prepared evidence, or an integrated investigation, selected once.
 *
 * @param <J> the concrete judge type
 */
abstract class GeneratedRequirementStage<J extends Judge> implements EvidenceStep<String>, ReadyJudge {

	private final EvalModel runtime;

	GeneratedRequirementStage(EvalModel runtime) {
		this.runtime = runtime;
	}

	/**
	 * The concrete judge over one execution.
	 * @param execution one judging
	 * @return the judge
	 */
	abstract J judge(Supplier<Judgment> execution);

	/**
	 * One generated audit of the fixed requirement.
	 * @param model the configured harness
	 * @param input the evidence or investigation instruction
	 * @return the requirement's judgment
	 */
	abstract Judgment generated(EvalModel model, String input);

	@Override
	public ReadyJudge evidence(String evidence) {
		Objects.requireNonNull(evidence);
		return evidenceSupplier(() -> evidence);
	}

	@Override
	public ReadyJudge evidenceSupplier(Supplier<? extends String> evidence) {
		Objects.requireNonNull(evidence);
		runtime.requireInput(GeneratedInput.PREPARED_EVIDENCE);
		return () -> judge(() -> generated(runtime,
				"Prepared evidence:\n" + Objects.requireNonNull(evidence.get(), "acquired evidence")));
	}

	/**
	 * Builds an integrated investigation; native workspace/tools stay on the harness.
	 * @return ready investigative Judge
	 */
	@Override
	public J build() {
		runtime.requireInput(GeneratedInput.INTEGRATED_INVESTIGATION);
		return judge(() -> generated(runtime, "Investigate with the configured native workspace and tools."));
	}

}
