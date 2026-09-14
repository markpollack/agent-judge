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

import io.github.markpollack.judge.jury.NotApplicablePolicy;

/**
 * A {@link io.github.markpollack.judge.jury.SimpleJury} as configured: its strategy and its
 * seats in order.
 *
 * <p>
 * {@code seats().size()} is the number of judgments the jury submits to its strategy, which
 * the aggregation evidence reports as {@code inputCount}.
 * </p>
 *
 * <h2>Portable form</h2>
 * <pre>
 * {"descriptionVersion": 2, "kind": "SIMPLE", "aggregateMayBeNotApplicable": false,
 *  "strategy": {...}, "seats": [{...}, ...]}
 * </pre>
 * <p>
 * Nested in a tier or member, the version is omitted; it belongs to the root.
 * </p>
 *
 * @param strategy the voting strategy
 * @param seats the seats, in position order
 * @author Mark Pollack
 * @since 0.17.0
 * @see SeatDescription
 */
public record SimpleJuryDescription(StrategyDescription strategy, List<SeatDescription> seats)
		implements JuryDescription {

	/**
	 * Validate and copy the seats.
	 * @throws IllegalArgumentException if a seat's position is not its index in the list
	 */
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

	/**
	 * A capable seat exists and the strategy is configured to honour an exclusion.
	 * <p>
	 * Both halves are needed. A capable seat under a strategy that refuses exclusions produces
	 * an error rather than an excluded aggregate, and a strategy that would honour one has
	 * nothing to honour when no seat may exclude.
	 * </p>
	 * @return true when the aggregate may be not applicable
	 */
	@Override
	public boolean aggregateMayBeNotApplicable() {
		return strategy.notApplicablePolicy() == NotApplicablePolicy.EXCLUDE
				&& seats.stream().anyMatch(seat -> seat.judge().notApplicableWhen() != null);
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
		tree.put("strategy", strategy.portableTree());
		tree.put("seats", seatTrees);
		return tree;
	}

}
