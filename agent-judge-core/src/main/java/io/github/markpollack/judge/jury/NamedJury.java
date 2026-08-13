/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury;

import java.text.Normalizer;
import java.util.Objects;

/**
 * A jury paired with its stable configured composite-member identity.
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

	static String requireValidName(String name) {
		Objects.requireNonNull(name, "name must not be null");
		if (name.isEmpty() || name.isBlank()) {
			throw new IllegalArgumentException("name must be non-blank");
		}
		if (!Normalizer.isNormalized(name, Normalizer.Form.NFC)) {
			throw new IllegalArgumentException("name must already be NFC-normalized");
		}
		int scalarCount = 0;
		for (int offset = 0; offset < name.length();) {
			char current = name.charAt(offset);
			if (Character.isSurrogate(current) && (!Character.isHighSurrogate(current) || offset + 1 >= name.length()
					|| !Character.isLowSurrogate(name.charAt(offset + 1)))) {
				throw new IllegalArgumentException("name must contain only Unicode scalar values");
			}
			int codePoint = name.codePointAt(offset);
			int type = Character.getType(codePoint);
			if (Character.isISOControl(codePoint) || type == Character.FORMAT || type == Character.LINE_SEPARATOR
					|| type == Character.PARAGRAPH_SEPARATOR) {
				throw new IllegalArgumentException("name contains a forbidden Unicode code point");
			}
			offset += Character.charCount(codePoint);
			scalarCount++;
		}
		if (scalarCount > 64) {
			throw new IllegalArgumentException("name must contain at most 64 Unicode scalar values");
		}
		int first = name.codePointAt(0);
		int last = name.codePointBefore(name.length());
		if (isUnicodeWhitespace(first) || isUnicodeWhitespace(last)) {
			throw new IllegalArgumentException("name must not have leading or trailing Unicode whitespace");
		}
		return name;
	}

	private static boolean isUnicodeWhitespace(int codePoint) {
		return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
	}

}
