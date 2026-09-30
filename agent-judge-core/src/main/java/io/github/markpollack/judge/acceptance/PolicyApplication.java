/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.acceptance;

import org.jspecify.annotations.Nullable;

import io.github.markpollack.judge.provenance.PolicyRef;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Stored policy result. Application functions are independent of this immutable value.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
@JsonSubTypes({ @JsonSubTypes.Type(value = AppliedPolicy.class, name = "applied"),
		@JsonSubTypes.Type(value = PolicyFailure.class, name = "failure") })
public sealed interface PolicyApplication permits AppliedPolicy, PolicyFailure {

	/**
	 * Returns policy identity.
	 * @return policy identity
	 */
	@Nullable PolicyRef policy();

	/**
	 * Returns explanation of the application outcome.
	 * @return explanation of the application outcome
	 */
	String reason();

}
