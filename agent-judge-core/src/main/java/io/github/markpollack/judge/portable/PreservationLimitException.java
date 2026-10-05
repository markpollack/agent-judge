/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.portable;

import java.util.Objects;

/**
 * A preservation bound refused the complete original; containment must never replace it.
 * The caller owns the original bytes/object and may persist them outside the typed codec.
 */
public final class PreservationLimitException extends RuntimeException {

	private final Object original;

	/**
	 * Retain the complete original rather than truncate it.
	 * @param message violated bound
	 * @param original complete original return or document
	 */
	public PreservationLimitException(String message, Object original) {
		super(message);
		this.original = Objects.requireNonNull(original);
	}

	/**
	 * Retain a complete document when a nested preservation refusal was wrapped.
	 * @param message violated bound
	 * @param original complete input document
	 * @param cause original nested refusal
	 */
	public PreservationLimitException(String message, Object original, Throwable cause) {
		super(message, cause);
		this.original = Objects.requireNonNull(original);
	}

	/**
	 * Complete original, including native SDK objects when capture exceeded a bound.
	 * @return unchanged original
	 */
	public Object original() {
		return original;
	}

	/**
	 * Propagate preservation refusal through asynchronous exception wrappers.
	 * @param failure observed failure
	 */
	public static void propagate(Throwable failure) {
		var seen = new java.util.IdentityHashMap<Throwable, Boolean>();
		for (Throwable current = failure; current != null
				&& seen.put(current, Boolean.TRUE) == null; current = current.getCause())
			if (current instanceof PreservationLimitException limit)
				throw limit;
	}

}
