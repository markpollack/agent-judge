/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.testing;

import java.util.*;
import io.github.markpollack.judge.judgment.Judgment;
import io.github.markpollack.judge.voting.*;
import io.github.markpollack.judge.verdict.*;

/**
 * Migrates existing test-vector inputs to typed ballots. No production compatibility API.
 */
public final class TestBallots {

	private TestBallots() {
	}

	public static List<Ballot> legacy(List<Judgment> judgments, Map<String, Double> weights) {
		if (judgments == null)
			return null;
		var result = new ArrayList<Ballot>();
		for (int i = 0; i < judgments.size(); i++)
			result.add(new Ballot(i, "Seat#" + (i + 1), judgments.get(i), judgments.get(i), Participation.NOT_RECORDED,
					weights == null ? null : weights.get(Integer.toString(i))));
		return List.copyOf(result);
	}

	public static Map<String, Double> weights(Verdict verdict) {
		var weights = new LinkedHashMap<String, Double>();
		for (var seat : verdict.seats())
			if (seat.declaredWeight() != null)
				weights.put(Integer.toString(seat.position()), seat.declaredWeight());
		return weights;
	}

	public static List<Seat> weighted(List<Seat> seats, Map<String, Double> weights) {
		return seats.stream()
			.map(seat -> weights.containsKey(Integer.toString(seat.position()))
					? seat.weighted(weights.get(Integer.toString(seat.position()))) : seat)
			.toList();
	}

}
