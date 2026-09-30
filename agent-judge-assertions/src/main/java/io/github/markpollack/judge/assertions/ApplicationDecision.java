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
import io.github.markpollack.judge.result.JudgmentStatus;
import io.github.markpollack.judge.result.Policies;
import io.github.markpollack.judge.result.PolicyApplication;
import io.github.markpollack.judge.result.PolicyBinding;
import io.github.markpollack.judge.result.PolicyRef;

/**
 * The application's final policy decision, separate from all policies that produced a
 * Jury's Verdict. Exactly one of {@code application} and {@code bypass} is present.
 * This value retains no executable policy function.
 * @param policy resolved final policy identity
 * @param source final policy resolution source
 * @param application successful application or policy failure, otherwise null
 * @param bypass reason policy was not invoked, otherwise null
 */
public record ApplicationDecision(PolicyRef policy, PolicySource source, @Nullable PolicyApplication application,
        @Nullable Bypass bypass) {

    /** Validate identity and mutually exclusive application/bypass evidence. */
    public ApplicationDecision {
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(source, "source");
        if ((application == null) == (bypass == null)) {
            throw new IllegalArgumentException("Exactly one final policy application or bypass is required");
        }
        if (application != null && !policy.equals(application.policy())) {
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
        /** The raw aggregate is ERROR, even if a tier established a rejection. */
        AGGREGATE_ERROR,
        /** The raw aggregate is NOT_APPLICABLE and cannot receive policy. */
        AGGREGATE_NOT_APPLICABLE
    }

    static @Nullable Bypass requiredBypass(Verdict verdict, Interpretation interpretation) {
        if (interpretation.readingSupport() != ReadingSupport.SUPPORTED || interpretation.reading() == null) {
            return Bypass.UNSUPPORTED_READING;
        }
        switch (interpretation.reading()) {
            case NOT_APPLICABLE: return Bypass.NOT_APPLICABLE;
            case NOT_ASSESSED: return Bypass.NOT_ASSESSED;
            default: break;
        }
        if (verdict.aggregated().producerStatus() == JudgmentStatus.ERROR) return Bypass.AGGREGATE_ERROR;
        if (verdict.aggregated().producerStatus() == JudgmentStatus.NOT_APPLICABLE) return Bypass.AGGREGATE_NOT_APPLICABLE;
        return null;
    }

    static ApplicationDecision evaluate(Verdict verdict, Interpretation interpretation, PolicyBinding binding,
            PolicySource source) {
        Bypass bypass = requiredBypass(verdict, interpretation);
        if (bypass != null) return new ApplicationDecision(binding.reference(), source, null, bypass);
        // Preserve the raw-view contract without inserting this temporary policy-bearing
        // Judgment into the Jury's original Verdict.
        var applied = Policies.apply(verdict.aggregated(), binding.reference(), binding.policy()).policyApplication();
        return new ApplicationDecision(binding.reference(), source, Objects.requireNonNull(applied), null);
    }
}
