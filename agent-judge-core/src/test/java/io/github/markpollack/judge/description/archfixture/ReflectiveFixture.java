/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.description.archfixture;

import java.lang.reflect.Field;

/**
 * A deliberate violation, kept in test sources under the description package tree, that
 * proves the {@code java.lang.reflect} rule can fail. The main-source check never imports it.
 */
public final class ReflectiveFixture {

	private ReflectiveFixture() {
	}

	public static Object read(Field field, Object target) throws IllegalAccessException {
		return field.get(target);
	}

}
