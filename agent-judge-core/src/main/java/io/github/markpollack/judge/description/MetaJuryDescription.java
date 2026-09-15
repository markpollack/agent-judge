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
 * A meta-jury as configured: the strategy that aggregates its members' verdicts, and its
 * named members in execution order.
 *
 * <p>
 * Compare each member here with the {@link io.github.markpollack.judge.jury.CompositeAttempt}
 * of the same name. A member that failed to execute is recorded as an attempt with a failure
 * and makes the meta-jury's aggregate a bare error judgment that carries no aggregation
 * evidence, so the member count must come from this description, not from the evidence.
 * </p>
 *
 * <h2>Portable form</h2>
 * <pre>
 * {"descriptionVersion": 2, "kind": "META", "aggregateMayBeNotApplicable": false,
 *  "strategy": {...}, "members": [{...}, ...]}
 * </pre>
 *
 * <h2>Why the capability is carried rather than derived</h2>
 * <p>
 * Exactly as in {@link SimpleJuryDescription}: the strategy's not-applicable policy is only
 * derivable from a description that declared it, and reading its absence as {@code REFUSE}
 * publishes a confident {@code false} for a meta-jury that can in fact return an exclusion. The
 * jury states what it is, and a declared policy is cross-checked against it.
 * </p>
 *
 * @param strategy the strategy over member aggregates
 * @param members the members, in execution order
 * @param aggregateMayBeNotApplicable whether this jury's aggregate may be
 * {@code NOT_APPLICABLE}, as the jury itself reports it
 * @author Mark Pollack
 * @since 0.17.0
 * @see MemberDescription
 */
public record MetaJuryDescription(StrategyDescription strategy, List<MemberDescription> members,
		boolean aggregateMayBeNotApplicable) implements JuryDescription {

	/**
	 * Validate and copy the members, and check the stated capability against what is derivable.
	 * @throws IllegalArgumentException if the strategy declared a not-applicable policy that
	 * contradicts the stated capability
	 */
	public MetaJuryDescription {
		Objects.requireNonNull(strategy, "strategy must not be null");
		members = List.copyOf(Objects.requireNonNull(members, "members must not be null"));
		NotApplicablePolicy declared = strategy.notApplicablePolicy();
		if (declared != null) {
			boolean derived = declared == NotApplicablePolicy.EXCLUDE
					&& members.stream().anyMatch(member -> member.jury().aggregateMayBeNotApplicable());
			if (derived != aggregateMayBeNotApplicable) {
				throw new IllegalArgumentException("strategy '" + strategy.name() + "' declares notApplicablePolicy "
						+ declared + " over " + members.size() + " member(s), from which aggregateMayBeNotApplicable "
						+ "is " + derived + "; the description states " + aggregateMayBeNotApplicable);
			}
		}
	}

	@Override
	public Map<String, Object> toPortable() {
		return PortableForm.freezeRoot(portableTree(), "jury");
	}

	Map<String, Object> portableTree() {
		List<Object> memberTrees = new ArrayList<>(members.size());
		for (MemberDescription member : members) {
			memberTrees.add(member.portableTree());
		}
		Map<String, Object> tree = new LinkedHashMap<>();
		tree.put("kind", "META");
		tree.put("aggregateMayBeNotApplicable", aggregateMayBeNotApplicable());
		tree.put("strategy", strategy.portableTree());
		tree.put("members", memberTrees);
		return tree;
	}

}
