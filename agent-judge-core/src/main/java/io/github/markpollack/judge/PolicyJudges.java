/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge;

import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

import io.github.markpollack.judge.description.ConfiguredJudge;
import io.github.markpollack.judge.acceptance.AcceptancePolicy;
import io.github.markpollack.judge.judgment.Judgment;
import io.github.markpollack.judge.acceptance.Policies;
import io.github.markpollack.judge.provenance.PolicyRef;

/** Compose an existing judge with an application-owned acceptance policy. */
public final class PolicyJudges {

	private PolicyJudges() {
	}

	/**
	 * Apply an internal reliance rule without a recording identity.
	 * @param <E> evidence type
	 * @param judge producer
	 * @param policy application-owned rule
	 * @return composed evaluator
	 */
	public static <E> Judge<E> apply(Judge<? super E> judge, AcceptancePolicy policy) {
		return apply(judge, Policies.referenceOf(policy), policy);
	}

	/**
	 * Wrap a judge, invoking it exactly once per evaluation before applying policy. Judge
	 * exceptions/null results remain invocation failures; only policy failures become
	 * PolicyFailure. Effective exclusion capability and metadata are retained.
	 * @param <E> evidence type
	 * @param judge raw judge
	 * @param reference explicit policy identity
	 * @param policy acceptance function
	 * @return the composed judge
	 * @throws NullPointerException if an argument is null
	 */
	public static <E> Judge<E> apply(Judge<? super E> judge, @Nullable PolicyRef reference, AcceptancePolicy policy) {
		Objects.requireNonNull(judge, "judge");
		Objects.requireNonNull(policy, "policy");
		return judge instanceof JudgeWithMetadata<?> ? new MetadataPolicyJudge<E>(judge, reference, policy)
				: new PolicyJudge<E>(judge, reference, policy);
	}

	private static class PolicyJudge<E> implements ConfiguredJudge<E> {

		final Judge<? super E> delegate;

		private final @Nullable PolicyRef reference;

		private final AcceptancePolicy policy;

		PolicyJudge(Judge<? super E> delegate, @Nullable PolicyRef reference, AcceptancePolicy policy) {
			this.delegate = delegate;
			this.reference = reference;
			this.policy = policy;
		}

		@Override
		public Judgment judge(E context) {
			return Policies.apply(delegate.judge(context), reference, policy);
		}

		@Override
		public Map<String, Object> configuration() {
			if (reference == null)
				return Map.of("judge", Judges.describe(delegate).toPortable());
			return Map.of("policy", Map.of("id", reference.id(), "revision", reference.revision(),
					"configurationDigest", reference.configurationDigest()), "judge",
					Judges.describe(delegate).toPortable());
		}

	}

	private static final class MetadataPolicyJudge<E> extends PolicyJudge<E> implements JudgeWithMetadata<E> {

		MetadataPolicyJudge(Judge<? super E> delegate, @Nullable PolicyRef reference, AcceptancePolicy policy) {
			super(delegate, reference, policy);
		}

		@Override
		public JudgeMetadata metadata() {
			JudgeMetadata metadata = Objects.requireNonNull(((JudgeWithMetadata<?>) delegate).metadata(),
					"judge metadata");
			return new JudgeMetadata(metadata.name(), metadata.description(), metadata.type(),
					Judges.notApplicableCapability(delegate).orElse(null));
		}

	}

}
