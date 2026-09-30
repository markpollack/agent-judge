/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.assertions;

import java.util.Objects;
import org.jspecify.annotations.Nullable;
import io.github.markpollack.judge.assertions.AssertionResult.PolicySource;
import io.github.markpollack.judge.jury.Verdict;
import io.github.markpollack.judge.jury.interpretation.Interpretation;
import io.github.markpollack.judge.jury.interpretation.ReadingSupport;
import io.github.markpollack.judge.judgment.JudgmentStatus;
import io.github.markpollack.judge.acceptance.Policies;
import io.github.markpollack.judge.acceptance.PolicyApplication;
import io.github.markpollack.judge.acceptance.AcceptancePolicy;
import io.github.markpollack.judge.provenance.PolicyRef;

/**
 * The retained execution of the application's final acceptance policy, separate from all
 * policies that produced a Jury's Verdict. Exactly one of {@code application} and
 * {@code bypass} is present. This value retains no executable policy function.
 *
 * @param policy optional final policy recording identity
 * @param source final policy resolution source
 * @param application successful application or policy failure, otherwise null
 * @param bypass reason policy was not invoked, otherwise null
 */
public record AcceptanceExecution(@Nullable PolicyRef policy, PolicySource source,
		@Nullable PolicyApplication application, @Nullable Bypass bypass) {

	/** Validate identity and mutually exclusive application/bypass evidence. */
	public AcceptanceExecution {
		Objects.requireNonNull(source, "source");
		if ((application == null) == (bypass == null)) {
			throw new IllegalArgumentException("Exactly one final policy application or bypass is required");
		}
		if (application != null && !Objects.equals(policy, application.policy())) {
			throw new IllegalArgumentException("Final application must match the resolved policy identity");
		}
	}

	/** Reasons the retained evaluation cannot be submitted to final policy. */
	public enum Bypass {

		/** The complete Verdict does not support an authoritative reading. */
		UNSUPPORTED_READING,
		/** The retained interpretation reports a declared exclusion. */
		NOT_APPLICABLE,
		/** The retained interpretation reports instrument failure. */
		NOT_ASSESSED,
		/** The collective Judgment is ERROR, even if a tier established a rejection. */
		JUDGMENT_ERROR,
		/** The collective Judgment is NOT_APPLICABLE and cannot receive policy. */
		JUDGMENT_NOT_APPLICABLE

	}

	static @Nullable Bypass requiredBypass(Verdict verdict, Interpretation interpretation) {
		if (interpretation.readingSupport() != ReadingSupport.SUPPORTED || interpretation.outcome() == null) {
			return Bypass.UNSUPPORTED_READING;
		}
		switch (interpretation.outcome()) {
			case NOT_APPLICABLE:
				return Bypass.NOT_APPLICABLE;
			case NOT_ASSESSED:
				return Bypass.NOT_ASSESSED;
			default:
				break;
		}
		if (verdict.judgment().producerStatus() == JudgmentStatus.ERROR)
			return Bypass.JUDGMENT_ERROR;
		if (verdict.judgment().producerStatus() == JudgmentStatus.NOT_APPLICABLE)
			return Bypass.JUDGMENT_NOT_APPLICABLE;
		return null;
	}

	static AcceptanceExecution evaluate(Verdict verdict, Interpretation interpretation, AcceptancePolicy binding,
			PolicySource source) {
		Bypass bypass = requiredBypass(verdict, interpretation);
		if (bypass != null)
			return new AcceptanceExecution(Policies.referenceOf(binding), source, null, bypass);
		// Preserve the raw-view contract without inserting this temporary policy-bearing
		// Judgment into the Jury's original Verdict.
		var applied = Policies.apply(verdict.judgment(), Policies.referenceOf(binding), binding).policyApplication();
		return new AcceptanceExecution(Policies.referenceOf(binding), source, Objects.requireNonNull(applied), null);
	}
}
