/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.evaluation;

import java.util.Objects;
import io.github.markpollack.judge.policy.PolicyDecision;
import io.github.markpollack.judge.policy.PolicyAttribution;
import org.jspecify.annotations.Nullable;

/** Execution facts of an optional policy. No skipped requested-policy state exists. */
public sealed interface PolicyResult {

	/** No policy was configured. */
	record NotRequested() implements PolicyResult {
	}

	/**
	 * Retains the original successful decision.
	 *
	 * @param decision original decision
	 * @param attribution caller policy identity/configuration, or null
	 */
	record Decided(PolicyDecision decision, @Nullable PolicyAttribution attribution) implements PolicyResult {
		/**
		 * Unattributed functional policy decision.
		 * @param decision original decision
		 */
		public Decided(PolicyDecision decision) {
			this(decision, null);
		}

		/** Reject null decisions. */
		public Decided {
			Objects.requireNonNull(decision, "decision");
		}
	}

	/**
	 * Retains the original in-memory failure, without fabricating a policy action.
	 *
	 * @param cause original exception
	 * @param attribution caller policy identity/configuration, or null
	 */
	record Failed(Throwable cause, @Nullable PolicyAttribution attribution) implements PolicyResult {
		/**
		 * Unattributed functional policy failure.
		 * @param cause unchanged original exception
		 */
		public Failed(Throwable cause) {
			this(cause, null);
		}

		/** Reject null causes. */
		public Failed {
			Objects.requireNonNull(cause, "cause");
		}
	}

}
