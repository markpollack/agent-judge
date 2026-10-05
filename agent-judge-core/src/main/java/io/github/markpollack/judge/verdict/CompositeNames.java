package io.github.markpollack.judge.verdict;
import java.util.*;
import java.text.Normalizer;
/** Validation of retained local member identities. */
public final class CompositeNames { private CompositeNames() {}
 /** Validate a local NFC name with at most 64 Unicode scalars and no controls.
  * @param name retained local identity
  * @return the unchanged valid name
  */
	public static String requireValidName(String name) {
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
