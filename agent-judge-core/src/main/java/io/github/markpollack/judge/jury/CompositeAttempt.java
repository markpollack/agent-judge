/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.jspecify.annotations.Nullable;

/**
 * One named composite stage that was entered during jury execution.
 *
 * @param name stable configured sibling identity
 * @param relation relationship to the parent verdict
 * @param policy cascade policy, required only for {@link CompositeRelation#CASCADE_TIER}
 * @param verdict complete returned verdict, mutually exclusive with {@code failure}
 * @param failure portable failure evidence, mutually exclusive with {@code verdict}
 * @since 0.14.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "name", "relation", "policy", "verdict", "failure" })
public record CompositeAttempt(String name, CompositeRelation relation, @Nullable TierPolicy policy,
		@Nullable Verdict verdict, @Nullable CompositeFailure failure) {

	/** Validate identity, relation/policy legality, and the exactly-one outcome rule. */
	public CompositeAttempt {
		name = NamedJury.requireValidName(name);
		Objects.requireNonNull(relation, "relation must not be null");
		if ((verdict == null) == (failure == null)) {
			throw new IllegalArgumentException("exactly one of verdict and failure must be present");
		}
		if (relation == CompositeRelation.CASCADE_TIER && policy == null) {
			throw new IllegalArgumentException("CASCADE_TIER requires a policy");
		}
		if (relation == CompositeRelation.META_MEMBER && policy != null) {
			throw new IllegalArgumentException("META_MEMBER forbids a policy");
		}
	}

}
