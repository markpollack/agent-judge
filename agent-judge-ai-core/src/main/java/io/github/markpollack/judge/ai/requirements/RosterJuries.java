/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.ai.requirements;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import io.github.markpollack.judge.ai.model.EvalModelRequest;
import io.github.markpollack.judge.ai.model.EvalModelResponse;
import io.github.markpollack.judge.description.AuditJuryDescription;
import io.github.markpollack.judge.execution.EvalRuntime;
import io.github.markpollack.judge.execution.NativeExecution;
import io.github.markpollack.judge.execution.RequirementRequest;
import io.github.markpollack.judge.judgment.Judgment;
import io.github.markpollack.judge.jury.NamedJury;
import io.github.markpollack.judge.provenance.Invocation;
import io.github.markpollack.judge.requirement.Requirement;
import io.github.markpollack.judge.verdict.AttemptDisposition;
import io.github.markpollack.judge.verdict.CompositeAttempt;
import io.github.markpollack.judge.verdict.CompositeFailure;
import io.github.markpollack.judge.verdict.CompositeFailureCode;
import io.github.markpollack.judge.verdict.CompositeRelation;
import io.github.markpollack.judge.verdict.DispositionReason;
import io.github.markpollack.judge.verdict.Verdict;
import io.github.markpollack.judge.verdict.VerdictProvenance;
import io.github.markpollack.judge.verdict.VerdictProvenanceKind;
import org.jspecify.annotations.Nullable;

/**
 * Roster execution shared by {@link EarsJury} and {@link Rfc2119Jury}: snapshot and
 * evidence checks, the structured per-item path, the attempts of a generated roster
 * audit, and the collective roster verdict. Each jury supplies its specification type
 * and how a specification declares its applicability.
 */
final class RosterJuries {

	private RosterJuries() {
	}

	/**
	 * Freeze and validate a declared roster before execution.
	 * @param <S> specification type
	 * @param name description name of the jury kind
	 * @param source declared roster
	 * @return immutable roster
	 */
	static <S> List<Requirement<S>> snapshot(String name, List<? extends Requirement<S>> source) {
		List<Requirement<S>> roster = List.copyOf(source);
		new AuditJuryDescription(name, new ArrayList<Requirement<?>>(roster), false);
		roster.forEach(q -> NamedJury.requireValidName(q.id()));
		return roster;
	}

	/**
	 * Require requirement-specific evidence to cover exactly the declared roster.
	 * @param <E> evidence type
	 * @param requirements declared roster
	 * @param values evidence keyed by requirement identity
	 * @return immutable copy in roster order
	 */
	static <E> Map<String, E> checked(List<? extends Requirement<?>> requirements, Map<String, ? extends E> values) {
		Objects.requireNonNull(values, "evidence map");
		Set<String> ids = new LinkedHashSet<>();
		requirements.forEach(req -> ids.add(req.id()));
		if (!ids.equals(values.keySet()))
			throw new IllegalArgumentException("Evidence must cover exactly the declared roster");
		Map<String, E> copy = new LinkedHashMap<>();
		requirements.forEach(req -> copy.put(req.id(), Objects.requireNonNull(values.get(req.id()), "item evidence")));
		return Collections.unmodifiableMap(copy);
	}

	/**
	 * The same evidence for every declared requirement.
	 * @param <E> evidence type
	 * @param requirements declared roster
	 * @param value acquired evidence
	 * @return evidence keyed by requirement identity, in roster order
	 */
	static <E> Map<String, E> common(List<? extends Requirement<?>> requirements, E value) {
		Map<String, E> values = new LinkedHashMap<>();
		requirements.forEach(req -> values.put(req.id(), value));
		return values;
	}

	/**
	 * Execute each declared requirement once with its own evidence.
	 * @param <S> specification type
	 * @param <E> evidence type
	 * @param requirements declared roster
	 * @param runtime native per-item protocol
	 * @param evidence evidence keyed by requirement identity
	 * @param applicability the specification's declared exclusion condition
	 * @return collective roster verdict
	 */
	static <S, E> Verdict structured(List<Requirement<S>> requirements,
			EvalRuntime<RequirementRequest<S, E>, Judgment> runtime, Map<String, E> evidence,
			Function<S, @Nullable String> applicability) {
		List<CompositeAttempt> attempts = new ArrayList<>();
		List<Invocation> invocations = new ArrayList<>();
		Set<String> nativeIds = new HashSet<>();
		for (var req : requirements) {
			try {
				var result = Objects.requireNonNull(
						runtime.execute(new RequirementRequest<>(req, evidence.get(req.id()))), "native result");
				if (!nativeIds.add(result.invocation().id()))
					throw new IllegalArgumentException(
							"Native execution reused invocation identity: " + result.invocation().id());
				invocations.add(result.invocation());
				invocations.addAll(result.answer().invocations());
				attempts.add(itemAttempt(req, referenced(result), applicability));
			}
			catch (java.util.concurrent.CancellationException cancellation) {
				throw cancellation;
			}
			catch (RuntimeException failure) {
				io.github.markpollack.judge.portable.PreservationLimitException.propagate(failure);
				if (Thread.currentThread().isInterrupted())
					throw new java.util.concurrent.CancellationException("Roster interrupted");
				attempts.add(CompositeAttempt.executionFailed(req.id(), CompositeRelation.ROSTER_ITEM, null,
						new CompositeFailure(CompositeFailureCode.JURY_EXECUTION_FAILED, failure)));
			}
		}
		return complete(requirements, attempts, unique(invocations), Map.of());
	}

