/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.assertions;

import io.github.markpollack.judge.requirement.Requirement;
import io.github.markpollack.judge.result.PolicyBinding;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.context.JudgmentContext;
import io.github.markpollack.judge.jury.interpretation.ReadingSupport;
import io.github.markpollack.judge.result.ArtifactRef;
import io.github.markpollack.judge.result.AcceptanceAction;
import io.github.markpollack.judge.result.AppliedPolicy;
import io.github.markpollack.judge.result.PolicyFailure;
import io.github.markpollack.judge.jury.interpretation.VerdictReading;

/**
 * Evaluates requirements through a configured Judge and application policy, then asserts
 * the original Verdict's authoritative Interpretation together with a separate final
 * application decision. Configure one instance in a test
 * fixture and call {@link #assertThat(JudgmentContext)} to bind evidence. Its
 * {@code satisfies} terminal evaluates eagerly;
 * {@link #evaluate(JudgmentContext, Requirement)} instead returns the result for
 * inspection before {@link #requireSatisfied(AssertionResult)}.
 *
 * <p>
 * Policy decides whether to use, withhold or request escalation of the Judge's
 * assessment; it does not rewrite that assessment. This one-seat facade records
 * escalation intent but does not call another Judge or a human. There is no global
 * policy, implicit threshold or resource lookup. Resolve provider resources in caller
 * setup. Route resolution occurs on the caller thread before the normal one-seat jury,
 * which invokes its judge on that same thread so caller cancellation remains visible; the
 * caller must provide thread-safe route, Judge and policy delegates and immutable
 * evidence payloads for parallel use. This facade never changes the context goal or
 * metadata to make evidence match a requirement. A supported Interpretation means its
 * recorded structure supports the reading, not that the evaluator is confident or its
 * assessment is correct.
 */
public final class SemanticAssertions {

	private final Function<Requirement<?>, Judge<JudgmentContext>> route;

	private final @Nullable PolicyBinding defaultPolicy;

	/**
	 * Configure explicit routing and consequence.
	 * @param route caller-thread requirement-to-Judge resolution
	 * @param defaultPolicy default, or null when every named requirement needs an
	 * override
	 */
	public SemanticAssertions(Function<Requirement<?>, Judge<JudgmentContext>> route, @Nullable PolicyBinding defaultPolicy) {
		this.route = Objects.requireNonNull(route);
		this.defaultPolicy = defaultPolicy;
	}

	/**
	 * Bind an immutable context, without inference.
	 * @param evidence selected requirement-specific evidence
	 * @return immutable terminal assertion
	 */
	public SemanticAssertion assertThat(JudgmentContext evidence) {
		return new SemanticAssertion(this, Objects.requireNonNull(evidence));
	}

	/**
	 * Invoke the configured Judge once, retaining a normal jury result. This may perform
	 * provider inference. Inspect or store the returned result and then pass it to
	 * {@link #requireSatisfied(AssertionResult)} to assert it without evaluating again.
	 * Setup errors fail before inference; judge invocation failures are contained by the
	 * jury. This method does not assert success. Calling it after {@code satisfies}
	 * performs a second evaluation, which may yield a different assessment.
	 * @param evidence exact requirement-specific context
	 * @param requirement named requirement and optional prior policy
	 * @return retained resolution, verdict and authoritative reading
	 */
	public AssertionResult evaluate(JudgmentContext evidence, Requirement<?> requirement) {
		Objects.requireNonNull(evidence);
		Objects.requireNonNull(requirement);
		PolicyBinding override = requirement.acceptancePolicy();
		PolicyBinding binding = override == null ? defaultPolicy : override;
		if (binding == null)
			throw new IllegalStateException("Explicit acceptance policy required before inference");
		if (!requirement.text().equals(evidence.goal()))
			throw new IllegalArgumentException(
					"Evidence goal must exactly match requirement text; rebinding is forbidden");
		Judge<JudgmentContext> judge = Objects.requireNonNull(route.apply(requirement), "route returned no judge");
		return AssertionResult.applyPolicy(requirement, binding,
				override == null ? AssertionResult.PolicySource.DEFAULT : AssertionResult.PolicySource.ASSOCIATED,
				RequirementAssertions.singleJudgeVerdict(judge, evidence));
	}

	Requirement<?> stringRequirement(String text) {
		if (defaultPolicy == null)
			throw new IllegalStateException("String requirements need a configured default policy before inference");
		Requirement.requireText(text);
		String digest = ArtifactRef.ofBytes("requirement", text.getBytes(StandardCharsets.UTF_8), null).sha256();
		return Requirement.text("text:sha256:" + digest, "1", text);
	}

	/**
	 * Assert an existing result without running evaluation again. Only a
	 * {@link ReadingSupport#SUPPORTED SUPPORTED} Interpretation with an ACCEPTED reading
	 * and a successful final USE_ASSESSMENT application returns normally. Final policy
	 * never rewrites the Jury's retained Interpretation. This check invokes no Judge, provider or policy,
	 * and does not modify the result or its retained producer assessment.
	 *
	 * <p>
	 * Use this after {@link #evaluate(JudgmentContext, Requirement)} or with a result
	 * reconstructed from supported current typed V2 values. Unknown/legacy documents
	 * belong to the stored-map Interpretation reader; do not manufacture a supported
	 * modern result from them. An accepted negative assessment is still REJECTED, and
	 * ABSTAIN/ERROR outcomes stay distinct from subject violations.
	 * @param result existing result with its authoritative Interpretation
	 * @throws SemanticAssertionError.Rejected for supported REJECTED
	 * @throws SemanticAssertionError.Inconclusive for supported UNDECIDED or final
	 * ABSTAIN/ESCALATE
	 * @throws SemanticAssertionError.InstrumentFailure for supported NOT_ASSESSED or
	 * final policy failure
	 * @throws SemanticAssertionError.NotApplicable for supported NOT_APPLICABLE
	 * @throws SemanticAssertionError.UnsupportedReading for absent, contradicted or
	 * undetermined reading support
	 * @throws NullPointerException if result is null
	 * @since 0.18.0
	 */
	public static void requireSatisfied(AssertionResult result) {
		Objects.requireNonNull(result, "result");
		var reading = result.interpretation();
		if (reading.readingSupport() != ReadingSupport.SUPPORTED || reading.reading() == null)
			throw new SemanticAssertionError.UnsupportedReading(result);
		if (reading.reading() == VerdictReading.NOT_APPLICABLE)
			throw new SemanticAssertionError.NotApplicable(result);
		if (reading.reading() == VerdictReading.NOT_ASSESSED)
			throw new SemanticAssertionError.InstrumentFailure(result);
		var application = result.applicationDecision().application();
		if (application instanceof PolicyFailure)
			throw new SemanticAssertionError.InstrumentFailure(result);
		if (application instanceof AppliedPolicy applied && applied.action() != AcceptanceAction.USE_ASSESSMENT)
			throw new SemanticAssertionError.Inconclusive(result);
		switch (reading.reading()) {
			case ACCEPTED -> {
				if (!(application instanceof AppliedPolicy))
					throw new SemanticAssertionError.UnsupportedReading(result);
			}
			case REJECTED -> throw new SemanticAssertionError.Rejected(result);
			case UNDECIDED -> throw new SemanticAssertionError.Inconclusive(result);
			case NOT_ASSESSED -> throw new SemanticAssertionError.InstrumentFailure(result);
			case NOT_APPLICABLE -> throw new SemanticAssertionError.NotApplicable(result);
		}
	}

}
