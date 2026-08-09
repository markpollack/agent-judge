/*
 * Copyright (c) 2026 Mark Pollack
 * See LICENSE.txt in the repository root for license terms.
 */

package io.github.markpollack.judge.result;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

/**
 * The portable value algebra enforced on {@link Judgment} result metadata.
 *
 * <p>
 * A result crosses process and language boundaries, so its metadata is restricted to values
 * an ordinary JSON consumer can represent: strings, booleans, interoperable integers, finite
 * numbers, arrays, and string-keyed objects, recursively. Everything else — a live
 * exception, a {@code Duration}, an SDK response, an enum constant, an arbitrary-precision
 * number — is a Java identity rather than a value, and is refused at construction rather
 * than discovered later by whatever tries to serialize it.
 * </p>
 *
 * <p>
 * Absence is expressed by omitting a key, exactly as an absent {@code score} or
 * {@code label} is expressed by omitting a property. {@code null} is therefore not a member
 * of the algebra at any depth.
 * </p>
 *
 * <p>
 * Accepted containers are copied and frozen on the way in. A caller keeps its own map, list,
 * or array and may keep mutating it; the constructed judgment is unaffected. Arrays
 * normalize into immutable lists because an array cannot be frozen.
 * </p>
 *
 * @author Mark Pollack
 * @since 0.14.0
 */
final class PortableValues {

	/**
	 * The largest magnitude an IEEE-754 double represents exactly. A JSON consumer parsing
	 * numbers as doubles — which is the common case — silently rounds anything larger, so
	 * an integer outside this range cannot survive the boundary as the value it was.
	 */
	static final long MAX_INTEROPERABLE_INTEGER = 9007199254740991L;

	private static final String ROOT = "metadata";

	private static final String ACCEPTED_TYPES = "Use a string, boolean, interoperable integer, finite number, "
			+ "array, or string-keyed object; keep live Java objects in JudgmentContext or another non-result surface.";

	private PortableValues() {
	}

	/**
	 * Validate and freeze a metadata map for storage on a {@link Judgment}.
	 * @param metadata the caller-supplied metadata
	 * @return an immutable, recursively frozen copy in encounter order
	 * @throws IllegalArgumentException if any value at any depth is not portable; the
	 * message names the exact path of the offending value
	 */
	static Map<String, Object> normalizeMetadata(Map<String, Object> metadata) {
		Map<String, Object> normalized = normalizeObject(metadata, ROOT);
		Object elapsedMillis = normalized.get(Judgment.ELAPSED_MILLIS_KEY);
		if (elapsedMillis != null) {
			requireElapsedMillis(elapsedMillis);
		}
		return normalized;
	}