	/** The item's answer, referencing its own invocation and every lower one it carries. */
	private static Judgment referenced(NativeExecution<Judgment> result) {
		Judgment associated = result.answer();
		var references = new LinkedHashSet<>(associated.invocationIds());
		associated.invocations().forEach(nativeFact -> references.add(nativeFact.id()));
		references.add(result.invocation().id());
		return associated.withInvocationIds(new ArrayList<>(references));
	}

	private static <S> CompositeAttempt itemAttempt(Requirement<S> req, Judgment original,
			Function<S, @Nullable String> applicability) {
		try {
			return CompositeAttempt.used(req.id(), CompositeRelation.ROSTER_ITEM, null,
					child(req, original.forRequirement(req), applicability));
		}
		catch (IllegalArgumentException rejected) {
			io.github.markpollack.judge.portable.PreservationLimitException.propagate(rejected);
			// The native call returned. Preserve its actual association and observations
			// without claiming an invocation failure or violation.
			Verdict unbound = Verdict.observed(req.id(), original, applicability.apply(req.specification()));
			if (original.requirement() != null)
				unbound = unbound.forRequirement(original.requirement());
			return CompositeAttempt.stageFailed(req.id(), CompositeRelation.ROSTER_ITEM, null,
					DispositionReason.PROTOCOL_UNBOUND, unbound);
		}
	}

	private static List<Invocation> unique(List<Invocation> invocations) {
		Map<String, Invocation> unique = new LinkedHashMap<>();
		for (var observed : invocations) {
			var previous = unique.putIfAbsent(observed.id(), observed);
			if (previous != null && !previous.equals(observed))
				throw new IllegalArgumentException("Conflicting native invocation identity: " + observed.id());
		}
		return List.copyOf(unique.values());
	}

	/**
	 * The single generated request for a whole roster: each requirement's header, then
	 * the rendered audit prompt.
	 * @param requirements declared roster
	 * @param prompt rendered audit prompt
	 * @return generated request
	 */
	static EvalModelRequest request(List<? extends Requirement<?>> requirements, String prompt) {
		return EvalModelRequest.user(
				requirements.stream().map(RequirementPrompts::header).collect(java.util.stream.Collectors.joining())
						+ prompt);
	}

	/**
	 * The roster verdict of one generated audit: one attempt per declared requirement,
	 * each referencing the audit's single invocation.
	 * @param <S> specification type
	 * @param requirements declared roster
	 * @param parsed the parser's rollup, one check per requirement in roster order
	 * @param result the generated execution
	 * @param applicability the specification's declared exclusion condition
	 * @return collective roster verdict
	 */
	static <S> Verdict generated(List<Requirement<S>> requirements, Judgment parsed,
			NativeExecution<EvalModelResponse> result, Function<S, @Nullable String> applicability) {
		boolean bound = Boolean.TRUE.equals(parsed.metadata().get("protocolIdentityBound"));
		List<CompositeAttempt> attempts = new ArrayList<>();
		for (int i = 0; i < requirements.size(); i++) {
			var req = requirements.get(i);
			Judgment original = parsed.checks()
				.get(i)
				.judgment()
				.forRequirement(req)
				.withInvocationIds(List.of(result.invocation().id()));
			Verdict child = child(req, original, applicability);
			attempts.add(bound ? CompositeAttempt.used(req.id(), CompositeRelation.ROSTER_ITEM, null, child)
					: CompositeAttempt.stageFailed(req.id(), CompositeRelation.ROSTER_ITEM, null,
							DispositionReason.PROTOCOL_UNBOUND, child));
		}
		return complete(requirements, attempts, List.of(result.invocation()), parsed.metadata());
	}

	private static <S> Verdict child(Requirement<S> req, Judgment original,
			Function<S, @Nullable String> applicability) {
		return Verdict.observed(req.id(), original, applicability.apply(req.specification())).forRequirement(req);
	}

	private static <S> Verdict complete(List<Requirement<S>> roster, List<CompositeAttempt> attempts,
			List<Invocation> invocations, Map<String, Object> facts) {
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
		return Verdict.advancedBuilder()
			.judgment(collective)
			.roster(roster)
			.declaredCardinality(roster.size())
			.invocations(invocations)
			.compositeAttempts(attempts)
			.provenance(new VerdictProvenance(VerdictProvenanceKind.ROSTER, null, null))
			.build();
	}

}
