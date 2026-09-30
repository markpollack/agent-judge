/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.judgment;

import org.jspecify.annotations.Nullable;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A true, false or unresolved determination supporting a Judgment.
 *
 * @param value selected boolean value, or null when unresolved
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BooleanFinding(@Nullable Boolean value) {
}
