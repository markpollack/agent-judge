/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.description;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import io.github.markpollack.judge.result.Judgment;
import io.github.markpollack.judge.result.JudgmentStatus;

/**
 * The seam between description trees and the portable-value algebra.
 *
 * <p>
 * The algebra lives in the package-private {@code PortableValues} of the {@code result}
 * package, and {@link Judgment} is its only public entry: its constructor validates and
 * freezes {@code metadata}. A description tree is validated by carrying it as one metadata
 * entry of a throwaway judgment, so there is one set of rules and no second copy of them,
 * and {@code PortableValues} stays out of the public API.
 * </p>
 *
 * <p>
 * The algebra names a failing value by its path from {@code metadata}. The carrier's key is
 * the description's own root, so removing the {@code metadata.} prefix leaves the path
 * within the description.
 * </p>
 */
final class PortableForm {

	static final String DECLARED = "declared";

	static final String VALUES = "values";

	static final String VALUE = "value";

	static final String DESCRIPTION_VERSION = "descriptionVersion";

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
	static Map<String, Object> freezeRoot(Map<String, Object> tree, String root) {
		Map<String, Object> versioned = new LinkedHashMap<>();
		versioned.put(DESCRIPTION_VERSION, JuryDescription.DESCRIPTION_VERSION);
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
	static Map<String, Object> freeze(Map<String, Object> tree, String root) {
		Judgment carrier;
		try {
			carrier = new Judgment(JudgmentStatus.PASS, null, null, null, "", List.of(), Map.of(root, tree));
		}
		catch (IllegalArgumentException ex) {
			throw new IllegalArgumentException(relocate(String.valueOf(ex.getMessage())), ex);
		}
		return asObject(Objects.requireNonNull(carrier.metadata().get(root), root));
	}

	/**
	 * Validate caller-supplied values and return them frozen, with every map's keys in
	 * ascending order at every depth.
	 * <p>
	 * A caller's map type decides its iteration order, and {@code Map.of} iterates in a
	 * different order in each JVM. Ordering here keeps a description byte-stable across runs.
	 * </p>
	 * @param values the caller-supplied values
	 * @param root the name the values are reported under in a diagnostic
	 * @return the validated, ordered, recursively immutable values
	 */
	static Map<String, Object> ordered(Map<String, Object> values, String root) {
		return asObject(order(freeze(values, root)));
	}

	static Map<String, Object> undeclared() {
		Map<String, Object> node = new LinkedHashMap<>();
		node.put(DECLARED, false);
		return node;
	}

	static Map<String, Object> declaredValues(Map<String, Object> values) {
		Map<String, Object> node = new LinkedHashMap<>();
		node.put(DECLARED, true);
		node.put(VALUES, values);
		return node;
	}

	static Map<String, Object> declaredValue(Map<String, Object> value) {
		Map<String, Object> node = new LinkedHashMap<>();
		node.put(DECLARED, true);
		node.put(VALUE, value);
		return node;
	}

	static Map<String, Object> juryTree(JuryDescription description) {
		return switch (description) {
			case SimpleJuryDescription simple -> simple.portableTree();
			case CascadedJuryDescription cascaded -> cascaded.portableTree();
			case MetaJuryDescription meta -> meta.portableTree();
			case OpaqueJuryDescription opaque -> opaque.portableTree();
		};
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
