/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.description;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import io.github.markpollack.judge.JudgeType;

/**
 * One judge as configured: its metadata, the metadata of the judge it wraps, the class that
 * actually implements it, and the configuration it declared.
 *
 * <p>
 * Obtained from {@link io.github.markpollack.judge.Judges#describe(io.github.markpollack.judge.Judge)},
 * which looks through {@link io.github.markpollack.judge.NamedJudge} wrappers. The outer
 * metadata names the judge in a verdict; the delegate metadata is what the wrapped judge says
 * about itself, which can differ. {@code Juries.fromJudges} wraps a judge whose name collides
 * with an earlier one as {@code DETERMINISTIC}, whatever the judge really is, and the
 * delegate type is where the real type is still visible.
 * </p>
 *
 * <h2>Portable form</h2>
 * <pre>
 * {
 *   "descriptionVersion": 1,
 *   "metadata":         {"declared": true, "values": {"name": "...", "type": "LLM_POWERED"}},
 *   "delegateMetadata": {"declared": false},
 *   "implementation":   {"form": "NAMED", "className": "..."},
 *   "configuration":    {"declared": true, "values": {...}}
 * }
 * </pre>
 * <p>
 * A metadata node is declared when a name or a type is present, and its {@code values} hold
 * only those present. {@code "configuration": {"declared": false}} means the judge does not
 * implement {@link ConfiguredJudge}; {@code {"declared": true, "values": {}}} means it does and
 * declared nothing.
 * </p>
 *
 * @param name the judge's name from its outer metadata, or null when it declares none
 * @param type the judge's type from its outer metadata, or null when it declares none
 * @param delegateName the name declared by the judge the outer wrapper wraps directly, or
 * null when nothing is wrapped or that judge declares none
 * @param delegateType the type declared by the judge the outer wrapper wraps directly, or
 * null when nothing is wrapped or that judge declares none
 * @param implementation the class of the innermost judge that is not a wrapper
 * @param configuration the declared configuration, ordered by key; null when undeclared,
 * empty when declared empty
 * @author Mark Pollack
 * @since 0.17.0
 */
public record JudgeDescription(@Nullable String name, @Nullable JudgeType type, @Nullable String delegateName,
		@Nullable JudgeType delegateType, ImplementationIdentity implementation,
		@Nullable Map<String, Object> configuration) {

	/**
	 * Validate the implementation and freeze the configuration.
	 * @throws IllegalArgumentException if the configuration holds a non-portable value; the
	 * message names its path from {@code configuration}
	 */
	public JudgeDescription {
		Objects.requireNonNull(implementation, "implementation must not be null");
		if (configuration != null) {
			configuration = PortableForm.ordered(configuration, "configuration");
		}
	}

	/**
	 * The portable form described in the class documentation.
	 * @return an ordered, validated, immutable map
	 */
	public Map<String, Object> toPortable() {
		return PortableForm.freezeRoot(portableTree(), "judge");
	}

	Map<String, Object> portableTree() {
		Map<String, Object> tree = new LinkedHashMap<>();
		tree.put("metadata", metadataNode(name, type));
		tree.put("delegateMetadata", metadataNode(delegateName, delegateType));
		tree.put("implementation", implementation.portableTree());
		Map<String, Object> declared = configuration;
		tree.put("configuration", declared == null ? PortableForm.undeclared() : PortableForm.declaredValues(declared));
		return tree;
	}

	private static Map<String, Object> metadataNode(@Nullable String name, @Nullable JudgeType type) {
		if (name == null && type == null) {
			return PortableForm.undeclared();
		}
		Map<String, Object> values = new LinkedHashMap<>();
		if (name != null) {
			values.put("name", name);
		}
		if (type != null) {
			values.put("type", type.name());
		}
		return PortableForm.declaredValues(values);
	}

}
