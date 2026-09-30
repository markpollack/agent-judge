/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.judgment;

import java.util.List;
import org.jspecify.annotations.Nullable;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A classification on an explicitly declared domain, in configured order.
 *
 * @param selected selected category, or null
 * @param alternatives unique nonblank declared alternatives
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CategoryFinding(@Nullable String selected, List<String> alternatives) {
	/** Validate and freeze this value. */
	public CategoryFinding {
		alternatives = ValueRequirements.domain(alternatives, "alternatives");
		if (selected != null && !alternatives.contains(selected)) {
			throw new IllegalArgumentException("selected category must belong to alternatives");
		}
	}
}
