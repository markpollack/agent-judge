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
 * <h2>Why the capability is carried rather than derived</h2>
 * <p>
 * The bound is "a capable seat exists <em>and</em> the strategy honours an exclusion", and the
 * second half is only derivable from a description when the strategy declared its
 * not-applicable policy. A custom strategy may legitimately override
 * {@link io.github.markpollack.judge.jury.VotingStrategy#notApplicablePolicy()} and leave
 * {@code describe()} at its supported default, and a derivation would then read that absence as
 * {@code REFUSE} and publish {@code false} for a jury that will happily return an exclusion. A
 * confident {@code false} is worse than an absent value: absence says "not recorded" and a
 * reader can go and find out, whereas a false {@code false} is indistinguishable from a jury
 * that really cannot exclude — and the reason to publish the capability at all is so a reader
 * need not run the jury to learn it.
 * </p>
 * <p>
 * So the jury states what it is, and a description whose strategy <em>did</em> declare its
 * policy is cross-checked against the derivation, which makes a contradictory hand-built
 * description a construction error rather than a stored claim nobody can check.
 * </p>
 *
 * @param strategy the voting strategy
 * @param seats the seats, in position order
 * @param aggregateMayBeNotApplicable whether this jury's aggregate may be
 * {@code NOT_APPLICABLE}, as the jury itself reports it
 * @author Mark Pollack
 * @since 0.17.0
 * @see SeatDescription
 */
public record SimpleJuryDescription(StrategyDescription strategy, List<SeatDescription> seats,
		boolean aggregateMayBeNotApplicable) implements JuryDescription {

	/**
	 * Validate and copy the seats, and check the stated capability against what is derivable.
	 * @throws IllegalArgumentException if a seat's position is not its index in the list, or if
	 * the strategy declared a not-applicable policy that contradicts the stated capability
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
		NotApplicablePolicy declared = strategy.notApplicablePolicy();
		if (declared != null) {
			boolean derived = declared == NotApplicablePolicy.EXCLUDE
					&& seats.stream().anyMatch(seat -> seat.judge().notApplicableWhen() != null);
			if (derived != aggregateMayBeNotApplicable) {
				throw new IllegalArgumentException("strategy '" + strategy.name() + "' declares notApplicablePolicy "
						+ declared + " over " + seats.size() + " seat(s), from which aggregateMayBeNotApplicable is "
						+ derived + "; the description states " + aggregateMayBeNotApplicable);
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
		tree.put("strategy", strategy.portableTree());
		tree.put("seats", seatTrees);
		return tree;
	}

}
