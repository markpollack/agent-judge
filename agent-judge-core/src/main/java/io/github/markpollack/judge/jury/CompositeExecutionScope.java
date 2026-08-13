/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury;

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

	static Verdict invokeChild(Supplier<Verdict> invocation) {
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
			return invocation.get();
		}
		finally {
			scope.depth = parentDepth;
		}
	}

	static void validateTree(List<CompositeAttempt> rootAttempts) {
		Deque<AttemptAtDepth> pending = new ArrayDeque<>();
		pushSiblings(pending, rootAttempts, 1);
		int count = 0;
		while (!pending.isEmpty()) {
			AttemptAtDepth current = pending.pop();
			if (current.depth() > MAX_DEPTH) {
				throw new CompositeLimitExceededException("Composite depth limit of " + MAX_DEPTH + " exceeded");
			}
			count++;
			if (count > MAX_ATTEMPTS) {
				throw new CompositeLimitExceededException("Composite attempt limit of " + MAX_ATTEMPTS + " exceeded");
			}
			Verdict child = current.attempt().verdict();
			if (child != null) {
				pushSiblings(pending, child.compositeAttempts(), current.depth() + 1);
			}
		}
	}

	private static void pushSiblings(Deque<AttemptAtDepth> pending, List<CompositeAttempt> attempts, int depth) {
		Set<String> names = new HashSet<>();
		for (CompositeAttempt attempt : attempts) {
			if (!names.add(attempt.name())) {
				throw new IllegalArgumentException("Duplicate composite attempt name: " + attempt.name());
			}
		}
		for (int index = attempts.size() - 1; index >= 0; index--) {
			pending.push(new AttemptAtDepth(attempts.get(index), depth));
		}
	}

	private record AttemptAtDepth(CompositeAttempt attempt, int depth) {
	}

}
