/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.assertions;

import java.util.Objects;
import org.jspecify.annotations.Nullable;
import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.requirement.Requirement;
import io.github.markpollack.judge.requirement.RequirementEvidence;
import io.github.markpollack.judge.acceptance.AcceptanceAction;
import io.github.markpollack.judge.acceptance.AcceptanceDecision;
import io.github.markpollack.judge.acceptance.AcceptancePolicy;
import io.github.markpollack.judge.acceptance.AppliedPolicy;
import io.github.markpollack.judge.acceptance.PolicyFailure;
import io.github.markpollack.judge.jury.AllMustPassStrategy;
import io.github.markpollack.judge.jury.ErrorPolicy;
import io.github.markpollack.judge.jury.Jury;
import io.github.markpollack.judge.jury.NotApplicablePolicy;
import io.github.markpollack.judge.jury.SimpleJury;
import io.github.markpollack.judge.jury.Verdict;
import io.github.markpollack.judge.jury.interpretation.Interpretation;
import io.github.markpollack.judge.jury.interpretation.ReadingSupport;
import io.github.markpollack.judge.jury.interpretation.RequirementOutcome;

/**
 * Application-owned reliance rules and requirement assertions. Requirements contain no
 * policy. An explicit terminal-stage rule overrides this fixture's default. Neither
 * changes internal Jury policies or the original Verdict and Interpretation.
 */
public final class RequirementAssertions {

	private final @Nullable AcceptancePolicy defaultPolicy;

	/**
	 * Configure a fixture's reliance rule.
	 * @param defaultPolicy rule, or null to require an explicit override
	 */
	public RequirementAssertions(@Nullable AcceptancePolicy defaultPolicy) {
		this.defaultPolicy = defaultPolicy;
	}

	/**
	 * Use the Judgment as rendered, without imposing a confidence threshold.
	 * @return immutable ordinary assertion configuration
	 */
	public static RequirementAssertions relyingOnJudgment() {
		return new RequirementAssertions(
				judgment -> new AcceptanceDecision(AcceptanceAction.RELY, "Rely on the Judgment as rendered"));
	}

	/**
	 * Evaluate an evidence-only Judge once through a normal one-seat Jury.
	 * @param <S> specification type
	 * @param <E> evidence type
	 * @param requirement requirement being checked
	 * @param judge evidence evaluator
	 * @param evidence exact evidence
	 * @param override final reliance override, or null for the fixture default
	 * @return complete retained result; no assertion is performed
	 */
	public <S, E> AssertionResult evaluate(Requirement<S> requirement, Judge<? super E> judge, E evidence,
			@Nullable AcceptancePolicy override) {
		Objects.requireNonNull(requirement, "requirement");
		var resolution = resolve(override);
		return AssertionResult.applyPolicy(requirement, resolution.policy(), resolution.source(),
				singleJudgeVerdict(Objects.requireNonNull(judge), Objects.requireNonNull(evidence)));
	}

	/**
	 * Evaluate a configured Jury once, retaining every individual and composition fact.
	 * @param <S> specification type
	 * @param <E> evidence type
	 * @param requirement requirement being checked
	 * @param jury complete configured Jury
	 * @param evidence exact evidence
	 * @param override final reliance override, or null for the fixture default
	 * @return original complete evaluation plus final acceptance execution
	 */
	public <S, E> AssertionResult evaluate(Requirement<S> requirement, Jury<E> jury, E evidence,
			@Nullable AcceptancePolicy override) {
		Objects.requireNonNull(requirement, "requirement");
		var resolution = resolve(override);
		return AssertionResult.applyPolicy(requirement, resolution.policy(), resolution.source(),
				Objects.requireNonNull(jury).vote(Objects.requireNonNull(evidence)));
	}

	/**
	 * Supply a requirement-aware Judge with the exact requirement and evidence.
	 * @param <S> specification type
	 * @param <E> evidence type
	 * @param requirement original native requirement
	 * @param judge evaluator that needs the specification
	 * @param evidence original evidence
	 * @param override final reliance override, or null
	 * @return complete retained result
	 */
	public <S, E> AssertionResult evaluateRequirement(Requirement<S> requirement,
			Judge<? super RequirementEvidence<S, E>> judge, E evidence, @Nullable AcceptancePolicy override) {
		return evaluate(requirement, judge, new RequirementEvidence<>(requirement, evidence), override);
	}

