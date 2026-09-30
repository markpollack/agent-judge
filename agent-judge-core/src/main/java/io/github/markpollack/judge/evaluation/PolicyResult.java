/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.evaluation;

import java.util.Objects;
import io.github.markpollack.judge.policy.PolicyDecision;

/** Execution facts of an optional policy. No skipped requested-policy state exists. */
public sealed interface PolicyResult {

	/** No policy was configured. */
	record NotRequested() implements PolicyResult {
	}

	/**
	 * Retains the original successful decision.
	 *
	 * @param decision original decision
	 */
	record Decided(PolicyDecision decision) implements PolicyResult {
		/** Reject null decisions. */
		public Decided {
			Objects.requireNonNull(decision, "decision");
		}
	}

	/**
	 * Retains the original in-memory failure, without fabricating a policy action.
	 *
	 * @param cause original exception
	 */
	record Failed(Throwable cause) implements PolicyResult {
		/** Reject null causes. */
		public Failed {
			Objects.requireNonNull(cause, "cause");
		}
	}

}
