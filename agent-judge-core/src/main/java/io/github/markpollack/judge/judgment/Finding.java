/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.judgment;

import org.jspecify.annotations.Nullable;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Optional structured determinations supporting a Judgment. Boolean, numeric and category
 * components may coexist: a producer can report a numeric measure and its category
 * without treating either as a separate Judgment. Absence of this record means no
 * structured finding.
 *
 * @param booleanFinding optional true, false or unresolved boolean determination
 * @param numeric optional raw numeric finding
 * @param category optional category
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Finding(@Nullable BooleanFinding booleanFinding, @Nullable NumericFinding numeric,
		@Nullable CategoryFinding category) {
	/** Validate and freeze this value. */
	public Finding {
		if (booleanFinding == null && numeric == null && category == null) {
			throw new IllegalArgumentException("finding requires at least one component");
		}
	}

	boolean has(FindingTarget target) {
		return switch (target) {
			case BOOLEAN -> booleanFinding != null;
			case NUMERIC -> numeric != null;
			case CATEGORY -> category != null;
		};
	}
}
