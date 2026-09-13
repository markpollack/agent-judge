/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.description;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * One named member of a meta-jury built by
 * {@link io.github.markpollack.judge.jury.Juries#meta(io.github.markpollack.judge.jury.VotingStrategy, io.github.markpollack.judge.jury.NamedJury...)}.
 *
 * <p>
 * The name is the one the member's {@link io.github.markpollack.judge.jury.CompositeAttempt}
 * carries.
 * </p>
 *
 * <h2>Portable form</h2>
 * <pre>
 * {"name": "style", "jury": {...}}
 * </pre>
 *
 * @param name the member name
 * @param jury the member jury
 * @author Mark Pollack
 * @since 0.17.0
 */
public record MemberDescription(String name, JuryDescription jury) {

	/** Validate that every component is present. */
	public MemberDescription {
		Objects.requireNonNull(name, "name must not be null");
		Objects.requireNonNull(jury, "jury must not be null");
	}

	Map<String, Object> portableTree() {
		Map<String, Object> tree = new LinkedHashMap<>();
		tree.put("name", name);
		tree.put("jury", PortableForm.juryTree(jury));
		return tree;
	}

}
