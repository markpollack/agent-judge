/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.provenance;

import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Immutable observed native execution facts. Unknown quantities are absent. Native
 * details use the portable value algebra or protected artifact references. An ID
 * identifies one invocation, including its internal turns, not one requirement.
 *
 * @param id invocation identity
 * @param protocol versioned native protocol
 * @param completed native completion fact
 * @param model reported model, when available
 * @param durationMillis measured elapsed milliseconds
 * @param nativeFacts exact available portable native facts
 * @param artifacts protected detail references
 * @param cause original observed failure, memory only; excluded from portable equality
 */
@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
public record Invocation(String id, String protocol,
		@com.fasterxml.jackson.annotation.JsonProperty(required = true) boolean completed, @Nullable String model,
		@com.fasterxml.jackson.annotation.JsonProperty(required = true) long durationMillis,
		Map<String, Object> nativeFacts, List<ArtifactRef> artifacts,
		@com.fasterxml.jackson.annotation.JsonIgnore @Nullable Throwable cause) {
	/**
	 * Observations without a thrown failure.
	 * @param id invocation identity
	 * @param protocol versioned protocol
	 * @param completed native completion
	 * @param model reported model
	 * @param durationMillis elapsed milliseconds
	 * @param nativeFacts native observations
	 * @param artifacts protected references
	 */
	public Invocation(String id, String protocol, boolean completed, @Nullable String model, long durationMillis,
			Map<String, Object> nativeFacts, List<ArtifactRef> artifacts) {
		this(id, protocol, completed, model, durationMillis, nativeFacts, artifacts, null);
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof Invocation v && id.equals(v.id) && protocol.equals(v.protocol)
				&& completed == v.completed && java.util.Objects.equals(model, v.model)
				&& durationMillis == v.durationMillis && nativeFacts.equals(v.nativeFacts)
				&& artifacts.equals(v.artifacts);
	}

	@Override
	public int hashCode() {
		return java.util.Objects.hash(id, protocol, completed, model, durationMillis, nativeFacts, artifacts);
	}

	/** Validates and freezes observations. */
	public Invocation {
		io.github.markpollack.judge.requirement.Requirement.requireText(id);
		io.github.markpollack.judge.requirement.Requirement.requireText(protocol);
		if (durationMillis < 0)
			throw new IllegalArgumentException("Negative duration");
		nativeFacts = io.github.markpollack.judge.judgment.PortableValues.copy(nativeFacts, "invocation.nativeFacts");
		artifacts = List.copyOf(artifacts);
	}
}