	private static Object normalize(Object value, String path) {
		if (value instanceof String text) {
			requireWellFormedText(text, path);
			return text;
		}
		if (value instanceof Boolean flag) {
			return flag;
		}
		if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
			long integral = ((Number) value).longValue();
			if (integral > MAX_INTEROPERABLE_INTEGER || integral < -MAX_INTEROPERABLE_INTEGER) {
				throw new IllegalArgumentException(path + ": " + integral
						+ " is outside the interoperable integer range of +/-" + MAX_INTEROPERABLE_INTEGER
						+ ", so it cannot cross a JSON boundary as the value it is.");
			}
			return value;
		}
		if (value instanceof Double number) {
			if (!Double.isFinite(number)) {
				throw new IllegalArgumentException(
						path + ": " + number + " has no JSON representation. Numbers must be finite.");
			}
			return number;
		}
		if (value instanceof Float number) {
			if (!Float.isFinite(number)) {
				throw new IllegalArgumentException(
						path + ": " + number + " has no JSON representation. Numbers must be finite.");
			}
			return number;
		}
		if (value instanceof Map<?, ?> object) {
			return normalizeObject(object, path);
		}
		if (value instanceof Collection<?> elements) {
			return normalizeElements(elements, path);
		}
		if (value.getClass().isArray()) {
			return normalizeArray(value, path);
		}
		throw new IllegalArgumentException(
				path + ": " + typeName(value) + " is not a portable metadata value type. " + ACCEPTED_TYPES);
	}

	private static Map<String, Object> normalizeObject(Map<?, ?> object, String path) {
		Map<String, Object> copy = new LinkedHashMap<>();
		for (Map.Entry<?, ?> entry : object.entrySet()) {
			@Nullable
			Object key = entry.getKey();
			if (!(key instanceof String name)) {
				throw new IllegalArgumentException(path + ": metadata object keys must be strings, but found "
						+ typeName(key) + ". Project the key to its string form.");
			}
			String childPath = path + "." + name;
			@Nullable
			Object value = entry.getValue();
			if (value == null) {
				throw new IllegalArgumentException(childPath
						+ ": null is not a portable metadata value. Absence is expressed by omitting the key.");
			}
			copy.put(name, normalize(value, childPath));
		}
		return Collections.unmodifiableMap(copy);
	}

	private static List<Object> normalizeElements(Collection<?> elements, String path) {
		List<Object> copy = new ArrayList<>(elements.size());
		int index = 0;
		for (Object element : elements) {
			copy.add(normalizeElement(element, path, index));
			index++;
		}
		return Collections.unmodifiableList(copy);
	}

	private static List<Object> normalizeArray(Object array, String path) {
		int length = Array.getLength(array);
		List<Object> copy = new ArrayList<>(length);
		for (int index = 0; index < length; index++) {
			copy.add(normalizeElement(Array.get(array, index), path, index));
		}
		return Collections.unmodifiableList(copy);
	}

	private static Object normalizeElement(@Nullable Object element, String path, int index) {
		String childPath = path + "[" + index + "]";
		if (element == null) {
			throw new IllegalArgumentException(childPath
					+ ": null is not a portable metadata element. Omit the element rather than carrying a hole.");
		}
		return normalize(element, childPath);
	}

	/**
	 * Reject text that cannot be encoded, so a value is refused where it was supplied
	 * rather than where it is written. Java strings are UTF-16 sequences and may hold an
	 * unpaired surrogate, which has no UTF-8 encoding and no JSON representation.
	 */
	private static void requireWellFormedText(String text, String path) {
		for (int index = 0; index < text.length(); index++) {
			char current = text.charAt(index);
			if (Character.isHighSurrogate(current)) {
				if (index + 1 >= text.length() || !Character.isLowSurrogate(text.charAt(index + 1))) {
					throw unpairedSurrogate(path, index);
				}
				index++;
			}
			else if (Character.isLowSurrogate(current)) {
				throw unpairedSurrogate(path, index);
			}
		}
	}

	private static IllegalArgumentException unpairedSurrogate(String path, int index) {
		return new IllegalArgumentException(path + ": the value holds an unpaired surrogate at index " + index
				+ ", which is not encodable text.");
	}

	/**
	 * Result timing is a portable integer count of milliseconds, not a live time object.
	 * The unit lives in the key so the number is never ambiguous.
	 */
	private static void requireElapsedMillis(Object value) {
		String path = ROOT + "." + Judgment.ELAPSED_MILLIS_KEY;
		if (!(value instanceof Byte || value instanceof Short || value instanceof Integer
				|| value instanceof Long)) {
			throw new IllegalArgumentException(path
					+ ": elapsed result timing must be a non-negative interoperable integer count of milliseconds, but was "
					+ typeName(value) + ".");
		}
		long millis = ((Number) value).longValue();
		if (millis < 0) {
			throw new IllegalArgumentException(path
					+ ": elapsed result timing must be a non-negative interoperable integer count of milliseconds, but was "
					+ millis + ".");
		}
	}

	private static String typeName(@Nullable Object value) {
		return value == null ? "null" : value.getClass().getName();
	}

}
