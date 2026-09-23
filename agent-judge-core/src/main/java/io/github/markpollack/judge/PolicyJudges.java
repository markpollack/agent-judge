/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge;

import java.util.Map;
import java.util.Objects;

import io.github.markpollack.judge.context.JudgmentContext;
import io.github.markpollack.judge.description.ConfiguredJudge;
import io.github.markpollack.judge.result.AcceptancePolicy;
import io.github.markpollack.judge.result.Judgment;
import io.github.markpollack.judge.result.Policies;
import io.github.markpollack.judge.result.PolicyRef;

/** Compose an existing judge with an application-owned acceptance policy. */
public final class PolicyJudges {

	private PolicyJudges() {
	}

	/**
	 * Wrap a judge, invoking it exactly once per evaluation before applying policy. Judge
	 * exceptions/null results remain invocation failures; only policy failures become
	 * PolicyFailure. Effective exclusion capability and metadata are retained.
	 * @param judge raw judge
	 * @param reference explicit policy identity
	 * @param policy acceptance function
	 * @return the composed judge
	 * @throws NullPointerException if an argument is null
	 */
	public static Judge apply(Judge judge, PolicyRef reference, AcceptancePolicy policy) {
		Objects.requireNonNull(judge, "judge");
		Objects.requireNonNull(reference, "reference");
		Objects.requireNonNull(policy, "policy");
		return judge instanceof JudgeWithMetadata ? new MetadataPolicyJudge(judge, reference, policy)
				: new PolicyJudge(judge, reference, policy);
	}

	private static class PolicyJudge implements ConfiguredJudge {

		final Judge delegate;

		private final PolicyRef reference;

		private final AcceptancePolicy policy;

		PolicyJudge(Judge delegate, PolicyRef reference, AcceptancePolicy policy) {
			this.delegate = delegate;
			this.reference = reference;
			this.policy = policy;
		}

		@Override
		public Judgment judge(JudgmentContext context) {
			return Policies.apply(delegate.judge(context), reference, policy);
		}

		@Override
		public Map<String, Object> configuration() {
			return Map.of("policy", Map.of("id", reference.id(), "revision", reference.revision(),
					"configurationDigest", reference.configurationDigest()), "judge",
					Judges.describe(delegate).toPortable());
		}

	}

	private static final class MetadataPolicyJudge extends PolicyJudge implements JudgeWithMetadata {

		MetadataPolicyJudge(Judge delegate, PolicyRef reference, AcceptancePolicy policy) {
			super(delegate, reference, policy);
		}

		@Override
		public JudgeMetadata metadata() {
			JudgeMetadata metadata = Objects.requireNonNull(((JudgeWithMetadata) delegate).metadata(),
					"judge metadata");
			return new JudgeMetadata(metadata.name(), metadata.description(), metadata.type(),
					Judges.notApplicableCapability(delegate).orElse(null));
		}

	}

}
