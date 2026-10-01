/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.ai.requirements;

import io.github.markpollack.judge.requirement.Requirement;

/** Input association is rendered by the producer, never reconstructed by the model. */
final class RequirementPrompts {

	private RequirementPrompts() {
	}

	static String header(Requirement<?> actual) {
		return "Configured requirement: " + actual.id() + "; revision: " + actual.revision() + "; source: "
				+ actual.source().artifact().id() + "; sha256: " + actual.source().artifact().sha256() + "\n";
	}

}