	/**
	 * Supply a requirement-aware Jury with the exact pair without reconfiguring it.
	 * @param <S> specification type
	 * @param <E> evidence type
	 * @param requirement original native requirement
	 * @param jury configured requirement-aware Jury
	 * @param evidence original evidence
	 * @param override final reliance override, or null
	 * @return complete retained result
	 */
	public <S, E> AssertionResult evaluateRequirement(Requirement<S> requirement, Jury<RequirementEvidence<S, E>> jury,
			E evidence, @Nullable AcceptancePolicy override) {
		return evaluate(requirement, jury, new RequirementEvidence<>(requirement, evidence), override);
	}

	static <E> Verdict singleJudgeVerdict(Judge<E> judge, E evidence) {
		return SimpleJury.<E>builder()
			.judge(judge)
			.votingStrategy(new AllMustPassStrategy(ErrorPolicy.PROPAGATE, NotApplicablePolicy.EXCLUDE))
			.parallel(false)
			.build()
			.vote(evidence);
	}

	private Resolution resolve(@Nullable AcceptancePolicy override) {
		if (override != null)
			return new Resolution(override, AssertionResult.PolicySource.EXPLICIT);
		if (defaultPolicy != null)
			return new Resolution(defaultPolicy, AssertionResult.PolicySource.DEFAULT);
		throw new IllegalStateException("Acceptance policy must resolve before evaluation");
	}

	private record Resolution(AcceptancePolicy policy, AssertionResult.PolicySource source) {
	}

	/**
	 * Assert an existing result without running evaluation again. Only a
	 * {@link ReadingSupport#SUPPORTED SUPPORTED} Interpretation with an SATISFIED reading
	 * and a successful final RELY application returns normally. Final policy never
	 * rewrites the Jury's retained Interpretation. This check invokes no Judge, provider
	 * or policy, and does not modify the result or its retained producer finding.
	 *
	 * <p>
	 * Use this after evaluation or with a result reconstructed from supported current
	 * typed V3 values. Unknown/legacy documents belong to the stored-map Interpretation
	 * reader; do not manufacture a supported modern result from them. An accepted
	 * negative finding is still VIOLATED, and ABSTAIN/ERROR outcomes stay distinct from
	 * subject violations.
	 * @param result existing result with its authoritative Interpretation
	 * @throws RequirementAssertionError.Rejected for supported VIOLATED
	 * @throws RequirementAssertionError.Inconclusive for supported UNRESOLVED or final
	 * ABSTAIN/ESCALATE
	 * @throws RequirementAssertionError.InstrumentFailure for supported NOT_ASSESSED or
	 * final policy failure
	 * @throws RequirementAssertionError.NotApplicable for supported NOT_APPLICABLE
	 * @throws RequirementAssertionError.UnsupportedReading for absent, contradicted or
	 * undetermined reading support
	 * @throws NullPointerException if result is null
	 * @since 0.18.0
	 */
	public static void requireSatisfied(AssertionResult result) {
		Objects.requireNonNull(result, "result");
		var reading = result.interpretation();
		if (reading.readingSupport() != ReadingSupport.SUPPORTED || reading.outcome() == null)
			throw new RequirementAssertionError.UnsupportedReading(result);
		if (reading.outcome() == RequirementOutcome.NOT_APPLICABLE)
			throw new RequirementAssertionError.NotApplicable(result);
		if (reading.outcome() == RequirementOutcome.NOT_ASSESSED)
			throw new RequirementAssertionError.InstrumentFailure(result);
		var application = result.acceptanceExecution().application();
		if (application instanceof PolicyFailure)
			throw new RequirementAssertionError.InstrumentFailure(result);
		if (application instanceof AppliedPolicy applied && applied.action() != AcceptanceAction.RELY)
			throw new RequirementAssertionError.Inconclusive(result);
		switch (reading.outcome()) {
			case SATISFIED -> {
				if (!(application instanceof AppliedPolicy))
					throw new RequirementAssertionError.UnsupportedReading(result);
			}
			case VIOLATED -> throw new RequirementAssertionError.Rejected(result);
			case UNRESOLVED -> throw new RequirementAssertionError.Inconclusive(result);
			case NOT_ASSESSED -> throw new RequirementAssertionError.InstrumentFailure(result);
			case NOT_APPLICABLE -> throw new RequirementAssertionError.NotApplicable(result);
		}
	}

}
