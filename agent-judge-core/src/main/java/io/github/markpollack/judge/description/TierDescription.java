/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.description;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import io.github.markpollack.judge.jury.TierPolicy;

/**
 * One tier of a {@link io.github.markpollack.judge.jury.CascadedJury}.
 *
 * <p>
 * The name is the same one a {@link io.github.markpollack.judge.jury.CompositeAttempt}
 * carries, so a tier joins its attempt by name.
 * </p>
 *
 * <h2>Portable form</h2>
 * <pre>
 * {"name": "fast", "policy": "REJECT_ON_ANY_FAIL", "jury": {...}}
 * </pre>
 *
 * @param name the tier name
 * @param policy the tier's stop-or-escalate policy
 * @param jury the tier's jury
 * @author Mark Pollack
 * @since 0.17.0
 */
public record TierDescription(String name, TierPolicy policy, JuryDescription jury) {

	/** Validate that every component is present. */
	public TierDescription {
		Objects.requireNonNull(name, "name must not be null");
		Objects.requireNonNull(policy, "policy must not be null");
		Objects.requireNonNull(jury, "jury must not be null");
	}

	Map<String, Object> portableTree() {
		Map<String, Object> tree = new LinkedHashMap<>();
		tree.put("name", name);
		tree.put("policy", policy.wireName());
		tree.put("jury", PortableForm.juryTree(jury));
		return tree;
	}

}
