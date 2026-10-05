/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.verdict;

import io.github.markpollack.judge.provenance.Invocation;

import java.util.*;
import io.github.markpollack.judge.judgment.Judgment;

/**
 * Resolves invocation ownership across one complete retained result without execution.
 */
public final class InvocationRecords {

	private InvocationRecords() {
	}

	/**
	 * Validates references and returns each observed invocation once.
	 * @param root complete retained record, including shared owners
	 * @return ordered unique observations
	 */
	public static List<Invocation> of(Verdict root) {
		Map<String, Invocation> facts = new LinkedHashMap<>();
		Set<String> references = new LinkedHashSet<>();
		collect(root, facts, references, new IdentityHashMap<>());
		if (!facts.keySet().containsAll(references)) {
			references.removeAll(facts.keySet());
			throw new IllegalArgumentException("Unresolved native invocation references: " + references);
		}
		return List.copyOf(facts.values());
	}

	private static void collect(Verdict node, Map<String, Invocation> facts, Set<String> references,
			IdentityHashMap<Object, Boolean> seen) {
		if (seen.put(node, Boolean.TRUE) != null)
			return;
		node.invocations().forEach(value -> add(value, facts));
		collect(node.judgment(), facts, references, seen);
		node.individual().forEach(value -> collect(value, facts, references, seen));
		node.seats().forEach(seat -> {
			if (seat.rejection() != null)
				collect(seat.rejection(), facts, references, seen);
		});
		node.compositeAttempts().forEach(attempt -> {
			if (attempt.verdict() != null)
				collect(attempt.verdict(), facts, references, seen);
		});
	}

	private static void collect(Judgment value, Map<String, Invocation> facts, Set<String> references,
			IdentityHashMap<Object, Boolean> seen) {
		if (seen.put(value, Boolean.TRUE) != null)
			return;
		value.invocations().forEach(invocation -> add(invocation, facts));
		references.addAll(value.invocationIds());
		value.checks().forEach(check -> collect(check.judgment(), facts, references, seen));
		if (value.refusedReturn() != null)
			collect(value.refusedReturn().original(), facts, references, seen);
	}

	private static void add(Invocation value, Map<String, Invocation> facts) {
		var previous = facts.putIfAbsent(value.id(), value);
		if (previous != null && !previous.equals(value))
			throw new IllegalArgumentException("Conflicting native invocation identity: " + value.id());
	}

}
