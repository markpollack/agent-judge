/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury;

import io.github.markpollack.judge.result.PolicyBinding;

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
     * Configure an application policy on every leaf of a built-in jury. The original jury
     * is unchanged. Seats, order, weights, executor, exclusion declarations and tier
     * strategies are retained; voting still uses the real engine. Policy runs on each
     * leaf's producer facts before reduction/routing, not on a discarded aggregate.
     * Custom jury implementations require an explicit application-owned policy route.
     * @param <E> evidence type
     * @param jury built-in jury
     * @param policy application consequence
     * @return configured jury preserving its full structure
     * @throws IllegalArgumentException for an opaque or subclassed engine implementation
     */
    public static <E> Jury<E> withAcceptancePolicy(Jury<E> jury, PolicyBinding policy) {
        java.util.Objects.requireNonNull(jury, "jury");
        java.util.Objects.requireNonNull(policy, "policy");
        if (jury.getClass() == SimpleJury.class && jury instanceof SimpleJury<E> simple)
            return simple.withPolicy(policy);
        if (jury.getClass() == MetaJury.class && jury instanceof MetaJury<E> meta)
            return meta.withPolicy(policy);
        if (jury.getClass() == CascadedJury.class && jury instanceof CascadedJury<E> cascade)
            return cascade.withPolicy(policy);
        throw new IllegalArgumentException("Application policy composition requires a built-in Jury; opaque wrappers cannot bypass engine guards");
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
	 * @throws IllegalArgumentException if no judges are given, or a judge's metadata cannot be
	 * read because its {@code metadata()} returns null or throws; the message names the
	 * position
	 */
	public static <E> Jury<E> fromJudges(VotingStrategy strategy, Judge<E>... judges) {
		if (judges == null || judges.length == 0) {
			throw new IllegalArgumentException("At least one judge is required");
		}

		SimpleJury.Builder<E> builder = SimpleJury.<E>builder().votingStrategy(strategy);

		Map<String, Integer> nameCount = new HashMap<>();

		for (int i = 0; i < judges.length; i++) {
			Judge<E> judge = judges[i];
			// Names are needed now to break collisions, so unreadable metadata is a construction
			// error here rather than an ERROR seat at vote time.
			SimpleJury.SeatKey key = SimpleJury.SeatKey.of(judge, i);
			if (key.metadataFailure() != null) {
				throw new IllegalArgumentException(key.unreadableMetadata(), key.cause());
			}
			String baseName = key.verdictKey();

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

			// Wrap with unique name if needed, and record that the key was manufactured here
			if (!uniqueName.equals(baseName)) {
				builder.deduplicatedJudge(Judges.named(judge, uniqueName, null, JudgeType.DETERMINISTIC));
			}
			else {
				builder.judge(judge);
			}
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
	public static <E> Jury<E> combine(Jury<E> first, Jury<E> second, VotingStrategy metaStrategy) {
		if (first == null || second == null) {
			throw new IllegalArgumentException("Both juries must be non-null");
		}
		return meta(metaStrategy, new NamedJury<E>("member-1", first), new NamedJury<E>("member-2", second));
	}

	/**
	 * Create a meta-jury from multiple juries.
	 * @param strategy the voting strategy for aggregating jury verdicts
	 * @param juries the juries to combine
	 * @return a meta-jury combining all juries
	 * @deprecated use {@link #meta(VotingStrategy, NamedJury...)} with explicit names
	 */
	@Deprecated(since = "0.14.0")
	public static <E> Jury<E> allOf(VotingStrategy strategy, Jury<E>... juries) {
		if (juries == null || juries.length == 0) {
			throw new IllegalArgumentException("At least one jury is required");
		}
		List<NamedJury<E>> members = new java.util.ArrayList<>();
		for (int index = 0; index < juries.length; index++) {
			members.add(new NamedJury<E>("member-" + (index + 1), juries[index]));
		}
		return new MetaJury<E>(members, strategy);
	}

	/**
	 * Create a meta-jury from explicitly named members.
	 * @param strategy strategy that aggregates successful member aggregates
	 * @param members named members in execution order
	 * @return configured named meta-jury
	 */
	public static <E> Jury<E> meta(VotingStrategy strategy, NamedJury<E>... members) {
		if (members == null || members.length == 0) {
			throw new IllegalArgumentException("At least one named jury is required");
		}
		return new MetaJury<E>(List.of(members), strategy);
	}

}
