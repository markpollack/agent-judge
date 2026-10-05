package io.github.markpollack.judge.verdict;
import java.util.*;
/** Retained composition tree bounds shared with execution. */
public final class CompositeBounds {
 private CompositeBounds() {}
 /** Maximum entered composition depth. */
 public static final int MAX_DEPTH=8;
 /** Maximum non-roster attempts. */
 public static final int MAX_ATTEMPTS=32;
 /** Maximum retained roster items. */
 public static final int MAX_ROSTER_ITEMS=256;
 /** Validate retained sibling identities and total tree bounds.
  * @param rootAttempts complete direct children
  */
	public static void validateTree(List<CompositeAttempt> rootAttempts) {
		Deque<AttemptAtDepth> pending = new ArrayDeque<>();
		pushSiblings(pending, rootAttempts, 1);
		int count = 0;
		int rosterCount = 0;
		while (!pending.isEmpty()) {
			AttemptAtDepth current = pending.pop();
			if (current.depth() > MAX_DEPTH) {
				throw new CompositeLimitExceededException("Composite depth limit of " + MAX_DEPTH + " exceeded");
			}
			if (current.attempt().relation() == CompositeRelation.ROSTER_ITEM) {
				if (++rosterCount > MAX_ROSTER_ITEMS)
					throw new CompositeLimitExceededException(
							"Roster population limit of " + MAX_ROSTER_ITEMS + " exceeded");
			}
			else
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
