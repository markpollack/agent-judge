/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.ai.requirements;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

import io.github.markpollack.judge.execution.EvalRuntime;
import io.github.markpollack.judge.execution.RequirementRequest;
import io.github.markpollack.judge.judgment.Judgment;
import io.github.markpollack.judge.jury.Jury;
import io.github.markpollack.judge.jury.JuryEvidenceStep;
import io.github.markpollack.judge.jury.ReadyJury;
import io.github.markpollack.judge.requirement.Requirement;
import io.github.markpollack.judge.verdict.Verdict;
import org.jspecify.annotations.Nullable;

/**
 * The evidence stage of a structured roster, shared by the EARS and RFC 2119 juries:
 * common evidence, or one bundle per declared requirement, acquired once per vote.
 *
 * @param <S> specification type
 * @param <E> evidence type
 */
abstract class StructuredRosterStage<S, E> implements JuryEvidenceStep<E> {

	private final EvalRuntime<RequirementRequest<S, E>, Judgment> runtime;

	private final List<Requirement<S>> requirements;

	private final Function<S, @Nullable String> applicability;

	StructuredRosterStage(EvalRuntime<RequirementRequest<S, E>, Judgment> runtime, List<Requirement<S>> requirements,
			Function<S, @Nullable String> applicability) {
		this.runtime = runtime;
		this.requirements = requirements;
		this.applicability = applicability;
	}

	/**
	 * The concrete jury over this roster.
	 * @param requirements declared roster
	 * @param operation one vote
	 * @return the jury
	 */
	abstract Jury jury(List<Requirement<S>> requirements, Supplier<Verdict> operation);

	@Override
	public ReadyJury evidence(E evidence) {
		Objects.requireNonNull(evidence);
		return evidenceSupplier(() -> evidence);
	}

	@Override
	public ReadyJury evidenceSupplier(Supplier<? extends E> evidence) {
		Objects.requireNonNull(evidence);
		return ready(
				() -> RosterJuries.common(requirements, Objects.requireNonNull(evidence.get(), "acquired evidence")));
	}

	/**
	 * Selects a separate actual evidence bundle for each declared requirement.
	 * @param evidence complete map keyed by actual requirement identity
	 * @return ready Jury construction
	 */
	public ReadyJury evidenceByRequirement(Map<String, ? extends E> evidence) {
		Map<String, E> captured = RosterJuries.checked(requirements, evidence);
		return ready(() -> captured);
	}

	/**
	 * Acquires one coherent collection snapshot before any native item executes.
	 * @param evidence complete requirement-specific acquisition
	 * @return ready Jury construction
	 */
	public ReadyJury evidenceByRequirementSupplier(Supplier<? extends Map<String, ? extends E>> evidence) {
		Objects.requireNonNull(evidence);
		return ready(() -> RosterJuries.checked(requirements, evidence.get()));
	}

	private ReadyJury ready(Supplier<Map<String, E>> values) {
		return () -> jury(requirements,
				() -> RosterJuries.structured(requirements, runtime, values.get(), applicability));
	}

}
