/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.description;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * Which class implements a judge, jury or strategy, stated only as far as the name is stable
 * across JVM runs.
 *
 * <p>
 * A runtime class name is not always an identity. A lambda's class is a hidden class whose
 * name carries {@code $$Lambda} and a memory address that differs in every run. An anonymous
 * class is named by its position, {@code Outer$1}, and becomes {@code Outer$2} when an
 * unrelated anonymous class is added above it. Recording either would make two runs of the
 * same configuration describe differently. {@link #of(Class)} therefore applies three rules:
 * </p>
 * <ul>
 * <li>a hidden class is {@link Form#HIDDEN}, with no class name at all;</li>
 * <li>an anonymous or local class, or a class nested inside one, records its form and its
 * enclosing top-level class;</li>
 * <li>every other class records its binary name.</li>
 * </ul>
 * <p>
 * The consequence is deliberate: two different lambdas are indistinguishable here. A judge
 * that needs to be told apart should carry metadata or be a named class.
 * </p>
 *
 * @param form how the implementing class is named
 * @param className the binary class name; present only for {@link Form#NAMED}
 * @param enclosingClassName the enclosing top-level class's binary name; present only for
 * {@link Form#ANONYMOUS} and {@link Form#LOCAL}
 * @author Mark Pollack
 * @since 0.17.0
 */
public record ImplementationIdentity(Form form, @Nullable String className, @Nullable String enclosingClassName) {

	/** Validate that the names present are exactly the ones the form permits. */
	public ImplementationIdentity {
		Objects.requireNonNull(form, "form must not be null");
		switch (form) {
			case NAMED -> {
				if (className == null || className.isBlank()) {
					throw new IllegalArgumentException("A NAMED implementation requires its binary class name");
				}
				if (enclosingClassName != null) {
					throw new IllegalArgumentException("A NAMED implementation records no enclosing class");
				}
			}
			case ANONYMOUS, LOCAL -> {
				if (className != null) {
					throw new IllegalArgumentException("An " + form
							+ " implementation records no class name: its binary name carries a positional ordinal");
				}
				if (enclosingClassName == null || enclosingClassName.isBlank()) {
					throw new IllegalArgumentException(
							"An " + form + " implementation requires its enclosing top-level class name");
				}
			}
			case HIDDEN -> {
				if (className != null || enclosingClassName != null) {
					throw new IllegalArgumentException(
							"A HIDDEN implementation records no class name: a hidden class has no stable name");
				}
			}
		}
	}

	/**
	 * Describe the class that implements something.
	 * @param type the implementing class
	 * @return its identity, naming it only as far as its name is stable
	 */
	public static ImplementationIdentity of(Class<?> type) {
		Objects.requireNonNull(type, "type must not be null");
		if (type.isHidden()) {
			return new ImplementationIdentity(Form.HIDDEN, null, null);
		}
		Form positional = null;
		Class<?> topLevel = type;
		for (Class<?> current = type; current != null; current = current.getEnclosingClass()) {
			if (positional == null) {
				if (current.isAnonymousClass()) {
					positional = Form.ANONYMOUS;
				}
				else if (current.isLocalClass()) {
					positional = Form.LOCAL;
				}
			}
			topLevel = current;
		}
		if (positional != null) {
			return new ImplementationIdentity(positional, null, topLevel.getName());
		}
		return new ImplementationIdentity(Form.NAMED, type.getName(), null);
	}

	/**
	 * The portable form: {@code form}, then {@code className} for a named class or
	 * {@code enclosingClassName} for an anonymous or local one. A hidden class carries only
	 * its form.
	 * @return an ordered, validated, immutable map
	 */
	public Map<String, Object> toPortable() {
		return PortableForm.freeze(portableTree(), "implementation");
	}

	Map<String, Object> portableTree() {
		Map<String, Object> tree = new LinkedHashMap<>();
		tree.put("form", form.wireName());
		switch (form) {
			case NAMED -> tree.put("className", Objects.requireNonNull(className));
			case ANONYMOUS, LOCAL -> tree.put("enclosingClassName", Objects.requireNonNull(enclosingClassName));
			case HIDDEN -> {
				// A hidden class has no stable name to record.
			}
		}
		return tree;
	}

	/**
	 * How an implementing class is named.
	 */
	public enum Form {

		/** A top-level or member class, recorded by its binary name. */
		NAMED("NAMED"),

		/** An anonymous class, recorded by its enclosing top-level class. */
		ANONYMOUS("ANONYMOUS"),

		/** A local class, recorded by its enclosing top-level class. */
		LOCAL("LOCAL"),

		/** A hidden class, such as a lambda. No class name is recorded. */
		HIDDEN("HIDDEN");

		private final String wireName;

		Form(String wireName) {
			this.wireName = wireName;
		}

		/**
		 * The stable token used in the portable form.
		 * <p>
		 * An explicit field rather than {@link #name()}, so renaming a constant cannot alter
		 * the published contract.
		 * </p>
		 * @return the wire token
		 */
		public String wireName() {
			return wireName;
		}

	}

}
