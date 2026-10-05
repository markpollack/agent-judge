/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.description;
import io.github.markpollack.judge.portable.PortableForm;
import io.github.markpollack.judge.voting.StrategyDescription;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.github.markpollack.judge.voting.ExclusionHandling;

/**
 * A meta-jury as configured: the strategy that aggregates its members' verdicts, and its
 * named members in execution order.
 *
 * <p>
 * Compare each member here with the
 * {@link io.github.markpollack.judge.verdict.CompositeAttempt} of the same name. A member
 * that failed to execute is recorded as an attempt with a failure and makes the
 * meta-jury's aggregate a bare error judgment that carries no aggregation evidence, so
 * the member count must come from this description, not from the evidence.
 * </p>
 *
 * <h2>Portable form</h2> <pre>
 * {"descriptionVersion": 3, "kind": "META", "aggregateMayBeNotApplicable": false,
 *  "strategy": {...}, "members": [{...}, ...]}
 * </pre>
 *
 * Applicability and routing-opinion bounds derive from the structured configuration.
 * Custom strategies must describe their exclusion policy explicitly.
 *
 * @param strategy the strategy over member aggregates
 * @param members the members, in execution order
 * @author Mark Pollack
 * @since 0.17.0
 * @see MemberDescription
 */
public record MetaJuryDescription(StrategyDescription strategy,
		List<MemberDescription> members) implements JuryDescription {

	/** Validates and freezes the configured structure before any execution. */
	public MetaJuryDescription {
		Objects.requireNonNull(strategy, "strategy must not be null");
		members = List.copyOf(Objects.requireNonNull(members, "members must not be null"));
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
		tree.put("routingOpinionBound", routingOpinionBound().name());
		tree.put("strategy", strategy.portableTree());
		tree.put("members", memberTrees);
		return tree;
	}

	@Override
	public OpinionBound routingOpinionBound() {
		return members.size() == 1 ? members.get(0).jury().routingOpinionBound() : OpinionBound.MAY;
	}

	@Override
	public boolean aggregateMayBeNotApplicable() {
		return (members.size() == 1 || strategy.exclusionHandling() == ExclusionHandling.EXCLUDE)
				&& members.stream().anyMatch(member -> member.jury().aggregateMayBeNotApplicable());
	}
}
