/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.judgment;

import java.util.*;
import io.github.markpollack.judge.portable.PreservationLimitException;

/**
 * Bounded complete Judgment trees; aliases are permitted, active-path cycles are refused.
 */
public final class JudgmentBounds {

	/** Maximum nested complete refusals. */
	public static final int MAX_REFUSAL_DEPTH = 8;

	/** Maximum distinct retained Judgment nodes. */
	public static final int MAX_NODES = 256;

	private JudgmentBounds() {
	}

	/**
	 * Validate one complete original.
	 * @param value result tree
	 * @param refusalDepth enclosing refusal count
	 * @param original complete original to retain on failure
	 */
	public static void validate(Judgment value, int refusalDepth, Object original) {
		walk(value, refusalDepth, original, new IdentityHashMap<>(), new IdentityHashMap<>());
	}

	/**
	 * Validate all distinct Judgment nodes in one complete retained result.
	 * @param roots complete judgment roots, including refused children
	 * @param original complete original result
	 */
	public static void validateForest(List<Judgment> roots, Object original) {
		var active = new IdentityHashMap<Judgment, Boolean>();
		var seen = new IdentityHashMap<Judgment, Boolean>();
		for (var root : roots)
			walk(root, 0, original, active, seen);
	}

	private static void walk(Judgment value, int depth, Object original, IdentityHashMap<Judgment, Boolean> active,
			IdentityHashMap<Judgment, Boolean> seen) {
		if (depth > MAX_REFUSAL_DEPTH)
			throw new PreservationLimitException("Refusal depth exceeds " + MAX_REFUSAL_DEPTH, original);
		if (active.put(value, Boolean.TRUE) != null)
			throw new PreservationLimitException("Cyclic Judgment tree", original);
		if (seen.put(value, Boolean.TRUE) == null && seen.size() > MAX_NODES)
			throw new PreservationLimitException("Judgment tree exceeds " + MAX_NODES + " nodes", original);
		for (var check : value.checks())
			walk(check.judgment(), depth, original, active, seen);
		if (value.refusedReturn() != null)
			walk(value.refusedReturn().original(), depth + 1, original, active, seen);
		active.remove(value);
	}

}
