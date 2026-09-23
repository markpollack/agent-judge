/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.assertions;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.PolicyJudges;
import io.github.markpollack.judge.context.JudgmentContext;
import io.github.markpollack.judge.jury.SimpleJury;
import io.github.markpollack.judge.jury.AllMustPassStrategy;
import io.github.markpollack.judge.jury.ErrorPolicy;
import io.github.markpollack.judge.jury.NotApplicablePolicy;
import io.github.markpollack.judge.jury.interpretation.ReadingSupport;
import io.github.markpollack.judge.result.ArtifactRef;

/**
 * Immutable ordinary-assertion facade; no global policy, implicit threshold or resource
 * lookup. Resolve provider resources in caller setup. Route resolution occurs on the
 * caller thread before the normal one-seat jury, which invokes its judge on that same
 * thread so caller cancellation remains visible; the caller must provide thread-safe
 * route, Judge and policy delegates and immutable evidence payloads for parallel use.
 * This facade never changes the context goal or metadata to make evidence match a
 * requirement.
 */
public final class SemanticAssertions {

	private final Function<Requirement, Judge> route;

	private final @Nullable PolicyBinding defaultPolicy;

	/**
	 * Configure explicit routing and consequence.
	 * @param route caller-thread requirement-to-Judge resolution
	 * @param defaultPolicy default, or null when every named requirement needs an
	 * override
	 */
	public SemanticAssertions(Function<Requirement, Judge> route, @Nullable PolicyBinding defaultPolicy) {
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
	 * Evaluate once for audit/replay consumers, retaining a normal jury result. Setup
	 * errors fail before inference; judge invocation failures are contained by the jury.
	 * This method does not assert success.
	 * @param evidence exact requirement-specific context
	 * @param requirement named requirement and optional prior policy
	 * @return retained resolution, verdict and authoritative reading
	 */
	public AssertionResult evaluate(JudgmentContext evidence, Requirement requirement) {
		Objects.requireNonNull(evidence);
		Objects.requireNonNull(requirement);
		PolicyBinding override = requirement.acceptancePolicy();
		PolicyBinding binding = override == null ? defaultPolicy : override;
		if (binding == null)
			throw new IllegalStateException("Explicit acceptance policy required before inference");
		if (!requirement.text().equals(evidence.goal()))
			throw new IllegalArgumentException(
					"Evidence goal must exactly match requirement text; rebinding is forbidden");
		Judge judge = Objects.requireNonNull(route.apply(requirement), "route returned no judge");
		var jury = SimpleJury.builder()
			.judge(PolicyJudges.apply(judge, binding.reference(), binding.policy()))
			.votingStrategy(new AllMustPassStrategy(ErrorPolicy.PROPAGATE, NotApplicablePolicy.EXCLUDE))
			.parallel(false)
			.build();
		return new AssertionResult(requirement, binding.reference(),
				override == null ? AssertionResult.PolicySource.DEFAULT : AssertionResult.PolicySource.REQUIREMENT,
				jury.vote(evidence));
	}

	Requirement stringRequirement(String text) {
		if (defaultPolicy == null)
			throw new IllegalStateException("String requirements need a configured default policy before inference");
		Requirement.requireText(text);
		String digest = ArtifactRef.ofBytes("requirement", text.getBytes(StandardCharsets.UTF_8), null).sha256();
		return new Requirement("text:sha256:" + digest, "1", text);
	}

	static void requireSatisfied(AssertionResult result) {
		var reading = result.interpretation();
		if (reading.readingSupport() != ReadingSupport.SUPPORTED || reading.reading() == null)
			throw new SemanticAssertionError.UnsupportedReading(result);
		switch (reading.reading()) {
			case ACCEPTED -> {
			}
			case REJECTED -> throw new SemanticAssertionError.Rejected(result);
			case UNDECIDED -> throw new SemanticAssertionError.Inconclusive(result);
			case NOT_ASSESSED -> throw new SemanticAssertionError.InstrumentFailure(result);
			case NOT_APPLICABLE -> throw new SemanticAssertionError.NotApplicable(result);
		}
	}

}
