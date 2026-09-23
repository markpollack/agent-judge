/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.result;

import org.jspecify.annotations.Nullable;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Product of independently present assessment components.
 *
 * @param proposition optional proposition
 * @param numeric optional raw numeric assessment
 * @param category optional category
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Assessment(@Nullable Proposition proposition, @Nullable NumericAssessment numeric,
		@Nullable Category category) {
	/** Validate and freeze this value. */
	public Assessment {
		if (proposition == null && numeric == null && category == null) {
			throw new IllegalArgumentException("assessment requires at least one component");
		}
	}

	boolean has(AssessmentTarget target) {
		return switch (target) {
			case PROPOSITION -> proposition != null;
			case NUMERIC -> numeric != null;
			case CATEGORY -> category != null;
		};
	}
}
