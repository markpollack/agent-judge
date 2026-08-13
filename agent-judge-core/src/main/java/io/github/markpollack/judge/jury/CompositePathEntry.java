/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury;

import java.util.Objects;

/**
 * One canonical path and its corresponding composite attempt.
 * @param path RFC 6901-style path from the omitted root
 * @param attempt attempt at that path
 * @since 0.14.0
 */
public record CompositePathEntry(String path, CompositeAttempt attempt) {

	/** Validate the entry. */
	public CompositePathEntry {
		Objects.requireNonNull(path, "path must not be null");
		Objects.requireNonNull(attempt, "attempt must not be null");
		if (path.isEmpty() || path.charAt(0) != '/') {
			throw new IllegalArgumentException("attempt path must begin with '/'");
		}
	}

}
