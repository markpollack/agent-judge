/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.portable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;


/**
 * Immutable description trees validated by the portable-value algebra.
 *
 * Values are validated directly by PortableValues and copied into immutable trees.
 */
public final class PortableForm {

	/** Declaration marker key. */
	public static final String DECLARED = "declared";

	/** Declared parameter map key. */
	/** Declared single value key. */
	public static final String VALUES = "values";

	/** Declared single value key. */
	public static final String VALUE = "value";

	/** Root description version key. */
	public static final String DESCRIPTION_VERSION = "descriptionVersion";

	private static final String CARRIER_PATH_PREFIX = "metadata.";

	private PortableForm() {
	}

	/**
	 * Validate and freeze a description that is returned as a root, with
	 * {@value #DESCRIPTION_VERSION} as its first key.
	 * @param tree the description tree
	 * @param root the name the tree is reported under in a diagnostic
	 * @return the versioned, validated, recursively immutable root
	 */
	public static Map<String, Object> freezeRoot(Map<String, Object> tree, String root) {
		Map<String, Object> versioned = new LinkedHashMap<>();
		versioned.put(DESCRIPTION_VERSION, 3);
		versioned.putAll(tree);
		return freeze(versioned, root);
	}

	/**
	 * Validate a tree against the portable-value algebra and return its frozen copy.
	 * @param tree the tree to validate
	 * @param root the name the tree is reported under in a diagnostic
	 * @return a recursively immutable copy in encounter order
	 * @throws IllegalArgumentException naming the path of the first non-portable value
	 */
	public static Map<String, Object> freeze(Map<String, Object> tree, String root) {
		return PortableValues.copy(tree, root);
	}

	/**
	 * Validate caller-supplied values and return them frozen, with every map's keys in
	 * ascending order at every depth.
	 * <p>
	 * A caller's map type decides its iteration order, and {@code Map.of} iterates in a
	 * different order in each JVM. Ordering here keeps a description byte-stable across
	 * runs.
	 * </p>
	 * @param values the caller-supplied values
	 * @param root the name the values are reported under in a diagnostic
	 * @return the validated, ordered, recursively immutable values
	 */
	public static Map<String, Object> ordered(Map<String, Object> values, String root) {
		return asObject(order(freeze(values, root)));
	}

	/** Create an explicit undeclared node.
	 * @return declaration node
	 */
	public static Map<String, Object> undeclared() {
		Map<String, Object> node = new LinkedHashMap<>();
		node.put(DECLARED, false);
		return node;
	}

	/** Create a declared parameter node.
	 * @param values declared value
	 * @return declaration node
	 */
	public static Map<String, Object> declaredValues(Map<String, Object> values) {
		Map<String, Object> node = new LinkedHashMap<>();
		node.put(DECLARED, true);
		node.put(VALUES, values);
		return node;
	}

	/** Create a declared text node.
	 * @param value declared value
	 * @return declaration node
	 */
	public static Map<String, Object> declaredString(String value) {
		Map<String, Object> node = new LinkedHashMap<>();
		node.put(DECLARED, true);
		node.put(VALUE, value);
		return node;
	}

	/** Create a declared nested node.
	 * @param value declared value
	 * @return declaration node
	 */
	public static Map<String, Object> declaredValue(Map<String, Object> value) {
		Map<String, Object> node = new LinkedHashMap<>();
		node.put(DECLARED, true);
		node.put(VALUE, value);
		return node;
	}


	private static String relocate(String message) {
		return message.startsWith(CARRIER_PATH_PREFIX) ? message.substring(CARRIER_PATH_PREFIX.length()) : message;
	}

	private static Object order(Object value) {
		if (value instanceof Map<?, ?> object) {
			Map<String, Object> sorted = new TreeMap<>();
			for (Map.Entry<?, ?> entry : object.entrySet()) {
				sorted.put((String) entry.getKey(), order(Objects.requireNonNull(entry.getValue())));
			}
			return Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
		}
		if (value instanceof List<?> elements) {
			List<Object> copy = new ArrayList<>(elements.size());
			for (Object element : elements) {
				copy.add(order(Objects.requireNonNull(element)));
			}
			return Collections.unmodifiableList(copy);
		}
		return value;
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> asObject(Object value) {
		return (Map<String, Object>) value;
	}

}
