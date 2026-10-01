/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.ai.requirements;

import java.util.*;
import java.util.function.Supplier;
import io.github.markpollack.judge.ai.model.*;
import io.github.markpollack.judge.execution.*;
import io.github.markpollack.judge.requirement.Requirement;
import io.github.markpollack.judge.jury.*;
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
		public GeneratedBuilder runtime(JudgeModel runtime) {
			return new GeneratedBuilder(Objects.requireNonNull(runtime));
		}

		/**
		 * Configures explicitly counted per-requirement structured execution.
		 * @param <E> typed evidence
		 * @param runtime native per-item protocol
		 * @return typed roster stage
		 */
		public <E> StructuredBuilder<E> runtime(
				NativeRuntime<RequirementRequest<EarsSpecification, E>, Judgment> runtime) {
			return new StructuredBuilder<>(runtime);
		}

	}

	/** Generated roster construction. */
	public static final class GeneratedBuilder {

		private final JudgeModel runtime;

		private GeneratedBuilder(JudgeModel runtime) {
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

		private final JudgeModel runtime;

		private final List<Requirement<EarsSpecification>> requirements;

		private GeneratedRoster(JudgeModel runtime, List<Requirement<EarsSpecification>> requirements) {
			this.runtime = runtime;
			this.requirements = requirements;
		}

		/**
		 * Selects prepared evidence without executing.
		 * @param evidence prepared evidence
		 * @return ready Jury construction
		 */
		public io.github.markpollack.judge.construction.ReadyJury evidence(String evidence) {
			Objects.requireNonNull(evidence);
			return evidenceSupplier(() -> evidence);
		}

		/**
		 * Configures fresh acquisition, once for a coherent whole-roster snapshot.
		 * @param evidence acquisition provider
		 * @return ready Jury construction
		 */
		public io.github.markpollack.judge.construction.ReadyJury evidenceSupplier(Supplier<String> evidence) {
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

		private final NativeRuntime<RequirementRequest<EarsSpecification, E>, Judgment> runtime;

		private StructuredBuilder(NativeRuntime<RequirementRequest<EarsSpecification, E>, Judgment> runtime) {
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
	public static final class StructuredRoster<E>
			implements io.github.markpollack.judge.construction.JuryEvidenceStep<E> {

		private final NativeRuntime<RequirementRequest<EarsSpecification, E>, Judgment> runtime;

		private final List<Requirement<EarsSpecification>> requirements;

		private StructuredRoster(NativeRuntime<RequirementRequest<EarsSpecification, E>, Judgment> runtime,
				List<Requirement<EarsSpecification>> requirements) {
			this.runtime = runtime;
			this.requirements = requirements;
		}

		@Override
		public io.github.markpollack.judge.construction.ReadyJury evidence(E evidence) {
			Objects.requireNonNull(evidence);
			return evidenceSupplier(() -> evidence);
		}

		@Override
		public io.github.markpollack.judge.construction.ReadyJury evidenceSupplier(Supplier<? extends E> evidence) {
			Objects.requireNonNull(evidence);
			return () -> new EarsJury(requirements, () -> {
				E value = Objects.requireNonNull(evidence.get(), "acquired evidence");
				Map<String, E> values = new LinkedHashMap<>();
				requirements.forEach(req -> values.put(req.id(), value));
				return structured(values);
			});
		}

		/**
		 * Selects a separate actual evidence bundle for each declared requirement.
		 * @param evidence complete map keyed by actual requirement identity
		 * @return ready Jury construction
		 */
		public io.github.markpollack.judge.construction.ReadyJury evidenceByRequirement(
				Map<String, ? extends E> evidence) {
			Map<String, E> captured = checked(evidence);
			return evidenceByRequirementSupplier(() -> captured);
		}

		/**
		 * Acquires one coherent collection snapshot before any native item executes.
		 * @param evidence complete requirement-specific acquisition
		 * @return ready Jury construction
		 */
		public io.github.markpollack.judge.construction.ReadyJury evidenceByRequirementSupplier(
				Supplier<? extends Map<String, ? extends E>> evidence) {
			Objects.requireNonNull(evidence);
			return () -> new EarsJury(requirements, () -> structured(checked(evidence.get())));
		}

		private Map<String, E> checked(Map<String, ? extends E> values) {
			Objects.requireNonNull(values, "evidence map");
			Set<String> ids = new LinkedHashSet<>();
			requirements.forEach(req -> ids.add(req.id()));
			if (!ids.equals(values.keySet()))
				throw new IllegalArgumentException("Evidence must cover exactly the declared roster");
			Map<String, E> copy = new LinkedHashMap<>();
			requirements
				.forEach(req -> copy.put(req.id(), Objects.requireNonNull(values.get(req.id()), "item evidence")));
			return Collections.unmodifiableMap(copy);
		}

		private Verdict structured(Map<String, E> evidence) {
			List<CompositeAttempt> attempts = new ArrayList<>();
			List<io.github.markpollack.judge.provenance.Invocation> invocations = new ArrayList<>();
			Set<String> nativeIds = new HashSet<>();
			for (var req : requirements) {
				try {
					var result = Objects.requireNonNull(
							runtime.execute(new RequirementRequest<>(req, evidence.get(req.id()))), "native result");
					if (!nativeIds.add(result.invocation().id()))
						throw new IllegalArgumentException(
								"Native execution reused invocation identity: " + result.invocation().id());
					invocations.add(result.invocation());
					Judgment associated = result.answer().forRequirement(req);
					invocations.addAll(associated.invocations());
					var references = new LinkedHashSet<>(associated.invocationIds());
					associated.invocations().forEach(nativeFact -> references.add(nativeFact.id()));
					references.add(result.invocation().id());
					Judgment original = associated.withInvocationIds(new ArrayList<>(references));
					attempts.add(
							CompositeAttempt.used(req.id(), CompositeRelation.ROSTER_ITEM, null, child(req, original)));
				}
				catch (java.util.concurrent.CancellationException cancellation) {
					throw cancellation;
				}
				catch (RuntimeException failure) {
					if (Thread.currentThread().isInterrupted())
						throw new java.util.concurrent.CancellationException("Roster interrupted");
					attempts.add(CompositeAttempt.executionFailed(req.id(), CompositeRelation.ROSTER_ITEM, null,
							new CompositeFailure(CompositeFailureCode.JURY_EXECUTION_FAILED, failure)));
				}
			}
			Map<String, io.github.markpollack.judge.provenance.Invocation> unique = new LinkedHashMap<>();
			for (var observed : invocations) {
				var previous = unique.putIfAbsent(observed.id(), observed);
				if (previous != null && !previous.equals(observed))
					throw new IllegalArgumentException("Conflicting native invocation identity: " + observed.id());
			}
			return complete(requirements, attempts, List.copyOf(unique.values()), Map.of());
		}

	}

	private static List<Requirement<EarsSpecification>> snapshot(
			List<? extends Requirement<EarsSpecification>> source) {
		List<Requirement<EarsSpecification>> roster = List.copyOf(source);
		new AuditJuryDescription("Ears", new ArrayList<Requirement<?>>(roster), false);
		roster.forEach(q -> NamedJury.requireValidName(q.id()));
		return roster;
	}

	private static Verdict child(Requirement<EarsSpecification> req, Judgment original) {
		return Verdict.observed(req.id(), original, req.specification().applicability()).forRequirement(req);
	}

	private static Verdict generated(JudgeModel runtime, List<Requirement<EarsSpecification>> requirements,
			String input) {
		List<EarsCriterion> nativeRoster = requirements.stream()
			.map(req -> new EarsCriterion(req.id(), req.specification().title(), req.specification().requirement(),
					req.specification().applicability()))
			.toList();
		String prompt = EarsParser.templateFor("Ears roster", nativeRoster).render(Map.of("workspace", input));
		var result = runtime.execute(JudgeModelRequest
			.user(requirements.stream().map(RequirementPrompts::header).collect(java.util.stream.Collectors.joining())
					+ prompt));
		Judgment parsed = EarsParser.rollupFor(nativeRoster, result.answer());
		boolean bound = Boolean.TRUE.equals(parsed.metadata().get("protocolIdentityBound"));
		List<CompositeAttempt> attempts = new ArrayList<>();
		for (int i = 0; i < requirements.size(); i++) {
			var req = requirements.get(i);
			Judgment original = parsed.checks()
				.get(i)
				.judgment()
				.forRequirement(req)
				.withInvocationIds(List.of(result.invocation().id()));
			Verdict child = child(req, original);
			attempts.add(bound ? CompositeAttempt.used(req.id(), CompositeRelation.ROSTER_ITEM, null, child)
					: CompositeAttempt.stageFailed(req.id(), CompositeRelation.ROSTER_ITEM, null,
							DispositionReason.PROTOCOL_UNBOUND, child));
		}
		return complete(requirements, attempts, List.of(result.invocation()), parsed.metadata());
	}

	private static Verdict complete(List<Requirement<EarsSpecification>> roster, List<CompositeAttempt> attempts,
			List<io.github.markpollack.judge.provenance.Invocation> invocations, Map<String, Object> facts) {
		boolean failed = false, incomplete = false, applicable = false;
		for (var attempt : attempts) {
			if (attempt.disposition() != AttemptDisposition.USED) {
				incomplete = true;
				continue;
			}
			var c = Objects.requireNonNull(attempt.verdict()).conclusion();
			failed |= c == Verdict.Conclusion.FAIL;
			applicable |= c != Verdict.Conclusion.NOT_APPLICABLE;
			incomplete |= c != Verdict.Conclusion.PASS && c != Verdict.Conclusion.NOT_APPLICABLE;
		}
		Judgment collective = failed ? Judgment.fail("An established roster requirement was violated")
				: incomplete ? Judgment.abstain("Not every applicable declared requirement was established")
						: applicable ? Judgment.pass("Every applicable declared requirement passed")
								: Judgment.notApplicable("Every declared requirement was justifiably excluded");
		collective = collective.toBuilder().metadata(facts).build();
		return Verdict.builder()
			.judgment(collective)
			.roster(roster)
			.declaredCardinality(roster.size())
			.invocations(invocations)
			.compositeAttempts(attempts)
			.provenance(new VerdictProvenance(VerdictProvenanceKind.ROSTER, null, null))
			.build();
	}

}
