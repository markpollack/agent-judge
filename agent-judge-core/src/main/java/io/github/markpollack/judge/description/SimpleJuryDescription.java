/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.description;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.github.markpollack.judge.jury.ExclusionHandling;

/**
 * A {@link io.github.markpollack.judge.jury.SimpleJury} as configured: its strategy and
 * its seats in order.
 *
 * <p>
 * {@code seats().size()} is the declared cardinality. One valid returned seat is carried
 * unchanged without a strategy call or new aggregation evidence; otherwise the reduction
 * evidence reports its input count.
 * </p>
 *
 * <h2>Portable form</h2> <pre>
 * {"descriptionVersion": 3, "kind": "SIMPLE", "aggregateMayBeNotApplicable": false,
 *  "strategy": {...}, "seats": [{...}, ...]}
 * </pre>
 * <p>
 * Nested in a tier or member, the version is omitted; it belongs to the root.
 * </p>
 *
 * Applicability and routing-opinion bounds derive from the structured configuration.
 * Custom strategies must describe their exclusion policy explicitly.
 *
 * @param strategy the voting strategy
 * @param seats the seats, in position order
 * @author Mark Pollack
 * @since 0.17.0
 * @see SeatDescription
 */
public record SimpleJuryDescription(StrategyDescription strategy,
		List<SeatDescription> seats) implements JuryDescription {

	/** Validates and freezes the configured structure before any execution. */
	public SimpleJuryDescription {
		Objects.requireNonNull(strategy, "strategy must not be null");
		seats = List.copyOf(Objects.requireNonNull(seats, "seats must not be null"));
		for (int index = 0; index < seats.size(); index++) {
			if (seats.get(index).position() != index) {
				throw new IllegalArgumentException("seat at index " + index + " declares position "
						+ seats.get(index).position() + "; positions must match seat order");
			}
		}
	}

	@Override
	public Map<String, Object> toPortable() {
		return PortableForm.freezeRoot(portableTree(), "jury");
	}

	Map<String, Object> portableTree() {
		List<Object> seatTrees = new ArrayList<>(seats.size());
		for (SeatDescription seat : seats) {
			seatTrees.add(seat.portableTree());
		}
		Map<String, Object> tree = new LinkedHashMap<>();
		tree.put("kind", "SIMPLE");
		tree.put("aggregateMayBeNotApplicable", aggregateMayBeNotApplicable());
		tree.put("routingOpinionBound", routingOpinionBound().name());
		tree.put("strategy", strategy.portableTree());
		tree.put("seats", seatTrees);
		return tree;
	}

	@Override
	public OpinionBound routingOpinionBound() {
		return OpinionBound.MAY;
	}

	@Override
	public boolean aggregateMayBeNotApplicable() {
		return (seats.size() == 1 || strategy.exclusionHandling() == ExclusionHandling.EXCLUDE)
				&& seats.stream().anyMatch(seat -> seat.judge().notApplicableWhen() != null);
	}
}
