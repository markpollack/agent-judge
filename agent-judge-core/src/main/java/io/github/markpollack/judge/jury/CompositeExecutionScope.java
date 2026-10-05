/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury;
import io.github.markpollack.judge.verdict.CompositeAttempt;
import io.github.markpollack.judge.verdict.CompositeLimitExceededException;
import io.github.markpollack.judge.verdict.CompositeRelation;
import io.github.markpollack.judge.verdict.Verdict;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/** Per-call execution budget shared by nested built-in composite juries. */
final class CompositeExecutionScope {

	static final int MAX_DEPTH = 8;

	static final int MAX_ATTEMPTS = 32;

	static final int MAX_ROSTER_ITEMS = 256;

	private static final ThreadLocal<CompositeExecutionScope> CURRENT = new ThreadLocal<>();

	private int depth;

	private int attemptCount;

	private CompositeExecutionScope() {
	}

	static Verdict withinCompositeVote(Supplier<Verdict> vote) {
		CompositeExecutionScope existing = CURRENT.get();
		if (existing != null) {
			return vote.get();
		}
		CompositeExecutionScope created = new CompositeExecutionScope();
		CURRENT.set(created);
		try {
			return vote.get();
		}
		finally {
			CURRENT.remove();
		}
	}

	/**
	 * Invoke a child jury within the parent's budget, and require it to have produced
	 * something.
	 * <p>
	 * The non-null check belongs <em>here</em>, inside the invocation, because this is
	 * the boundary the parent wraps in its failure handler. A child that returns nothing
	 * produced nothing, exactly like one that threw; checked one line later, in the
	 * parent, the resulting {@code NullPointerException} would be raised outside that
	 * handler and would take the whole parent down — a meta-jury losing the members that
	 * succeeded, a cascade never reaching the healthy final tier behind the broken one.
	 * </p>
	 * @param child the child's configured name, for the failure the parent records
	 * @param invocation the child's vote
	 * @return the child's verdict, never null
	 */
	static Verdict invokeChild(String child, Supplier<Verdict> invocation) {
		CompositeExecutionScope scope = CURRENT.get();
		if (scope == null) {
			throw new IllegalStateException("composite execution scope is not installed");
		}
		int destinationDepth = scope.depth + 1;
		if (destinationDepth > MAX_DEPTH) {
			throw new CompositeLimitExceededException("Composite depth limit of " + MAX_DEPTH + " exceeded");
		}
		if (scope.attemptCount >= MAX_ATTEMPTS) {
			throw new CompositeLimitExceededException("Composite attempt limit of " + MAX_ATTEMPTS + " exceeded");
		}
		scope.attemptCount++;
		int parentDepth = scope.depth;
		scope.depth = destinationDepth;
		try {
			Verdict verdict = invocation.get();
			if (verdict == null) {
				throw new IllegalStateException("Jury '" + child + "' returned no verdict");
			}
			return verdict;
		}
		finally {
			scope.depth = parentDepth;
		}
	}

}
