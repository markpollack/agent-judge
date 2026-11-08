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
 * A complete Rfc2119 roster audit. A generated investigative execution receives the
 * entire roster once; parsed constituents do not execute additional Judges. Each vote is
 * fresh. Native tools/workspace are configured on the runtime.
 */
public final class Rfc2119Jury implements Jury {

	private final List<Requirement<Rfc2119Specification>> requirements;

	private final Supplier<Verdict> operation;

	private Rfc2119Jury(List<Requirement<Rfc2119Specification>> requirements, Supplier<Verdict> operation) {
		this.requirements = requirements;
		this.operation = operation;
	}

	@Override
	public Verdict vote() {
		return operation.get();
	}

	@Override
	public JuryDescription describe() {
		return new AuditJuryDescription("Rfc2119", new ArrayList<Requirement<?>>(requirements),
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
			return new GeneratedBuilder(runtime, PromptLimits.DEFAULT_MAX_PROMPT_CHARACTERS);
		}

		/**
		 * Configures explicitly counted per-requirement structured execution.
		 * @param <E> typed evidence
		 * @param runtime native per-item protocol
		 * @return typed roster stage
		 */
		public <E> StructuredBuilder<E> runtime(
				EvalRuntime<RequirementRequest<Rfc2119Specification, E>, Judgment> runtime) {
			return new StructuredBuilder<>(runtime);
		}

	}

	/** Generated roster construction. */
	public static final class GeneratedBuilder {

		private final EvalModel runtime;

		private final int maxPromptCharacters;

		private GeneratedBuilder(EvalModel runtime, int maxPromptCharacters) {
			this.runtime = Objects.requireNonNull(runtime);
			this.maxPromptCharacters = maxPromptCharacters;
		}

		/**
		 * Sets the largest prompt this roster may send in its one call; a larger one is an
		 * {@code ERROR} with no call. The default is
		 * {@link PromptLimits#DEFAULT_MAX_PROMPT_CHARACTERS}.
		 * @param maxPromptCharacters positive limit in characters
		 * @return this configuration with the limit
		 * @since 0.19.0
		 */
		public GeneratedBuilder maxPromptCharacters(int maxPromptCharacters) {
			return new GeneratedBuilder(runtime, PromptLimits.require(maxPromptCharacters));
		}

		/**
		 * Declares the actual complete roster.
		 * @param requirements native requirements
		 * @return configured roster, with optional prepared evidence
		 */
		public GeneratedRoster requirements(List<? extends Requirement<Rfc2119Specification>> requirements) {
			return new GeneratedRoster(runtime, snapshot(requirements), maxPromptCharacters);
		}

	}

	/** A roster fixed before execution. */
	public static final class GeneratedRoster {

		private final EvalModel runtime;

		private final List<Requirement<Rfc2119Specification>> requirements;

		private final int maxPromptCharacters;

		private GeneratedRoster(EvalModel runtime, List<Requirement<Rfc2119Specification>> requirements, int maxPromptCharacters) {
			this.runtime = runtime;
			this.requirements = requirements;
			this.maxPromptCharacters = maxPromptCharacters;
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
			return () -> new Rfc2119Jury(requirements, () -> generated(runtime, requirements, maxPromptCharacters,
					"Prepared evidence:\n" + Objects.requireNonNull(evidence.get())));
		}

		/**
		 * Builds an integrated investigative audit.
		 * @return ready roster Jury
		 */
		public Rfc2119Jury build() {
			runtime.requireInput(GeneratedInput.INTEGRATED_INVESTIGATION);
			return new Rfc2119Jury(requirements, () -> generated(runtime, requirements, maxPromptCharacters,
					"Investigate with the configured native workspace and tools."));
		}

	}

	/**
	 * Typed structured roster; each requirement owns its own native execution.
	 *
	 * @param <E> evidence type
	 */
	public static final class StructuredBuilder<E> {

		private final EvalRuntime<RequirementRequest<Rfc2119Specification, E>, Judgment> runtime;

		private StructuredBuilder(EvalRuntime<RequirementRequest<Rfc2119Specification, E>, Judgment> runtime) {
			this.runtime = Objects.requireNonNull(runtime);
		}

		/**
		 * Selects actual requirements before typed evidence.
		 * @param source declared roster
		 * @return evidence stage
		 */
		public StructuredRoster<E> requirements(List<? extends Requirement<Rfc2119Specification>> source) {
			return new StructuredRoster<>(runtime, snapshot(source));
		}

	}

	/**
	 * Structured roster with coherent common or requirement-specific evidence.
	 *
	 * @param <E> evidence type
	 */
	public static final class StructuredRoster<E> extends StructuredRosterStage<Rfc2119Specification, E> {

		private StructuredRoster(EvalRuntime<RequirementRequest<Rfc2119Specification, E>, Judgment> runtime,
				List<Requirement<Rfc2119Specification>> requirements) {
			super(runtime, requirements, Rfc2119Specification::applicability);
		}

		@Override
		Rfc2119Jury jury(List<Requirement<Rfc2119Specification>> requirements, Supplier<Verdict> operation) {
			return new Rfc2119Jury(requirements, operation);
		}

	}

	private static List<Requirement<Rfc2119Specification>> snapshot(List<? extends Requirement<Rfc2119Specification>> source) {
		return RosterJuries.snapshot("Rfc2119", source);
	}

	private static Verdict generated(EvalModel runtime, List<Requirement<Rfc2119Specification>> requirements, int maxPromptCharacters,
			String input) {
		List<Rfc2119Constraint> nativeRoster = requirements.stream()
			.map(req -> new Rfc2119Constraint(req.id(), req.specification().keyword(),
					req.specification().requirement(), req.specification().reason(),
					req.specification().applicability()))
			.toList();
		String prompt = Rfc2119Parser.templateFor("Rfc2119 roster", nativeRoster).render(Map.of("workspace", input));
		String refusal = RosterJuries.oversized(requirements, prompt, input, maxPromptCharacters);
		if (refusal != null)
			return RosterJuries.refused(requirements, refusal);
		var result = runtime.execute(RosterJuries.request(requirements, prompt));
		return RosterJuries.generated(requirements, Rfc2119Parser.rollupFor(nativeRoster, result.answer()), result,
				Rfc2119Specification::applicability);
	}

}
