/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury;

import java.text.Normalizer;
import java.util.Objects;

/**
 * A jury paired with its stable configured composite-member identity.
 *
 * @param name unique sibling identity
 * @param jury configured jury
 * @since 0.14.0
 */
public record NamedJury(String name, Jury jury) {

	/** Validate the configured name and jury. */
	public NamedJury {
		name = requireValidName(name);
		Objects.requireNonNull(jury, "jury must not be null");
	}

	/**
	 * Validates a stable local composition name.
	 * @param name proposed name
	 * @return the unchanged validated name
	 * @throws IllegalArgumentException if blank, noncanonical, unsafe or longer than 128
	 * Unicode scalars
	 */
	public static String requireValidName(String name) { return io.github.markpollack.judge.verdict.CompositeNames.requireValidName(name); }
}
