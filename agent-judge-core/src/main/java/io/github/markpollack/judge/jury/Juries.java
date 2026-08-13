/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury;

import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.JudgeType;
import io.github.markpollack.judge.Judges;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility class for jury composition and transformation.
 *
 * <p>
 * Provides factory methods and composition utilities for creating juries from judges,
 * combining multiple juries, and building meta-juries (juries of juries).
 * </p>
 *
 * <p>
 * Example usage:
 * </p>
 * Executable examples are maintained in the Agent Judge Tutorial: https://github.com/markpollack/agent-judge-tutorial.
 *
 * @author Mark Pollack
 * @since 0.1.0
 * @see Jury
 * @see SimpleJury
 * @see MetaJury
 */
public final class Juries {

	private Juries() {
		// Utility class - no instantiation
	}

	/**
	 * Create a jury from judges with automatic naming and unique identity preservation.
	 *
	 * <p>
	 * Judges without metadata are auto-named as "Judge#1", "Judge#2", etc. If duplicate
	 * names are detected, suffixes "-2", "-3", etc. are added deterministically to ensure
	 * uniqueness.
	 * </p>
	 * @param strategy the voting strategy
	 * @param judges the judges to include
	 * @return a simple jury with named judges
	 */
	public static Jury fromJudges(VotingStrategy strategy, Judge... judges) {
		if (judges == null || judges.length == 0) {
			throw new IllegalArgumentException("At least one judge is required");
		}

		SimpleJury.Builder builder = SimpleJury.builder().votingStrategy(strategy);

		Map<String, Integer> nameCount = new HashMap<>();

		for (int i = 0; i < judges.length; i++) {
			Judge judge = judges[i];
			String baseName = Judges.tryMetadata(judge).map(m -> m.name()).orElse("Judge#" + (i + 1));

			// Handle duplicate names with suffix
			String uniqueName = baseName;
			if (nameCount.containsKey(baseName)) {
				int count = nameCount.get(baseName) + 1;
				nameCount.put(baseName, count);
				uniqueName = baseName + "-" + count;
			}
			else {
				nameCount.put(baseName, 1);
			}

			// Wrap with unique name if needed
			if (!uniqueName.equals(baseName)) {
				judge = Judges.named(judge, uniqueName, null, JudgeType.DETERMINISTIC);
			}

			builder.judge(judge);
		}

		return builder.build();
	}

	/**
	 * Combine two juries into a meta-jury.
	 * @param first the first jury
	 * @param second the second jury
	 * @param metaStrategy the voting strategy for aggregating jury verdicts
	 * @return a meta-jury combining both juries
	 * @deprecated use {@link #meta(VotingStrategy, NamedJury...)} with explicit names
	 */
	@Deprecated(since = "0.14.0")
	public static Jury combine(Jury first, Jury second, VotingStrategy metaStrategy) {
		if (first == null || second == null) {
			throw new IllegalArgumentException("Both juries must be non-null");
		}
		return meta(metaStrategy, new NamedJury("member-1", first), new NamedJury("member-2", second));
	}

	/**
	 * Create a meta-jury from multiple juries.
	 * @param strategy the voting strategy for aggregating jury verdicts
	 * @param juries the juries to combine
	 * @return a meta-jury combining all juries
	 * @deprecated use {@link #meta(VotingStrategy, NamedJury...)} with explicit names
	 */
	@Deprecated(since = "0.14.0")
	public static Jury allOf(VotingStrategy strategy, Jury... juries) {
		if (juries == null || juries.length == 0) {
			throw new IllegalArgumentException("At least one jury is required");
		}
		NamedJury[] members = new NamedJury[juries.length];
		for (int index = 0; index < juries.length; index++) {
			members[index] = new NamedJury("member-" + (index + 1), juries[index]);
		}
		return meta(strategy, members);
	}

	/**
	 * Create a meta-jury from explicitly named members.
	 * @param strategy strategy that aggregates successful member aggregates
	 * @param members named members in execution order
	 * @return configured named meta-jury
	 */
	public static Jury meta(VotingStrategy strategy, NamedJury... members) {
		if (members == null || members.length == 0) {
			throw new IllegalArgumentException("At least one named jury is required");
		}
		return new MetaJury(List.of(members), strategy);
	}

}
