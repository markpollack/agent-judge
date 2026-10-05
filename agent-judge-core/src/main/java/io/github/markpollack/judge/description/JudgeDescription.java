/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.description;
import io.github.markpollack.judge.portable.ImplementationIdentity;
import io.github.markpollack.judge.portable.PortableForm;

import io.github.markpollack.judge.*;
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
 * Obtained from {@link io.github.markpollack.judge.description.JudgeDescription#of(io.github.markpollack.judge.Judge)},
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
 *   "metadata":           {"declared": true, "values": {"name": "...", "type": "LLM_POWERED"}},
 *   "delegateMetadata":   {"declared": false},
 *   "notApplicableWhen":  {"declared": true, "value": "the repository contains no Java sources"},
 *   "implementation":     {"form": "NAMED", "className": "..."},
 *   "configuration":      {"declared": true, "values": {...}}
 * }
 * </pre>
 * <p>
 * {@code notApplicableWhen} is the <em>effective</em> declaration — the first one found walking
 * the wrapper chain outward in, which is the one a jury honours — rather than whatever the
 * outermost wrapper happens to hold. A reader can therefore tell from the description alone
 * whether this seat is permitted to leave the denominator.
 * </p>
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
 * @param notApplicableWhen the effective condition under which this judge may return
 * {@code NOT_APPLICABLE}, or null when it declares none
 * @param implementation the class of the innermost judge that is not a wrapper
 * @param configuration the declared configuration, ordered by key; null when undeclared,
 * empty when declared empty
 * @author Mark Pollack
 * @since 0.17.0
 */
public record JudgeDescription(@Nullable String name, @Nullable JudgeType type, @Nullable String delegateName,
		@Nullable JudgeType delegateType, @Nullable String notApplicableWhen, ImplementationIdentity implementation,
		@Nullable Map<String, Object> configuration) {

	/**
	 * Describe a judge as configured, looking through {@link NamedJudge} wrappers.
	 * <p>
	 * The description carries the outer metadata, which names the judge in a verdict, and
	 * the metadata of the judge the outer wrapper wraps directly, which can differ:
	 * {@code Juries.fromJudges} re-wraps a judge whose name collides as
	 * {@code DETERMINISTIC}, whatever its real type, and the wrapped judge's metadata
	 * still states that type. The implementation is the innermost judge that is not a
	 * {@code NamedJudge}, identified by {@link ImplementationIdentity#of(Class)}, so a
	 * lambda, including every combinator in this class, is described as {@code HIDDEN}
	 * with no class name. The configuration is that judge's
	 * {@link ConfiguredJudge#configuration()}, or undeclared when it does not implement
	 * {@link ConfiguredJudge}. The exclusion capability is the effective one from
	 * {@link Judges#notApplicableCapability(Judge)}, so the description says what a jury would
	 * actually honour rather than what the outermost wrapper happens to hold.
	 * </p>
	 * @param judge the judge to describe
	 * @return its description
	 * @throws IllegalArgumentException if the judge declares a configuration that is not
	 * portable, the message naming the judge and the path of the offending value; or if
	 * the judge, or the judge a {@code NamedJudge} wraps directly, is a
	 * {@link JudgeWithMetadata} whose {@code metadata()} returns null or throws, since
	 * describing it as undeclared would misstate it
	 *
	 * @since 0.17.0
	 */
	public static JudgeDescription of(Judge judge) {
		Objects.requireNonNull(judge, "judge must not be null");
		Judge innermost = judge;
		while (innermost instanceof NamedJudge named) {
			innermost = Objects.requireNonNull(named.delegate(), "a NamedJudge must wrap a judge");
		}
		ImplementationIdentity implementation = ImplementationIdentity.of(innermost.getClass());
		JudgeMetadata outer = Judges.readableMetadataOf(judge, "Judge implemented by " + implementation.toPortable());
		// The delegate metadata is what the directly wrapped judge declares. For the
		// NamedJudge
		// around a NamedJudge that Juries.fromJudges builds for a duplicate name, that is
		// the
		// caller's own label and type, not the innermost implementation's (usually none).
		JudgeMetadata inner = null;
		if (judge instanceof NamedJudge wrapper) {
			String outerLabel = (outer != null && outer.name() != null) ? "'" + outer.name() + "'"
					: "implemented by " + implementation.toPortable();
			inner = Judges.readableMetadataOf(wrapper.delegate(), "The judge wrapped by judge " + outerLabel);
		}
		Map<String, Object> configuration = null;
		if (innermost instanceof ConfiguredJudge configured) {
			configuration = configured.configuration();
			if (configuration == null) {
				throw new NullPointerException("ConfiguredJudge " + implementation.toPortable()
						+ " returned a null configuration; return an empty map to declare no values");
			}
		}
		try {
			return new JudgeDescription(outer == null ? null : outer.name(), outer == null ? null : outer.type(),
					inner == null ? null : inner.name(), inner == null ? null : inner.type(),
					Judges.notApplicableCapability(judge).orElse(null), implementation, configuration);
		}
		catch (IllegalArgumentException ex) {
			String label = (outer != null && outer.name() != null) ? "'" + outer.name() + "'"
					: "implemented by " + implementation.toPortable();
			throw new IllegalArgumentException(
					"Judge " + label + " declared a configuration that is not portable: " + ex.getMessage(), ex);
		}
	}

	/**
	 * Validate the implementation and freeze the configuration.
	 * @throws IllegalArgumentException if the configuration holds a non-portable value; the
	 * message names its path from {@code configuration}
	 */
	public JudgeDescription {
		Objects.requireNonNull(implementation, "implementation must not be null");
		if (notApplicableWhen != null && notApplicableWhen.isBlank()) {
			throw new IllegalArgumentException("notApplicableWhen must be non-blank when present");
		}
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
		String capability = notApplicableWhen;
		tree.put("notApplicableWhen",
				capability == null ? PortableForm.undeclared() : PortableForm.declaredString(capability));
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
