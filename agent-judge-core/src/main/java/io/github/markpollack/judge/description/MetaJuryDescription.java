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
 * @param strategy the strategy over member aggregates
 * @param members the members, in execution order
 * @author Mark Pollack
 * @since 0.17.0
 * @see MemberDescription
 */
public record MetaJuryDescription(StrategyDescription strategy, List<MemberDescription> members)
		implements JuryDescription {

	/** Validate and copy the members. */
	public MetaJuryDescription {
		Objects.requireNonNull(strategy, "strategy must not be null");
		members = List.copyOf(Objects.requireNonNull(members, "members must not be null"));
	}

	/**
	 * A member whose aggregate may be excluded exists, and the strategy is configured to honour
	 * an exclusion.
	 * @return true when the aggregate may be not applicable
	 */
	@Override
	public boolean aggregateMayBeNotApplicable() {
		return strategy.notApplicablePolicy() == NotApplicablePolicy.EXCLUDE
				&& members.stream().anyMatch(member -> member.jury().aggregateMayBeNotApplicable());
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
