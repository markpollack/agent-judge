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

/**
 * A {@link io.github.markpollack.judge.jury.CascadedJury} as configured: its tiers in
 * evaluation order.
 *
 * <p>
 * ⚠️ <b>Count a cascade per tier, never also by its aggregate.</b> A cascade's verdict copies
 * {@code aggregated}, {@code individual} and {@code weights} from the tier that stopped it.
 * Compare each tier here with the {@link io.github.markpollack.judge.jury.CompositeAttempt}
 * of the same name in {@code Verdict.compositeAttempts()}. Counting the top-level verdict as
 * well counts the stopping tier twice. A tier that was never entered appears here and has no
 * attempt, which is how an early stop shows.
 * </p>
 *
 * <h2>Portable form</h2>
 * <pre>
 * {"descriptionVersion": 2, "kind": "CASCADED", "aggregateMayBeNotApplicable": false,
 *  "tiers": [{...}, ...]}
 * </pre>
 *
 * @param tiers the tiers, in evaluation order
 * @author Mark Pollack
 * @since 0.17.0
 * @see TierDescription
 */
public record CascadedJuryDescription(List<TierDescription> tiers) implements JuryDescription {

	/** Validate and copy the tiers. */
	public CascadedJuryDescription {
		tiers = List.copyOf(Objects.requireNonNull(tiers, "tiers must not be null"));
	}

	/**
	 * Some tier's aggregate may be excluded.
	 * <p>
	 * A cascade has no strategy of its own: it adopts a tier's verdict, so its bound is the
	 * union of its tiers'. A cascade that stops on an individual rejection builds a machinery
	 * error rather than an exclusion, so that path never widens this bound.
	 * </p>
	 * @return true when the aggregate may be not applicable
	 */
	@Override
	public boolean aggregateMayBeNotApplicable() {
		return tiers.stream().anyMatch(tier -> tier.jury().aggregateMayBeNotApplicable());
	}

	@Override
	public Map<String, Object> toPortable() {
		return PortableForm.freezeRoot(portableTree(), "jury");
	}

	Map<String, Object> portableTree() {
		List<Object> tierTrees = new ArrayList<>(tiers.size());
		for (TierDescription tier : tiers) {
			tierTrees.add(tier.portableTree());
		}
		Map<String, Object> tree = new LinkedHashMap<>();
		tree.put("kind", "CASCADED");
		tree.put("aggregateMayBeNotApplicable", aggregateMayBeNotApplicable());
		tree.put("tiers", tierTrees);
		return tree;
	}

}
