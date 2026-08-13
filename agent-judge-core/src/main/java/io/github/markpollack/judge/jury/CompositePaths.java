/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/**
 * Derives deterministic canonical paths for a composite verdict tree.
 * @since 0.14.0
 */
public final class CompositePaths {

	private CompositePaths() {
	}

	/**
	 * Flatten a verdict's attempts in preorder depth-first order, omitting the root.
	 * @param verdict composite or leaf verdict
	 * @return unmodifiable canonical path entries
	 */
	public static List<CompositePathEntry> flatten(Verdict verdict) {
		Objects.requireNonNull(verdict, "verdict must not be null");
		CompositeExecutionScope.validateTree(verdict.compositeAttempts());
		List<CompositePathEntry> entries = new ArrayList<>();
		Deque<PendingEntry> pending = new ArrayDeque<>();
		pushChildren(pending, "", verdict.compositeAttempts());
		while (!pending.isEmpty()) {
			PendingEntry current = pending.pop();
			entries.add(new CompositePathEntry(current.path(), current.attempt()));
			Verdict child = current.attempt().verdict();
			if (child != null) {
				pushChildren(pending, current.path(), child.compositeAttempts());
			}
		}
		return List.copyOf(entries);
	}

	/**
	 * Encode one configured name as an RFC 6901 path segment.
	 * @param segment configured name
	 * @return escaped segment
	 */
	public static String encodeSegment(String segment) {
		Objects.requireNonNull(segment, "segment must not be null");
		return segment.replace("~", "~0").replace("/", "~1");
	}

	/**
	 * Strictly decode one RFC 6901 path segment.
	 * @param encodedSegment encoded segment
	 * @return decoded configured name
	 * @throws IllegalArgumentException for any tilde escape other than {@code ~0} or {@code ~1}
	 */
	public static String decodeSegment(String encodedSegment) {
		Objects.requireNonNull(encodedSegment, "encodedSegment must not be null");
		StringBuilder decoded = new StringBuilder(encodedSegment.length());
		for (int index = 0; index < encodedSegment.length(); index++) {
			char current = encodedSegment.charAt(index);
			if (current != '~') {
				decoded.append(current);
				continue;
			}
			if (index + 1 >= encodedSegment.length()) {
				throw new IllegalArgumentException("Invalid RFC 6901 tilde escape");
			}
			char escaped = encodedSegment.charAt(++index);
			if (escaped == '0') {
				decoded.append('~');
			}
			else if (escaped == '1') {
				decoded.append('/');
			}
			else {
				throw new IllegalArgumentException("Invalid RFC 6901 tilde escape: ~" + escaped);
			}
		}
		return decoded.toString();
	}

	private static void pushChildren(Deque<PendingEntry> pending, String parent,
			List<CompositeAttempt> attempts) {
		for (int index = attempts.size() - 1; index >= 0; index--) {
			CompositeAttempt attempt = attempts.get(index);
			pending.push(new PendingEntry(parent + "/" + encodeSegment(attempt.name()), attempt));
		}
	}

	private record PendingEntry(String path, CompositeAttempt attempt) {
	}

}
