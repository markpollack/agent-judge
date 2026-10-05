/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.voting;

import java.util.*;
import io.github.markpollack.judge.judgment.Judgment;

/** Pure typed voting input conveniences, with no string-keyed weight map. */
public final class Ballots {

	private Ballots() {
	}

	/**
	 * Bind unweighted observed opinions in encounter order.
	 * @param judgments complete original opinions
	 * @return immutable ballots
	 */
	public static List<Ballot> of(List<Judgment> judgments) {
		if (judgments == null)
			throw new IllegalArgumentException("Cannot aggregate empty judgment list");
		List<Ballot> result = new ArrayList<>();
		for (int i = 0; i < judgments.size(); i++)
			result.add(new Ballot(i, "Seat#" + (i + 1), judgments.get(i), judgments.get(i), Participation.NOT_RECORDED,
					null));
		return List.copyOf(result);
	}

	/**
	 * Project treatment inputs without changing configured positions or originals.
	 * @param ballots complete typed input
	 * @return ordered treatment judgments
	 */
	public static List<Judgment> judgments(List<Ballot> ballots) {
		if (ballots == null)
			throw new IllegalArgumentException("Cannot aggregate empty judgment list");
		var positions = new HashSet<Integer>();
		for (var ballot : ballots)
			if (!positions.add(ballot.position()))
				throw new IllegalArgumentException("Duplicate ballot position");
		return ballots.stream().map(Ballot::treatment).toList();
	}

}
