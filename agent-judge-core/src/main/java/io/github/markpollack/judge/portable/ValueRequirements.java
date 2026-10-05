/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.portable;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Shared construction requirements for portable result values. */
public final class ValueRequirements {

	private ValueRequirements() {
	}

	/** Require nonblank text.
	 * @param value value to validate
	 * @param name diagnostic name
	 * @return unchanged valid text
	 */
	public static String text(String value, String name) {
		Objects.requireNonNull(value, name + " must not be null");
		if (value.isBlank()) {
			throw new IllegalArgumentException(name + " must be non-blank");
		}
		return value;
	}

	/** Require an explicit version suffix.
	 * @param value value to validate
	 * @param name diagnostic name
	 * @return unchanged version identity
	 */
	public static String versioned(String value, String name) {
		text(value, name);
		// Version identity is explicit, not inferred from a mutable display name.
		if (!value.matches(".+(?:[:/@#-]v?[0-9]+)(?:[.][0-9]+)*")) {
			throw new IllegalArgumentException(name + " requires an explicit version suffix (for example :v1)");
		}
		return value;
	}

	/** Require a lowercase SHA-256 digest.
	 * @param value value to validate
	 * @return unchanged digest
	 */
	public static String digest(String value) {
		if (!text(value, "sha256").matches("[0-9a-f]{64}")) {
			throw new IllegalArgumentException("sha256 must contain 64 lowercase hexadecimal characters");
		}
		return value;
	}

	/** Copy a nonempty unique text domain.
	 * @param values domain values
	 * @param name diagnostic name
	 * @return immutable domain
	 */
	public static List<String> domain(List<String> values, String name) {
		List<String> copy = List.copyOf(values);
		if (copy.isEmpty()) {
			throw new IllegalArgumentException(name + " must not be empty");
		}
		copy.forEach(value -> text(value, name));
		if (new HashSet<>(copy).size() != copy.size()) {
			throw new IllegalArgumentException(name + " must be unique");
		}
		return copy;
	}

	/** Require a finite probability in the unit interval.
	 * @param value value to validate
	 */
	public static void probability(double value) {
		if (!Double.isFinite(value) || value < 0 || value > 1) {
			throw new IllegalArgumentException("probability/support must be finite in [0,1]");
		}
	}

}
