/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.ai.requirements;

import io.github.markpollack.judge.verdict.AttemptDisposition;
import io.github.markpollack.judge.verdict.CompositeAttempt;
import io.github.markpollack.judge.verdict.CompositeFailure;
import io.github.markpollack.judge.verdict.CompositeFailureCode;
import io.github.markpollack.judge.verdict.CompositeRelation;
import io.github.markpollack.judge.verdict.DispositionReason;
import io.github.markpollack.judge.verdict.Verdict;
import io.github.markpollack.judge.verdict.VerdictProvenance;
import io.github.markpollack.judge.verdict.VerdictProvenanceKind;

import java.util.*;
import java.util.function.Supplier;
import io.github.markpollack.judge.ai.model.*;
import io.github.markpollack.judge.execution.*;
import io.github.markpollack.judge.requirement.Requirement;
import io.github.markpollack.judge.jury.*;
import io.github.markpollack.judge.verdict.*;
import io.github.markpollack.judge.voting.*;
import io.github.markpollack.judge.judgment.*;
import io.github.markpollack.judge.description.*;

/**
 * A complete Ears roster audit. A generated investigative execution receives the entire
 * roster once; parsed constituents do not execute additional Judges. Each vote is fresh.
 * Native tools/workspace are configured on the runtime.
 */
public final class EarsJury implements Jury {

	private final List<Requirement<EarsSpecification>> requirements;

	private final Supplier<Verdict> operation;

	private EarsJury(List<Requirement<EarsSpecification>> requirements, Supplier<Verdict> operation) {
		this.requirements = requirements;
		this.operation = operation;
	}

	@Override
	public Verdict vote() {
		return operation.get();
	}

	@Override
	public JuryDescription describe() {
		return new AuditJuryDescription("Ears", new ArrayList<Requirement<?>>(requirements),
				requirements.stream().allMatch(q -> q.specification().applicability() != null));
	}

	/**
	 * Begins runtime configuration.
	 * @return runtime stage
	 */
	public static RuntimeStep builder() {
		return new RuntimeStep();
	}

	/** Native roster execution choices. */
	public static final class RuntimeStep {

		private RuntimeStep() {
		}

		/**
		 * Configures one generated/agent execution for the whole roster.
		 * @param runtime generated-answer harness
		 * @return roster stage
		 */
		public GeneratedBuilder runtime(EvalModel runtime) {
			return new GeneratedBuilder(Objects.requireNonNull(runtime));
		}

		/**
		 * Configures explicitly counted per-requirement structured execution.
		 * @param <E> typed evidence
		 * @param runtime native per-item protocol
		 * @return typed roster stage
		 */
		public <E> StructuredBuilder<E> runtime(
				EvalRuntime<RequirementRequest<EarsSpecification, E>, Judgment> runtime) {
			return new StructuredBuilder<>(runtime);
		}

	}

	/** Generated roster construction. */
	public static final class GeneratedBuilder {

		private final EvalModel runtime;

		private GeneratedBuilder(EvalModel runtime) {
			this.runtime = runtime;
		}

		/**
		 * Declares the actual complete roster.
		 * @param requirements native requirements
		 * @return configured roster, with optional prepared evidence
		 */
		public GeneratedRoster requirements(List<? extends Requirement<EarsSpecification>> requirements) {
			return new GeneratedRoster(runtime, snapshot(requirements));
		}

	}

	/** A roster fixed before execution. */
	public static final class GeneratedRoster {

		private final EvalModel runtime;

		private final List<Requirement<EarsSpecification>> requirements;

		private GeneratedRoster(EvalModel runtime, List<Requirement<EarsSpecification>> requirements) {
			this.runtime = runtime;
			this.requirements = requirements;
		}

		/**
		 * Selects prepared evidence without executing.
		 * @param evidence prepared evidence
		 * @return ready Jury construction
		 */
		public io.github.markpollack.judge.jury.ReadyJury evidence(String evidence) {
			Objects.requireNonNull(evidence);
			return evidenceSupplier(() -> evidence);
		}

		/**
		 * Configures fresh acquisition, once for a coherent whole-roster snapshot.
		 * @param evidence acquisition provider
		 * @return ready Jury construction
		 */
		public io.github.markpollack.judge.jury.ReadyJury evidenceSupplier(Supplier<String> evidence) {
			Objects.requireNonNull(evidence);
			runtime.requireInput(GeneratedInput.PREPARED_EVIDENCE);
			return () -> new EarsJury(requirements, () -> generated(runtime, requirements,
					"Prepared evidence:\n" + Objects.requireNonNull(evidence.get())));
		}

		/**
		 * Builds an integrated investigative audit.
		 * @return ready roster Jury
		 */
		public EarsJury build() {
			runtime.requireInput(GeneratedInput.INTEGRATED_INVESTIGATION);
			return new EarsJury(requirements, () -> generated(runtime, requirements,
					"Investigate with the configured native workspace and tools."));
		}

	}

	/**
	 * Typed structured roster; each requirement owns its own native execution.
	 *
	 * @param <E> evidence type
	 */
	public static final class StructuredBuilder<E> {

		private final EvalRuntime<RequirementRequest<EarsSpecification, E>, Judgment> runtime;

		private StructuredBuilder(EvalRuntime<RequirementRequest<EarsSpecification, E>, Judgment> runtime) {
			this.runtime = Objects.requireNonNull(runtime);
		}

		/**
		 * Selects actual requirements before typed evidence.
		 * @param source declared roster
		 * @return evidence stage
		 */
		public StructuredRoster<E> requirements(List<? extends Requirement<EarsSpecification>> source) {
			return new StructuredRoster<>(runtime, snapshot(source));
		}

	}

	/**
	 * Structured roster with coherent common or requirement-specific evidence.
	 *
	 * @param <E> evidence type
	 */
	public static final class StructuredRoster<E> extends StructuredRosterStage<EarsSpecification, E> {

		private StructuredRoster(EvalRuntime<RequirementRequest<EarsSpecification, E>, Judgment> runtime,
				List<Requirement<EarsSpecification>> requirements) {
			super(runtime, requirements, EarsSpecification::applicability);
		}

		@Override
		EarsJury jury(List<Requirement<EarsSpecification>> requirements, Supplier<Verdict> operation) {
			return new EarsJury(requirements, operation);
		}

	}

	private static List<Requirement<EarsSpecification>> snapshot(List<? extends Requirement<EarsSpecification>> source) {
		return RosterJuries.snapshot("Ears", source);
	}

	private static Verdict generated(EvalModel runtime, List<Requirement<EarsSpecification>> requirements, String input) {
		List<EarsCriterion> nativeRoster = requirements.stream()
			.map(req -> new EarsCriterion(req.id(), req.specification().title(), req.specification().requirement(),
					req.specification().applicability()))
			.toList();
		String prompt = EarsParser.templateFor("Ears roster", nativeRoster).render(Map.of("workspace", input));
		var result = runtime.execute(RosterJuries.request(requirements, prompt));
		return RosterJuries.generated(requirements, EarsParser.rollupFor(nativeRoster, result.answer()), result,
				EarsSpecification::applicability);
	}

}
