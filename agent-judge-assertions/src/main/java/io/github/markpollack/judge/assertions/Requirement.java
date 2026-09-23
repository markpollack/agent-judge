/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.assertions;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Exact requirement identity and text, with optional prior consequence policy.
 *
 * @param id stable requirement identity
 * @param revision requirement revision
 * @param text exact requirement, matching the evidence context goal
 * @param acceptancePolicy named override, or null to use the facade default
 */
public record Requirement(String id, String revision, String text, @Nullable PolicyBinding acceptancePolicy) {
	/** Validate exact, nonblank values without trimming or normalization. */
	public Requirement {
		requireText(id);
		requireText(revision);
		requireText(text);
	}

	/**
	 * Create a requirement using the facade default policy.
	 * @param id requirement identity
	 * @param revision requirement revision
	 * @param text exact requirement
	 */
	public Requirement(String id, String revision, String text) {
		this(id, revision, text, null);
	}

	/**
	 * Bind consequence before evaluation without changing this value.
	 * @param policy explicit override
	 * @return a new requirement
	 */
	public Requirement under(PolicyBinding policy) {
		return new Requirement(id, revision, text, Objects.requireNonNull(policy));
	}

	static void requireText(String text) {
		if (Objects.requireNonNull(text).isBlank())
			throw new IllegalArgumentException("Nonblank requirement identity and text required");
		for (int i = 0; i < text.length(); i++) {
			char value = text.charAt(i);
			if (Character.isHighSurrogate(value)) {
				if (++i == text.length() || !Character.isLowSurrogate(text.charAt(i)))
					throw new IllegalArgumentException("Unpaired surrogate in requirement");
			}
			else if (Character.isLowSurrogate(value))
				throw new IllegalArgumentException("Unpaired surrogate in requirement");
		}
	}
}
