/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.requirement;

import java.util.List;
import java.util.HashSet;

/**
 * Pure specification requiring every declared constituent. No evaluator or policy belongs
 * here. Constituents have unique IDs within this parent. Revision and complete
 * specification remain associated.
 *
 * @param constituents complete ordered required roster
 * @param applicable explicit applicability of the parent itself for the described case
 */
public record AllOf(List<Requirement<?>> constituents, boolean applicable) {
	/** Require a nonempty, unambiguous immutable roster. */
	public AllOf {
		constituents = List.copyOf(constituents);
		if (constituents.isEmpty())
			throw new IllegalArgumentException("All-of requires constituents");
		var ids = new HashSet<String>();
		for (var child : constituents)
			if (!ids.add(child.id()))
				throw new IllegalArgumentException("Ambiguous constituent ID: " + child.id());
	}

	/**
	 * An applicable all-of specification.
	 * @param constituents required roster
	 */
	public AllOf(List<Requirement<?>> constituents) {
		this(constituents, true);
	}
}
