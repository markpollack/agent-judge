/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.description.archfixture;

/**
 * Reflection that never names a {@code java.lang.reflect} type in its bytecode: it asks
 * {@code java.lang.Class} for members and uses only the array length. A package-dependency rule
 * alone does not see it, which is why the member-lookup rule exists.
 */
public final class ReflectiveMemberListing {

	private ReflectiveMemberListing() {
	}

	public static int declaredFieldCount(Object value) {
		return value.getClass().getDeclaredFields().length;
	}

}
